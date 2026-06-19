# Auth S1 — Tenancy & Token Backbone (design)

**Date:** 2026-06-19 · **Branch:** `auth-s1` · **Governs:** the first slice of the
ADR 0011 auth epic. **ADR:** 0021 (Proposed — auth pulled into active build +
S1–S6 decomposition; **must be Accepted before build**). Implements the S1 subset
of `specs/prototype/04-auth.md` + `specs/auth-and-family-design.md`.

## Goal & scope

Real, dynamically-issued, revocable tokens + per-request multi-tenant
authorization + a multitenancy/IDOR test matrix — **backend-only, Firebase-
stubbed, non-breaking**. After S1 the platform can mint/verify its own tokens,
enforce tenancy per request, and prove isolation in tests; the existing household
token keeps working unchanged.

**In scope:** the token service (mint/verify/refresh), the one mandatory
auth+authz middleware, the tenancy data model (users/identities/memberships/
credentials/refresh_tokens), `POST /families` (create + owner membership), the
pluggable identity boundary (stub + gated dev-token), JWKS, and the test matrix.

**Out of scope (later S-slices):** Firebase identity (S2), CLI device-grant RFC
8628 (S3), invites + add-member/approvals + ≥1-owner-on-remove (S4), all client
UI (S5/S6), SMS abuse defenses, full account delete/export, teen (ADR 0005).

## Decisions (incl. folded review fixes)

- **Coexistence (non-breaking).** Tenant routes accept **either** a valid
  access-JWT **or** the legacy `HOUSEHOLD_SECRET`. The live deploy + CLI keep
  working until S3 issues real CLI tokens. **[review S-2] Legacy is a SEPARATE
  authz path** — credential-`family_scope`, **content-scope only, no membership
  resolution** — and is **provably unable to reach owner-only/self routes**.
  **Cutover: the legacy branch is removed in S3** (tracked debt).
- **Identity boundary.** `IdentityVerifier` interface; S1 ships `StubVerifier`
  (tests inject identities; `provider='dev'`) + a **gated dev-token endpoint**.
  Firebase Admin verifier drops in here at S2.
  - **[review S-1] dev-token gate = ALL of:** `ENABLE_DEV_AUTH=1` (unset by
    default, never set in prod/preview) **AND** hard-refuse when `VERCEL_ENV ∈
    {production, preview}` **AND** a required `DEV_AUTH_SECRET` bearer **AND**
    dev-distinct `iss`/`aud` **AND** audit-log every mint. Net: **local-dev +
    tests only**.
- **Multitenancy test** = two distinct owners each `POST /families`, then assert
  A's token → B → 404 (no invite dependency; same-family multi-member is S4).

## Data model (`apps/api/migrations/0002_auth.sql` — extends existing tables)

```
users(id, display_name, created_at, updated_at, deleted_at)
user_identities(id, user_id FK, provider, provider_uid, email_verified,
    UNIQUE(provider, provider_uid))
memberships(user_id FK, family_id FK, role CHECK in ('owner','adult'),
    status CHECK in ('pending','active','removed') DEFAULT 'active',
    joined_at, updated_at, PRIMARY KEY(user_id, family_id))
refresh_tokens(token_hash PK, credential_id FK, superseded_by, consumed_at,
    created_at, expires_at)
-- EXTEND existing:
families   ADD created_by text, ADD created_at/updated_at/deleted_at (defaulted)
credentials ADD refresh-related cols (label, last_used_at, last_used_ip) NULLABLE
```

**[review C-1] Backward-compat:** every added column on the existing `families`/
`credentials` tables is **nullable or defaulted** so the live household
credential row + deploy keep working. The existing `apps/api/test/api.test.ts`
(household-token round-trip) is the regression guard — it MUST stay green.

Indexes: `memberships(user_id, family_id)` (PK), `memberships(family_id, status)`,
`user_identities(provider, provider_uid)` (UNIQUE), `refresh_tokens(credential_id)`.
Enums as CHECK constraints. **Roles:** `owner`, `adult` (teen deferred, ADR 0005).
**Invariant ≥1 active owner per family** enforced at remove/leave/transfer —
those endpoints are S4, so S1 only guarantees create makes the creator an owner.

