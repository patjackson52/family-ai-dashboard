# Auth S4 — Owner-Approved Invites + multi-family credential fix (design)

**Date:** 2026-06-19 · **Branch:** `auth-s4` · **ADR:** 0021 (S4 slice) + ADR 0011 §5/§11.
Implements `05-invite.md` + `auth-and-family-design §Flow 3a`, on the AUTH-S1
backbone (credentials, memberships, `authorizeTenant`, dev-token) + the S3
owner-gate pattern.

## Goal & scope

Owner-approved family invites (no auto-join) **and** the family-agnostic
credential fix that lets a user belong to >1 family (clears the documented S1
two-family debt). Backend-only.

**In scope:** `invites` table + `memberships.invite_id`; the credential-model
change (app creds family-agnostic); endpoints mint / redeem / approve / decline /
revoke-invite / remove-member / list; security controls; the multi-family test
that un-skips the S1 limit.

**Out of scope (deferred):** QR rendering, the approval-queue / invitee-identity-
display / "waiting for approval" **screens** (S6, design-gated, needs A8b);
Universal/App-Link domain (`OQ-deeplink-domain`); teen role (ADR 0005); push
notifications (the pending queue is the source of truth); the expiry **sweep**
mechanism (same deferred-sweep follow as S3 m-2 — the redeem-time guard still
blocks stale invites); Firebase identity (S2); E2EE key-handoff (M1).

## Decisions (brainstorm)

- **Credential model [clears S1 debt]:** `kind='app'` (interactive) credentials
  are **family-agnostic** — `family_scope = NULL`. Access is governed purely by
  per-request `membership(user=sub, family=path-fid)` in `authorizeTenant` (its
  `cred.family_scope && cred.family_scope !== fid` check short-circuits when null,
  so a null-scope app cred is allowed on any family the user is an active member
  of). **Drop the `POST /families` binding UPDATE**; mint app creds null-scope
  (dev-token + the `/families` flow + future Firebase session). **CLI (`kind='cli'`)
  creds stay family-scoped** (one device = one family) — satisfies the existing
  `CHECK (kind <> 'cli' OR family_scope IS NOT NULL)`. No data migration (S1/S3 are
  not deployed → no live app creds bound).
- **Invitee authenticates via access-JWT** (dev-token now, Firebase S2) — invite ≠
  identity. **Owner-only** mint/approve/decline/revoke/remove. **Role allowlist
  `{adult}`** (teen deferred; **never `owner`** via invite — ownership is
  transfer-only, ADR 0011 §11).
- **Backend-only;** all UI → S6.

## Data model (`apps/api/migrations/0005_invites.sql`)

```sql
CREATE TABLE invites (
  id          text PRIMARY KEY,
  family_id   text NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  role        text NOT NULL DEFAULT 'adult' CHECK (role IN ('adult')),  -- teen later; never owner
  token_hash  text NOT NULL UNIQUE,            -- SHA-256 of a ≥128-bit CSPRNG token
  mode        text NOT NULL CHECK (mode IN ('qr','link')),
  max_uses    int  NOT NULL DEFAULT 1 CHECK (max_uses >= 1),
  used_count  int  NOT NULL DEFAULT 0,
  status      text NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked','exhausted','expired')),
  created_by  text NOT NULL REFERENCES users(id),
  created_at  timestamptz NOT NULL DEFAULT now(),
  expires_at  timestamptz NOT NULL
);
CREATE INDEX ON invites (family_id, status);

ALTER TABLE memberships ADD COLUMN invite_id text REFERENCES invites(id);  -- approval provenance
```
`token_hash UNIQUE` is the redeem lookup key. Enums as CHECK. (memberships PK
`(user_id, family_id)` already exists from 0002 — the redeem INSERT relies on it.)

## Credential change (S1 core, `apps/api/src/app.ts` + `identity.ts`)
- `POST /families`: **remove** `UPDATE credentials SET family_scope=$1 WHERE
  user_id=$2 AND family_scope IS NULL`. App creds stay null-scope.
