# Operator Inbox

Questions and ratifications awaiting the operator. Swept weekly (per
`context/values-and-direction.md`). Nothing auto-applies; items aging >2
sweeps escalate in the digest. Newest first.

Format: `INB-N · date · urgency(high/med/low) · status(open/answered/stale)`
Each item: question, context link, **proposed default**, urgency.

---

- **INB-8 · 2026-06-18 · med · open — Ratify ADR 0007 (Prototype scope) + batch w/ 0006.**
  You approved the prototype scope in-session (operator-driven dumb renderer:
  cloud DB + content API, Claude-Code push skill, two surfaces + in-app
  tap-through, CMP native Android+iOS, single-household token; push/auth/
  integrations/Universal-Links/widgets/minor-accounts all deferred). ADR 0007
  presumes ADR 0006 (Event Hubs), so **proposed default:** ratify 0006 + 0007
  together in this sweep, then iteration 1 builds A3 exactly. Confirm.

- **INB-7 · 2026-06-18 · high · open — Accept ADR 0006 (Event Hubs surface)?**
  Brainstormed a second co-equal surface: **Event Hubs** — persistent,
  AI-curated event dossiers (vacation/college/move/etc.), app-owned, push-
  curated via the content API; power users author upstream + instruct Claude
  to push. Plausibly *the* defensible wedge validation said was missing.
  Design: `specs/event-hubs-design.md`. Scope-class → your decision.
  **Proposed default:** accept ADR 0006 (scope expansion + system-of-record
  reconciliation), promote the design to a binding spec at C1b, documents =
  links+small-refs only at MVP. Accept / amend / defer.

- **INB-6 · 2026-06-18 · high · open — Accept ADR 0005 (14+ minor accounts)?**
  Operator asked whether teen (14+) access is OK without email integration.
  Answer: yes — 14+ is outside COPPA and the Google account-age block; no
  email keeps it clear of restricted-scope CASA. Drafted as **Proposed ADR
  0005** (supersedes 0004's adults-only clause): 14+ accounts, no email for
  minor profiles, age-gate at signup, strictest data posture. **Legal +
  ADR-class → your decision, not mine.** **Proposed default:** accept ADR
  0005 conditionally, pending a one-line counsel confirm on (a) self-attested
  age gate sufficiency and (b) Maryland Kids Code DPIA trigger; fall back to
  adults-only if counsel flags burden. Accept / amend / keep adults-only.

- **INB-1 · 2026-06-18 · high · open — Validation verdict & direction.**
  Round 1 verdict: **CONDITIONAL — learning-lab GO, standalone-business
  NO-GO** (`research/validation-round1-2026-06.md`). The AI-briefing concept
  is commoditized (Gemini Daily Brief, Alexa+, Ohai ~$44M, Maple; Huxe
  shut down). **Proposed default:** proceed building the **dogfood
  content-API MVP** as a learning artifact (KS-7 not tripped); keep the
  business path NO-GO until a flip-condition or niche (A1) is evidenced.
  Confirm or redirect.

- **INB-2 · 2026-06-18 · high · open — MVP scope guardrails (ratify).**
  To dodge the three fatal walls, ADR 0004 fixes the MVP as **adults-only
  accounts, no direct Gmail OAuth (Calendar-only sensitive scope + content-
  API/forward path), plain deep-links (no commerce API)**. **Proposed
  default:** ratify as the MVP scope; any expansion (child accounts, Gmail
  ingestion, affiliate commerce) is a separate ADR. Confirm.

- **INB-3 · 2026-06-18 · med · open — Cheapest kill-checks (you, ~2 hrs).**
  Before/while building: (a) run Gemini Daily Brief's school-email→family-
  digest flow yourself; (b) use Maple+ a bit and name what it can't do for a
  niche. These most cheaply move the verdict (KS-6 / OQ-niche).
  **Proposed default:** you run these this month; report back into A1.

- **INB-4 · 2026-06-18 · low · open — Pricing direction (no decision yet).**
  Market anchor is $39-79/yr per family; $15/mo is above ceiling. Pricing is
  an ADR-class constant (guardrail #2) — not set now. **Proposed default:**
  defer to B6; when set, lean **annual-first ~$39-59/yr**. Acknowledge.

- **INB-5 · 2026-06-18 · low · open — Loop start.** Bootstrap complete.
  **Proposed default:** start the loop with iteration 1 selecting **A3
  (content-API MVP capability spike)** toward Gate G1a, with A1 (niche
  brief) in parallel. Say "run a loop iteration" to begin. Confirm or
  re-prioritize.

## Answered

*(move items here with the answer + date)*
