# Agent: tech-platform (2026-06-18)

**Summary:** Feasibility MIXED, one load-bearing kill-risk.

- **CMP:** Android + iOS Stable (1.8.0, May 2025, production-ready). **Web
  (Kotlin/Wasm) is BETA only** (1.9.0, Sept 2025) — early-adopter, not
  rock-solid. iOS dev requires a Mac; Swift export experimental.
- **Gmail scopes:** `gmail.readonly` AND `gmail.metadata` both RESTRICTED.
  Server-side handling → mandatory **CASA** security assessment, re-verified
  every 12 months. Free self-scan deprecated; paid lab ~$540 (TAC, Tier 2)
  to $800-1,500; Tier 3 pentest ~$4,500-8,000+. **Calendar read scopes are
  only SENSITIVE → verification only, NO CASA.** → Calendar-only MVP dodges
  the gate.
- **Instacart:** Developer Platform (prefilled cart) currently CLOSED to new
  applicants, no waitlist. **Walmart add-to-cart URL** is the solo-dev
  fallback. Amazon ATC gated/deprecating.
- **Weather:** free commercial tiers — US NWS api.weather.gov (no key);
  WeatherAPI.com (100k/mo free, commercial OK).
- **LLM cost:** trivial — ~$0.0005-0.008/briefing (GPT-nano/Gemini-Flash/
  Haiku); $0.016-0.24/family/mo. Not the constraint.
- **Native overlap:** Gemini Daily Brief (May 19 2026) reads Gmail+Calendar
  but is **single-account, paid-tier, US-only, 18+**. **No native OS offers
  a multi-member household-tenant briefing — the defensible (time-sensitive)
  wedge.** Android At-a-Glance = glanceable widget only; Apple ships no
  aggregated briefing.

Sources: blog.jetbrains.com CMP releases; developers.google.com OAuth
scopes + restricted-scope verification; appdefensealliance.dev/casa;
docs.instacart.com; walmart.io/docs/atc; weather.gov; platform.claude.com
pricing; gemini.google/overview/daily-brief.

Residual: written CASA quote (Tier 2 vs 3); confirm on-device Gmail exemption;
re-check Instacart IDP reopening; re-check Gemini family variant quarterly;
confirm CMP Web stability at build time; Mac available for iOS builds.
