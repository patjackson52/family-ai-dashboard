# Backlog — Now

## ⚠ Time-sensitive (hard dates — keep pinned at top)

- **Quarterly:** re-check whether Google ships a *free, family-shared*
  Gemini Daily Brief variant (KS-6 / OQ-gemini-family). First check ~2026-09.
- **Next P0 viability review due 2026-07-18** (or +10 iterations).

Stage: **M0 render prototype BUILT + cloud-live (2026-06-19).** server · Kotlin
CLI · KMP/Compose client · feed — on Vercel + Neon, rendering on the Pixel 10.
Validation verdict still stands: **CONDITIONAL — learning-lab GO, business
NO-GO** → **building to learn**; the business unknowns (OQ-wtp / niche / gemini)
are **untouched by design**. The "brains" (G1 authoring loop) is a deliberate
later milestone; interim authoring = operator + Claude Code via the CLI. Next
build = **TASK-SYNC** (offline-first persistence).

## Design-first gate (ADR 0008) — status

The **feed-only** M0 slice was built **build-first** (operator-directed) from the
initial Now mockups in `designs/`. ADR 0008 **still governs unbuilt surfaces**:
**Event Hubs render** and the **M1 trigger surface** need their hi-fi mockups
(A8 full Now+Hubs; trigger v2 = INB-13) **before** they're built.

## Operator actions pending

- [ ] **INB-3** kill-checks (~2 hrs): Gemini Daily Brief + Maple+ hands-on;
  note the niche gap → feeds A1. *(Only matters if pursuing the business path.)*
- [ ] **INB-13** hand the trigger-design v2 fix-list (`designs/DESIGN-BRIEF-
  triggers.md §6b`) back to Claude Design before the M1 trigger surface.
- [ ] Counsel confirm for ADR 0005 (14+) — only if/when pursuing teen accounts.

## State (2026-06-18 — post 6-agent review)

- **Spec-build loop SUSPENDED** (cron stopped) — process right-sized for the
  solo M0 build (kept: spec-gate + inbox + multi-agent reviews).
- **Decisions:** M0 = **plaintext** (live E2EE → M1, ADR 0017 gate); M0 surface
  = **briefing-feed only** (Hubs → next slice); redux **1.0.0-alpha01** (INB-11
  superseded 2026-06-19 — operator owns reduxkotlin; `f(store.state)→UI`).
- **Spec suite + impl plan + JSON-schema contract = done; schema freeze
  unblocked.** Ready to build the M0 spine.
- **INB-9 RESOLVED → TypeScript on Vercel (ADR 0018).**
- **✅ M0 PROTOTYPE BUILT (12 build-loop iterations; build loop now STOPPED).**
  server (TS/Hono/Postgres) · CLI (Kotlin) · client core (redux-kotlin 0.6.2) ·
  feed UI (Compose, headless-render-tested) — all green, CI-enforced on GitHub,
  every component multi-agent-reviewed + fixes applied. Full CLI→API→DB→/sync
  round-trip verified live; the feed renders. Deploy config ready (Vercel + Neon
  pooler). Tracker: `specs/prototype/00-build-spec-plan.md`.
- **Blocked only on operator gates for literal G1a:** **INB-12** (create Vercel +
  Neon → deploy, recipe in inbox) · **INB-14** (Android SDK/device; iOS = Mac).
- **✅ DONE + MERGED — `TASK-KMP` (apps/client → true KMP module).** Merged to
  `main` 2026-06-19 (merge `0d621e5`, pushed to origin; feature branch deleted).
  Single Gradle build at `apps/` (8.11.1 + AGP 8.7.2); `:client` = KMP —
  commonMain holds all shared logic+UI+SQLDelight+ktor sync; **android + desktop
  + iOS** targets all build from it. `:androidApp` = thin app dep on `:client`
  (srcDir borrow + ContentStore/Main excludes GONE → TASK-SYNC step 2 unblocked).
  SyncClient → ktor `suspend`; driver via expect/actual `DriverFactory`. 17
  desktop tests + snapshots green; Android APK assembles; **both iOS targets
  compile + debug framework links** (iosX64/intel-sim dropped — granular alpha01
  lacks that publication). **Operator-gated remainder (DoD "run gated on Mac"):**
  Xcode iosApp project (Swift @main + signing + sim run) + iOS sync-config
  plumbing → folds into **TASK-SYNC**.
