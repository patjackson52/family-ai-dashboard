# Loop Journal

One entry per planning-loop iteration (step 6 of
`processes/planning-loop.md`). Newest first. Meta-review every 15
iterations. Format:

```
## Iteration N — YYYY-MM-DD — <board item>
Selected because: …
Outcome: …
Friction: …
Rework caused/avoided: …
Process-improvement candidate: … (or "none")
Tokens/agents used (if notable): …
```

---

## Iteration 0 — 2026-06-18 — Bootstrap

Selected because: operator said "bootstrap this project: family-ai-dashboard"
— a mobile/www AI-powered family briefing + smart-actions dashboard,
inspired by the sibling `ambient-ai` spec repo.

Outcome: Ran BOOTSTRAP.md end to end.
- Phase 1 interview (2 rounds, AskUserQuestion): north star = learning-lab
  (primary) + durable side income; patient kill-switch; moderate confidence
  bar; inbox + weekly engagement; dogfood own family first; CMP
  (Android/iOS/Web); subscription per family (deferred); integrations
  GCal/Gmail/lists/weather + a content API/CLI/Claude-skill authoring layer.
  Key refinement from operator: the **MVP = content API + CLI + Claude
  skill** (external AI loops author cards), auto-ingestion deferred.
- Phase 2 scaffold filled: values-and-direction, business-constitution
  (incl. NOT-list + render-don't-reason), goals-and-constraints,
  kill-switches (patient, with real fatal-fact thresholds).
- Phase 3 ADRs: 0001-0003 verified true → Accepted (2026-06-18); wrote
  **ADR 0004 Product Framing** (Accepted) — content-API-fed, adults-only,
  no-Gmail-OAuth, plain-deep-link MVP; multi-member family tenant as the one
  defensible surface; 5 rejected adjacent framings.
- Phase 4 validation fleet: 8 parallel subagents (market, competitors-direct,
  competitors-missed, tech-platform, compliance, pricing, strategist,
  skeptic). Synthesized `research/validation-round1-2026-06.md`; archived raw
  outputs. **Verdict: CONDITIONAL — learning-lab GO, business NO-GO.**
- Phase 5: instantiated Phase A board (two-track gate G1a/G1b), seeded
  open-questions + inbox (INB-1..5), armed viability clock (next 2026-07-18),
  this journal entry, memory.

Friction: the AI-family-briefing space is far more contested than the idea
implied — Google Gemini Daily Brief (May 2026), Alexa+, Ohai (~$44M), Maple,
and a funded shutdown (Huxe) all landed against the core wedge. The honest
synthesis required reframing the venture around its *primary* (learning)
goal rather than the income sub-goal. Three independent fatal walls (Gemini
commoditization, Gmail CASA, COPPA + Google supervised-account architecture)
forced the adults-only / no-Gmail-OAuth / content-API MVP scope — which
turned out to be exactly the cheap dogfood path the operator already wanted.

Rework avoided: catching the COPPA + Google-account wall now prevented
designing child logins that are structurally impossible to ship. Catching
the Gmail CASA cost now kept it out of the MVP critical path.

Process-improvement candidate: the validation fleet's "competitors-missed"
and "tech-platform" agents produced the highest-value findings (native-OS
commoditization, the family-tenant gap, the CASA/scope distinction). Worth
weighting those two roles heavily in future re-validation rounds.

Tokens/agents used: 8 subagents, ~307K subagent tokens total (~19-49K each),
all returned with sourced findings.
