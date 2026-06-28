# Loading States — Design

**Date:** 2026-06-27
**Branch:** `worktree-loading-states` (from `main`)
**Surface:** Dayfold Compose Multiplatform client (`apps/client`, `apps/androidApp`)
**Status:** Approved (design) + folded 3-agent review — pending implementation plan
**Toolchain (verified):** Compose-MP **1.11.1**, redux-kotlin **1.0.0-alpha03**,
Kotlin 2.3.20, Material 3. (Earlier draft said CMP 1.9.3 / alpha01 — wrong.)

## Problem

Several user-triggered async actions show no feedback; the app appears frozen.
Operator-named: **sign-in** ("Continue with Google", etc.) and **sign-out**. Audit
found more — including silent *failures*, not just silent loads.

### Audit findings

State pattern today: each Redux slice has its own `*Busy: Boolean` / `*Error: String?`
flags (`authBusy`, `syncing`, `hubsBusy`, `deviceBusy`, `approvalsBusy`, …) in
`Model.kt` (`AppState`, ~L283). No shared `Loadable`/`AsyncState` wrapper. Per-action
loading (which member row is approving) is not tracked. Only two real spinners exist
(`SplashScreen` cold-start, `DeviceFinishingScreen`). No skeletons/shimmer. Theme is
Material 3 (`theme/Theme.kt`), custom Box-based `AuthButton` (not M3 `Button`). All
ops are serialized through one `Mutex` in `AuthEngine` (`AuthEngine.kt:43`) — at most
one in flight at a time.

| Action | Composable | Current | Gap |
|---|---|---|---|
| Sign in (provider) | `SignInScreen` (AuthScreens.kt:185) | buttons disabled on `authBusy`, no spinner | silent-ish |
| Sign out | `AccountScreen` (AccountScreen.kt:151) | clickable Box, nothing | **silent** |
| Create family | `CreateFamilyScreen` (AuthScreens.kt:246) | text "Creating…" | weak |
| Join invite | `JoinInviteScreen` (:90) | text "Joining…" | weak |
| Load approvals/roster | `MembersScreen` (:38) | nothing | **silent** |
| Approve/Decline/Remove member | `MembersScreen` rows (:91,:116) | nothing, no per-row flag | **silent** |
| Load devices | `DevicesScreen` (:35) | nothing, no flag | **silent** |
| Revoke device | `DevicesScreen` row (:86) | nothing, no per-row flag | **silent** |
| Enter code / approve device | `DeviceApprovalScreens` (:151,:209) | text "Checking…/Working…" | weak |
| Feed initial sync | `FeedScreen` (:44) | text "Syncing…" | weak |
| Feed pull-to-refresh | `FeedScreen` | not implemented | **missing** |
| Feed error retry | `FeedScreen` (:125) | no busy on retry | weak |
| Load hubs / hub detail | `HubScreens` (:71,:200) | text "Loading hubs…/Loading…" | weak |
| Load hub audience | `WhoCanSeeSheet` (:229) | nothing | **silent** |
| Cold-start restore | `SplashScreen` | CircularProgressIndicator | ✅ ok |
| Device resume (deep link) | `DeviceFinishingScreen` | CircularProgressIndicator | ✅ ok |

## Decisions (operator-approved)

1. **Scope:** full coherent pass — fix silent loads, upgrade text-only states, add
   list/content skeletons + pull-to-refresh, **and** add first-class error + empty
   states (silent failures are part of the "doing nothing" feel). One consistent
   loading vocabulary.
2. **Vocabulary:** full Material 3 kit — button-busy, shimmer skeletons (with
   reduced-motion fallback), `PullToRefreshBox` + top `LinearProgressIndicator`,
   full-screen splash, reusable components.
3. **State model:** keep the `*Busy/*Error` boolean pattern; add a single nullable
   **op-id** per domain where per-row busy is needed (ops are Mutex-serialized → at
   most one in flight, so no `Set` needed). No `Loadable<T>` refactor.

## Principles (Material 3 + mobile UX)

