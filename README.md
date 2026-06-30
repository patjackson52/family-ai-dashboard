# Dayfold

A calm, AI-powered household dashboard. One account per family, members log
in (adults at MVP). It reads the family's existing signals — calendar,
email, lists/tasks, weather, location — and renders a single sleek daily
**briefing** plus a short list of **smart recommended actions** with deep
links ("party Saturday — ordered groceries? [list]"; "school email needs an
RSVP Thursday [reply]"; "rain at soccer 4pm — pack jackets").

Built mobile-first on **Compose Multiplatform** (Android/iOS/Desktop). The MVP
wedge is a **content API + CLI + Claude skill**: AI loops and scheduled tasks
author/update the cards — the dashboard *renders* intelligence produced
elsewhere; it is not an open-ended chatbot. Dogfooded on the operator's own
household first.

> **Status (2026-06-30):** M0 prototype **built + live** (Vercel + Neon;
> Android renders on-device; full sign-in → CLI device-login → author a hub →
> renders on the phone). Two-way member writes (toggle / delete / hide) and
> on-device Now-derived surfacing are built and tested. Validation round 1 verdict:
> **CONDITIONAL — learning-lab GO, standalone-business NO-GO**. Building to learn.

---

## How it works

```
┌────────────────────────────────────────────────────────────────────────────┐
│                           Dayfold Architecture                             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│   Author side                          Render side                        │
│                                                                            │
│  ┌──────────┐  PUT /families/:id/*   ┌──────────────────────────────────┐ │
│  │ dayfold  │ ─────────────────────▶ │     Content API                  │ │
│  │ CLI      │                        │   TypeScript / Hono              │ │
│  │ (Kotlin) │                        │   Vercel serverless              │ │
│  └──────────┘                        │   Neon Postgres                  │ │
│       ▲                              │                                  │ │
│       │ dayfold template/push/pull   │   Auth: EdDSA tokens             │ │
│       │                              │   RFC 8628 device grant          │ │
│  ┌──────────┐                        │   Firebase Auth (Google/Apple)   │ │
│  │ Claude   │                        │   Per-hub visibility             │ │
│  │ Curator  │                        └─────────────┬────────────────────┘ │
│  │ Skill    │                                      │ GET /families/:id/   │
│  └──────────┘                                      │ sync (cursor page)   │
│                                                    ▼                      │
│   ┌────────────────────────────────────────────────────────────────────┐  │
│   │              Client (Compose Multiplatform)                        │  │
│   │                                                                    │  │
│   │  Android · iOS · Desktop                                           │  │
│   │  KMP commonMain: redux-kotlin store + SQLDelight offline cache     │  │
│   │  network → DB → store → Compose UI  (unidirectional)              │  │
│   │                                                                    │  │
│   │  On-device Priority Engine (derived "Now" items):                  │  │
│   │    hub triggers[] + places → rank() → two-lane feed               │  │
│   │    (live position NEVER leaves device — ADR 0014/0044)            │  │
│   │                                                                    │  │
│   │  Two-way writes (member → server relay, not AI reasoning):         │  │
│   │    outbox → PUT /blocks/:id (If-Match + op_id idempotency)        │  │
│   └────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

**Content model.** Hubs are standing project/event pages (3-level tree: hub →
section → block). Briefing cards surface in the "Now" feed — time/place-relevant,
short-lived intelligence that deep-links into a hub.

**Privacy-first.** Live location never leaves the device. Geo-matching is
on-device. Content is currently plaintext (E2EE reserved for M1, ADR 0015).
No server-side email read; no child accounts at MVP (COPPA guardrail).

---

## Orientation

- [CLAUDE.md](CLAUDE.md) — session protocol, governance, directory map, agent routing
- [context/values-and-direction.md](context/values-and-direction.md) — operator-owned north star
- [context/business-constitution.md](context/business-constitution.md) — identity + scope firewall (what it is NOT)
- [adr/decisions-index.md](adr/decisions-index.md) — all 44 architecture decisions
- [backlog/now.md](backlog/now.md) — **current immediates (read first)**
- [backlog/operator-inbox.md](backlog/operator-inbox.md) — items awaiting the operator
- [CHANGELOG.md](CHANGELOG.md) — product, API, and CLI feature history

---

## Repository layout

| Path | What |
|---|---|
| `apps/api` | Content API — TypeScript / Hono / Postgres (Neon), on Vercel. Auth (EdDSA tokens, device-grant RFC 8628, Firebase verify), hubs + cards + blocks, scope + per-hub visibility, two-way mutations. |
| `apps/client` | Compose Multiplatform UI (Android/iOS/Desktop) — feed + hub renderer; redux-kotlin store; SQLDelight offline cache; on-device priority engine (derived Now); two-way outbox. |
| `apps/androidApp` | Android host — the dogfood target (Pixel 10). |
| `apps/cli` | The `dayfold` CLI (Kotlin) — `login` · `push` · `pull` · `template` · `delete` · `update` · `whoami`; authors content into the API. |
| `packages/schema` | Generated content schema (`content.schema.json` → Kotlin/TS) — the card/hub/block contract. Single source of truth. |
| `packages/linkrules` | Shared link-scheme allowlist (CLI + client, no drift). |
| `adr/` | 44 Architecture Decision Records — immutable once Accepted. |
| `specs/` | PRD, domain model, design specs — binding after design sign-off (ADR 0008). |
| `designs/` | Hi-fi UI/UX mockups — precede every build (ADR 0008). |
| `processes/` | Agent workflows: planning loop, dev loop, routing, fleet patterns, release runbooks. |
| `context/` | Operator-owned north star, constitution, constraints, kill-switches. |
| `backlog/` | `now.md` / `next.md` / `later.md` / `operator-inbox.md`. |
| `research/` | Validation reports, agent fleet outputs, design reviews. |

---

## Build & run

**Read `processes/agent-dev-loop.md` first** — it documents the fixed toolchain
(JDK17, Kotlin 2.3.20, redux-kotlin alpha01 gotchas) and the cheap feedback loop
(text action log, snapshot PNGs, devtools, cloud URL).

Quick start:
```sh
# API (Vercel/Neon — live): https://family-ai-dashboard.vercel.app
# Run API tests locally:
cd apps/api && npm ci && npx vitest run

# Client desktop tests + snapshots:
cd apps && JAVA_HOME=<jdk17> ./gradlew :client:desktopTest

# Android build:
cd apps && ./gradlew :androidApp:assembleDebug

# On-device demo (seeds DB, starts API, installs APK):
apps/scripts/ondevice-demo.sh
```

---

## Author content (CLI + Claude)

See `apps/cli/templates/README.md` for the typed-content authoring guide (cards
and hub trees) including the full markdown subset the app renders.

```sh
dayfold login                          # RFC 8628 device grant — owner approves in-app
dayfold whoami                         # confirm family + scope
dayfold template invite > card.json   # get a starter body
# ... edit card.json ...
dayfold push my-card-id card.json --type invite   # validate + push
dayfold pull                           # read back cards + hubs
```

---

## Curator skill (Claude Code)

`.claude/skills/dayfold-curator/` is the authoring wedge — a Claude Code skill
that analyzes your context (calendar, email, files, notes), runs an onboarding
questionnaire, and authors Hubs + BriefingCards through the `dayfold` CLI
(propose-confirm before every push).

```sh
sh .claude/skills/dayfold-curator/install.sh   # link globally
# Then in any Claude Code session: /dayfold-curator
```

Requires `dayfold` on PATH and `dayfold login` done first.

---

## Running the planning loop

- One-shot: open a session here and say **"run a loop iteration"** (follows
  `processes/planning-loop.md`).
- Sweep `backlog/operator-inbox.md` weekly.

---

## Lineage

Built from the **venture-loop template** (extracted from the KeepQR /
RevenueCatch projects). Process inspiration from the sibling `ambient-ai` spec
repo ("render, don't reason"; ADR + open-questions discipline; persona-driven key
moments).
