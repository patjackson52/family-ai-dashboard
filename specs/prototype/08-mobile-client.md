# 08 — Mobile Client (Compose Multiplatform)

> Status: **draft → in review**. Anchored by **ADR 0013** (KMP/CMP shared
> code+UI, redux-kotlin) + ADR 0009 (M3 Expressive) + ADR 0006 (deep-link) +
> ADR 0014 (triggers) + ADR 0015 (E2E). Renders content from `03-api` `sync`
> into a local cache; offline-first. **Milestone:** [M0] render-only (operator
> device, household token, no login UI) · [M1] auth/invite/device UI +
> multi-member · [later] background geofencing.

## Architecture & modules (redux-kotlin, package-by-feature)

```
app/        composition root, DI, nav host, theme (M3E)
core/       domain models (codegen from JSON schema), result/error types
infra/      api client, sqldelight cache, crypto (E2E), keychain, platform expect/actual
ui/         M3E component library, the Markdown renderer (mikepenz, lazy), shared widgets
feature/
  now/      {model, actions, reducer, effects, screen, selectors, tests}
  hubs/     {…}            ← list + detail (sections/blocks)
  auth/     {…}  [M1]
  settings/ {…}  (connected devices, places, permissions)  [M1/later]
```

Single redux-kotlin **`StableStore`**; `combineReducers` over feature
reducers; effects run in middleware (off-main, ADR 0013 Rule E,
`NotificationContext` marshals to main).

## State shape (sketch)

```
AppState {
  session:  { credential?, family_id?, status }           // M0: household token implicit
  sync:     { cursor?, lastSyncedAt?, status }
  content:  { hubsById, sectionsById, blocksById, cardsById, placesById }  // cache-backed
  triggers: { activeGeofences[], scheduledNotifs[] }      // derived
  nav:      { route, focusBlockId? }                       // state-keyed (Rule I) → deep-link
  ui:       { permissionStates, banners }
}
```

Render isolation (Rule C): composables bind the **narrowest slice** via
`selectorState`/`fieldStateOf` — never read `AppState` wholesale.

## Effects (middleware — the seams to other specs)

- **Sync effect:** background pull `GET /families/{fid}/sync?since=cursor` →
  apply `changes` + `tombstones` to the cache → advance cursor. Retry w/
  backoff; honors `ETag`/`304`. The **only** content read path.
- **Cache effect (SQLDelight / SQLCipher under E2E):** all reads come from the
  local DB; the store is hydrated from it; writes never bypass it (cache is the
  render source of truth, not an authz boundary — server is).
- **Crypto effect (E2E, ADR 0015):** **decrypt-once-into-cache** off-main
  (not per-render); `FCK`/private key from the OS keychain; cache DB key in
  keychain (SQLCipher).
- **Auth effect [M1]:** Firebase (GitLive + native glue) → token mint/refresh;
  app-driven linking; per-request token attach.
- **Trigger effect (ADR 0014):** on sync, (re)register the **nearest-N**
  geofences + schedule the **soonest-N** local notifications from synced
  triggers/places; on OS callback → dispatch → surface/boost the linked card.
- **Deep-link effect:** Universal/App-Link or in-app tap → dispatch
  `Navigate(HubRoute(hubId, focusBlockId?))`.

## Local cache & sync

- **SQLDelight** typed cache of hubs/sections/blocks/cards/places + sync
  cursor (SQLCipher with a keychain DB key if E2E).
- Apply `sync` deltas idempotently (upsert by id; tombstones soft-remove).
- **Offline-first:** UI always renders from cache; a failed sync shows a quiet
  stale indicator, never blocks render. Single-writer LWW server-side (M0)
  means no client merge.

## Rendering (M3E + markdown + deep-link)

- **Now** feed: M3E cards (action/info/weather/countdown), provenance chips,
  trigger chips (time/geo), empty + skeleton states.
- **Hubs**: list (Projects) + **detail** (collapsing header + sections of typed
  blocks). Markdown via **mikepenz** `LazyMarkdownSuccess` + off-main
  `parseMarkdownFlow` (long docs); link-scheme allowlist + images-off
  (event-hubs §Markdown).
- **Deep-link arrival (state-keyed, Rule I):** a card `target{hubId,sectionId?,
  blockId?}` → `nav.route=HubRoute(hubId)`, `nav.focusBlockId=blockId` →
  detail **scrolls to + transient-highlights** the block, expands its section.
  **Resolution runs against the LOCAL cache** (nearest-ancestor fallback →
  "that item moved"). This is the load-bearing interaction; it works because
  navigation is state (time-travel/deep-link for free).

## Trigger matcher (ADR 0014, on-device)

- Derive an **active set** from synced triggers within platform geofence limits
  (iOS ~20 / Android ~100): nearest-N geo + soonest-N time.
- **Geo:** register OS geofences (CLLocation region monitoring / Android
  Geofencing). **Progressive permission** (when-in-use → "Always" opt-in for
  background). **Time:** local scheduled notifications (notification permission
  only). On fire → surface/boost the card in Now or a calm notification
  (quiet hours, dedupe, daily cap). **Activity deferred.** Live position never
  leaves the device.

## Navigation & adaptive

- Navigation **is state** (redux nav slice); deep-links/triggers = dispatch a
  nav action. Adaptive (ADR 0009): phone bottom `NavigationBar` (Now/Hubs);
  rail/drawer at tablet/desktop; list-detail for Hubs on wide.

## Auth UI [M1]

Sign-in (Google/Apple/phone), onboarding/create-family, invite QR + the
**authorize-device** screen (user_code confirm + origin warning + family
selector), family members + pending approvals, connected devices, places,
permission priming. Per the A8/A8b mockups. (M0 has none of this — household
token, operator's own device.)

## Persistence / process death

`compose-saveable` + `SaveableStateRegistry` snapshot nav + scroll across
process death; the cache survives restarts so cold start renders immediately.

## Testing & verify (ADR 0012/0013)

Pure reducers → fast unit tests; effects tested with fakes; selector tests;
screenshot tests for M3E surfaces; **verify loop `./gradlew build`** (compile +
test + detekt + apiCheck) is the test-green-before gate. Ship **`AGENTS.md`** +
install **`.claude/skills/redux-kotlin/`** so agents build it well.

## Open questions
- Sync cadence/trigger (push-driven later vs poll) at M0 — likely manual/
  foreground refresh + periodic; ties to no-push-at-M0 (ADR 0007).
- Geofence active-set re-ranking as the user moves (significant-location-change
  wake) — battery vs freshness.
- SQLCipher KMP binding choice (if E2E) — confirm with the crypto lib (ADR 0015).
