# ADR 0032: Licensing & Open-Source Posture — Apache Client/CLI + AGPL Server, Hosted-SaaS Monetization

## Status

**Proposed** 2026-06-25 (agent-drafted from the licensing research; **operator-gated +
`[pending-counsel]`** — this is a business + legal + customer-data-handling decision, never
agent-decided per `CLAUDE.md`). Immutable once Accepted — supersede, do not edit. **Closes
ADR 0031's deferred license gate** and resolves `OQ-license`. Composes with **ADR 0004**
(product framing / learning-lab-primary), the **business constitution**, **ADR 0015** (E2EE /
trust posture), and **ADR 0031** (CLI Homebrew distribution — needs a license set here).
Report: `research/2026-06-25-licensing-open-source-strategy.md`.

## Context

ADR 0031 (CLI distribution) surfaced that the repo is **unlicensed**, and a public Homebrew tap
implies public distribution — deferring the license decision. The operator's goal: **open source
AND monetizable** — OSS for portfolio/resume/showcase value + free tooling, with a paid path still
funding durable side income. The project's direction is **learning-lab GO / standalone-business
NO-GO** (briefing intelligence is commoditized); durable side income is a co-goal, not the driver.

The research (two cited streams + a codebase security analysis) found the decision collapses on two
facts: (1) the **opportunity cost of OSS is ≈ zero** — the revenue ceiling is already tiny and the
only "leak" vector (self-hosting) does not overlap the convenience-seeking paying ICP; (2) the
**showcase + trust upside is high** and is the primary goal. So the license choice is a
*credibility/trust* decision, not a *moat* decision — the moat is hosting + brand, which OSS cannot
copy.

## Decision

1. **Open-source Dayfold for showcase + trust.** GO. The codebase is safe to publish — no embedded
   secrets (all via env/keychain), security is server-enforced (not obscurity; NIST Open Design /
   OWASP), and the auth/visibility design is already public-design-reviewed (ADRs 0011/0029/0030).

2. **Per-component license set:**
   - **`apps/client` + `apps/androidApp` → Apache-2.0** (max showcase/adoption + patent grant).
   - **`apps/cli` → Apache-2.0** (separate process over the API).
   - **`packages/schema` (codegen/contracts) → Apache-2.0** (must flow into both sides → permissive,
     compatible both directions).
   - **`apps/api` (server) → AGPL-3.0-or-later** (§13 network-copyleft — optics + insurance against a
     hypothetical same-stack competitor; the sole copyright owner stays free to run/sell a closed
     SaaS). **AGPL, not BSL** — AGPL is OSI-approved (keeps showcase credibility); BSL protects
     against hyperscaler resale, a threat a solo family app does not face (Elastic reverted SSPL→AGPL
     in 2024).
   - **The future G1 "authoring brains" → closed**, in a **separate private repo**, reached over the
     API — closed by *not publishing*, not by a license. (It does not exist in the repo today.)
   - Mixed set is legally clean: Apache→AGPL is one-directional compatible; an Apache client/CLI
     talking HTTP to an AGPL server is *aggregation*, not a derivative. **Never bundle/statically
     link AGPL server code into the Apache client/CLI.**

3. **Monetization = hosted SaaS as the sole paid path.** Closed-G1 open-core and GitHub Sponsors are
   thin secondary levers (enable Sponsors; keep G1 closed for optionality). **Reject support/
   services** — it violates the steady-state time ceiling (KS-3) and isn't durable side income.

4. **Contributions = DCO** (`Signed-off-by`, inbound=outbound, low friction). A **CLA is required
   only if a dual-license/commercial-sale is later intended** — that intent must be decided **before**
   opening contributions (relicensing contributors' code afterward needs re-consent).

5. **Pre-flight security gate (BLOCKING — must pass before any repo goes public):**
   - Scan the **full git history** (Gitleaks + TruffleHog), **rotate/revoke anything exposed**.
   - Enable GitHub **secret scanning + push protection**.
   - Add `SECURITY.md` + a coordinated-vulnerability-disclosure policy; add root `LICENSE` per repo +
     SPDX/REUSE per-component headers; README documents the per-component split (GitHub's
     root-`LICENSE`-only detection won't badge a mixed monorepo cleanly).

## Consequences

- **Showcase + trust** realized; **acquisition** gets a free GitHub channel; the moat (hosting +
  brand) is untouched by source availability.
- **One real cost — support burden** → mitigated by an explicit "no-SLA, community project → hosted
  Dayfold for it-just-works" README posture.
- **Maintenance:** per-component LICENSE/SPDX hygiene; the AGPL/Apache boundary must be respected.
- **Customer-data posture (guardrail #4) unaffected** — OSS does not change data handling; it makes
  it *auditable* (a trust gain).

## Operator gates (must be decided/done — not agent-decidable)

1. **Counsel sign-off** on the Apache(client/CLI/schema)+AGPL(server) split, the DCO-vs-CLA choice,
   and the dual-license question — `[pending-counsel]`.
2. **Decide dual-license/commercial-sale intent now** (drives CLA-vs-DCO before contributions open).
3. **Run the pre-flight security gate** (history scan + rotate + push protection) before flipping any
   repo public.
4. **Accept this ADR**, then: add the `LICENSE` files (this unblocks ADR 0031's license gate — set
   the formula's `license` from `:cannot_represent` to the real SPDX id), `SECURITY.md`, and the
   per-component SPDX headers.

## Alternatives considered

- **BSL/FSL server** — protects against hyperscaler resale (not a real threat here) at the cost of
  "not open source" + 2023–24 backlash baggage. **Rejected** (use only if non-modifying commercial
  hosting ever needs blocking).
- **Permissive everywhere (MIT/Apache server too)** — simplest + max adoption, but gives up the free
  AGPL insurance. Acceptable fallback if AGPL's adoption-chill is judged to outweigh the optics.
- **All-closed / source-available-only** — forfeits the primary showcase/trust goal for protection
  that isn't needed. Rejected.
- **Open-core as the *primary* revenue model** — the closed surface (G1) protects little (commodity
  intelligence); hosting is the real moat. Kept as a thin secondary lever only.
