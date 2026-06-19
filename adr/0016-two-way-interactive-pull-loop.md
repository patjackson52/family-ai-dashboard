# ADR 0016: Two-Way Interactive Pull-Loop (reserved, bounded-now)

## Status

**Proposed** (2026-06-18) — operator set the **bounded-now scope** + the
recording method in-session. Records decisions made now (scope boundary,
E2E-reasoning-in-the-loop principle, the cheap reservations) and **reserves**
the full two-way build for a later milestone (a conversational expansion would
need its own ADR + a constitution update). Composes with ADR 0004/0006/0007
(dumb store, render-don't-reason), 0011 (scopes), 0012 (agent loop), 0013
(redux client), 0015 (E2E).

## Context

The client will eventually be **two-way**: members create inputs (tap
**buttons**, submit **structured asks**, later free-text **prompts**) that are
**processed by an AI loop in a pull model** — the loop pulls pending inputs,
reasons, and pushes result cards back. Today the client is a one-way dumb
renderer. Does this change the architecture? **Mostly it reinforces it.**

## Decision

1. **The architecture stands — additive, no rework.** The reverse channel is a
   new **`intents`** write surface + new **scopes** (`intents:write` for member
   clients, `intents:resolve` for the loop credential), on the existing tenant-
   explicit API. redux-kotlin's unidirectional flow models it natively
   (button/ask → dispatch → effect `POST`s an intent → optimistic "thinking"
   state → `sync` pulls the result card).
2. **The server still never reasons.** "Pull/loop processed by AI" *is* the
   content-authoring loop (ADR 0012), now bidirectional: the **key-holding
   loop** pulls intents, reasons, pushes results. This preserves the dumb-store
   thesis for 2-way.
3. **E2E principle (load-bearing):** under ADR 0015, intents are **encrypted
   too**, so the loop must **decrypt** them → **AI reasoning runs only in a
   key-holding context (operator's machine / a member device), never server-
   side.** A *hosted* AI loop cannot decrypt and would break E2E — so the
   "where does the family AI loop run?" answer under E2E is **a key-holder
   only**. (If a hosted loop is ever wanted, it requires relaxing E2E for that
   path — a future ADR.)
4. **Scope boundary (bounded-now, operator-set):** MVP of 2-way = **buttons +
   structured/template-bounded asks** — stays within the constitution's "not
   an open-ended AI chatbot" line. **Free-text conversational prompts are
   reserved**, gated behind a future ADR + a constitution amendment. The
   **async pull-loop** ("ask now, calm answer later") is calm-compatible — not
   a real-time chat.
5. **Cheap reservations made now** (additive, costly to retrofit later):
   - **`actions[]` metadata on cards/blocks** (button/structured-action defs:
     `{label, action_id, params}`) — like `triggers[]`. Content can carry
     interactive affordances before the loop exists.
   - **Reserve the `intents` concept + scopes + the tenant-explicit path
     `/families/{fid}/intents`** in the data/API model (named, not built).

## Rejected alternatives

- **Open conversational from the start** — rejected (operator): drifts into the
  chatbot NOT-line; needs a constitution change first.
- **Server-side AI processing of intents** — rejected: breaks E2E and the
  dumb-store thesis; reasoning stays in the key-holding loop.
- **Build the 2-way channel now** — deferred: not needed for the prototype;
  only the cheap reservations land now.

## Consequences

Positive: a clear, additive path to interactivity that *reuses* redux flow +
scopes + the loop; the E2E story stays coherent (reasoning = key-holder);
no current rework.
Negative: a future conversational expansion is gated (deliberately) on a
constitution/ADR change; a hosted (non-key-holding) AI loop is foreclosed
under E2E; reserving `actions[]`/`intents` adds two unused fields/notes now.

## Revisit Trigger

Two-way is scheduled for build (write the full ADR then); OR free-text
conversational prompts are wanted (constitution amendment + ADR); OR a hosted
AI loop is desired (re-examine the E2E boundary).

## Reserved-now changes
- `02-data-model` / `event-hubs-design`: add `actions jsonb` to blocks + cards
  (reserved). Note an `intents` table shape for later.
- `03-api`: reserve `/families/{fid}/intents` (write by members, pull/resolve
  by the loop) — documented, not implemented.
- `04-auth`: reserve `intents:write` / `intents:resolve` scopes.
