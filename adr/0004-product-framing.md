# ADR 0004: Product Framing — Calm Family Briefing Surface, Content-API-Fed, Adults-Only MVP

## Status

Accepted 2026-06-18 (bootstrap). Immutable — supersede, do not edit.

## Context

The operator wants a sleek mobile-native household dashboard that gives each
family a single AI-powered daily **briefing** plus **smart recommended
actions** with deep links, drawing on calendar, email, lists, weather, and
location. North star is a **learning lab** (primary) with **durable side
income** as a co-goal; dogfooded on the operator's own household first.

Validation round 1 (`research/validation-round1-2026-06.md`) materially
shaped this framing. Key facts the framing must honor:

- The generic "AI daily briefing over Gmail + Calendar" is **already
  shipping** — Google Gemini Daily Brief (free-in-bundle, same data, even
  the family-school-email-digest use case), Amazon Alexa+, and funded
  verticals (Ohai ~$44M, Ollie, Maple at $3–5/mo). A comparably-funded
  daily-brief startup (Huxe) shut down May 2026. The briefing concept is
  **not a wedge**.
- The one capability **no native OS ships**: a single-tenant, **multi-
  member family briefing** with per-member logins. Native offerings are
  single-account (Gemini) or use isolated separate accounts (Apple/Android).
  This is the only defensible (if time-sensitive) product surface found.
- Reading Gmail uses Google **restricted** scopes → mandatory recurring
  CASA assessment (~$540–1,500/yr). Calendar read is only **sensitive** (no
  CASA). Children's accounts trigger COPPA + state child-privacy laws.
  Instacart's prefilled-cart API is currently closed to new applicants.

A decision is needed now to fix what this product **is**, what it is
**not**, and the MVP scope that avoids the fatal walls — before any build.

## Decision

**family-ai-dashboard is a calm, multi-member family briefing surface that
renders intelligence produced elsewhere.** Specifically:

1. **Render, don't reason (at the product surface).** The dashboard shows a
   curated daily briefing + a short, **template-catalog-bounded** list of
   recommended actions with deep links. Reasoning may run in external AI
   loops; the product is not an open-ended chatbot.
2. **Content-API-fed MVP.** The first prototype is a **content API + CLI +
   Claude skill**. External AI loops and scheduled tasks author/update the
   cards. This is the dogfood path and the primary learning artifact.
3. **Multi-member family tenant** is the differentiating surface: one
   household, per-member logins, a shared-but-personalized briefing — the
   gap native assistants leave open.
4. **Adults-only accounts at MVP.** Children appear only as *subjects*
   within a parent's own data, never as account holders. Adding child
   accounts is a separate ADR that re-opens the full COPPA burden.
5. **No direct Gmail OAuth at MVP.** Email-derived cards arrive via the
   content API / forward / paste path. Calendar (sensitive scope) may be
   read directly. Direct Gmail ingestion (restricted scope + CASA) is an
   ADR-gated post-MVP decision.
6. **Deep links are UX, not revenue, at MVP.** Use plain public deep links
   (and the Walmart add-to-cart URL where useful); affiliate/commerce
   integrations are entity-gated and deferred.
7. **Platform:** Compose Multiplatform — Android/iOS first (both Stable),
   Web treated as early-adopter (CMP Web is Beta). Pivot to per-surface
   native allowed if UX demands.

## Rationale

The briefing concept is commoditized, so the framing leans on what survives
adversarial scrutiny: the **content-authoring loop** (the learning prize and
genuinely cheap for this operator) and the **multi-member family tenant**
(the only un-shipped surface). The MVP scope is deliberately drawn to dodge
all three fatal walls (COPPA, Gmail CASA, commerce-API gates) while still
delivering daily value to the operator's household — proving the learning
goal regardless of whether the business materializes.

**Rejected adjacent framings:**

- **Generic "AI family assistant / daily brief"** — rejected: already
  shipped by Google/Amazon/funded verticals; no wedge; Huxe precedent.
- **Family chat / social / coordination hub** — rejected (constitution):
  competes for attention rather than saving it; different product.
- **Chore/allowance/gamified kids app** — rejected: COPPA-heavier,
  engagement-maximizing, opposite of "calm."
- **Calendar/email/list system of record** — rejected: raises switching
  cost and data-custody burden; the product reads and links, never owns.
- **Hardware family display (Skylight/Hearth model)** — rejected: capital,
  inventory, and ops incompatible with a solo low-budget learning lab.

## Consequences

Positive:
- Cheapest possible path to daily dogfood value; the MVP is buildable now.
- Avoids COPPA, CASA, and commerce-API walls by construction.
- Learning goal is satisfied even if revenue never appears.
- A real (if time-sensitive) differentiator is named, not hand-waved.

Negative:
- The content-API/CLI wedge overlaps Claude's native scheduled tasks —
  weak as a *paying* consumer wedge; its justification is learning.
- The multi-member-tenant moat is erodible (Google/Apple could add a family
  variant) — tracked as KS-6, re-checked quarterly.
- Adults-only + no-Gmail-OAuth removes features that some competitors ship,
  narrowing perceived parity at MVP.

## Revisit Trigger

Any of: (a) a defensible niche emerges (co-parenting / special-needs / 
eldercare) with evidenced willingness-to-pay; (b) Gemini/Apple ship a
family-shared briefing variant (erodes the wedge → re-scope or kill);
(c) the operator decides to add child accounts or direct Gmail ingestion
(each its own ADR); (d) a P0 viability review returns kill on both the
side-income case and the learning value.