- `identity.ts mintCredentialFor(userId)` and the dev-token credential INSERT:
  mint `kind='app'` with **`family_scope = NULL`** (drop the family arg / column).
- `authorizeTenant` unchanged — already allows null-scope app creds (membership is
  the gate). Verify with the multi-family test.

## Endpoints (owner ops via a generalized `ownerGate` = `authorizeTenant` + `role==='owner'` + `cred.kind==='app'`)

### `POST /families/:fid/invites` — owner mint
ownerGate. Per-owner mint rate-limit (`owner:mint:<sub>`, reuse S3 helpers) +
**pending-cap** (reject if the family already has ≥N pending memberships or ≥M
active invites). Generate token = `randomBytes(32).base64url` (≥128-bit; reject in
code if a supplied value is short — never accept a client token). Body
`{mode, role?, max_uses?}`: `role` allowlist `{adult}` (default adult; reject
`owner`/`teen`); `mode='qr'`→`max_uses=1`, TTL 15 min; `mode='link'`→`max_uses` as
given (cap, default 1), TTL 72 h. Insert `active`, store `token_hash` only. Return
`201 {invite_id, token, url, role, mode, expires_at}` — **raw token ONCE**,
**response not gzipped** (BREACH; raw token is a one-time secret). `url =
"<verification_uri>/invite/" + token` (the deep-link domain is `OQ-deeplink-domain`;
S4 uses a documented constant). Audit `invite.mint`.

### `POST /invites:redeem` — authenticated invitee
Authenticate the invitee's access-JWT (their own identity). **Per-account
rate-limit + lockout** (`account:redeem:<sub>`, S3 helpers) before lookup. Body
`{token}` (never the URL path on the server). One transaction:
```sql
-- INSERT-first: a use is consumed ONLY on a net-new pending membership.
WITH inv AS (
  SELECT id, family_id, role FROM invites
   WHERE token_hash=$1 AND status='active' AND used_count<max_uses AND expires_at>now()
   FOR UPDATE
)
INSERT INTO memberships(user_id, family_id, role, status, invite_id)
SELECT $sub, inv.family_id, inv.role, 'pending', inv.id FROM inv
ON CONFLICT (user_id, family_id) DO NOTHING
RETURNING family_id, role;
```
- 0 invite rows (not found/expired/exhausted/revoked) → **uniform 404** + count the
  redeem failure.
- net-new membership (RETURNING a row) → **atomically bump** the invite:
  `UPDATE invites SET used_count=used_count+1, status = CASE WHEN used_count+1 >= max_uses THEN 'exhausted' ELSE status END WHERE id=$invid` → `200 {family_id, role, status:'pending'}`.
- conflict (no row inserted, **no use consumed**) → `SELECT` the existing
  membership status → `pending`→`200` idempotent · `active`→`409` (route in) ·
  `removed`→`409` ("ask owner", no resurrect).
- `status='pending'` and `role` are **server-set** (mass-assignment guard).
Audit `invite.redeem`.

### `POST /families/:fid/members/:uid:approve` — owner
ownerGate. Transaction: `SELECT … FOR UPDATE` the `(uid, fid)` membership; assert
`status='pending'` (else: active→200 idempotent no-op; removed→409); re-resolve
approver is an **active owner** (ownerGate already did, re-assert in-txn);
re-check role against the allowlist → set `status='active'`, `joined_at=now()`.
Audit `invite.approve` (approver, invitee uid, invite_id, family).

### `POST /families/:fid/members/:uid:decline` — owner
ownerGate. pending→`removed` (204). not-pending→404/409. Audit `invite.decline`.

### `DELETE /families/:fid/invites/:id` — owner
ownerGate. `active`→`revoked` (sticky). Audit `invite.revoke`.