## Token model (`apps/api/src/auth/tokens.ts`)

- **Access = EdDSA (Ed25519) JWT** via `jose`. **[review F-1]** Node 24 WebCrypto
  supports Ed25519. Strict `alg` allowlist — reject `none`/HS/RS/anything ≠
  EdDSA. Header `kid`. Claims: `iss`, `aud`, `sub`=user_id, `cid`=credential_id,
  `exp` (TTL ≤5 min), `nbf`, `iat`, **`jti`** ([review G-1] reserved for
  emergency single-token kill; not on happy path). **Carries no authz** —
  family/role/scope re-resolved per request.
  - `iss`/`aud` are **env-pinned constants** (prod ≠ preview ≠ dev), never from
    request host. Verifier rejects any token whose `(iss, aud, kid)` ≠ the
    running env's. Clock-skew leeway ≤30s.
- **Refresh = opaque, hashed (sha256), lineage** in `refresh_tokens`
  (`superseded_by` chain per `credential_id`; absolute lifetime 30–60d).
  **Rotation = atomic CAS:** `UPDATE refresh_tokens SET consumed_at=now() WHERE
  token_hash=$1 AND consumed_at IS NULL RETURNING …`. **Reuse grace:** the
  immediately-prior token within ~20s re-serves the same new pair; an **older
  consumed** token = real reuse → **revoke the whole lineage by `credential_id`**
  (set `credential.revoked_at`) + audit.
- **Signing keys** — per-env Ed25519 keypair in Vercel env (`AUTH_SIGNING_KEY`
  JWK). **[review F-3]** Vercel env is dashboard-readable, so `04-auth`'s
  "secret-manager binds-but-can't-read-back" ideal is **not** fully met —
  accepted deviation for the slice; documented.
- **JWKS** at `/.well-known/jwks.json`, **[review F-2] self-published from the
  local env public key** — no external fetch, so `04-auth`'s fetch-on-miss /
  serve-stale / kid-injection hardening collapses to: build the `kid` allowlist
  from env **at boot** (deterministic per deploy, identical across serverless
  instances). `kid` resolved ONLY against that in-memory allowlist; `iss`
  validated before key selection. **[review G-3]** CDN cache headers on the JWKS
  route (no shared serverless memory for rate-limit).

## Auth + authz middleware (`apps/api/src/auth/middleware.ts`)

The one mandatory gate. **[review C-4] every existing + new route declares a
class; an undeclared route denies (default-deny, fail-closed).** Existing content
routes (`PUT/GET /families/:fid/cards`, `GET /families/:fid/sync`, `DELETE …
/cards/:id`) move under this middleware.

**Route classes:**
- **Tenant** (`/families/:fid/*`): the pipeline below.
- **Auth-bootstrap** (`/auth/session`, `/auth/refresh`): authenticate the
  verifier identity / refresh token; skip membership.
- **Self** (`/auth/signout`): authenticate access-JWT, operate on `sub`.

**Tenant pipeline (fail-closed; any error → 401):**
```
1. Bearer present? else 401.
2. Branch on token:
   2a. Access-JWT branch → verify alg=EdDSA + kid∈allowlist + iss=our-env + aud +
       exp/nbf (≤30s) → load credential by `cid`. (A Firebase/other-iss token
       here → 401, no confusion.)
   2b. Legacy branch → constantTimeEqual(token, HOUSEHOLD_SECRET); load the
       household credential by HOUSEHOLD_CREDENTIAL_ID. [S-2] content-scope only.
3. credential.revoked_at NOT NULL → 401. (lookup error/timeout → 401)
4. Resolve family from PATH {fid} ONLY (middleware-owned). cross-tenant → 404
   (identical body, no enumeration).
5. Authorization:
   - Access-JWT: re-resolve membership(user=sub, family=fid): status='active'
     else 403; load role. cred.family_scope (if set) must also == fid.
   - Legacy: cred.family_scope == fid else 404; NO membership; role = none.
6. Scope/role gate: content routes need content:write (mutations) / content:read.
   Owner-only action set (S4) and self routes are UNREACHABLE by legacy + by
   content-scoped creds (default-deny).
```

## Identity boundary (`apps/api/src/auth/identity.ts`)

