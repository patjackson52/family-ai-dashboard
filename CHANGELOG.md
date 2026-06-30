# Changelog

Product, API, CLI, and client feature history. Ordered newest-first.
Format: date + milestone/PR + what changed. ADR numbers in parentheses.

---

## 2026-06-30

### Now derived surfacing — Phase B (PR #259, ADR 0044)

**Triggers v2 opt-in flow.** Notification settings screen with opt-in ladder
(foreground → "While using" → "Always"), quiet-hours + daily cap controls,
offline-aware consent, and honesty messaging ("works best with location access").
Local notifications only — no FCM/APNs, no server change. Dumb-server invariant
preserved.

**Record-shown EFFECT (carryover from Phase B gate, PR #258).** The
`NowEngine` now starts the anti-nag decay clock when a derived item becomes
visible in the foreground feed. Write-once (`recordShownIfNew` — ON CONFLICT DO
NOTHING); continuous visibility starts the clock once, never resets it. Softening
and decay (dormant since Phase A) now engage.

**ADR 0044 — Background-location & local-notification posture.** "Always"
location permission is opt-in, reversible, never asked up-front. Live position
never leaves the device. Quiet-hours and daily-cap knobs are device-local,
never-synced `RankConfig`.

---

### Now derived surfacing — Phase A (PR #257, ADR 0043)

**Two-lane Now feed.** "Now" is split into:
- **Lane 1 — Derived:** client synthesizes items on-device from hub content
  (`triggers[]` / `places`) against the live clock/location. Never persisted
  server-side; dumb-server invariant intact.
- **Lane 2 — Authored:** `briefing_cards` gain optional `target` (deep-link
  into a hub), `why`, `trigger`, and bounded `importance` weight.

**On-device Priority & Ordering Engine (`rank()`):**
urgency → proximity → importance → reason-kind → anti-nag decay → dedup by
`subjectRef` → calm budget (now/soon/later bands, overflow never silently
dropped) → stable order + hysteresis.

**Surfacing state** (last-shown, dismissed, quiet-hours) is local-only and
never synced — syncing it would be a who-saw-what privacy leak.

**API / schema additions:**
- `BriefingCard`: new optional fields `target`, `why`, `trigger`, `importance` (bounded).
- `HubBlock`: new `triggers[]` array + `places` array (geo anchors).
- DB migrations 6 (`hub_block.triggers`, `place`, `surfacing_state`) and 7
  (`briefing_card.importance`).

**Client tests:** 579 desktop tests green (+44 from 534 baseline, incl. 5
`NowEngineTest` + 2 `visibleSubjectKeys` + 37 Phase-A slices).

---

## 2026-06-29

### Two-way collaborative content — Slice 6: Freshness contract (PR #256, ADR 0040)

**Stale-cursor full-resync directive.** `/sync` returns `full_resync:true` when
the caller's cursor is older than the tombstone-retention floor; the client wipes
synced content (preserving the outbox and local hidden set) and rebuilds.

**Content-tombstone GC.** New arm of `/cron/sweep` hard-purges soft-deleted
rows (cards, hubs, sections, blocks) older than `CONTENT_TOMBSTONE_RETENTION_DAYS`
(default 90, env-overridable). Ensures clients within the floor never miss a
delete.

**API tests:** 335 green (+3 stale-cursor `/sync` tests, +1 sweep GC test).

---

### Two-way collaborative content — Slice 5b: W4 delete client + W5 local hide (PR #255, ADR 0038/0039)

**W4 block delete (client).** Author-only delete sheet (bottom sheet, calm
warning). Optimistic "Removing…" state — row stays visible until the `/sync`
tombstone confirms. Five-rung vocab (saving hairline → pending pill → retry →
error chip → restored). Works offline.

**W5 hide (local-only).** Swipe-to-hide (`SwipeToDismissBox`) + overflow
`DropdownMenu` accessibility path. Collapsed "Hidden for you · N" with "Show
hidden" toggle. Local-only `hidden` table (never synced, wiped on tenancy reset).

**JWT sub decode fix.** `AuthClient` now decodes `userId` from the access-token
JWT `sub` field (decode-only). Without this fix the author-gate was permanently
closed (author-only actions required knowing your own user id).

**Client tests:** 509 green (+TDD: 204-ack, created_by round-trip, delete
egress E2E, hide model/partition, reducer, JWT-sub, 7 compose tests).

---

### Two-way collaborative content — Slice 5a: W4 delete server (PR #253, ADR 0038)

**`DELETE /blocks/:id`.** Soft-delete + tombstone. No-oracle authz:
absent/can't-see → 404, no-scope → 403, non-author (incl. owner) → 403,
idempotent re-delete → 204.

**`content:delete` scope.** Members get `content:delete` for their own content
(distinct from `content:write`; author-gated). `upsertBlock` now stamps
`created_by` set-once (INSERT only).

**API tests:** 326 green.

---

### Two-way collaborative content — Slice 4: interactive checklist toggle UI (PR #251, ADR 0038/0039)

**Tappable `ChecklistRow`.** Whole-row 48dp tap target, `Role.Checkbox` +
state description (a11y), coral check scale-overshoot + left→right strike wipe,
one haptic tick, reduced-motion aware.

**Optimistic toggle flow:** tap → `HubEngine.toggleItem` → `ContentStore.enqueueBlockToggle`
→ `SyncEngine.drainOutbox` → whole-block PUT with `If-Match`. Five-rung
optimistic vocabulary (saving hairline / calm inline Retry, never a modal).

**`ChecklistFold` burst machine.** ~2s debounce, batches multiple rapid toggles
into "N done", newest-first, count-only above 20.

**"Synced with your family" honesty chip** — only shown where a member-write
boundary exists (display-only lists stay static).

**Client tests:** 503 green (+22).

---

### Two-way collaborative content — Slices 1–3 (PRs #247–#250, ADRs 0038/0039/0040/0041/0042)

**Slice 1 — Schema + reserved shape (PR #247).** Checklist item `id`/`doneBy`/`doneAt`/`ord`;
CLI ULID stamp-on-push; migration 0015 reserves `op_log` + `created_by`/`author_kind`/
`writer_user_id` on blocks+cards; `block_type`/`card_kind` ENUM→text; `content:delete` scope.

**Slice 2 — Server must-fixes / member-write security gate (PR #248).**
If-Match → 412 (block+section), visibility-on-write (restricted → 404, no oracle;
403 only visible-but-scope-denied), 410-on-tombstone (no member resurrection),
`op_log` idempotency + 7-day TTL sweep, tolerant validator gated to plaintext-M0.
API: 320 tests green.

**Slice 3 — Client sync engine / egress lane (PR #249).** `ChecklistMerge`
(per-item done-triple LWW, convergent + idempotent) + `OutboxSender`
(412/410/backoff/cap FSM) + egress wiring (`outbox` SQLDelight table +
`version`/`local_state` columns, migration `4.sqm`). Client: 481 tests green.

**ADRs ratified (2026-06-29):** 0038 (two-way primitive), 0039 (mutation engine),
0040 (freshness spectrum), 0041 (constitution amendment — bounded member AI
commands), 0042 (intents channel for W3).

---

## 2026-06-26

### Visual enrichment shipped (PR #177, ADR 0036)

**Hub and card media fields.** `Hub.media` / `BriefingCard.media` + block
`link`/`document` `thumbnailUrl` + block `contact` `avatarUrl`/`accentColor`.

**Image privacy allowlist.** Hardened shared validator (3 lock-step copies: API
Zod-refine, CLI `MediaValidation.kt`, client `MediaValidation.kt`): https-only,
exact-host allowlist (currently `upload.wikimedia.org` only), reject
userinfo/punycode/alt-port/SVG.

**Client render.** HubRow leading tile, collapsing-capped hero banner, card
icon+accent kind-chip + thumb, contact avatar → initials, link/doc thumb. Image
→ icon+accent-tile → default fallback ladder.

**CLI.** `media` field now validated in `dayfold push`. Icon enum and `#RRGGBB`
accentColor validation.

---

### Migration runner shipped (ADR 0033)

**`npm run db:migrate`.** `schema_migrations` tracking table + `scripts/migrate.mjs`
(dry-run / apply / backfill). Applies pending SQL files in order, one transaction
each, skips applied, re-run-safe.

**GitHub Actions `migrate.yml`.** Manual-only workflow (workflow_dispatch) for
prod migrations, with dry-run as the safe default.

---

### Prod auth stabilised

First real on-device Google sign-in live on prod after fixing:
- All AUTH-epic migrations applied (previously never run on prod).
- `AUTH_SIGNING_KEY`/`AUTH_ISS`/`AUTH_AUD` environment variables set.
- Sync token-refresh-on-401 (PR #104).
- Debug-drawer Logs bridge (PR #106).
- `npm run preflight` recurrence guard (`env:check` + `db:check`).

---

## 2026-06-23

### AUTH epic — full S1–S6 shipped (PRs #2–#25)

**S1 — Tenancy & token backbone.** EdDSA tokens + refresh lineage + `authorizeTenant`
(JWT + legacy household path, default-deny, fail-closed, cross-tenant 404).
`/auth/refresh`, `/auth/signout`, `POST /families`, JWKS endpoint, gated dev-token.

**S3 — RFC 8628 CLI device grant.** `/device/authorize` + `/device/token` + owner
approve + lazy-mint. Kotlin CLI `login` / `whoami`. Refresh ~20s grace.

**S2 — Firebase Auth verify.** Direct JWKS (`jose`, no Admin SDK). New
`POST /auth/firebase`. Firebase Auth Emulator in CI (ADR 0027).

**S4 — Owner-approved invites.** Per-hub read/write scope picker.

**S5 — Sign-in / sign-out / account flow.** Google + Apple only (ADR 0023).

**S6 — Member roster, connected-devices, profile, data-export, account soft-delete.**

---

### CLI device grant & scope (ADR 0029)

**`dayfold login`.** RFC 8628 device-authorization flow: prints a QR code +
user code; the family owner approves in-app. Stores access + refresh tokens in the
OS keychain (or `--allow-env-key` for headless environments).

**`dayfold logout`.** Revokes the server session + clears local tokens.

**`dayfold whoami`.** Prints `family=<id> api=<url> (device|legacy)` + the
credential's resolved scope from the server (`scope=content:read,content:write,...`).

---

## 2026-06-22

### Per-member hub visibility (ADR 0030)

Hubs gain `visibility` (`family` | `restricted`) + `created_by`. Restricted hubs
are omitted from other members' `/sync` and hub-list responses (omit, not 403).
`resource_visibility` allow-list on hubs controls additional per-member access.

Cards carry a flat author-stamped `audience[]`.

---

## 2026-06-21

### Typed content library (ADR 0022)

Six card types: `file` · `link` · `invite` · `contact` · `geo` · `email`.

Each type has a `payload.<type>` object. Local `--type` validation in the CLI
validates the payload shape against the generated schema before network.

`dayfold template <type>` prints a valid starter for each type + `hub` / `section`
/ `block`.

---

## 2026-06-20

### Event Hubs (ADR 0006)

Hub tree: hub → section → block (3 levels, author-chosen slug IDs).

Hub types: `vacation` · `starting-college` · `move` · `party-event` · `new-baby`
· `medical` · `school-year`.

Block types: `text` · `markdown` · `checklist` · `link` · `document` ·
`milestone` · `contact` · `location` · `budget`.

**CLI hub authoring:**
```
dayfold push <hubId>     hub.json --hub
dayfold push <sectionId> section.json --section
dayfold push <blockId>   block.json --block
dayfold pull --hub <id>
```

---

## 2026-06-19

### M0 prototype — built and live

**API (TypeScript / Hono / Postgres on Vercel + Neon).**
Content endpoints: `PUT /families/:fid/cards/:id`, `GET /families/:fid/cards`,
`PUT /families/:fid/hubs/:id`, `GET /families/:fid/hubs`, hub tree endpoints.
Sync: `GET /families/:fid/sync` (keyset cursor, page-based). Cron: `/cron/sweep`.

**CLI (`dayfold` — Kotlin JVM).** `push` / `pull` / `template` / `validate`.
Ships as a `distTar` application (zero-config JVM via Homebrew).

**Client (Compose Multiplatform — Android / iOS / Desktop).**
Offline-first SQLDelight cache. Redux-kotlin store. Foreground sync poll (~45s).
Feed renders all 6 typed card layouts. Hub detail screen.

**Offline-first architecture (ADR 0020).** `network → DB (SQLDelight) → store → UI`
unidirectional. Instant offline cold-start. Crash-safe keyset cursor in `sync_meta`.

**Package naming.** `com.sloopworks.dayfold` (ADR 0026). CLI binary: `dayfold`.
Env var: `DAYFOLD_API`. npm scope: `@sloopworks/*`.

---

### Auth CLI commands added

- `dayfold login` — RFC 8628 device-authorization flow
- `dayfold logout`
- `dayfold whoami`

---

### Edge channel (ADR 0037)

Every push to `main` touching `apps/cli/**` or `packages/schema/**` builds and
refreshes the `cli-edge` GitHub pre-release (`dayfold-edge.tar`). Stable releases
remain tag-driven (`cli-v<semver>`, ADR 0031).

`dayfold update` delegates to `brew upgrade dayfold` when brew-managed, else
prints install/upgrade instructions. Throttled once/24h update nudge after
interactive `push` / `pull`.

---

## 2026-06-18

### Bootstrap

Project scaffolded. ADRs 0001–0004 established (source of truth, execution model,
planning loop, product framing). Validation fleet run (6-agent). Conditional
GO verdict: learning-lab go, standalone-business no-go.

Content schema (`content.schema.json`) + codegen (Zod + Kotlin `Content.kt`).
Postgres migration 0001 (M0 init).
