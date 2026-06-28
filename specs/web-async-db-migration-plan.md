# Plan — SQLDelight sync → async DB migration (the web-client DB prerequisite)

**Status: PLANNED, not started.** Prerequisite for a *functional* Compose-for-Web
(`wasmJs`) client (see `context/open-questions.md` → `OQ-web-target`). Do this
**only when web is greenlit** — it changes the shipping DB layer (Android/desktop/iOS)
for a target not yet committed, so it's premature to run speculatively.

## Why

The only web SQLDelight driver is `app.cash.sqldelight:web-worker-driver` (2.3.2,
wasmJs-published ✓), and it is **async**. SQLDelight will only generate async query
APIs when `generateAsync = true`. The current DB layer is **synchronous**, so the web
driver can't be used until the generated queries (and their call sites) are async.

Good news from sizing it (2026-06-28): the surface is **small** and the sync drivers
keep working — SQLDelight runs async-generated code on a synchronous driver (it
completes immediately). So Android/desktop/iOS keep `AndroidSqliteDriver` /
`JdbcSqliteDriver` / `NativeSqliteDriver`; only the **code** becomes suspend.

## Exact surface (verified against `ContentStore.kt` + callers)

1. **`apps/client/build.gradle.kts` → `sqldelight { databases { create("ContentDb") { … } } }`**
   add `generateAsync.set(true)`. (Keep the `sqlite-3-38-dialect` + `verifyMigrations`.)

2. **`ContentStore.kt` — make the one-shot queries + writes `suspend`** (flows are
   already async-friendly, leave them):
   - `activeCards()` → `suspend fun activeCards()` using `q.activeCards().awaitAsList()`.
   - `cursor()` → `suspend fun cursor()` using `q.getCursor().awaitAsOneOrNull()`.
   - `applyDelta(…)` → `suspend` (the insert/update/delete `.execute()`/transaction calls
     become suspend under `generateAsync`).
   - `wipe()` → `suspend`.
   - **Unchanged:** `activeCardsFlow()` / `activeHubsFlow()` / the `asFlow().mapToList()`
     reactive queries — `coroutines-extensions` flows already model async.

3. **Callers — thread `suspend`** (most are already in coroutine/suspend scope):
   - `SyncEngine.kt`: `contentStore.cursor()` (line ~101) and `contentStore.applyDelta(…)`
     (line ~102) are inside the suspend `loadPage` (it `await`s `syncClient.fetchPage`) —
     just add `await`/keep them in scope. `activeCardsFlow().collect{…}` / `activeHubsFlow`
     unchanged.
   - `wipe()` is called via a `clearCache: () -> Unit` lambda passed to `AuthEngine` (the
     iOS/desktop/android shells pass `clearCache = { cs.wipe() }`). **Change `AuthEngine`'s
     param to `suspend () -> Unit`** — both invocations are *already* suspend
     (`AuthEngine.signOut()` line ~113, `loadMemberships()` line ~294), so the lambdas just
     become suspend lambdas (Kotlin infers); no shell restructuring. **Confirmed clean.**
   - `activeCards()` — **confirmed used only in tests** (real code reads `activeCardsFlow`).
     `SyncEngineTest` already wraps every case in `runBlocking`, so the now-suspend
     `applyDelta`/`activeCards`/`cursor` calls work as-is — **no test rewiring.**
   - **The only compile-time unknown is the SQLDelight-async API itself** (the `transaction {}`
     block under `generateAsync`, and the exact `awaitAsList`/`awaitAsOneOrNull`/`.await()`
     names) — resolve by following the compiler.

4. **Startup is unchanged.** The shells construct `ContentStore(DriverFactory().createDriver())`
   eagerly (desktop `Main.kt`, android `MainActivity`); `createDriver()` stays sync — only the
   *queries* become suspend, run later in coroutines. No startup refactor needed for the
   existing platforms.

## Verification (all required before PR)

- `:client:desktopTest` green (the engine + ContentStore + feed tests exercise this path).
- `:androidApp:assembleDebug` compiles (Android target).
- iOS targets compile (`:client:compileKotlinIosArm64` / simulator) — no device run available;
  rely on compile + the shared commonMain coverage.
- Smoke-install on the Pixel + verify sync→render still works (the DB behavior is identical;
  this confirms async-on-`AndroidSqliteDriver` at runtime).

## Then (web, separate work)

Once async lands: add the `wasmJs { browser() }` target + `wasmJsMain` deps
(`web-worker-driver`, ktor wasmJs/js engine, coil), write the 6 actuals
(`DriverFactory.wasmjs` = `WebWorkerDriver` + a bundled sqlite wasm worker; `PlatformActions`
= `window.open`/`tel:`/`mailto:`; QR trio = unsupported stubs), the `index.html` + `main()`
(`ComposeViewport`), and a deploy path. Adaptive layout per
`designs/DESIGN-BRIEF-full-adaptive.md` (operator-gated, ADR 0008).

## Risk / rollback

Bounded + behaviour-preserving (async-on-sync-driver completes synchronously), but it does
touch the shipping DB API. If any caller can't be made suspend cleanly, or an Android runtime
regression appears, revert is a single `generateAsync` flip + the suspend signatures. Don't
ship without the Pixel smoke check.
