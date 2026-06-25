# Research Report — Licensing & Open-Source Strategy for Dayfold

**Date:** 2026-06-25 · **Answers:** `TASK-license-strategy` / `OQ-license` · **Brief:**
`research/licensing-open-source-strategy-brief-2026-06.md` · **Method:** deep research
(cited) + the `solo-business-strategist` agent + a codebase security-split analysis;
two-stream outputs cross-corroborated. **Class:** ADR-class → see Proposed **ADR 0032**.
Cited facts, **not legal advice** — the final license is operator-gated + `[pending-counsel]`.

```
=== VERDICT ===
Open-source for showcase:  GO  (HIGH confidence — near-zero opportunity cost)
Monetization:              GO  — hosted SaaS as the SOLE paid path
License posture:           Apache-2.0 (client/CLI/schema) + AGPL-3.0-or-later (server)
                           + G1 "brains" closed by NOT publishing it (no license needed)
Drop BSL.  The license is a TRUST/SHOWCASE decision, not a moat — the moat is hosting + brand.
===============
```

## Why this is the answer (the load-bearing logic)

The project is a **learning lab (GO)**; durable side income a **co-goal**; the standalone
business is **NO-GO** (briefing intelligence is commoditized by Gemini/Alexa+). Two facts
collapse the decision:

1. **Opportunity cost of OSS ≈ zero.** Revenue ceiling is already tiny (~$300–1,500/mo at
   100–200 retained payers, behind a 2–4% funnel) `[fact: research/.../pricing-structure.md]`,
   and the only "leak" vector — self-hosting — does **not overlap the paying ICP**: busy
   families want convenience, not to stand up Vercel+Neon+secrets for a spouse who wants a
   morning card. Self-hosters were never going to pay; they become GitHub stars/PRs, not lost
   revenue `[estimate]`. In open-core funnels, self-host is *top-of-funnel*, not a leak
   `[fact: stormy.ai open-source-saas-playbook]`.
2. **Showcase upside is high and is the primary goal.** A public multi-platform
   (Android/iOS/Web), E2E-encrypted, AI-fed, multi-tenant family app with hardened
   auth/visibility/scopes (ADRs 0011/0030/0029/0015) is a strong recruiter/portfolio
   artifact — OSS converts the build you're doing anyway into a legible credential, and is a
   **trust asset** for a family-data product ("don't trust us, read the code").

So: **high upside, ~zero downside → open-source it.** The license choice is therefore about
*credibility + trust*, not *protection*.

## A. Security — what is safe to open (codebase analysis)

- **No embedded secrets anywhere.** Every secret resolves from env/keychain
  (`process.env.DATABASE_URL`/`AUTH_SIGNING_KEY`/`HOUSEHOLD_SECRET`/`CRON_SECRET`/`DEV_AUTH_SECRET`/
  `FIREBASE_*`; `getenv("HOUSEHOLD_SECRET")` in the CLI). No `.env`/secret/credential files are
  committed. The security model is **server-enforced auth + secret keys**, not obscurity.
- **Obscurity is not security** (NIST SP 800-160 Open Design / Kerckhoffs; OWASP names it an
  anti-pattern). A sound authz layer **does not weaken when the code goes public** `[fact:
  nist SP800-160; owasp Authorization Cheat Sheet]`. The auth/visibility design is already
  public-design-reviewed in the ADRs — opening the implementation reveals nothing new.
- **The G1 "brains" don't exist in the repo yet** (authoring is "operator + Claude Code via the
  CLI"). Keeping the future intelligence layer closed is a **repo-boundary** move (a separate
  private repo, reached over the API) — no license mechanism, no AGPL entanglement.
- **Real incremental risks of going public:** (a) an unpatched authz/logic flaw becomes
  immediately readable → mitigation is *fix it*, not hide it; (b) trivial self-host forks → a
  *product*, not a *breach* concern (forks run on the forker's infra/data, can't reach our DB);
  (c) **secret leakage = the dominant danger** — GitGuardian 2026: 28.65M hardcoded secrets hit
  public GitHub in 2025 (+34% YoY), and **AI-generated code leaks at ~2× baseline** — directly
  relevant to an AI-assisted build `[fact: gitguardian 2026]`.
- **Pre-flight security gate (BLOCKING before public):** scan the **full git history** (not just
  the working tree) with Gitleaks + TruffleHog, rotate anything exposed; enable GitHub secret
  scanning + push protection; ship `SECURITY.md` + a coordinated-disclosure policy; never rely
  on private code `[fact: github docs; trufflehog; cisa.gov]`.

## B. License options (comparison)

- **MIT vs Apache-2.0:** Apache adds an explicit **patent grant** (§3) + retaliation + NOTICE;
  matters most for enterprise adopters, a credibility signal for a solo dev. Neither grants
  **trademark** (brand protected separately). Both let you run a **closed hosted SaaS on the
  same code** `[fact: choosealicense.com/apache-2.0]`.
- **GPL vs AGPL — the SaaS loophole:** GPL triggers on *conveying* (distribution); "mere network
  interaction is not conveying" → a host can run modified GPL code as SaaS with no source duty.
  **AGPL §13** closes it: users interacting over a network must be offered the Corresponding
  Source. Crucially, **AGPL binds *others*, not the sole copyright holder** — you may publish
  AGPL source AND run a closed offering / dual-license `[fact: gnu.org/agpl-3.0; plausible.io]`.
