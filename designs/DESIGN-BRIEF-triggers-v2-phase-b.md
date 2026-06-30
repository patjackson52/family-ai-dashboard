# Design Brief / Prompt — Phase B: Background Proximity & Local Notifications (v2, OPT-IN)

**Hand this whole file to a fresh Claude Code (Claude Design) session.** It is
self-contained. Authoritative sources:
`../adr/0044-phase-b-background-location-and-local-notification-posture.md`,
`../adr/0043-now-content-model-derived-plus-authored.md` (§Phasing + §2b),
`../adr/0014-private-trigger-engine.md`,
`../adr/0028-geocoding-and-location-privacy-tiers.md`,
`../adr/0024-settings-privacy-posture.md`,
`../adr/0009-design-system-m3-expressive-adaptive.md`. Reuse the existing
`Design-System.dc.html`, the signed-off `now-derived/` feed, and the
**first-pass** trigger mockups in
`Family AI dashboard design brief/designs/triggers/` (which you are REVISING to
v2 — they have an unresolved P0 honesty bug + missing states, listed in §6
below).

---

## 0. How to run this

> **You are designing the hi-fi UI/UX for Phase B of Dayfold's "Now" surface:
> background proximity + LOCAL notifications, as a fully OPT-IN, reversible
> feature.** Use the `frontend-design` skill. Produce **interactive HTML/CSS
> prototypes** that faithfully emulate **Material 3 Expressive** — reuse the
> tokens/type/shape/motion/components from `Design-System.dc.html` and the
> signed-off `now-derived/` feed; **do NOT invent a new system**. Mobile-first
> (~390–430px), **light + dark for every screen**. Map every component 1:1 to
> its M3 Compose name. Commit to `designs/triggers/` (revise the existing files
> in place + add the new Phase-B screens; update `index.html`). **Visuals only —
> no app code.** This is the ADR 0008 gate for ADR 0043 **Phase B** (the
> background-location posture itself is ratified in ADR 0044; this brief is the
> remaining design sign-off).

## 1. What this feature is (and what Phase A already shipped)

Phase A (shipped) is the **foreground, in-feed** two-lane "Now" feed: an
on-device engine derives + ranks calm, timely items while the app is open. Its
designs are signed off in `now-derived/` — **reuse them; do not redesign them.**

**Phase B is the closed-app story.** When Dayfold is closed, an on-device
background worker reacts to OS **geofence** region-enter events (and scheduled
times), runs the *same* engine, and posts the top-K as **LOCAL notifications**
(Android `NotificationManager` / iOS `UNUserNotificationCenter` — **no server
push, no FCM/APNs**). Privacy is structural: the device's **live position never
leaves the device**; only the *saved places* (home/school/store) are encrypted
family content on the server (ADR 0014). The feature is **few, timely, earned**
interruptions under quiet-hours + a daily cap — never a stream.

## 2. THE FRAMING THAT DRIVES THIS BRIEF: opt-in, progressive, reversible

This is the operator's explicit direction. **Background proximity is an opt-in
upgrade the user reaches deliberately — never a default, never up-front, always
reversible.** Design every surface to make that true and obvious:

- **Default-off.** A new user gets the foreground feed (Phase A) with **no
  background permission asked**. Nothing nags them toward "Always."
