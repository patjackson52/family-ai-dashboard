# 01 — Architecture Overview (Prototype / v1)

> Status: **accepted (review applied)** — 2 agents (correctness/feasibility +
> security/data-integrity), findings folded in. Synthesizes ADRs 0004–0012 +
> `specs/auth-and-family-design.md` + `specs/event-hubs-design.md`. Build
> sequencing per ADR 0011 (M0 dumb renderer → M1 auth). Authors 02–09 inherit
> the invariants below.

## Purpose & scope

A calm family dashboard that **renders content authored externally** (operator
via Claude Code), pushed through a content API. Two co-equal surfaces: **Now**
(briefing cards) and **Hubs** (event dossiers). The app reasons about nothing.

## System context

```
            ┌──────────────────────── Clients ────────────────────────┐
  operator → CLI / Claude Code ──push──▶                               │
                                        │  HTTPS / JSON (+gzip)         │
  family   → Mobile app (CMP            │                              │
            Android+iOS) ──read/auth──▶ │                              │
            └────────────────────────────┼────────────────────────────┘
                                          ▼
                              ┌────────────────────────┐
                              │ API service (TS/Vercel, │
                              │ stateless)              │
                              │ content · auth(M1) ·    │
                              │ invite(M1) · device(M1) │
                              └───┬─────────┬──────────┬┘
                                  ▼         ▼          ▼
                          Postgres   Object storage   Firebase Auth (M1)
                       (M0: families,(M1: doc + large  (Google/Apple/
                        content;      markdown spill,    phone-OTP)
                        M1: auth,      tenant-scoped)
                        creds)
```

## Decisions taken in review

- **API host = TypeScript on Vercel** `[pending-ratify → operator-inbox]`.
  Rationale: ADR 0012's deploy-autonomy rail (preview→promote→rollback via
  Vercel MCP) is first-class only on TS/Node; a JVM API needs a container host
  + standing cost against the <$50/mo cap. **CLI stays Kotlin/JVM.** Recover
  Kotlin's lost type-sharing by making the **JSON schema the single source of
  truth** and **codegen** both TS (zod) and Kotlin client types from it.
  (Platform choice → ADR-class; ratify at C3.)
- **M0 content = inline only (no object-storage spill).** Single-household,
  bounded content. Spill + signed URLs are **M1 / component 06**.

## Components

| Component | Responsibility | Milestone |
|---|---|---|
| Mobile client (CMP) | Render Now+Hubs from local cache; markdown (mikepenz, lazy); deep-link nav; auth UI | M0 render · M1 auth UI |
| CLI / Claude Code | Author+push content (cards, hubs, markdown files); **M0 reads household token from platform secret/OS-keychain**, M1 swaps to device-grant credential — same endpoints | M0 push · M1 device-grant |
| API service (TS/Vercel) | Content upsert/read (tenant-explicit), auth/invite/device (M1); **one mandatory tenancy middleware on every route** | M0 content · M1 auth |
| Postgres | M0: families, hubs/sections/blocks, briefing_cards · M1: users, identities, memberships, invites, device_authorizations, credentials | M0 content · M1 auth |
| Object storage | doc refs + large-markdown spill; **private ACL, tenant-prefixed keys, short-lived scoped signed URLs** | M1 |
| Firebase Auth | Google/Apple/phone-OTP (GitLive KMP + native glue) | M1 |

## Tenancy invariant (every path, both milestones)

**Default-deny via one middleware every route inherits** — write AND read.
Both are tenant-explicit:
- `PUT /families/{fid}/hubs/{id}` · `.../sections/{sid}` · `.../blocks/{bid}`
- `PUT /families/{fid}/cards/{id}` (briefing cards)
- `GET /families/{fid}/...` (read path — **not implicit**; same middleware)