- Acknowledge every tap in <100ms — action buttons flip to busy instantly.
- Skeletons where layout is known (lists, feed cards); spinners where it isn't
  (button actions, blocking restores). Inline over full-screen — block the whole
  screen only when it can't exist yet (cold-start restore).
- Every loading region resolves 3 ways: **content / empty / error**. A skeleton that
  resolves to a blank screen reads as a hang.
- No flash: screen-level skeletons gated by a **delay-show ~200ms, no min-floor**
  helper (lingering skeletons feel slower). Button-busy is exempt (it is the press
  acknowledgement).
- Accessible: busy buttons carry `semantics { stateDescription = "Busy" }`; skeletons
  are `invisibleToUser()` with ONE `liveRegion = Polite` node announcing "Loading …";
  shimmer respects reduced-motion; focus is placed sanely after teardown/route change.
  All states are network → indeterminate indicators.

## Component kit — new package `client/.../ui/loading/`

All `commonMain` (Compose-MP `rememberInfiniteTransition` for shimmer). Reusable,
unit/snapshot-testable.

| Component | Responsibility | Notes |
|---|---|---|
| `AuthButton(busy: Boolean)` (extend existing) | tapped provider/action button | label stays; leading glyph → ~20dp `CircularProgressIndicator` strokeWidth 2dp in `content` color; `enabled=false`; stable width (no reflow); `stateDescription="Busy"` |
| `RowBusy()` | spinner sized to the row's icon, wrapped in the same **48dp** touch box | replaces the trailing affordance without layout jump; sibling row actions disabled while in flight |
| `Modifier.shimmer()` | animated sweep brush; **static dimmed fill when reduce-motion on** | reduced-motion via `expect/actual` (Android `ANIMATOR_DURATION_SCALE`, iOS `isReduceMotionEnabled`, Web `prefers-reduced-motion`) |
| `SkeletonBox` + `ListSkeleton(rows, rowHeight)` + `FeedSkeleton` | placeholder layouts mirroring real content **metrics** (avoid layout shift on swap) | `invisibleToUser()`; one wrapping `liveRegion` "Loading …" |
| `ErrorRetry(message, onRetry, retrying)` | inline error text + Retry button (button-busy) | one component reused across feed/hubs/members/devices/audience |
| `EmptyState(icon, title, body)` | empty-list presentation | feed/hubs/members/devices |
| `FullScreenLoading` | center mark + spinner | **`SplashScreen` refactored to use it** (no third twin) |
| `rememberStableLoading(flag): Boolean` | anti-flash: delay-show ~200ms, no min-floor | screen-level skeletons only |

`PullToRefreshBox` (`androidx.compose.material3.pulltorefresh`, `@OptIn(ExperimentalMaterial3Api)`)
wraps the FeedScreen list for the manual gesture; a top indeterminate
`LinearProgressIndicator` covers programmatic/background refresh (resume, post-mutation).

## State changes (`Model.kt` + `Reducer.kt` + `AuthEngine.kt` + actions)

Additive; existing flags untouched.

- `pendingProvider: String?` — which sign-in button spins. Already carried by
  `SignInRequested(provider)`; cleared by `SignInSucceeded`/`SignInFailed`.
- `signOutBusy: Boolean` — set on `SignOutRequested` (today a reducer no-op);
  needs **no** failure path — `signOut()` always dispatches `SignedOut`, which resets
  to `AppState()` (auto-clears).
- `deviceListBusy: Boolean` — device *list* load (renamed to avoid colliding with the
  existing device-*approval* `deviceBusy`). Needs new Requested + Failed actions.
- `audienceBusy: Boolean` — WhoCanSee audience load. Needs a new **AudienceFailed**
  action (today only `HubAudienceLoaded` exists → on failure the sheet spins forever).
- roster load — surface the existing/added busy flag for `MemberListSkeleton`; needs a
  Failed action for the error state.
- `memberOpId: String?` / `deviceOpId: String?` — id of the row currently
  approving/declining/removing (member) or revoking (device). Single nullable, not a
  Set, because the `Mutex` serializes ops.

