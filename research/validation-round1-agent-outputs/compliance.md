# Agent: compliance (2026-06-18)

**Summary:** COPPA is the binding federal floor; applies in full the moment
under-13 children get their own profiles/logins ingesting Gmail/Calendar/
location. NO small-business/pre-revenue exemption. Amended COPPA Rule (eff.
2025-06-23; full compliance 2026-04-22, already past) requires **separate**
verifiable parental consent before disclosing a child's data to any third
party — captures routing child data to a third-party LLM. "Email-plus" VPC
barred once you disclose to a third party. Penalty **$53,088/violation**
(Disney paid $10M, Sept 2025).

**Biggest hard-stop (structural, below COPPA):** Google account
architecture. Under-13s can't hold standard Google accounts (supervised
Family Link only); Gmail not provisioned by default; a child **cannot
self-grant restricted OAuth scopes** — parent-gated in Family Link. This
kills "kids have their own logins reading their own Gmail" at the
data-source layer. **Viable reframe: parent-owned account + parent-granted
OAuth + parent as data controller/consent-giver** — which also clears the
COPPA VPC path.

- **Gmail read = RESTRICTED** (CASA assessment, recurring). **Calendar read
  = SENSITIVE** (verification only). Feeding Gmail to an LLM permitted under
  Google Limited Use ONLY as a prominent user-facing feature, vendor
  processor-only (no training/retention), no human review, full disclosure;
  training/ads/sale hard-banned.
- **LLM vendors:** Anthropic & OpenAI both default to no-training-on-API-
  data, provide DPAs (you = controller). Both PERMIT minor-serving API
  products with COPPA + safeguards (Anthropic's 18+ rule is consumer-Claude-
  only, NOT an API bar). OpenAI requires ZDR for under-13 personal data.
- **State laws:** most comprehensive laws exempt a small/pre-revenue app via
  thresholds (Texas/Nebraska exception, w/ SBA small-biz carve-out). CA AADC
  largely enjoined (9th Cir, 2026-03-12). **Maryland Kids Code live &
  enforceable**; CT/CO minors rules bite if targeting children.

Sources: ecfr.gov 16 CFR 312; federalregister.gov 2025-05904; ftc.gov COPPA
FAQ + penalties; support.google.com restricted-scope + Family Link;
developers.google.com user-data-policy; anthropic.com/legal; OpenAI data docs.

Residual: COPPA attorney review (is LLM disclosure "integral"?); ADR scope
decision (child accounts vs parent-centric — favors parent-centric);
build-time OAuth spike on a Family Link test account; SBA size standard;
execute both LLM DPAs.