- **BSL / source-available:** source public, free for non-prod; an Additional Use Grant gates
  production use; auto-converts to OSS on a Change Date (≤4 yrs). **Not OSI open source** (fails
  the OSD; OSI rejected SSPL). The 2023–24 relicensing wave (HashiCorp→BSL→OpenTofu fork;
  Sentry/Codecov→FSL; Mongo→SSPL; **Elastic 2021→SSPL then 2024 *reverted* + added AGPLv3**) was a
  defense against **hyperscalers** reselling a managed service `[fact: hashicorp.com; elastic.co;
  opensource.org/osd]`. **No hyperscaler forks a solo family app** → that threat doesn't apply.
- **Open-core / dual-license:** OSS core + closed premium/hosting; dual-license needs the licensor
  to **own all copyright → a CLA** the moment outside contributors appear `[fact: Wikipedia AGPL;
  apache.org/icla]`.

## C. Monetization while OSS (ranked for a solo founder)

| Rank | Model | Verdict |
|---|---|---|
| 1 | **Hosted SaaS (managed convenience)** | **The product.** Families pay to *not* run infra; the moat is ops + brand + "it just works," uncopyable by OSS. Survives full source availability. |
| 2 | **Open-core — G1 "brains" closed** | Thin secondary lever (intelligence is commoditized, so closed-brains protects little). Keep G1 closed for optionality; don't bank revenue on it. |
| 3 | **Sponsorship / donations** | Showcase-aligned, trivial $, near-zero ops. Enable as a low-effort signal. |
| 4 | **Support / services** | **Reject** — violates the ~2 hrs/wk steady-state ceiling (KS-3); a second job, not durable side income. |

## D. Business tradeoffs (assessed)

- **Pro — trust/transparency (highest-value here):** public auth/crypto/visibility code is a
  trust asset for a family-data product; serves the constitution's family-trust value + the
  "never system-of-record / honor delete" posture. Justifies OSS on its own.
- **Pro — recruiter/portfolio (the north star)** + **free marketing** (GitHub discovery is the
  cheapest acquisition channel; acquisition, not cost, is the binding constraint).
- **Con — support burden (the ONE real cost):** OSS invites "help me self-host" issues that burn
  the time ceiling. **Mitigate:** README states "community project, no support SLA → want it to
  just work? hosted Dayfold"; best-effort issues; don't accept PRs you must maintain.
- **Con — competitor forks / self-host cannibalization:** near-zero (funded verticals won't adopt
  a solo app's code; the self-host cohort ≠ the paying cohort).

## Recommendation matrix

| Component | License | Why |
|---|---|---|
| **Server API** (`apps/api`, TS) | **AGPL-3.0-or-later** | §13 network clause (optics + insurance vs a hypothetical same-stack competitor); you, sole owner, stay free to run/sell closed. OSI-approved → keeps showcase credibility (vs BSL). |
| **Client** (`apps/client`, `apps/androidApp`) | **Apache-2.0** | Permissive = max showcase/adoption + patent grant; Apache→AGPL is the legally allowed direction; HTTP boundary = aggregation. |
| **CLI** (`apps/cli`) | **Apache-2.0** | Same — separate process over the API, no copyleft propagation. |
| **Schema/codegen** (`packages/schema`) | **Apache-2.0** (or MIT) | Must flow into BOTH the AGPL server and the Apache client → permissive keeps it compatible both ways. |
| **G1 "brains"** (future) | **Closed** (separate private repo) | Reached over the API; closed by *not publishing*, not by license. |
| **Contributions** | **DCO now** | Low friction, inbound=outbound. A **CLA** is needed ONLY if a dual-license/commercial-sale is intended — decide that *before* opening contributions. |

**Mixed set is clean + common:** Apache→AGPL is one-directional compatible; an Apache CLI/client
talking HTTP to an AGPL server is *aggregation*, not a derivative — copyleft does not propagate
`[fact: apache.org/GPL-compatibility; gnu.org/gpl-faq]`. **Pitfall:** never statically link/bundle
AGPL server code into the Apache client/CLI (breaks the one-directional compatibility).

## Top 3 risks

1. **Secret leakage on flip-to-public** (the #1 empirical OSS failure, ~2× with AI-generated
   code) — full-history scan + rotate + push protection **before** going public.
2. **CLA/dual-license lock-out** — under DCO you cannot later relicense contributors' code without
   re-consent; decide dual-license intent before opening contributions.
3. **AGPL adoption chill / accidental copyleft bleed** — AGPL deters some corporate users; never
   bundle AGPL server code into the Apache client.

## Sources
- Business: stormy.ai (open-core funnel) · flowverify.co (2026 relicensing recap) · ossalt.com,
  getmonetizely.com (license-for-SaaS) · `research/.../pricing-structure.md` (revenue).
- License/security: gnu.org (GPL/AGPL/why-affero) · apache.org (Apache-2.0, GPL-compatibility,
  ICLA) · choosealicense.com · opensource.org (OSD, SSPL rejection) · mariadb.com (BSL) ·
  hashicorp.com / sentry.io / elastic.co / mongodb.com (relicensing wave) · NIST SP 800-160 ·
  OWASP (authz, ASVS) · GitGuardian 2026 · github docs (secret scanning, SECURITY.md) ·
  developercertificate.org (DCO) · spdx.org / reuse.software (mechanics).
