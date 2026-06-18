# Values & Direction (Operator-Owned)

**This file is set by the operator.** Agents never edit it directly — they
propose changes via `backlog/operator-inbox.md`. The planning loop treats
this file as its highest-priority context after live operator instructions.
Ratified 2026-06-18 from operator interview (BOOTSTRAP Phase 1).

## North star

**Learning-lab (primary) + durable side income (co-goal).**

- **Maximize:** learning — agentic build patterns (AI loops, scheduled
  tasks, content-API-fed UI), Compose Multiplatform reach, and the craft of
  a calm, genuinely useful family product. The build is the curriculum.
- **Then:** durable side income — a small subscription base of real
  families paying because the product quietly saves them time. A sellable
  asset is a bonus, not the driver.
- **Trade away:** speed-to-monetization, venture-scale ambition, and any
  feature that adds steady-state ops load without proportional learning or
  retention.

**Opportunism clause:** if strong signals emerge for a sellable-asset path
or a B2B2C angle (e.g. schools, family-services brands), surface the signal
at the next viability review and propose a lean — the operator decides the
pivot.

## Confidence bar

**MODERATE.** Build/spend is justified once validation finds **no fatal
flaws** — no compliance hard-stop, no incumbent already shipping the exact
experience for free, no broken unit economics at low scale. Affirmative
proof of recurring revenue is NOT required before building the dogfood
prototype, because the prototype's primary return is learning. The bar
rises before any *paid public launch*: at that gate, willingness-to-pay
must be affirmatively evidenced (real families, not the operator's own).

The confidence case (a Phase-A workstream) scores each pillar with evidence
tiers (desk-proven / inference / only-field-provable) and survives
adversarial review. **Gate G1a** (dogfood build authorized) passes on
no-fatal-flaws. The **paid-business path (Gate G1b)** requires the
affirmative WTP case; **paid public launch (G-LAUNCH)** — a post-build,
operate-phase milestone — happens only after that path is authorized and the
build ships. (Gate IDs are the canonical waterfall in
`planning/workstreams.md`: G1/G1a/G1b → G2 strategy → G3 spec → G4 build;
G-LAUNCH is the paid-launch milestone after G4.)

## Decision values (apply to every prioritization)

1. **Operator steady-state time is the scarcest resource.** Decline work
   whose ongoing maintenance cost exceeds its learning or retention
   contribution. Automation-first; the product must run itself.
2. **Family trust is non-negotiable** (constitution). Never sell or broker
   family data; never spam; calm-not-addictive by design. No growth tactic
   that erodes it.
3. **Patient risk posture.** Kill switches in `context/kill-switches.md`
   are real, measured, and checked — but the clock is long and the operator
   tolerates a slow path to first revenue.
4. **Compliance hard-stops everything.** Children's-data (COPPA) and
   Google restricted-scope rules halt work the moment they're crossed.
5. **Validate before build.** Evidence beats plausible narrative; cite-or-
   die and adversarial review are permanent.
6. **External actions are operator-only.** Outreach, sign-ups, app-store
   submissions, OAuth-verification filings, spend, signatures — agents
   draft, operator executes.

## Current direction (operator-set, revisable at any review)

- **Dogfood first.** Build for the operator's own household before
  generalizing. Real daily use is the primary feedback loop.
- **Content-API-fed MVP.** The first prototype is a content API + CLI +
  Claude skill: external AI loops and scheduled tasks author/update the
  briefing and action cards; the dashboard renders them. Auto-ingestion of
  Calendar/Gmail/weather is **deliberately post-MVP** — it carries the
  heaviest compliance and integration cost and should follow proven daily
  use.
- **Platform:** Compose Multiplatform (Android/iOS/Web shared UI). Pivot to
  SwiftUI/React-per-surface is allowed if UX demonstrably demands it —
  deliberately undecided pending dogfood feedback.
- **Pricing:** subscription per family, **deliberately undecided** on price
  and packaging pending the pricing workstream and WTP evidence.

## Engagement model

**Inbox + weekly review.** The loop runs autonomously, batches questions
and ratifications into `backlog/operator-inbox.md` with proposed defaults,
and the operator sweeps weekly. Kill-switch trips, gate decisions, and
compliance flags interrupt immediately (not batched).