- **Progressive ladder, shown honestly:** when-in-use (foreground "what's
  nearby while the app is open") → **explicit opt-in upgrade** to "Always" for
  background proximity. Each rung states plainly what it *does* and *doesn't*
  do. Reserve "the moment you walk in / without opening the app" language for
  the **Always** rung only — when-in-use cannot deliver it.
- **The value comes before the ask.** Design an honest **primer** that earns the
  upgrade (what you get, what it costs in battery/permission, the on-device
  promise) — a calm explainer, not a pressure screen. Primary + secondary CTAs
  as an M3 **button group / split button**; "Not now" is a peer, not greyed-out.
- **Reversible + visible state.** A **Settings toggle** ("Background proximity")
  turns it off; turning it off de-registers all geofences and says so. Show the
  on / off / "limited (when-in-use only)" / "denied" states. The OS permission
  state is **device-local and never syncs** (ADR 0024) — never show it as a
  family/account setting.
- **No dark patterns.** No engagement-bait, no red badges/counts, no
  retention/cancellation friction. Honest provenance on AI-authored content
  ("Added by Claude").

## 3. PvP — the privacy promise must be VISIBLE and TRUE (fixes the P0 bug)

The first-pass mockups shipped a **dishonest** claim. Fix it everywhere:

- ❌ NEVER: "saved place coords never leave the device", "stored on-device; live
  location never recorded", `cloud_off` over places, "Nothing is sent."
- ✅ The true two-part story: *"Saved places sync to your family, encrypted.
  Only your **live position** — where you are right now — stays on this phone
  and is matched on-device."*
- The reusable affordance chip reads **"Matched on your device"** (not
  "Location never leaves"); the "your live position never leaves this phone"
  line is true and may stay. Add a **one-time consent beat** when a user first
  saves home/school: "shared with your family, stored encrypted."
- Tag geo-proximity + background frames truthfully; the on-device promise is
  **strongest offline** — design that as a strength.

## 4. Screens & states to produce (light + dark each; revise existing + add new)

**A. The opt-in permission ladder (the heart of this brief)**
1. **When-in-use priming** — honest, foreground-only value ("see what's nearby
   while Dayfold is open"). Does NOT promise background.
2. **The "Always" upgrade primer** — the deliberate opt-in: the background value
   ("get a calm nudge the moment you arrive, without opening the app"), the
   honest tradeoff (always-permission + battery), the on-device promise, the
   App-Store-style justification tone. Softer glyph (`my_location`/`pin_drop`,
   not `radar`). Button-group CTAs; "Not now" is a calm peer.
3. **Notification priming** — why, what it looks like, quiet-hours + daily-cap
   preview ("a few, timely — never a stream").
4. **Limited / denied states** — when-in-use only → honest "open the app to see
   what's nearby" (no nagging); denied → graceful fallback to the foreground
   feed; a clear, friction-free path to change it later.

**B. LOCAL notifications + lock-screen (the closed-app payoff)**
5. **Lock-screen + notification-shade** appearance, **Android + iOS**: a
   proximity notification ("Near Safeway — party list?") and a time notification
   ("Soccer 4pm — pack jackets"). Calm copy, source attribution, the "matched on
   your device" affordance in the expanded view.
6. **Tap → deep-link** into the exact card/Hub block — reuse the Phase-A
   `now-derived/` deep-link arrival pulse (`CardAction.OpenHub`).
7. **Grouping / quiet-hours / daily-cap** — how "few, timely" looks as a calm
   digest-style group, never a stream; the quiet-hours-suppressed and
   cap-reached states ("you're all caught up — more in the app").

**C. Settings — the reversible control**
8. **Background-proximity settings** — the on/off toggle, the current-permission
   row (with the honest privacy line), **quiet-hours editor**, **daily-cap
   chooser**. These config values are **device-local, never synced** — never
   framed as a family setting. Off → "geofences removed; you'll still see
   nearby items when the app is open."

**D. Offline / cross-cutting**
9. **Offline trigger surfacing** (the MISSING §15 state) — time triggers still
   fire + geo still matches on-device offline; only deep-link content/map tiles
   degrade. "Offline · still matched on your device" tonal banner (privacy
   teal). Make the on-device promise feel strongest here.
10. **The reusable "matched on your device" affordance** — the chip/info-row/
    sheet used across A/B/C, with the honest two-part copy from §3.

## 5. Modern M3 Expressive (the four May-2025 signatures the first pass missed)

- **Physics motion:** replace CSS keyframe pulses with `MotionScheme.expressive()`
  — **spatial spring** (overshoot) on the active-card container/elevation lift;
  **effects spring** (high-damp) on the teal color crossfade + halo. Collapse to
  the 4 Design-System motion tokens.
- **New components:** Places add = a **FAB Menu** expanding to home/school/store/
  other; **Loading indicator** (waveform) on save + map-resolve + deep-link
  fetch; priming CTAs as a **button group / split button**.
- **Shape morph:** on proximity-active, **morph the card shape** synced to the
  spatial spring.
- **Emphasized type:** the M3E **emphasized** role on countdowns / imminent
  chips.

## 6. What you are FIXING from the first pass (3-agent review, INB-13 §6b)

The first pass (`triggers/` — `Notifications`, `Content-Triggers`, `Permissions`,
`Permission-Phone`) shipped 14.5/15 screens, calm + token-consistent, but:
- **P0 honesty bug** (§3 above) — rewrite all dishonest place/privacy copy.
- **P1 permission honesty** — when-in-use must not over-promise background.
- **P0 missing offline state** (#9 above) — add it.
- The four M3E signatures (§5) — add them.
- **a11y:** wrap all halo/pulse in `@media (prefers-reduced-motion)` → static
  ring; privacy chip ≥11px; 48dp hit-slop on the radius slider thumb; softer
  hero glyph.

**Note:** the Phase-A in-feed geo-active state, the content trigger *chips*, and
Places management already exist (signed off in `now-derived/` and/or the
first-pass `triggers/Places`/`Content-Triggers` — keep Places, just fix its
copy). **This brief's NEW work is the opt-in ladder (A), the closed-app
notification/lock-screen surface (B), the reversible settings (C), and the
offline state (D).**

## 7. Constraints & non-goals

- **Visuals only** — no app code, no real logic; light interactivity welcome.
- **Calm is a hard constraint** — no counts/badges, no urgency-red, no stream.
  Design the cap + quiet-hours, not a feed.
- **Honest privacy** — §3 copy must be true in every visual; no server-tracking
  iconography; provenance on AI content.
- **Opt-in is the spine** — every surface must read as deliberate, reversible,
  default-off.
- **Reuse, don't reinvent** — pull from `Design-System.dc.html` + `now-derived/`;
  reuse the deep-link arrival state; map every component to its M3 Compose name.
- **No server push** — these are LOCAL notifications; no FCM/APNs iconography or
  "delivered from the cloud" framing.

## 8. Definition of done

- The full opt-in ladder (A1–A4), light + dark, clickable from `index.html`.
- LOCAL notification + lock-screen designs for **both Android + iOS**, with the
  deep-link-on-tap state (reusing the `now-derived/` arrival pulse).
- The reversible background-proximity **settings** surface (toggle + quiet-hours
  + daily-cap), framed as device-local.
- The **offline** trigger-surfacing state.
- The honesty-fixed reusable "matched on your device" affordance, shown in
  context.
- All §6 first-pass fixes applied; the four §5 M3E signatures present.
- Calm / honest / opt-in / provenance constraints visibly satisfied → the
  operator can approve (ADR 0008 sign-off).
