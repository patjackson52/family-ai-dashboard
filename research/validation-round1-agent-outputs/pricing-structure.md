# Agent: pricing-structure (2026-06-18)

**Summary:** Pricing VIABLE but tight. Market ceiling ~$39-79/yr per
household (~$3.25-6.58/mo): Cozi Gold $39/yr [vendor], Skylight Plus $79/yr
[vendor, hardware-bundled], Maple+ $40/yr. A $5-15/mo price sits AT/ABOVE
the ceiling; $15/mo is above every software-only family app.

**LLM cost is NOT the threat:** Haiku 4.5 ~$0.006/briefing → $0.18-0.54/
family/mo (1-3/day); GPT-5 mini ~$0.001 → ~$0.09/mo. Contribution margin
78-91% at $5-15/mo. Infra break-even **~3-15 paying families**.

**Real constraint = acquisition + retention**, not cost: ~2-4% free→paid
conversion × 5-12%/mo consumer churn. 100 paying families needs ~3,300 free
signups; ~100-200 retained payers → ~$300-1,500/mo (side income, not income
replacement). **Annual-first billing** recommended (amortizes Stripe $0.30/
charge; cuts effective churn). **App Store/Play 15-30% commission would hit
margin far harder than LLM cost** — must check before store billing.

Math (labeled): briefing ~3k in / 600 out tokens [assumption]; Haiku 4.5
$1/$5 per MTok [fact: platform.claude.com/pricing]; Stripe 2.9%+$0.30 [fact].
$6/mo → ~$4 contribution. Infra floor ~$20-60/mo [assumption].

Sources: cozi.com/cozi-gold; myskylight.com; growmaple.com;
platform.claude.com/pricing; developers.openai.com/pricing; stripe.com/pricing;
revenuecat.com/state-of-subscription-apps.

Residual: confirm infra floor for chosen stack; pick model + token budget;
**pricing constant is ADR-class / operator-gated** (recommend annual ~$39-59/
yr); freemium vs hard-paywall; verify store commission applicability.