The middleware resolves the requester → the path's `family_id` → scope, then
checks **scope-vs-path**; it never bypasses. **M0:** the household token maps
to exactly **one fixed `family_id` + content-write scope** (a *degenerate
credential*, not a skip-authz flag). **M1:** active-membership resolution +
role/credential-not-revoked. The local SQLDelight cache is populated **only**
from tenant-scoped responses — the cache is not an authz boundary, the server
is. IDOR test matrix (per resource, family A → 403/404 on family B) **extends
at M1 to invite/device/credential/membership routes**, and to object-storage
URL minting/replay.

## Data flows

1. **Content push (M0):** CLI → `PUT /families/{fid}/{hubs|cards}/...`
   (idempotent, client-supplied stable IDs, gzip) → middleware authz →
   validate → **nested upsert requires parent to exist (else 409/404, no
   orphans)** → upsert Postgres. `body_md` inline at M0. **Single-writer LWW,
   enforced** (one active credential); `version`/`If-Match`/`updated_at`
   carried on rows now so multi-writer needs no retrofit.
2. **Render (M0):** client → `GET /families/{fid}/...` (tenant-scoped) →
   local cache → UI renders from cache; card `target{hubId,sectionId?,
   blockId?}` and deep-links resolve against the **local** cache
   (nearest-ancestor fallback).
3. **Auth (M1):** client → Firebase → ID token → API verifies (Admin SDK) →
   **mints** access+refresh (rules below) → app-driven provider linking.
4. **Invite (M1):** owner mints → Universal-Link QR → invitee authenticates →
   **pending → owner approves** → active.
5. **Device-grant (M1):** RFC 8628 → QR scan-to-approve (user_code confirm +
   origin warning) → scoped, revocable, content-only family credential.

## Cross-cutting

- **Token rules (ADR 0011 §8):** backend-minted, **asymmetric signing
  (EdDSA/RS256), strict `alg` allowlist, full `iss/aud/exp/nbf`, key
  rotation**; **never trust `family_id`/`scope`/`role` from the token —
  re-resolve per request**; refresh **reuse-detection → revoke token family**;
  revocation effective **within one request**.
- **M0 household token:** content-write scope, single `family_id`, no auth/
  invite/device/membership access, **server-side revocable + rotatable**,
  stored as a **platform secret** (Vercel encrypted env / secret manager) —
  never in repo or client bundle; **use logged** (ADR 0012 audit).
- **Secrets under agent-deploy (ADR 0012):** signing keys + Firebase Admin
  key + DB creds in a secret manager, **least-privilege — agent deploy role
  binds secrets, does not read plaintext** where the platform allows;
  **per-env keys** (preview never shares prod's signing key); rotation
  procedure; all use logged. Signing-key compromise = total tenancy bypass —
  treat as the crown jewel.
- **Markdown safety (both inline & spill, render path too):** CommonMark+GFM;
  raw HTML stripped on ingest; **link-scheme allowlist**; **images off at
  MVP** (image-URL exfiltration); signed URLs are transport-only — **never
  embedded into stored markdown**. Lazy render (mikepenz).
- **Idempotency:** client-supplied stable IDs; parent-exists on nested
  upsert; `version`/`If-Match` carried from M0.

## Environments & deploy (ADR 0012)

- **Local:** Postgres (+ object-storage stub if used). **Firebase Emulator
  added at M1** (no Firebase at M0).
- **Preview:** per-change deploy + smoke/browser verify — gate before prod.
- **Prod:** agents may promote behind the rails (test-green-before,
  verify-and-rollback-after, log).

## Tech lean (agent-buildability, C3 — TS host ratify pending)

API = **TypeScript / Vercel** (MCP) · DB = **Neon/Supabase Postgres** · object
storage (M1) = Vercel Blob / S3-compatible (private, tenant-prefixed) · auth =
**Firebase** · client = **CMP** · CLI = **Kotlin/JVM**. Types **codegen'd from
the JSON schema**. Final at C3.

## Open questions

- API-host language ratification (TS) — `OQ`/inbox, decide at C3.
- Object-storage provider + signed-URL model (component 06).
- M0 household-token rotation cadence (component 04/09).