```
interface IdentityVerifier { verify(assertion): Promise<{ provider, provider_uid, email_verified? }> }
StubVerifier   // tests pass an identity object directly (provider='dev')
DevVerifier    // backs POST /auth/dev-token, gated per [S-1]
// FirebaseVerifier added at S2.
```

**[review C-2] find-or-create user is atomic:** `INSERT INTO user_identities …
ON CONFLICT (provider, provider_uid) DO UPDATE … RETURNING user_id` (race-safe);
create the `users` row + identity in one transaction.

## Endpoints (S1)

- `POST /auth/session` — verifier → find-or-create user+identity → mint
  access+refresh. (Firebase verify = S2; S1 uses stub via tests / dev via
  dev-token.)
- `POST /auth/refresh` — rotation CAS + reuse→lineage-revoke + 20s grace.
- `POST /auth/signout` — **[review C-3]** set `credential.revoked_at` **AND**
  consume/revoke the refresh lineage.
- `POST /families` — authed (access-JWT) → create `family` (`created_by`) +
  `membership(owner, active)` in one txn.
- `GET /.well-known/jwks.json` — self-published public key(s).
- `POST /auth/dev-token` — gated per [S-1]; mints a token for a supplied dev
  identity. Audit-logged.
- Existing content routes — inherit the tenant middleware (accept JWT OR legacy).

## Testing (`apps/api/test/`, vitest vs live PG — the `api.test.ts` pattern)

- **Token:** mint/verify happy path; **[review G-2]** reject `none`/HS/RS,
  expired, `nbf`-future, wrong-`aud`, `kid`∉allowlist, tampered sig,
  other-`iss` (Firebase-style) at a tenant route → all 401.
- **[review S-3] cross-env isolation:** a dev-`iss` token → 401 at the prod
  verifier config and vice-versa.
- **Refresh:** rotation CAS; concurrent double-submit (loser gets nothing);
  20s-grace re-serve; older-consumed reuse → whole lineage revoked + subsequent
  access-JWT for that credential → 401.
- **Middleware:** access-JWT happy path; revoked credential → 401; inactive
  membership → 403; cross-tenant → 404; default-deny on an undeclared route.
- **Multitenancy/IDOR matrix:** user A's token on family B (cards, sync,
  card-mutation, `POST /families`-owned resources) → 404; A cannot mutate B.
- **[review S-2] legacy path:** household token still works on content routes
  (existing `api.test` green); household token → owner-only/self route → 403/401;
  household token cross-family → 404.
- **[review S-1] dev-token:** works with `ENABLE_DEV_AUTH=1`+`DEV_AUTH_SECRET` in
  test/dev; **refused when `VERCEL_ENV=production` or `preview`**; refused without
  the secret.
- **[review C-1] regression:** the full existing `api.test.ts` stays green.

## Scaling (`04-auth` posture)

**[review Sc-1]** Per-request membership + credential lookup is one indexed PK
query added to every tenant request (the offline client polls `/sync` ~45s).
Negligible at M0. **Do NOT cache authz** — the ≤5-min access TTL is defense-in-
depth, not a cache license; correctness (immediate revocation) wins. Revisit only
if it becomes hot.

## Sequencing & governance

- **[review S-10] ADR 0021 (Proposed) must be Accepted before build.** Spec +
  ADR first; the implementation plan can be written now; **build is gated on
  acceptance.**
- **[review S-9] dev-token kills LOCAL build/test hardcoding only.** Cloud/device
  (the Pixel) hardcoding fully dies at **S3** (CLI device grant). Recommended
  post-S1 order: **S1 → S3 → S2** (Firebase last; the stub/dev verifier covers
  identity for everything until real human login is needed) — confirm in ADR 0021.
- Design-first gate (ADR 0008) does not touch S1 (no UI).

## Definition of Done

New EdDSA token service (mint/verify/refresh) + the mandatory tenant middleware
live on all content routes; `POST /families` + `POST /auth/{session,refresh,
signout}` + JWKS + gated dev-token; `0002_auth.sql` applied, backward-compatible
(existing `api.test` green); the full test matrix above green vs live PG; the
household token still works end-to-end; nothing client-side or vendor-side
changed. Legacy branch carries a TODO marker referencing the S3 cutover.
