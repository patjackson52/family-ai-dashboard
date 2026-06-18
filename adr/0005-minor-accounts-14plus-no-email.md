# ADR 0005: Minor Accounts (14+) Permitted at MVP, No Email Integration for Minor Profiles

## Status

**Proposed** (2026-06-18). Supersedes the **adults-only** clause of ADR 0004
(§Decision 4) only — all other ADR 0004 decisions stand. Legal-posture +
scope decision → **operator-gated, never agent-decided** (CLAUDE.md
guardrail #1). Requires operator acceptance, ideally after a one-line
counsel confirm (see Open Items). Until Accepted, ADR 0004's adults-only
scope remains in force.

## Context

ADR 0004 set an **adults-only** MVP to avoid COPPA and Google's supervised-
account architecture, both of which are **under-13-specific** (validation
round 1: `research/validation-round1-2026-06.md`, `.../compliance.md`).
The operator asked whether **14+ (teen) access** is acceptable if the MVP
keeps its **no-direct-email-integration** scope.

Relevant facts from round 1:
- **COPPA applies only to under-13.** 14+ accounts are entirely outside the
  COPPA verifiable-parental-consent regime and its $53,088/violation exposure.
- **Google standard-account minimum is 13.** A 14+ user can hold a normal
  account and self-grant OAuth scopes; the supervised-Family-Link grant
  block was under-13-specific (moot here — no email integration).
- **No email = no restricted scope.** Gmail read is RESTRICTED (recurring
  CASA ~$540-1,500/yr); Calendar read is only SENSITIVE (no CASA). Minor
  profiles see briefing/calendar/list/weather cards, not parsed email.
- **State minor-privacy laws** (CT/CO minors rules; CA §1798.120 under-16;
  **Maryland Kids Code**, the most enforceable) bite on selling minor data,
  targeted ads to minors, and addictive design — all already banned by the
  business constitution. Maryland may require a **DPIA** if the app is
  "reasonably likely to be accessed by minors."

## Decision

1. **Permit accounts for users aged 14+** in the family tenant at MVP.
   Children **under 13 remain excluded** (and 13 is excluded too, keeping a
   one-year buffer above the COPPA line — hence "14+", not "13+").
2. **No email integration for minor (14-17) profiles.** Minor profiles are
   limited to non-restricted sources (calendar, lists, weather) and content-
   API-authored cards. Direct Gmail OAuth stays out of scope for everyone at
   MVP per ADR 0004; this ADR additionally bars it for minors even post-MVP
   until separately revisited.
3. **Age gate at signup.** A reasonable age-assurance screen keeps under-13s
   out; an account self-reporting under-14 is refused (or routed to a
   parent-managed view with no independent login). Parent/owner attests to
   minors' ages when adding them to the household.
4. **Minor profiles inherit the strictest data posture** (constitution): no
   data sale/brokering, no targeted ads, no engagement-bait/addictive design,
   honor export+delete. This satisfies the substance of the state minor laws.
5. **Adults-only remains a fallback.** If counsel flags the Maryland DPIA or
   any state rule as more burden than warranted at MVP, the venture ships
   adults-only first and adds 14+ post-validation.

## Rationale

14+ sidesteps the two walls that forced adults-only (COPPA, Google account
age) without reopening either, and the no-email scope keeps minor data low-
sensitivity. A teen-inclusive family dashboard is materially more useful
(the whole household uses it) than adults-only, at modest, mostly-already-
satisfied compliance cost. The decision is reversible (fallback to adults-
only) and bounded (no email for minors).

**Rejected alternatives:** (a) **13+** — rejected; the one-year buffer above
the COPPA line cheaply removes age-edge-case risk. (b) **Under-13 with VPC**
— rejected (ADR 0004): COPPA + Google account architecture make it
infeasible for a solo learning-lab. (c) **Email integration for minors** —
rejected: re-arms restricted-scope CASA and raises minor-data sensitivity
for little MVP value.

## Consequences

Positive: whole-family use without the COPPA/CASA walls; reversible; aligns
with the constitution's existing posture.
Negative: requires an age-gate mechanism; possible Maryland DPIA; app-store
age-categorization obligations (UT/TX/LA) at distribution; ages are self-
attested (no hard verification) — acceptable for a no-restricted-data MVP,
revisit if sensitivity rises.

## Open Items (resolve before Accepted)

- **Counsel confirm (operator-gated):** (1) is a self-attested age gate
  sufficient given no restricted/email data for minors? (2) does the app
  trip the Maryland Kids Code DPIA trigger, and if so is the DPIA burden
  acceptable at MVP? → feeds `context/open-questions.md`.
- Decide age-gate UX (self-report vs parent-attested-on-add).

## Revisit Trigger

Any of: minors gain email/restricted-data access (new ADR); a state law
adds hard age-verification for this app class; counsel flags the DPIA or a
state rule as MVP-blocking; or the operator reverts to adults-only.
