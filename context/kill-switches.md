# Kill-Switch Register

Measurable conditions that **halt the planning loop and force an operator
kill/pivot decision**. **Patient** bundle, ratified 2026-06-18. Checked at
every P0 viability review and whenever new data lands on any input. Tripping
a switch never auto-kills — it halts work and escalates; only the operator
kills or pivots.

> **Framing note (from validation round 1, 2026-06).** The standalone-
> *business* case is already weak: Google Gemini "Daily Brief," Amazon
> Alexa+, and Apple Siri AI are commoditizing the family briefing for free
> at the platform layer; funded verticals (Ohai ~$44M, Ollie, Maple at
> $3–5/mo) ship the exact feature set; and a comparably-funded daily-brief
> startup (Huxe) shut down May 2026. The operator's **primary** north star
> is the **learning lab**, which these facts do NOT kill. KS-1/KS-5 below
> therefore gate the *side-income* sub-goal, not the project's existence —
> the project survives on learning value until a viability review says the
> learning, too, has plateaued (KS-7).

| ID | Condition | Threshold | Clock starts | Current status |
|----|-----------|-----------|--------------|----------------|
| KS-1 | Paying-customer traction (side-income sub-goal) | No paying family within **9 months** of **G-LAUNCH** (paid public launch; post-G4, per values file) | G-LAUNCH date | Not started (pre-launch) |
| KS-2 | Cash burn | Cumulative project cash > **$10,000** | Already running | ~$0 |
| KS-3 | Operator hours | Sustained > **25 hrs/wk** (build budget) for **8+** consecutive weeks AND steady-state > ~2 hrs/wk after launch | Already running | Within budget |
| KS-4 | Compliance red flag (always armed, interrupts) | COPPA makes children's profiles unlawful on all viable designs; **OR** Google restricted-scope/CASA verification is required and its cost/recurrence exceeds the cash ceiling with no scope-avoiding path; **OR** an LLM-data-handling obligation makes routing family email/calendar content non-compliant | Always armed | OPEN — pending tech-platform + compliance findings (round 1) |
| KS-5 | Demand / willingness-to-pay signal | Concierge pilot: **0 of ~5** non-operator families will pay for hand-authored briefings after a real trial | First pilot family | Not started |
| KS-6 | Product-ceiling collapse (the fatal fact) | A free, **family-shared** native briefing (Gemini Daily Brief / Alexa+ / Siri) ships to the operator's target users AND no defensible niche or action-layer revenue wedge has been found — i.e. zero reason to pay over the default | Continuous (incumbents already shipping) | ELEVATED — Gemini Daily Brief rolling out US 2026; currently subscriber-gated/single-user (the open flip-condition) |
| KS-7 | Viability verdict | A P0 adversarial viability review returns **kill** on BOTH the side-income case AND the learning value | Each review | Round 1 = CONDITIONAL (learning-lab GO, standalone-business NO-GO) — not a kill |

Rules:
- Status column updated with evidence + date at every viability review;
  stale (>45 days) status is itself an escalation.
- Thresholds change only by operator edit or ADR — never by a working doc.
- Near-miss (within 20% of a threshold) → flagged in the next digest.
- **Patient hard-stops:** any KS-4 compliance trip, or **two consecutive**
  failed P0 viability reviews (KS-7), force an immediate kill/pivot
  decision regardless of the long clocks above.