### `DELETE /families/:fid/members/:uid` — owner, **≥1-owner guarded**
ownerGate. Removing an **owner** is rejected if it would leave **0 active owners**
(`SELECT count(*) … role='owner' AND status='active' FOR UPDATE`); → `removed`.
**Not retroactive** (revokes future access only; ADR 0011). Audit `member.remove`.

### `GET /families/:fid/invites` + pending members — owner
ownerGate. Returns outstanding `active` invites (no token/hash) + `pending`
memberships (uid, role, invite_id, requested_at) — the approval **queue**, the
source of truth (no push dependency).

## Security (ADR 0011 + 05-invite — all load-bearing)
- **Owner-only** mint/approve/decline/revoke/remove (role + kind gate, re-resolved
  per request; legacy household token has `role=null` → 403, S3 pattern).
- **Entropy is the control:** ≥128-bit (32-byte) CSPRNG token; SHA-256 at rest;
  reject short tokens in code.
- **Atomic INSERT-first claim** → no double-redeem race, a no-op conflict never
  burns a `max_uses` slot.
- **Uniform 404** on bad/expired/exhausted/revoked → no invite enumeration.
- **Leaked/forwarded token yields only a `pending` membership** → zero family data
  until the owner approves (the reason auto-join was removed).
- **Never `owner` via invite**; ≥1-owner invariant on remove/decline.
- **Redeem rate-limit + lockout** per-account (authed) — stops a signed-in user
  brute-forcing tokens behind the uniform 404.
- **Pending caps** (per family + per invite) + mint rate-limit per owner → a
  forwarded link-mode invite can't flood the approval queue into a rubber-stamp.
- Mass-assignment guard: `status`/`role` server-set, `family_id` from path/invite.

## Forward-compat
- **Firebase (S2):** the invitee/approver authenticate via access-JWT exactly as
  today; at S2 those JWTs come from Firebase verify instead of dev-token — no S4
  change. Approval needs the invitee's display identity (name/email) — available
  from `user_identities` (dev provider now, Firebase later); the **display** is S6.
- **E2EE (M1):** approval is the natural family-key handoff point (wrap the FCK to
  the joiner's pubkey at approve) — note only, no S4 code.
- **S6 UI:** every endpoint returns the data the screens need (queue, statuses,
  invitee identity); QR/Universal-Link rendering is S6.

## Testing (vitest vs live PG)
- **Cred fix / multi-family:** a user creates family A AND joins family B (invite
  → approve); ONE app token works on both; cross-family still 404 — **un-skip the
  S1 two-family test.**
- **Mint:** owner-only (non-owner 403, kind='cli' 403); token ≥128-bit + hashed
  (raw returned once, only hash stored); role allowlist rejects `owner`/`teen`;
  qr→max_uses=1/15m, link→max_uses/72h; per-owner rate-limit; pending-cap reject.
- **Redeem:** net-new→pending + used_count bumped (exhaust on last); **double
  redeem of a max_uses=1 invite by two users → one pending + one 404, never 2
  uses**; same-user re-redeem→200 idempotent (no extra use); active→409; removed→409;
  uniform 404 (not-found/expired/revoked/exhausted); role from invite not body;
  per-account lockout after N.
- **Approve:** owner-only; pending→active+joined_at; re-approve active→200; removed
  →409; role re-checked; concurrent approve (FOR UPDATE) single-activates.
- **Decline/revoke/remove:** decline→removed; revoke→then redeem 404; remove member;
  **≥1-owner: removing/declining the last owner → rejected.**
- Whole API suite + S1/S3 regression green.

## Definition of Done
`invites` migrated + `memberships.invite_id`; app creds family-agnostic (S1 binding
removed) with the two-family test un-skipped + green; mint/redeem/approve/decline/
revoke/remove/list endpoints implemented + fully tested (state machine, atomic
claim, uniform 404, owner+kind gate, ≥1-owner, rate-limit/lockout, pending caps);
whole suite + household regression green; Vercel bundle regenerated. UI, QR,
deep-link domain, teen, sweep, Firebase, E2EE handoff explicitly deferred.
