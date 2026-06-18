# Goals and Constraints

North star and decision values live in `context/values-and-direction.md`
(operator-owned). Kill thresholds live in `context/kill-switches.md`. This
file holds the quantitative targets and hard constraints.

## Operator context

Solo founder, US-based, bootstrapped. Strong agentic-tooling fluency
(Claude Code, scheduled tasks, AI loops, MCP/skills) — this is the
operator's leverage and the reason the content-API-fed MVP is cheap for
*this* builder specifically. Building for his own household first (real
daily users on hand). Day-job-compatible cadence: patient, part-time.
Existing assets: this venture-loop template + sibling `ambient-ai` spec
repo (reusable process + "render, don't reason" pattern). No existing
audience, entity, or family-app distribution.

## Goals (targets, not facts — label sources)

| Goal | Target | Source status |
|---|---|---|
| Dogfood prototype in daily family use | Operator's household using it ~daily within ~8–12 weeks of build start | [estimate] |
| First non-operator family using it (free) | 3–5 friendly families by ~month 6 | [estimate] |
| First paying family | ≤ ~12 months from build start (patient bundle) | [estimate] |
| Price point (if/when paid) | $5–10/family/mo band (category anchor $3–8/mo) | [estimate — pricing workstream owns this] |
| Steady-state ops load | < ~2 hrs/wk to keep running | [estimate] |
| Learning outcomes (primary) | Shipped CMP app + content-API/loop authoring pattern proven | [estimate] |

## Hard constraints

- **Cash:** ≤ ~$5–10k cumulative to first paying customer (patient
  bundle); ongoing infra **< ~$50/mo** until revenue justifies more —
  drives use of free/cheap tiers and bounded LLM token spend.
- **Operator time:** ~15–25 hrs/wk available during build (patient,
  part-time); **< ~2 hrs/wk steady state** — a first-class product
  requirement that drives automation-first design.
- **Compliance walls:** (1) **COPPA** if children under 13 get
  profiles/logins — verifiable-parental-consent obligations; (2) **Google
  API Services User Data Policy** restricted-scope rules + verification/CASA
  security-assessment gate for reading Gmail/Calendar; (3) third-party LLM
  data-handling disclosures for routing email/calendar content. Any of
  these crossing into "infeasible/unlawful as designed" is a hard stop.
- **No real-time SLA, no support promises** while dogfooding/free. The
  product must degrade gracefully and never page the operator.

## Validation gates before build

1. **Initial validation round complete** (claims verified with citations,
   adversarial review passed) — `research/validation-round1-2026-06.md`.
2. **Compliance gate:** a workable path exists for (a) children's data
   (COPPA) under the dogfood/MVP design and (b) Google restricted-scope
   access OR a confirmed MVP path that avoids it (e.g. manual/forwarded
   content via the content API, no direct Gmail OAuth at MVP).
3. **Pricing gate:** subscription model stress-tested at real low-scale
   family counts; contribution margin positive after LLM + infra cost at
   the category-anchored price.
4. **Field gate (for paid launch, not for dogfood):** ≥ ~5 non-operator
   families confirm the briefing+action value is worth paying for.
5. **Confidence case meets the operator's bar** (MODERATE: no fatal flaws)
   — adversarially reviewed. Note: the validation round surfaced that the
   AI-briefing concept is already shipped by funded competitors (Ohai,
   Ollie, Alexa+), so the confidence case must rest on the **content-API /
   power-user wedge + learning value**, not on the briefing concept alone.
