# ADR 0019: Client Observability & Tooling (redux-kotlin devtools, snapshots, debug, CLI)

## Status

**Proposed** 2026-06-19. Operator-gated (extends ADR 0013; the operator
maintains reduxkotlin). Composes with ADR 0013 (KMP/CMP + redux-kotlin) and the
findings in `research/reduxkotlin-1.0-feedback.md`.

## Context

ADR 0013 chose redux-kotlin partly *because* redux affords devtools,
time-travel, and a debuggable `f(store.state) → UI` model — but nothing codified
**how/when** we adopt that tooling, and there were no tests or decisions for it.
The client is now on redux-kotlin `1.0.0-alpha01` with `store.selectorState{}`.
Some tooling is adoptable today; some depends on reduxkotlin modules not yet on
Maven Central.

## Decision

Adopt client observability in two tiers — **now** (what's published) and
**when-published** (gated on reduxkotlin releases) — and back each with tests.

**Now (implemented):**
1. **Debug middleware** — `applyMiddleware(loggingMiddleware)` on the store logs
   every action + state delta (`createAppStore(debug = true)`; off in release).
   This is the interim stand-in for the devtools module.
2. **Compose UI snapshot tests** — `runComposeUiTest { … captureToImage() }`
   renders `FeedScreen(state)` off-screen (headless, no device) and captures
   pixels. `FeedSnapshotTest` establishes the pipeline.

**When-published (gated on reduxkotlin — tracked in the feedback doc / INB-15):**
3. **DevTools integration** — wire the real `redux-kotlin-devtools` enhancer
   (action log, time-travel) once it ships; it replaces/augments the debug
   middleware. Time-travel also underpins the card→block deep-link (ADR 0013).
4. **Golden-image snapshot diffing** — add Roborazzi (Android) / a desktop
   golden harness so snapshots *assert against goldens* in CI, not just capture.
   Adopt any reduxkotlin screenshot helper if/when published.
5. **redux-kotlin CLI** — adopt for store/reducer scaffolding + devtools driving
   once published; wire into the agent-build flow (ADR 0012) if it fits.

**Test policy:** every store-driven surface gets (a) reducer unit tests, (b) a
Compose snapshot, and — once #4 lands — a golden assertion in CI.

## Rationale

The debug middleware + snapshot capture are cheap, real today, and give
immediate debuggability + regression coverage of the `f(store.state)→UI` path.
Deferring devtools/golden/CLI to when-published avoids depending on
unpublished artifacts while keeping a clear adoption path — and the integration
gaps feed back into reduxkotlin 1.0.0 (INB-15).

## Consequences

Positive: actions/state are observable in dev; UI snapshots guard rendering; a
documented path to full devtools/time-travel. Negative: the debug middleware is
a temporary stand-in; golden diffing + devtools are pending external releases
(can't be CI-enforced until then); snapshot golden files add repo weight when #4
lands.

## Revisit Trigger

`redux-kotlin-devtools` / a screenshot module / the CLI publish to Maven Central
(adopt #3–#5); or time-travel becomes load-bearing for the deep-link surface.
