# CL-9 — Map render strategy (spike + decision)

**Date:** 2026-06-21 · **Epic:** `planning/content-detail-epic.md` §CL-9 ·
**ADRs:** 0014 (private trigger engine / location posture), 0015 (E2EE),
0006 (render-don't-reason), 0020 (read-only M0). **Guardrail 3** (restricted
data / third-party data handling).

## Decision (TL;DR)

**M0 = keep the stylized `MapStrip()` placeholder + one-tap Navigate handoff.
Already shipped — no key, no cost, no third-party coordinate leak, honest
privacy chip. No code change.** Static map *images* are the M1 path, but only
behind a new ADR (third-party map-provider disclosure) and author-time
stamping. Embedded maps SDK is rejected for the foreseeable future. Self-host
(Protomaps) is parked as an M2+ escape hatch if third-party disclosure is
ever judged unacceptable.

This records the CL-9 decision and closes the spike. The DoD ("geo card +
detail render a map affordance + working Navigate handoff within the ADR 0014
privacy posture; decision recorded") is **met**: the affordance + handoff
already exist (`MapStrip`, `CardAction.Navigate`), and this note is the
record.

## What exists today (verified in code)

- `cards/TypedCards.kt :: MapStrip()` — a 92dp stylized surface (two
  decorative "roads" from `DayfoldExtendedColors.mapBackground/mapLine/
  mapRoad`, light+dark). `clearAndSetSemantics {}` → it is correctly
  invisible to a11y (decorative, not a real map).
- Used by `GeoCard` (Now strip) and `DetailScreen :: HeroMedia` (detail hero).
- Primary action: `TypedCardLogic` → `"Navigate" → CardAction.Navigate(address|label)`.
- Handoff vetting (CL-PLAT): `cardActionUri(Navigate)` →
  `geo:0,0?q=<percent-encoded place query>` — **never coordinates**, per ADR
  0014. OS maps app resolves the query. No key, no embedded view, no position.
- **Structural invariant (document where it would be broken):** `GeoPayload`
  *does* carry `lat`/`lng` (`Model.kt`), but the Navigate URI builder
  **deliberately ignores them** and uses `address ?: label` text only. Using
  lat/lng for "precision" would be an **ADR-0014 regression** (it would put a
  real coordinate into an outbound URI). A geo card with only lat/lng and no
  address/label yields no handoff (`null`) — fail-closed, not a coord leak.

So the *renderer* question ("CSS placeholder") is already answered in M0; the
open question CL-9 actually had to settle is **whether to upgrade the
placeholder to a real map, and at what privacy/cost price.**

## The three options

### A. Stylized placeholder (current M0) — CHOSEN for M0

- **Privacy:** zero third-party calls; the place coordinate never leaves our
  stack at render. Navigate sends a *text place query* (not lat/lng) to the
  OS maps app only on explicit tap.
- **Cost:** $0. No API key, no provider account, no per-request billing.
- **Cohesion:** matches the M0 "dumb renderer" posture (ADR 0020) and the
  OG-unfurl rule (CL-2: no server fetch / no client re-fetch). Themed via the
  existing `DayfoldExtendedColors` map tokens.
- **Honesty (ADR 0014/0015):** a placeholder makes no map-data claim, so no
  honesty risk. A privacy chip can truthfully say location data stays local.
- **Cost of NOT upgrading:** lower visual richness — a generic strip instead
  of the real street layout. Acceptable for a learning-lab M0; the Navigate
  handoff carries the actual utility.

### B. Static map image (third-party provider) — M1, ADR-gated

Provider landscape (June 2026, researched):

| Provider | Free tier | $/1k static | Caching allowed? | Attribution |
|---|---|---|---|---|
| Google Maps Static | 10k/mo | **$2.00** | **NO** (image caching prohibited) | "Google" baked in, non-removable |
| Mapbox Static Images | 50k/mo | $1.00 | Yes, ≤30 days | Mapbox + OSM |
| MapTiler | 100k/mo | ~$1.50 | Paid plans | OSM/MapTiler |
| **Geoapify** | 3k/day | **~$0.06–0.10/1k** | **Yes — store/redistribute freely** | "Powered by Geoapify" + OSM (white-label on paid) |
| **Stadia Maps** | 200k cred/mo (~10k img) | $0.30–0.60/1k | **Yes — special cacheable endpoint (paid)** | OSM + Stadia |

**Provenance (checked 2026-06-21):** free-tier sizes + the caching/attribution
policy columns are `[fact:<provider pricing+ToS pages>]`; all `$/1k` figures
are `[estimate]` — Google/Mapbox/MapTiler are list prices, Geoapify/Stadia are
inferred from plan-price ÷ included credits and vary with image size/markers.
The **Google "image caching prohibited"** claim is load-bearing (it eliminates
Google from the M1 shortlist, since author-time stamping *requires* caching the
image) → `[fact:cloud.google.com/maps-platform/terms + Maps Static caching
guidance, checked 2026-06-21]`. Provider ToS/pricing drift — **re-verify at
CL-9b time**, do not treat these as durable.

- **The privacy crux (why this is ADR-class, not a task tweak):** ADR 0014
  draws a hard line — the *device's live position* never leaves, but
  *authored place coordinates* ARE family-scoped content that goes to **our
  server** (encrypted at rest). A static-map call adds a **new actor**: the
  coordinate must be transmitted to the **provider's** servers to render the
  image. This is intrinsic to *every* hosted provider above — there is no
  keyless/no-call static endpoint. That is a **third-party data flow ADR 0014
  did not authorize** and touches Guardrail 3 (third-party data handling) +
  the "privacy by architecture" brand promise. It must be an explicit ADR
  with disclosure, not a silent dependency.
- **The clean M1 design (mirrors CL-2 OG-unfurl):** stamp the static image
  **at author time** in the authoring loop (over the operator's OWN data),
  store it immutable; the **server never fetches at render (no SSRF)** and the
  **client never calls a map provider at render (no key on device, no
  render-time leak/timing oracle).** Under that model the coordinate leaves to
  the provider **once, at author time, over the operator's own data** — the
  same posture that keeps email authoring clear of CASA (Guardrail 3 analog).
  Pick a **caching-allowed** provider (Geoapify or Stadia; Google is out — it
  forbids caching the image, which breaks author-time stamping).
- **Provider-logging exposure (the actual new exposure — do not under-state):**
  the OG-unfurl analogy is imperfect. OG-unfurl fetches a URL the operator is
  *already linking to publicly* (non-sensitive by nature); a static-map stamp
  transmits a **home/school place coordinate**, which ADR 0014 flags as
  sensitive family content that must be **encrypted at rest, family-scoped, and
  never logged**. Even "once, at author time, over the operator's own data,"
  that coordinate **appears in the map provider's request logs** — every
  provider logs request params. That, not chip wording, is the real new
  exposure, and it must be assessed against ADR 0014's "never logged" property
  before CL-9b ships.
- **Honesty follow:** any privacy chip on a geo card with a real map must NOT
  imply the location stayed local — it left to a map provider at author time.
  Chip copy gets a CL-6-style audit when this lands.
- **E2EE follow (ADR 0015) — *speculative M1 detail, not M0 scope*:** the
  stamped image URL/bytes are a ciphertext-candidate field (it encodes a
  place) — mark `"x-e2e":"ciphertext"` in schema so codegen preserves it,
  consistent with `body`/place `label/lat/lng`. (Belongs in the CL-9b
  ADR/spec; recorded here so it is not forgotten.)

### C. Embedded maps SDK (Google Maps / MapLibre native) — REJECTED

- Heavy per-platform dependency (Android Maps SDK, MapKit/MapLibre iOS, a web
  map lib) — large binary, licensing, per-platform parity work; directly
  fights the "smallest correct design" and the single-`:client` KMP goal.
- A live interactive map invites render-time provider calls and can leak the
  device viewport/position — the exact thing ADR 0014 forecloses.
- No M0/M1 product need: the OS maps app (via Navigate) already gives real
  interactive maps + routing for free, on the user's own map app + account.
- **Rejected** unless a future surface genuinely needs an *in-app* interactive
  map (none planned); even then, prefer MapLibre + self-host tiles over a
  proprietary SDK.

## Self-host escape hatch (Protomaps / PMTiles) — parked, M2+

The only path where the coordinate never leaves our infrastructure even at
author time: render from OSM data locally (Protomaps PMTiles single-file +
MapLibre/tileserver-gl rasterize to PNG). Cost ≈ storage/bandwidth
("practically free" for one region) but real ops: own the renderer, style,
and a render service. Over-built for a solo learning-lab M0/M1. Hold as the
fallback if third-party disclosure (option B) is ever judged unacceptable.

## Recommendation & follow-ups

1. **M0: ship as-is** (placeholder + Navigate). No code, no spend, no new
   permission. ✅ already satisfied.
2. **M1 (deferred, ADR-gated):** `CL-9b` — author-time-stamped static map
   image. Prereqs, in order: (a) **new ADR** for third-party map-provider
   disclosure (extends ADR 0014's place-coord boundary to "+ one map
   provider, at author time"); (b) provider pick (Geoapify or Stadia —
   caching-allowed, cheap, OSM-attributed); (c) author-loop stamps the image,
   stores immutable, server/client never fetch at render (CL-2 OG-unfurl
   pattern); (d) schema `"x-e2e":"ciphertext"` on the image field (ADR 0015);
   (e) privacy-chip honesty audit (ADR 0014/0015); (f) **accept + assess the
   provider-logging exposure** — the place coordinate lands in the map
   provider's request logs at author time, which collides with ADR 0014's
   "place coords never logged" property; this is the genuine new exposure the
   ADR must weigh (beyond chip copy). **Not agent-decidable** — the ADR is
   operator-gated.
3. **No change to ADR 0014.** This spike operationalizes it; it does not move
   the line.

## Review & verification

- Doc-only; no app code changed. The load-bearing handoff invariant was
  **exercised, not asserted**: `./gradlew :client:desktopTest --tests
  PlatformActionsTest --tests TypedCardLogicTest` → **BUILD SUCCESSFUL**
  (2026-06-21), including `navigate is a percent-encoded geo query, never
  coordinates`. Other geo coverage (`FeedSnapshotTest` geo card + `detail-geo`,
  `ContentStoreTest` geo round-trip) is unaffected by a doc-only change.
- Pre-impl + final adversarial review of this note's reasoning (privacy crux,
  provider facts, option scoring) per the build loop.
