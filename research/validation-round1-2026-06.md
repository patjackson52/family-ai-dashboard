# Validation Round 1 — family-ai-dashboard

**Date:** 2026-06-18 · **Type:** Bootstrap validation fleet (8 agents:
6 verification + 2 adversarial) · **Counts as:** P0 viability review #1.
Raw agent outputs archived in `validation-round1-agent-outputs/`.

> **Ground truth at time of review:** nothing built, no customers/prospects
> contacted, no entity formed, no money spent. All figures labeled
> [fact:source] / [estimate] / [assumption]; every external claim carries a
> source in the raw outputs.

---

## VERDICT

**CONDITIONAL — learning-lab GO, standalone-business NO-GO.**
Confidence: **high** that the generic AI-briefing wedge is closed (multiple
funded + platform players already ship it); **medium** on whether a niche or
flip-condition reopens the business path; **medium** on learning value. The
business is *currently* NO-GO — not provably permanently so (KS-6 is
ELEVATED with an open flip-condition, not tripped).

Build the dogfood prototype. It clears the operator's MODERATE bar **for the
learning-lab goal** (cheap, fast, no fatal flaw *as a learning exercise*).
It does **not** clear the bar as a durable-side-income business: the core
"AI family briefing" concept is being commoditized from the platform layer
and is already shipped by funded verticals. Do not assume the dogfood path
becomes a business unless a flip-condition fires.

**Flip-conditions (any one re-opens the business case):**
1. Google Gemini "Daily Brief" stays single-account / paid-tier — does NOT
   ship a free, **family-shared** variant in ~12 months.
2. A high-pain niche (co-parenting/split households, special-needs/IEP
   logistics, eldercare) shows willingness-to-pay horizontal incumbents
   won't serve.
3. The action layer (deep links to commerce) earns affiliate/referral
   revenue the subscription alone can't.

---

## Confirmed / refuted claims

| Claim under test | Verdict | Evidence |
|---|---|---|
| Family-organizer market is real & sizeable | **Confirmed** | ~$1.7B parenting-app market 2025, ~11-13% CAGR; ~32-34M US families w/ kids [estimate, Census-derived]; Cozi 20M+ registered users [fact] |
| Families will pay a subscription for this | **Partially-confirmed (weak)** | Proven price anchor is LOW ($39/yr Cozi, $40/yr Maple, $79/yr Skylight+hardware); consumer churn brutal (~13-14%/mo median); demand for AI *family briefing specifically* is undocumented |
| The AI-briefing-over-Google-data concept is a wedge | **REFUTED** | Already shipping: Gemini Daily Brief (free-in-bundle, incl. family school-email digest), Alexa+ "summarize your day", Ohai (~$44M), Ollie, Maple ($3-5/mo). Huxe (funded daily-brief) shut down May 2026 |
| LLM cost threatens unit economics | **REFUTED** | ~$0.016-0.54/family/mo across Haiku/Gemini-Flash/GPT-nano; 78-91% contribution margin at $5-15/mo; infra break-even ~3-15 paying families |
| Gmail can be read cheaply at MVP | **REFUTED** | gmail.readonly + gmail.metadata both RESTRICTED → mandatory recurring CASA assessment ~$540-1,500/yr. Calendar read is only SENSITIVE (no CASA) |
| Kids can have their own logins | **REFUTED (structural)** | Under-13s can't hold standard Google accounts (supervised Family Link only); can't self-grant restricted OAuth. Plus COPPA amended rule (full force 2026-04-22): separate VPC for third-party/LLM disclosure; $53,088/violation |
| Instacart "party→groceries" affiliate action is buildable now | **Refuted (MVP)** | Instacart Developer Platform CLOSED to new applicants, no waitlist; affiliate needs entity + Tastemakers + 30-40d approval. Walmart add-to-cart URL is the solo fallback |
| CMP gives Android/iOS/Web from one codebase | **Confirmed w/ caveat** | iOS Stable (1.8, May 2025); **Web/Wasm BETA only** (early-adopter); iOS builds need a Mac |

## Missed competitors (highest-value findings)

