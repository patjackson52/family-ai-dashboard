# CL-6 — Client UI: DetailScreen + redux nav (design)

**Epic:** `planning/content-detail-epic.md` (CL-6) · **Design:** `designs/content/
Detail-Phone.dc.html` (signed off, INB-16) · **ADR:** 0008/0009 / 0013 (redux nav
via middleware) / 0020 (read-only) / 0022 · **Depends:** CL-4 typed `Card`, CL-5
cards + `CardAction`, CL-PLAT effect layer — all on `cl-next`.

## Goal

Full-screen per-type detail, reached by tapping a card; back returns to the feed.
Wire the `CardAction.OpenDetail` intent (emitted by CL-5, no-op'd by CL-PLAT)
through the **redux store** so nav is app state (ADR 0013), not a side channel.

## Nav model — a stack, not a scalar (review best-practice)

- `AppState.detailStack: List<String> = emptyList()` (card ids; top = current
  detail; empty = feed). A stack so related-edges (CL-8) can chain detail→detail
  and back pops one level; survives process death as plain state.
- Redux actions (Model.kt `Action`): `NavToDetail(cardId)` (push if not already
  top), `NavBack` (pop last). Reducer handles both; no middleware needed at M0
  (pure state) — the ADR 0013 middleware routing is for *effects*, which stay in
  CL-PLAT.
- Selector `currentDetailCard(state): Card?` = top-of-stack id resolved against
  `state.cards`; **null if the card is gone** (synced away) → host shows feed
  (graceful; stale id is harmless).

## Host wiring (FeedApp)

`FeedApp(store, onPlatformAction)` builds one handler:
`OpenDetail → store.dispatch(NavToDetail)`, everything else → `onPlatformAction`
(the shell's `PlatformActions::perform`). Then: `currentDetailCard != null` →
`DetailScreen(card, onBack = dispatch(NavBack), onAction = handler)` else
`FeedScreen(state, handler)`. The same handler powers detail actions (Navigate/
Call/Reply/Copy/Share are real handoffs now; related→nav lands with CL-8).

## DetailScreen layout (per the mockup)

Colored **hero header** (accent container bg): back + share (`CardAction.Share`)
+ overflow (M0: omit/no-op); accent tile; kicker chip; title (Outfit 27); sub.
Scrollable body:
1. **Per-type hero media** (`when card.type`): file = page-count preview block;
   link = OG card (domain/title/desc, **no fetch** — authored only); invite =
   date block + when/place/host + **display-only** RSVP; contact = avatar +
   name/role + reach buttons (Call/Text/Email handoffs); geo = stylized map strip
   (reuse CL-5) + ETA + Navigate; email = from/avatar + body excerpt + attachment
   chip(s).
2. **Actions row**: per-type pills — primary + **safe handoffs only**
   (Open/Navigate/Call/Reply/Copy/Share). **Exclude Add-to-Hub / Save / RSVP-
   write** (backend mutations, no M0 write path — ADR 0020).
3. **DETAILS** meta list: derived label/value rows from the payload.
4. **PROVENANCE + PRIVACY** chips: `sourceLabel` + the **honest** privacy mapping
   from CL-5 (`privacy.storage` enum → enforced copy) — NOT the mockup's literal
   "Location never leaves" (INB-13 honesty bug: place coords are family content;
   only live position is local).
5. **RELATED** rows — **deferred to CL-8** (omit the section at M0).

## Shared chrome (DRY)

Promote CL-5's `AccentTile` / `KickerChip` / `accentColors` / `ProvenanceChip` /
`PrivacyChip` (+ a `privacyLabel` fn) from `private` → `internal` in TypedCards so
DetailScreen reuses them (one source; no duplicate styling). No behavior change to
CL-5 cards (snapshots must stay identical).

## Cuts / scope (M0)

- **No container-transform / shared-element / predictive-back** — that's CL-7.
  CL-6 is an instant feed↔detail swap (AnimatedContent optional, plain swap fine).
- **No related-edges** (CL-8). **No adaptive two-pane** (CL-10, design-blocked).
- **No bottom-nav shell** yet (single feed↔detail pair); the nav-shell task is
  noted (epic) — DetailScreen is full-screen over the feed for M0.
- Overflow menu: omitted (no M0 actions belong there that aren't already pills).

## Security / privacy / a11y

- Read-only (ADR 0020): every detail action is an OS handoff or in-app nav; RSVP
  display-only; **no Add-to-Hub/Save**. Actions flow through the same vetted
  `CardAction` → `cardActionUri` seam (CL-PLAT) — no arbitrary intents.
- Privacy chip = enforced honest copy (ADR 0014/0015); link/email show authored
  content only (no OG/favicon/mail fetch).
- a11y: back button ≥48dp + contentDescription; reach buttons ≥48dp + labels;
  decorative tiles `clearAndSetSemantics`; AA contrast on the colored hero
  (`onXxxContainer` text).

## Files

- `cards/DetailScreen.kt` (new) — DetailScreen + per-type hero composables +
  DETAILS/meta derivation.
- `cards/DetailMeta.kt` (new, pure) — `detailMeta(card): List<MetaRow>` +
  `detailActions(card): List<DetailAction>` (label + CardAction + style),
  unit-tested (golden-stable, no PNG).
- `Model.kt` — `detailStack` + `NavToDetail`/`NavBack` actions.
- `Reducer.kt` — nav reducer cases. `Selectors.kt` — `currentDetailCard`.
- `FeedApp.kt` — host: split OpenDetail→nav vs platform; feed↔detail switch.
- `TypedCards.kt` — chrome `private`→`internal`.

## Test plan (`desktopTest`)

1. Reducer: NavToDetail pushes (dedup top), NavBack pops, empty stack = feed.
2. Selector: currentDetailCard resolves top id; null when card absent.
3. Pure `detailMeta`/`detailActions` per type: right rows; actions exclude
   mutations (no Add-to-Hub), only safe `CardAction`s; missing payload → no NPE.
4. Snapshot: DetailScreen for each of the 6 types, light + dark (PNG, manual
   review).
5. Existing 54 tests + CL-5 snapshots stay green (chrome refactor is behavior-
   neutral).

## DoD

Detail renders for all 6 types light+dark matching the mockup; open via tap
(OpenDetail→NavToDetail) + back via NavBack, both through the store; no
backend-write affordance; `:client:desktopTest` green; Android + iOS-sim compile.

## Review-driven decisions / M0 cuts (folded from both review rounds)

- **Nav stack hardening:** `CardsLoaded` prunes synced-away ids from `detailStack`
  (no orphan); `currentDetailCard` null → feed; re-tap of the top is deduped.
  Not persisted → cold start returns to feed (no process-death restore at M0).
- **Honest privacy:** geo cards must be authored `on_device` (coords are cached
  family content), never `location_local` — that enum is reserved for *live
  position* (ADR 0014). The chip renders the enforced enum; no test/snapshot
  authors a false geo claim.
- **Hardware/gesture back = DEFERRED to CL-7.** The `ui` module has no plain
  `BackHandler` composable at Compose-MP 1.9.3 (only the back-gesture dispatcher
  internals); CL-7 already owns `PredictiveBackHandler` and will wire
  hardware/interactive-pop → `NavBack` there. M0 back = the on-screen "← Back".
  **Interim UX wart:** Android hardware-back from detail exits the app until CL-7.
- **Generic `InfoPanel` hero (M0 cut):** the per-type hero MEDIA is a single
  neutral data panel (+ the geo `MapStrip`), not the mockup's distinct heroes
  (contact/email avatar, invite date-block, file page-preview, link OG card). All
  the right DATA renders, light+dark, with correct accent/actions/privacy; the
  distinct hero visuals are a fidelity follow (with CL-0b glyphs / CL-9 map),
  consistent with CL-5's "layout fidelity is the bar, glyphs are a follow".
- **Perf follow (pre-existing):** `FeedApp` still subscribes to the whole state
  (`selectorState { it }`, the CL-5 pattern); scoping it (feedCards vs
  currentDetailCard) to shrink recomposition is tracked, not done here. The
  OpenDetail/platform handler is `remember`ed so feed/detail stay skippable.

## Risks

- Chrome `private→internal` refactor could shift CL-5 snapshots — re-run + eyeball
  the typed-cards PNGs to confirm pixel-identical.
- Detail is a big composable — keep per-type heroes small; richer media (real PDF
  thumb, map) are CL-9/later follows.
