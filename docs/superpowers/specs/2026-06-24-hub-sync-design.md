# Hub Sync — offline incremental sync of Hubs (design v2, post round-1 review)

**Date:** 2026-06-24 · **Status:** v2 — round-1 panel changes folded; for round-2
sign-off. **Branch (proposed):** `claude/hub-sync` off `main`. **ADRs:** 0013
(f(state)→UI, effects-in-engine, pure reducer), 0020 (DB-as-truth cache), 0029
(resource-scoped grants), 0030 (per-member visibility + revocation). **Design gate:**
ADR 0008 **CLEARED** for the Hubs surface (INB-22, 2026-06-24; `Hubs-Visibility.dc.html`
signed off). **Hub-sync changes NO visible surface** — it is a pure data-path swap
behind the *identical* `HubListScreen`/`HubDetailScreen` selectors, so no new mockup
is required (snapshot parity is a DoD test, §6). **Specs:** `03-api.md §Sync`,
`08-mobile-client.md §R1/R2/R3`, `event-hubs-design.md`.

Offline incremental sync for Hubs (hubs + sections + blocks), mirroring shipped
**card-sync**, so the Hubs surface (shipped #42, plain GET, no cache) gains instant
offline cold-start, foreground freshness, and **ADR-0030 revocation**.

> **v2 changelog (round-1 panel):** single **merged-typed keyset cursor** replaces
> the v1 compound cursor (matches `03-api.md`, kills the codec/carry-forward/prefix
> defects); **server-side visibility filter + tombstone fan-out** for sections/blocks
> (closes a restricted-content leak + the revocation gap — no client cascade);
> **SQLDelight version bump + `.sqm`** (verified: bare `Schema.create` can't add
> tables on a populated DB); **no new redux actions** (repurpose `HubsLoaded`/
> `HubTreeLoaded` DB-fed, single writer per slice, reducer prunes `currentHubId`);
> **two bridges + narrow selectors** (don't merge); **iOS pause/resume** fixed in
> this slice; R3 background folded into the existing `TASK-SYNC` R3.

## 0. Why now
Render shipped (#42); the revocation touch-trigger already ships
(`0009_visibility.sql trg_resvis_touch_hub`, currently unused). The deferral reason
("nothing caches hubs") is gone. Deferral lifted.

## 1. Template (card-sync, mirrored)
Server `GET /families/:fid/sync?since=<cursor>` (`app.ts:788`): keyset
`base64(updated_at|id)`, `SYNC_LIMIT=200`, `WHERE (updated_at,id)>cursor ORDER BY
updated_at,id LIMIT n` (`repo.ts:57`), envelope `{changes:{cards}, tombstones:
[{type,id}], next_cursor, has_more}`; soft-delete (`deleted_at`) bumps `updated_at`
(`trg_touch`) → tombstone; visibility-invisible rows → tombstones (`cardVisible`).
Client: `SyncClient.fetchPage(since)` → `ContentStore.applyDelta` (atomic txn; `card`
+ `sync_meta(id=0)`) → `activeCardsFlow()` → `CardsLoaded` → pure reducer (full
replace + prune `detailStack`). `SyncEngine` (mutex; `start`=bridge, `resume`=sync+45s
poll, `pause`=cancel). **403/404 → `wipe()` + `SignedOut`.** Cursor in DB, not redux.

## 2. Server data model — ready; one additive migration needed
`hubs`/`sections`/`blocks` exist (`0001_m0_init.sql`) with `(family_id,updated_at,id)`,
`version`, `deleted_at`; hubs add `visibility(family|restricted)`/`created_by`,
`resource_visibility` allow-list + **`trg_resvis_touch_hub`** (`0009`). Read filter
`hubVisible(row,caller,allowListHas)` (`content/hubs.ts:12`). **Sections/blocks have NO
visibility column** — they inherit the hub (only reachable via the hub today).
**New migration `0010_hub_sync_fanout.sql`** (see §3.3): extend the visibility trigger
to fan `updated_at` out to children.

## 3. Decisions (v2 — folded)

### 3.1 ONE merged-typed keyset cursor over a typed UNION — **DECIDED (supersedes v1 compound)**
Extend the existing `GET /families/:fid/sync` to stream cards+hubs+sections+blocks as a
**single keyset** over a typed union, matching `03-api.md`'s single `next_cursor`:
```
SELECT updated_at, 'hub'   AS type, id, … FROM hubs     WHERE family_id=$1 AND (updated_at,id) > (...)
UNION ALL SELECT … 'section' … FROM sections …
UNION ALL SELECT … 'block'   … FROM blocks …
UNION ALL SELECT … 'card'    … FROM briefing_cards …
ORDER BY updated_at, type, id LIMIT 200
```
Cursor = `base64(updated_at|type|id)` (3-part). `has_more = rows.length >= 200`
(single query — no per-table carry-forward, no compound codec). **Back-compat:** an
old client sends the legacy 2-part `base64(updated_at|id)` → server detects part-count
==2 → **legacy cards-only mode** (unchanged `repo.ts:57` path, 2-part next_cursor); a
new/empty cursor → merged mode (3-part next_cursor). An unparseable/unknown cursor →
full resync from `-infinity`. `type` makes `(updated_at,type,id)` globally unique
across tables, so the keyset tiebreak is sound even when many rows share one `now()`
(e.g. a hub soft-delete stamping its children). *(This resolves round-1 spec#1/#3,
data-sync#4/#6, and the documented-contract divergence.)*

**`places`:** the `03-api.md` envelope also lists `places` in `changes`; hub-sync
**does not** add it (places are unrelated to hubs). Noted explicitly so it isn't a
silent drop — places can join the same merged stream in a later slice.

### 3.2 Tree consistency — store flat, join on read; orphans never shown
hubs/sections/blocks interleave by `(updated_at,type,id)`; a block can precede its
hub. Store all flat (client tables carry `section.hub_id` + `block.section_id`); the
`hubTree(hubId)` query joins only present rows; the active-hub render **excludes any
block/section whose hub is absent-or-tombstoned locally** (orphan filter — the same
"render what's present" stance the card reducer already encodes at `Reducer.kt:45`).

### 3.3 Revocation — **server-side tombstone fan-out (DECIDED; no client cascade)**
Two ADR-0030 cases, both server-authoritative so the client stays dumb + the reducer
pure:
1. *Excluded-but-still-member:* a `resource_visibility` change (or hub `visibility`
   flip) must re-surface the hub **and its sections/blocks** in the keyset so the
   per-row visibility join (below) emits them as tombstones to the now-excluded
   caller. The shipped trigger bumps only `hubs.updated_at`; **migration `0010`
   extends it to fan out:**
   ```sql
   -- 0010_hub_sync_fanout.sql
   CREATE OR REPLACE FUNCTION touch_hub_from_visibility() RETURNS trigger AS $$
   DECLARE h text := COALESCE(NEW.hub_id, OLD.hub_id);
           f text := COALESCE(NEW.family_id, OLD.family_id);
   BEGIN
     UPDATE hubs     SET updated_at=now() WHERE family_id=f AND id=h;
     UPDATE sections SET updated_at=now() WHERE family_id=f AND hub_id=h;
     UPDATE blocks   SET updated_at=now() WHERE family_id=f AND section_id IN
            (SELECT id FROM sections WHERE family_id=f AND hub_id=h);
     RETURN NULL;
   END $$ LANGUAGE plpgsql;
   ```
   (Hub `visibility` UPDATEs need the same fan-out — add an `AFTER UPDATE OF
   visibility ON hubs` trigger that touches children. Hub **soft-delete** already
   cascades `deleted_at`+`updated_at` to children via `softDeleteHub`, `hubs.ts:98` —
   confirm in a test.) This makes every transition (incl. the visible→invisible→
   visible flap) self-healing: re-visible children re-surface as live.
2. *Removed-from-family:* the removed member 401/404s at the tenancy gate → reuse the
   shared card path: **403/404 → `wipe()` + `SignedOut`**, ONE handler
   (`SyncEngine.syncNow`, `:81`), `wipe()` extended to drop hub/section/block tables
   in the same call. Single `SignedOut`.

**Server visibility filter for sections/blocks** (closes the round-1 leak): the merged
query joins each section/block to its parent hub and applies `hubVisible(hub, caller,
allowListHas)`; a row is **live** iff its hub is visible, else **tombstone**. (Sections/
blocks have no own visibility column — gate via the hub, never a non-existent
`section.visibility`.) *(Resolves data-sync#1/#2/#3.)*

### 3.4 Client cache + reducer — HubStore (SQLDelight) + two bridges, DB-fed, pure
- **SQLDelight (schema v2 + migration):** add `hub`/`section`/`block` tables mirroring
  `card` (each: business cols + JSON-TEXT cols decoded at projection + `hub_id`/
  `section_id` FKs + `deleted` flag). **Bump `ContentDb.Schema.version 1→2** and add
  `1.sqm`** creating the three tables (bare `Schema.create` can't add tables to a
  populated DB — the Android/iOS drivers run `Schema.migrate(1→2)` on existing
  devices). Reconcile the explicit `ContentDb.Schema.create(driver)` at
  `ContentStore.kt:79` with driver-managed create+migrate (let the driver own it).
  Cursor stays the single `sync_meta(id=0).cursor` row — the merged cursor is one
  string, **no cursor-schema change**.
- **HubStore.applyDelta(hubs, sections, blocks, tombstones, cursor)** — ONE atomic
  SQLDelight txn: upsert rows, apply tombstones by `type` (delete hub/section/block by
  id), write the single cursor. Corrupt-JSON guarded → null (cache disposable). Server
  fan-out means **no client cascade logic** — tombstones for children arrive explicitly.
- **Bridges:** `SyncEngine` gains a SECOND bridge `activeHubsFlow()`/`hubTreeFlow()` →
  dispatch. **Keep two independent bridges** (the thread-safe store serializes dispatch;
  slices are disjoint — do NOT merge into one action). Each is a pure **full-replace of
  a disjoint slice**.
- **Actions — NO new types; repurpose existing as DB-fed (single writer per slice):**
  `HubsLoaded(hubs)` and `HubTreeLoaded(tree)` become dispatched **only** from the DB
  flow. `HubEngine.loadHubs/openHub` **stop dispatching network data**; they write the
  sync result to the DB (via the shared drain) and read back through the flow.
  `HubEngine` keeps `HubClient` only as the transport the `SyncEngine` drain calls.
  Reducer arms (pure):
  ```kotlin
  is HubsLoaded -> state.copy(hubs = action.hubs,
    currentHubId = state.currentHubId?.takeIf { id -> action.hubs.any { it.id == id } },
    currentHubTree = if (state.currentHubId != null && action.hubs.none { it.id == state.currentHubId }) null else state.currentHubTree)
  is HubTreeLoaded -> state.copy(currentHubTree = action.tree)
  ```
  i.e. a hub tombstone that removes the open hub **prunes `currentHubId`/
  `currentHubTree`** (mirrors `detailStack` pruning). Pruning is in the **reducer** on
  the DB-fed action; cascade/orphan deletion is in **HubStore** — never in the engine.
- **Selectors:** `HubsHost`/`HubDetailScreen` read **narrow** slices
  (`store.selectorState { it.hubs }`, `{ it.currentHubTree }` — extension form) so a
  45s hub-poll re-emit can't recompose Feed and vice-versa. Requires
  `redux-kotlin-granular` on the compose classpath (already present; note: no iosX64
  granular publish — don't add an iosX64 target). Narrow + `data class` structural
  equality makes redundant identical re-emits free.
- **busy/error:** cold-start renders the cached hubs with `hubsBusy=false`; the sync
  drain drives the shared `syncing` status (`SyncStarted/Succeeded`, shared with cards)
  — not a per-hub spinner. `openHub` of an **uncached** hub sets `hubsBusy=true` and
  triggers a sync; busy clears when the tree flow emits OR the drain ends (never
  terminal — the "Loading must never wedge" rule). First-ever open (empty DB +
  in-flight network) shows the existing empty/loading state until the flow emits.

### 3.5 Offline/background — R1/R2 in scope (mirror cards); R3 folded into TASK-SYNC
- **R1/R2:** hub-sync joins the existing `SyncEngine` drain (one round-trip, §3.1) →
  instant offline cold-start + 45s foreground poll + pull-to-refresh.
- **iOS poll-leak fix (in THIS slice):** desktop/iOS never call `pause()`
  (`MainViewController.kt:27`), so the 45s poll fetches **restricted-hub data while
  backgrounded**. Wire iOS background/foreground → `pause()`/`resume()` mirroring
  Android `repeatOnLifecycle` (`MainActivity.kt:80`). Desktop's always-on poll is fine
  (no true background) — noted, not changed.
- **R3 background — OUT, folded into the existing `TASK-SYNC` R3 follow** (not a new
  task; `next.md:507` already defers "Android WorkManager + iOS BGTaskScheduler → the
  shared SyncEngine.syncNow"). Revocation does **not** require background: ADR-0030 is
  *convergence on next successful sync*, and next-foreground-open re-runs `syncNow`
  (re-evaluates `hubVisible`, fires tombstones / the 403/404 wipe). **Worst case
  without background:** a dropped member retains restricted-hub plaintext locally until
  they next foreground-open — **identical to cards today**, an accepted M0 risk
  (plaintext at-rest; SQLCipher at M1, `08-mobile-client.md:127`). iOS background is
  *not guaranteed* (BGAppRefreshTask is opportunistic), so it could never be a
  correctness dependency anyway. **Requirements to record on `TASK-SYNC` R3** so it's
  well-scoped: Android `CoroutineWorker` + `PeriodicWorkRequest` (15-min floor, Doze
  best-effort, `NetworkType.CONNECTED`, `ExistingPeriodicWorkPolicy.KEEP`, idempotent
  via the DB cursor, `Result.retry()`); iOS `BGAppRefreshTask` (register pre-launch,
  reschedule-on-completion, ~30s budget + `expirationHandler` cancels cleanly, no
  guaranteed exec, operator-Mac-gated → iOS-background DoD is operator-manual not CI);
  both call the one shared `syncNow`, one cursor, one 403/404 wipe.

## 4. Scope
**In (agent-buildable, headless/CI-tested):**
1. **Server:** migration `0010` (visibility fan-out); extend `/sync` to the merged
   typed keyset (cards+hubs+sections+blocks) with the section/block hub-visibility
   join + tombstones; `repo.syncContent` (one merged query); legacy 2-part cursor
   back-compat. vitest.
2. **Client:** schema v2 + `1.sqm` (+ hub/section/block tables, queries, orphan-filter
   tree join); `HubStore.applyDelta`; `SyncClient` envelope extension (decode
   hubs/sections/blocks + typed tombstones); `SyncEngine` second bridge + hub delta in
   the same drain + extended `wipe()`; `HubsLoaded/HubTreeLoaded` DB-fed + reducer
   prune; `HubEngine` rewired DB-first; narrow hub selectors; iOS pause/resume.
   desktopTest + snapshot parity.
**Out (deferred, ADR cover):** R3 background (→ `TASK-SYNC` R3); `places` sync; hub
authoring/write-from-client (ADR 0016); E2EE (0015/0017); per-hub scope *selection* UI
(ADR 0029 §5).

## 5. Risks / guardrail note
**Restricted-content disclosure (Guardrail 3-adjacent):** §3.3's section/block
hub-visibility join is the security seam — a regression there leaks restricted-hub
content to non-allow-listed family members. The §6 test matrix makes it explicit +
adversarial. Not a *legal* guardrail trip (same-family adults, no Gmail/restricted
scopes), but it is the highest-severity correctness item — review it hardest.

## 6. Test matrix (DoD)
- **API (vitest):** merged keyset hit + pagination across all four types; soft-delete
  tombstone (card + hub + section + block); **visibility flip → hub + its sections +
  blocks all tombstone for the dropped user** (drive a `resource_visibility` DELETE →
  assert all three re-surface as tombstones via the 0010 fan-out); **section/block of a
  restricted hub are NEVER emitted live to a non-allow-listed member** (the leak test);
  visible→invisible→visible flap re-emits children live; legacy 2-part cursor from an
  already-deployed device still round-trips after the server emits 3-part cursors;
  full-resync on an unknown cursor.
- **Client (desktopTest):** cold-start renders cached hubs, zero network; drain writes
  hubs+sections+blocks + advances the single cursor; hub tombstone removes from DB +
  store + **prunes `currentHubId`/`currentHubTree` when the open hub dies**; 403/404
  wipes the hub cache via the **same** shared `wipe()`; cursor survives restart;
  `HubStore` round-trips + corrupt-JSON guard; orphan block (hub absent) not shown;
  HubList/HubDetail snapshots byte-unchanged (DB-fed parity); narrow selector — a card
  poll doesn't recompose the hub host (and vice-versa); per-action mutation test for
  `HubsLoaded`/`HubTreeLoaded` (the `(state,Any)` reducer isn't compiler-exhaustive →
  a forgotten arm silently no-ops).
- **E2E (dev-auth, desktop):** author restricted hub → sync → author sees it; add member
  to allow-list → sync → visible; remove → next sync tombstones hub+sections+blocks.

## 7. Round-2 outcome — SIGNED OFF (decisions of record)

**Verdicts:** data-sync/security **SIGN-OFF** (all round-1 defects closed: merged
cursor retires the codec/carry-forward/prefix bugs; `(updated_at,type,id)` unique;
section/block hub-visibility join closes the leak; 0010 fan-out + atomic txn close
revocation). Spec **SIGN-OFF-WITH-CHANGES** (narrow seams below). Simplification
**SIMPLIFY-FIRST** (reuse + PR split below). No blocks.

**Adopted round-2 simplifications (these govern the plan):**
- **S1 — fold into `ContentStore`, NO separate `HubStore`.** Hub/section/block tables,
  flows, upserts live on the existing `ContentStore` (one `ContentDb` driver, one JSON
  instance, one `sync_meta` cursor, ONE `wipe()`, ONE `applyDelta` txn). Resolves the
  "shared wipe must be atomic" requirement for free.
- **S2 — delete `HubClient`.** Hubs ride the same `GET /sync` envelope via
  `SyncClient.fetchPage`; there is no separate hub endpoint to own.
- **S3 — delete `HubEngine`** (cards have no engine). The `SyncEngine` bridge dispatches
  `HubsLoaded`/`HubTreeLoaded` straight from the DB flow (single writer per slice).
  `openHub` of an uncached hub = a tiny effect `scope.launch { syncEngine.syncNow() }` +
  reducer `hubsBusy=true`; busy clears on the flow emit / drain end.
- **S4 — PR SPLIT.**
  - **PR1 = hubs-list offline + revocation. NO migration needed** — the shipped `0009
    trg_resvis_touch_hub` already bumps `hubs.updated_at`, so hub-list revocation works
    with zero DDL. Scope: `hub` table in `ContentStore`; `/sync` UNION = cards+hubs (two
    types); `hubVisible` filter → hub tombstones; 3-part cursor + legacy 2-part mode;
    `activeHubsFlow()` bridge; `SyncResponse.changes.hubs` + typed tombstones; `wipe()`
    drops hubs in the same txn; `HubsLoaded` DB-fed + reducer prune of `currentHubId`;
    **iOS pause/resume** (poll-leak fix; Android already has it at
    `apps/androidApp/.../MainActivity.kt` `repeatOnLifecycle(STARTED)`); HubList snapshot
    parity. Delivers the whole core value (offline hub render + revocation).
  - **PR2 = hub detail tree (sections/blocks).** The security-critical section/block→hub
    visibility JOIN; `0010` child fan-out (resource_visibility fan-out only — see S5);
    section/block tables; `hubTreeFlow(hubId)` → `HubTree` projection with the orphan
    filter; `HubTreeLoaded`; UNION extends to four types. Reviewed hardest (§5).
- **S5 — defer the hub-`visibility`-UPDATE fan-out trigger.** At M0 no actor flips a hub
  `family↔restricted` (authoring is ADR-0016/0029-deferred); only `resource_visibility`
  allow-list edits change who-sees-a-hub. So `0010` (PR2) only needs the
  `resource_visibility` child fan-out; the `AFTER UPDATE OF visibility ON hubs` trigger
  lands with the visibility-toggle authoring slice. (Record the gap so it's not lost.)
- **S6 — legacy 2-part cursor:** dogfood-only today (no external installs), so dropping
  the legacy branch and letting old card cursors full-resync once is acceptable — but
  keep the part-count dispatch (it's one cheap branch) unless the implementer confirms
  zero external clients. (Plan: keep, it's trivial + safe.)

**Narrow-seam checklist for the plan (round-2 cautions, all non-blocking):**
- Keyset comparison is the **row-wise** `(updated_at,type,id) > ($1,$2,$3)` (NOT
  hand-expanded AND/OR) — mirror `repo.ts:59`; add a page-boundary vitest landing
  between two rows that share `updated_at` and differ only by `type`.
- Cursor decode validates `type ∈ {card,hub,section,block}` (enum, never lexical into
  the WHERE); unknown/short/bad → full resync.
- `applyDelta` tombstone-by-`type` is **total** (unknown type ignored, never crash);
  `wipe()` stays ONE txn across all tables + cursor.
- Reconcile `ContentStore.kt:79` explicit `Schema.create` with driver-managed
  create/migrate — **drop the explicit create, let the driver own create-fresh /
  migrate-existing**; test a *populated-DB* 1→2 upgrade, not just fresh install.
- Narrow selectors: pin the exact granular `store.selectorState { it.hubs }` import +
  confirm it exists on every shipped target (granular has no iosX64 — none shipped);
  **also narrow `FeedApp.kt:66`'s whole-state `selectorState { it }`** or a hub-poll
  re-emit still recomposes Feed.
- 0010 fan-out write-amplification (O(members×children) per allow-list edit; `upsertHub`
  does DELETE+re-INSERT audience = N+M firings) is acceptable at M0 — record it; revisit
  with a statement-level trigger if hubs grow large.
- `hubTreeFlow(hubId)` is **keyed** by `currentHubId` (re-subscribe on `OpenHub`/
  `CloseHub`) — unlike the single un-keyed cards bridge; the projection assembles flat
  rows → `HubTree` (group blocks under sections by `ord,id` per `hubs.ts:119`) and
  applies the orphan filter **in the projection, not the reducer**.