- **Google Gemini Daily Brief** (May 2026) — the single most damaging:
  free-in-bundle, same Gmail+Calendar+Tasks data, explicitly demoed
  family-school-email digests. Feature, not a product — but it's *the*
  product, from the platform owner.
- **Amazon Alexa+** — proactive whole-family brief + nudges on owned hardware.
- **Apple Siri AI** (June 2026) — cross-app personal-context retrieval;
  no family digest *yet*.
- **Ohai (~$44M, Care.com founder), Ollie ($5M), Maple, Nori, Norton, Gether**
  — dense funded field already shipping briefing+action variants.
- **ClassDojo / ParentSquare (Remind)** — own the school→action lane at source.
- **Claude scheduled tasks + Notion AI** — DIY substitutes that overlap the
  *content-API/CLI wedge* itself.
- **Huxe** — funded ($4.6M, ex-NotebookLM) daily-brief, launched Jun 2025,
  **shut down May 2026**. Category-viability red flag.

## The one defensible (time-sensitive) surface

No native OS ships a **single-tenant, multi-member family briefing** with
per-member logins (Gemini = single-account; Apple/Android = isolated
separate accounts). This is the only un-shipped surface found — erodible if
Google/Apple add a family variant (tracked as KS-6, re-checked quarterly).

## Unit economics (first pass, strategist + pricing agents)

- Price band realistic: **$39-79/yr per family** (anchors: Cozi $39, Maple
  $40, Skylight $79-bundled). $15/mo is above market ceiling.
- At $6/mo: contribution ~$4/family/mo (~67-78%) after LLM + Stripe +
  support [estimate]. **Cost is not the constraint; acquisition + retention
  is** — 2-4% free→paid conversion × 5-12%/mo churn means ~100-200 retained
  paying families (=> ~3,300-6,700 free signups) for ~$300-1,500/mo — a
  side-income ceiling, not income replacement.
- **Annual-first billing** recommended (amortizes Stripe per-charge fee,
  cuts effective churn). App Store/Play 15-30% commission would hit margin
  far harder than LLM cost — operator must check before mobile-store billing.

## Kill-criteria checks (cheapest experiments that would move the verdict)

1. **Use Gemini Daily Brief yourself** (~30 min, $0) — run the
   school-email→family-digest flow. If it covers the wedge for your
   household, the business case is effectively dead.
2. **Read Google restricted-scope + CASA docs** (~30 min, $0) — confirm the
   Gmail wall before designing any ingestion. (Done in this round.)
3. **Read FTC COPPA FAQ + amended-rule + Apple Kids guidelines** (~1 hr) —
   confirm adults-only is the right MVP scope. (Done; confirmed.)
4. **Use Maple+ for a month** — name the specific thing it can't/won't do
   for a niche. If you can't, NO-GO on differentiation is confirmed.
5. **Concierge pilot** — would ~5 non-operator families *pay* for
   hand-authored briefings (no build)? Tests the real WTP gate the dogfood
   cannot.

## Kill-switch register updates (see `context/kill-switches.md`)

- **KS-4 (compliance):** OPEN → mitigated-by-design. Adults-only + no direct
  Gmail OAuth + Calendar-only(sensitive) avoids COPPA and CASA at MVP.
  Re-arms if child accounts or Gmail ingestion are added (each ADR-gated).
- **KS-6 (product-ceiling):** ELEVATED. Gemini Daily Brief shipping but
  still single-account — the open flip-condition. Re-check quarterly.
- **KS-7 (viability):** Round 1 = CONDITIONAL, not a kill. Learning value
  intact.

## What this round did NOT establish (residual, operator/field)

- Actual willingness-to-pay (no families contacted) — the single weakest,
  product-defining assumption; only field-provable.
- Whether on-device-only Gmail processing exempts CASA (architecture spike).
- Live Family Link OAuth behavior for a supervised under-13 account (build-
  time spike) — would firm the one partially-confirmed compliance claim.
- Exact CASA tier/fee quote; SBA size standard for state-law applicability;
  Maryland Kids Code applicability. All flagged for legal review (guardrail).