**New actions required** (review P0 — current code has no per-row Requested actions and
several failure paths drop the id):
- `MemberOpRequested(uid)` / `MemberOpFailed(uid?)`, `MemberRemoveRequested(uid)` /
  `MemberRemoveFailed(uid?)`; `DeviceRevokeRequested(id)` / `DeviceRevokeFailed(id?)`;
  `DevicesRequested` / `DevicesFailed`; `RosterRequested` / `RosterFailed`;
  `AudienceFailed`. `AuthEngine` dispatches the Requested action before each call and
  the Failed action in the catch.

**Reducer:** set the op-id / boolean on the Requested action; clear it on the matching
success, the matching Failed, or a reload (`RosterLoaded`/`DevicesLoaded`). Because at
most one op is in flight, clearing on any terminal action of that domain is safe.

**Recompose hygiene:** pass each row a plain `Boolean` (`uid == memberOpId`) so
unrelated rows don't recompose. (`FeedApp.kt` currently selects the whole tree, so this
adds no new churn; immutable `copy` keeps equality value-based.)

## Screen application

- **Sign-in:** `pendingProvider` → that `AuthButton` busy, others `enabled=false`.
- **Sign-out:** confirm → `signOutBusy` button-busy + immediate route change; teardown
  behind the new screen. **No scrim.**
- **Create-family / join / enter-code / approve-device:** text-morph → `AuthButton(busy=)`.
- **Feed:** initial load → `FeedSkeleton` via `rememberStableLoading`; `PullToRefreshBox`
  for the gesture (driven by a refresh flag distinct from initial `syncing`); top
  `LinearProgressIndicator` for programmatic refresh; load error → `ErrorRetry`; no
  cards + loaded → `EmptyState`.
- **Hubs list/detail:** "Loading…" text → `ListSkeleton` / detail skeleton; error →
  `ErrorRetry`; empty → `EmptyState`.
- **Members / Devices:** list load → `ListSkeleton`; per-row `RowBusy` via the op-ids;
  error → `ErrorRetry`; empty → `EmptyState`.
- **WhoCanSeeSheet:** small spinner on `audienceBusy`; `AudienceFailed` → inline retry.

## Verification

- **Fake backend latency knob:** add `latencyMs` to `FakeBackendData`
  (`FakeBackend.kt:49`) and `delay(it)` in the **suspend** MockEngine adapters
  (`desktopMain/.../fake/FakeHttpClient.kt:24` and
  `androidApp/src/debug/.../FakeBackend.kt:26`) — NOT in the pure non-suspend
  `handle()`. Debug-only, zero prod surface. Reuse scenarios `sync-error`,
  `owner-approvals`, `busy-family`, `empty-new`. Desktop `DAYFOLD_API=fake://…`;
  Android debug drawer.
- **`rk` snapshot PNGs** + `apps/scripts/ondevice-demo.sh` for on-device visual check.
- **Tests:** reducer tests for op-id set/clear across success **and** failure actions;
  Compose snapshot tests (`runComposeUiTest` + `captureToImage()`, per
  `AuthScreensSnapshotTest.kt`, ADR 0036) for `ListSkeleton`/`FeedSkeleton`/
  `ErrorRetry`/`EmptyState` and the busy `AuthButton`/`RowBusy`. Assert skeleton bounds
  ≈ loaded bounds (no layout shift).

## Out of scope

- No `Loadable<T>`/sealed `AsyncState` refactor (deferred).
- No new network behavior; no optimistic updates (approve/remove stay
  spinner-then-resolve).
- No changes to non-client surfaces (api, cli).

## Review trail

Three parallel agents reviewed the first draft: mobile-UX/M3, Compose+redux
correctness, simplification/YAGNI. Folded: factual version fixes, op-id (not Set) +
the missing Requested/Failed actions, sign-out scrim removed, anti-flash single
strategy, a11y (busy semantics / reduced-motion / liveRegion), refresh split
(gesture vs programmatic), latency-knob placement, component consolidation. Added by
operator decision: first-class error/empty states; shimmer kept with reduced-motion
fallback.