- **✅ DONE + MERGED — `TASK-SYNC` (offline-first DB-as-SoT, ADR 0020).** Merged to
  `main` 2026-06-19 (merge `13db28b`, pushed; branch deleted). Delivered **R1**
  (instant offline cold-start), **R2** (foreground poll ~45s + sync-on-resume),
  **R4** (unidirectional `network→DB→store→UI`, crash-safe cursor in `sync_meta`).
  `SyncClient`→transport (`fetchPage`); new **`SyncEngine`** owns the mutex-guarded
  drain loop + DB→store bridge (`activeCardsFlow`→`CardsLoaded`) + poll lifecycle
  (`start`/`resume`/`pause`/`stop`, public `syncNow` = future push hook). Store is a
  pure DB projection (no network→store path); cursor removed from `AppState`. Desktop
  file DB + WAL; iOS native driver. **24 desktop tests green, Android APK assembles,
  iOS framework links.** Built subagent-driven (spec+plan in `docs/superpowers/`,
  3 review rounds + final whole-branch review). kotlinx-datetime bumped 0.6.1→0.7.1
  (`Clock`→`kotlin.time`). **OUT (deferred):** R3 background (WorkManager/BGTask),
  push (FCM/APNs/SSE), E2EE (ADR 0017), 2-way/outbox (ADR 0016), iOS sync-config,
  the `payload`/`$defs` richer card fields. **ADR 0020 still marked *Proposed* —
  operator may flip to *Accepted* now that it's built.**
- **Deferred by design (operator, 2026-06-19): G1 content-authoring loop ("the
  brains") = much-later milestone; interim authoring = operator + Claude Code via
  the CLI.**
- **Still-queued gaps: G2 usefulness signal, the `payload` $defs, the
  Claude-Design triggers v2 (INB-13) + M3-Expressive upgrades.**

## Done this period

- Bootstrap (2026-06-18): scaffold, ADRs 0001-0004, validation fleet, board.
- Event Hubs design + block-level deep-linking (ADR 0006).
- Prototype scope locked (ADR 0007); design-first gate added (ADR 0008).
- Inbox swept: INB-1/2/4/5/6/7/8 answered; INB-3 pending operator.
- Design system = M3 Expressive, adaptive (ADR 0009); design brief shipped;
  initial Now/Hubs/Auth mockups added; repo public on GitHub.
- Auth/family/invite architected (ADR 0010) → **5-agent review**
  (`research/design-review-auth-2026-06.md`) → **hardened (ADR 0011
  supersedes 0010)**: all-invites-owner-approved, email→push cut, device-
  grant anti-phishing, no-auto-link, per-request revocation, Firebase dedupe
  corrected, relational content tables. Spec + Hub schema hardened.

## Auth is now in ACTIVE BUILD (ADR 0021, supersedes the "later milestone" note)

Operator pulled auth forward 2026-06-19 (ADR 0021, extends 0011): build order
**S1→S3→S2→S4→S5/S6**. **AUTH-S1 (tenancy & token backbone) ✅ DONE + MERGED**
(`main` 5753b32): EdDSA tokens + refresh lineage + `authorizeTenant` (JWT + legacy
household path, default-deny, fail-closed, cross-tenant 404) + `/auth/{refresh,
signout}` + `POST /families` + JWKS + gated local-only dev-token. 51 tests + final
security review passed; legacy household token still works (cutover at S3). Details
+ carried debt (S3 refresh-grace, S4 per-family cred binding) in `backlog/next.md`.
**AUTH-S3 ✅ DONE + MERGED** (PR #2, `main` 1fc1918): RFC 8628 CLI device grant
(`/device/*` + owner approve + lazy-mint) + refresh ~20s grace + Kotlin CLI
`login`/`push`. Twice multi-agent-reviewed; CI green. Legacy household token still
works (removal gated). **NEXT = AUTH-S2** (Firebase identity) or **S4** (invites). S5/S6 (client auth UI) stay ADR 0008
design-gated (A8b auth mockups, incl. authorize-device screen, design via Claude
Design). Prod deploy of the auth surface = operator-gated (set `AUTH_*` env in Vercel).
