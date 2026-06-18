# 03 — API Design

> Status: **draft → in review**. The HTTP contract off `01-architecture.md` +
> `02-data-model.md`. Tenant-explicit, default-deny, idempotent. The machine-
> readable OpenAPI is **generated from the JSON schema** (single source of
> truth); this doc is the human contract. Milestone tags **[M0]** / **[M1]**.

## Conventions

- **Base:** `https://api.<host>/v1`. JSON in/out; **gzip** request + response.
- **Auth:** `Authorization: Bearer <token>`.
  - **[M0]** household token (static secret, content-write, single family).
  - **[M1]** minted access token (short-lived); rotate via refresh.
- **Tenancy:** every family-scoped route is `/families/{fid}/...` and passes
  the one membership/scope middleware. **Cross-tenant access returns `404`**
  (not 403) to avoid existence enumeration. Default-deny.
- **Idempotent writes:** content is `PUT` by client-supplied **stable ID**
  (ULID). Re-PUT = update, never duplicate. **Nested PUT requires the parent
  to exist** → `409` otherwise (no orphans).
- **Concurrency:** responses carry `ETag: "<version>"`; writes may send
  `If-Match`. Mismatch → `412`. M0 is single-writer LWW (If-Match optional).
- **Errors:** `application/problem+json` (RFC 9457): `{type,title,status,
  detail,instance}`. Codes: `400` malformed · `401` no/!valid token · `403`
  authenticated-but-forbidden (in-tenant) · `404` not-found/cross-tenant ·
  `409` parent-missing/conflict · `412` version mismatch · `422` schema-invalid
  · `429` rate-limited (+`Retry-After`).
- **Pagination:** cursor (`?cursor=&limit=`), `next_cursor` in body.
- **Validation:** bodies validate against the JSON schema; failure → `422`
  with field errors.

## [M0] Content — write (CLI / Claude Code)

| Method · Path | Body | Notes |
|---|---|---|
| `PUT /families/{fid}/hubs/{id}` | Hub (no children, or with) | upsert hub |
| `PUT /families/{fid}/hubs/{hid}/sections/{sid}` | Section | parent hub must exist |
| `PUT /families/{fid}/hubs/{hid}/sections/{sid}/blocks/{bid}` | Block (incl. `body_md`) | parent section must exist; gzip for long markdown |
| `POST /families/{fid}/hubs/{id}:archive` | — | sets `status=archived` |
| `DELETE /families/{fid}/hubs/{id}` | — | **soft-delete** (`deleted_at`); cascades in app |
| `PUT /families/{fid}/cards/{id}` | BriefingCard (+`target`) | the "Now" surface |
| `DELETE /families/{fid}/cards/{id}` | — | soft-delete |

- A whole markdown file → one `block` (`type:"markdown"`, `body_md`) via the
  block PUT. Bodies > body-limit → `413` (M0 has no spill; spill is M1/06).
- Bulk convenience: `PUT /families/{fid}/hubs/{id}` MAY embed sections+blocks
  (full-replace semantics, declared) for one-shot authoring from the CLI.

## [M0] Content — read / sync (mobile client)

| Method · Path | Notes |
|---|---|
| `GET /families/{fid}/cards?active=true` | Now feed (filters `not_before`/`expires_at`, `deleted_at IS NULL`) |
| `GET /families/{fid}/hubs?status=` | Hub list (Projects) |
| `GET /families/{fid}/hubs/{id}` | full dossier (sections+blocks) |
| `GET /families/{fid}/sync?since=<cursor>` | **delta pull** for the local cache: changed hubs/sections/blocks/cards since cursor (incl. tombstones for soft-deletes) → drives offline render |

The client renders only from its local cache, populated by `sync`. Deep-link
`target` resolves against the cache (nearest-ancestor fallback) — never a
server round-trip per tap.

## [M1] Auth & account

| Method · Path | Notes |
|---|---|
| `POST /auth/session` | body: Firebase ID token → verify (Admin SDK) → find-or-create user → mint access+refresh. On provider conflict → `409 {pending_link}` |
| `POST /auth/refresh` | rotating refresh; **reuse-detection → revoke family** |
| `POST /auth/signout` | revoke this credential (effective immediately) |
| `POST /auth/link` | app-driven provider link — **requires proof-of-control** (re-auth with existing provider) before attach |
| `GET /auth/export` · `DELETE /auth/account` | data export; delete (cascade + Apple `revokeToken`; honor last-owner) |

## [M1] Families, members, invites

| Method · Path | Notes |
|---|---|
| `POST /families` · `GET /families` | create (→ owner membership) · list mine |
| `GET /families/{fid}/members` · `DELETE …/members/{uid}` | list · remove (last-owner guarded) |
| `POST …/members/{uid}:approve` · `:decline` | owner approves a pending join |
| `POST /families/{fid}/invites` | mint (role, mode qr/link, ttl, max_uses) → returns raw token **once** + QR URL |
| `GET …/invites` · `DELETE …/invites/{id}` | outstanding · revoke |
| `POST /invites/{token}:redeem` | invitee (authenticated) → **pending** membership (`409` if expired/exhausted/revoked) |

All invites create a **pending** membership; owner approval activates (ADR 0011).

## [M1] CLI / Claude-Code device grant (RFC 8628)

| Method · Path | Notes |
|---|---|
| `POST /device/authorize` | CLI → `{device_code,user_code,verification_uri,verification_uri_complete,expires_in,interval}`. Server records request **origin IP/geo/ASN** for the approve screen |
| `POST /device/token` | CLI poll: `grant_type=urn:…:device_code` → `authorization_pending` / `slow_down` / `expired_token` / `access_denied` / `200 {access,refresh}` (content-only, family-scoped). `device_code` one-time |
| `POST /device/approve` | app (signed-in): body must echo the **confirmed `user_code`** + chosen `family_id`; server checks owner role + shows origin warning client-side. Datacenter-ASN origins flagged |
| `POST /device/deny` | app |
| `GET /credentials` · `DELETE /credentials/{id}` | Connected devices & apps · revoke (effective within one request) |

## Cross-cutting controls (in every handler)

- Re-resolve membership + scope + credential-not-revoked **per request**;
  never trust token claims (ADR 0011 §8).
- Rate-limit: `/device/authorize`, `/device/token` (poll interval+`slow_down`),
  `/auth/session` (per-IP), invite redeem, `/auth/refresh`. SMS handled in
  Firebase (region allowlist + App Check).
- Audit-log every write + every prod/cost-relevant action (ADR 0012).
- Object-storage signed URLs (M1) minted only post-authz, tenant-prefixed
  keys, ≤60s expiry — never embedded in stored markdown.

## Open questions
- Bulk authoring: full-replace vs merge semantics on embedded children — lock
  in 07-cli (the CLI is the main writer).
- `sync` cursor design (opaque `updated_at`+id vs change-log table) — decide
  with 08-mobile-client cache.
- Body-size limit for inline `body_md` at M0 (the `413` threshold).
