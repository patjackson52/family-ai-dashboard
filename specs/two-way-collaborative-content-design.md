# Two-Way Collaborative Content — Interactive To-Do Lists (Design)

**Date:** 2026-06-27
**Status:** Design synthesis from a 4-agent brainstorm + 2-round adversarial
review. **Pre-decision** — the durable calls are ADR-class and values-shaped, so
this drafts a **Proposed ADR 0038** + an operator-inbox item; nothing here
auto-applies. No build before operator ratification (and, for the client
surfaces, the ADR 0008 design-first gate).
**Surfaces:** content schema, API + Postgres, CLI/curator skill, Compose
Multiplatform client + sync engine.
**Authoritative refs:** `adr/0016` (two-way pull-loop / intents), `adr/0020`
(offline-first DB-as-SoT + reserved outbox), `adr/0015`/`adr/0017` (E2EE),
`adr/0022` (typed content + fold gesture), `adr/0029` (scoped grants),
`adr/0030` (per-member visibility), `adr/0025` (abuse limits),
`adr/0006` (hubs), `adr/0014` (trigger engine),
`specs/prototype/02-data-model.md`, `specs/prototype/03-api.md`,
`specs/domain-model/schemas/content.schema.json`.

---

## 1. Problem & why it matters now

To-do lists are a natural, high-frequency family content type, and they are the
first content that members want to **change from the device** — tick "packed the
tent," check off groceries. Today Dayfold is a **one-way dumb renderer**: the
client is a pure read-replica (ADR 0020), content is server-authored by the
CLI/loop, and the only checklist that exists (`block_type='checklist'`) renders
**read-only** (`apps/client/.../HubScreens.kt:475`, `ChecklistRow` — drawn, not
tappable).

Making a checkbox tappable is deceptively deep: it is Dayfold's **first
two-way data-flow primitive**, and the operator's brief is explicit that the
*primitives* must be right because this is the first of a family of mini-features
(budget `paid`, RSVP, etc.) that will reuse them. Get the primitive wrong and
every later interactive feature inherits the mistake — or forces a re-model right
when E2EE (M1) makes such a re-model most expensive.

This document synthesizes four independent brainstorms (content model; sync &
conflict resolution; API/server contract; UX/collaboration) and two adversarial
review rounds into one coherent design + the decisions the operator must ratify.

## 2. The two load-bearing insights

**Insight 1 — A toggle is a *direct collaborative mutation*, not an `intent`.**
ADR 0016 reserved a two-way channel as **`intents`**: a member submits an ask →
a **key-holding AI loop** pulls it, reasons, and pushes a result card. A checkbox
needs *no reasoning*. Routing a tick through the AI loop would add seconds-to-
minutes of latency ("your checkmark is thinking…"), require the loop to be online
to apply a tick, and break offline entirely. So a todo toggle is a **new
reverse-channel primitive — a "content delta"** — that sits *beside* `intents`,
not inside it. Critically, the dumb-store thesis survives: for `intents` the
reasoning lives in the key-holder; for content-deltas **there is no reasoning** —
the server mechanically relays an opaque block. "Server never reasons" holds for
both (ADR 0016 §2).

**Insight 2 — E2EE-forward forces conflict resolution to the client.** M0 is
plaintext, but ADR 0015/0017 make content **ciphertext at M1** with the server a
**zero-knowledge store** (AEAD AAD = `(family_id, id, version)`). Therefore any
conflict-resolution that needs the **server to read or merge plaintext** todo
state (server-side field merge, server LWW on `done`, a per-item `PATCH` that
splices one item, promoting `done` to an indexed column) is **structurally
impossible at M1** — and choosing it now is exactly the "painful re-model later"
the project repeatedly warns against. This single constraint decides most of the
architecture:

> **The server orders and relays opaque blocks by row; clients converge.
> Conflict resolution is a deterministic client-side merge over stable per-item
> identity. The server never reads `done`.**

Everything below follows from these two insights, and all four lenses converged
on them independently.

## 3. The primitive: the *addressable, togglable item*

The root cause of "two members clobber each other" is that the current
`ChecklistPayload` items are **positional and id-less**
(`content.schema.json:46` — `{ text, done, due, assignee }`). You cannot merge
concurrent toggles keyed on array index (a re-authored list shifts indices) or on
text (duplicate/edited text breaks it). **Stable per-item identity is the one
non-negotiable, must-land-at-M0 reservation** — the checklist analogue of ADR
0015's "lock the cleartext/ciphertext column split early." Every other field can
evolve later; without `id`, neither the merge (§5) nor the calm "Dad checked it"
byline (§7) is possible.

Define a reusable shape — **`AddressableItem`** — rather than a todo-only one,
because the same toggle primitive is already wanted in three places:
`checklist.done`, `budget.paid` (`content.schema.json:51` — structurally
identical, same id gap), and `invite.rsvpState` (the first thing a real two-way
write unblocks). The client already shares one DTO (`ChecklistItem` serves both
checklist **and** budget — `Model.kt:210`), so generalizing matches the code's
grain. **Recommendation: build the togglable-item primitive once.** (This is a
ratify item — see §11 — because "build the general primitive vs todo-first" is a
scope call.)

### 3.1 Item schema (extends `ChecklistPayload`)

```jsonc
"ChecklistPayload": {
  "type": "object", "required": ["items"],
  "properties": {
    "items": { "type": "array", "items": {
      "type": "object",
      "required": ["id", "text"],
      "properties": {
        "id":     { "$ref": "#/$defs/ulid" },        // NEW — stable per-item id (load-bearing)
        "text":   { "type": "string" },
        "done":   { "type": "boolean", "default": false },
        "doneBy": { "type": "string" },              // NEW — user id who toggled (display: "✓ Dad")
        "doneAt": { "$ref": "#/$defs/timestamp" },   // NEW — when (LWW stamp + "packed 4pm")
        "ord":    { "type": "integer", "default": 0 },// NEW — explicit order, survives reorder
        "due":    { "$ref": "#/$defs/timestamp" },
        "assignee": { "type": "string" },
        "rev":    { "type": "integer", "default": 0 } // NEW — per-item edit counter (text/due/assignee LWW; see §5.2)
      },
      "additionalProperties": false
    } }
  }
}
```

The whole `payload` carries the `x-e2e` annotation (ADR 0022 D2), so it becomes
**one ciphertext envelope at M1** — the server stores it opaquely. `id`/`ord`/
`rev`/`doneAt` are *inside* that envelope; the server sees none of them. (It is
fine that ULIDs are opaque even if the server could see them; it cannot at M1.)

### 3.2 Markdown ⇄ structured duality

The operator wants todos embeddable in markdown **and** interactive. Resolve it
by *direction of flow*, never by storing markdown as the source of truth:

- **Storage is always structured** (`payload.items[]`) — the addressable,
  mergeable, E2EE-clean representation.
- **Rendering to markdown is a projection.** A checklist renders trivially as a
  GitHub-style task list (`- [ ]` / `- [x] ~~…~~`); the client already draws the
  visual equivalent.
- **Authoring markdown → structured is a stamp step in the CLI/curator skill.**
  An author writes `- [ ] Pack jackets`; on `dayfold push` the CLI converts it to
  `{ id: <fresh ULID>, text, done:false, ord }`. This extends the skill's
  existing ULID rule ("new content → new id; update → reuse the id from `pull`").
  **Item ids are minted client-side (CLI/skill/app), never server-side** — the
  server can't read ciphertext to mint at M1.
- **Interactive checkboxes *inside prose*:** do **not** parse `- [ ]` out of
  `body_md` (server-side markdown parsing is an E2EE violation, and positional
  parsing reintroduces the id problem). If inline interactivity is ever wanted, a
  `markdown` block carries a reference token (e.g. `{{todo:<blockId>}}`) the
  renderer replaces with the live widget pulled from a sibling `checklist` block.
  **Deferred** — it needs new renderer syntax; the plain structured block needs
  none. Flagged, not in the first slice.

Net: **structured is canonical; markdown is both an input format (stamped in) and
an output rendering (projected out).**

## 4. The three renderings & the fold (UX)

One content item, three renderings (ADR 0022), but **not symmetric** — a
checklist lives as a *hub block*, so:

- **Now card — glance + deep-link, NOT directly checkable at M0.** A
  `BriefingCard` the loop emits pointing at the list (`target.blockId`):
  title = progress ("Packing — 3 of 5 done"), body = the top 1–2 undone items
  ("Still open: sunscreen, Theo's meds"), one **Open list ↗** button, provenance
  + privacy chip. The "3 of 5" is an **author-denormalized snapshot** the loop
  stamps — the Now surface never reads item internals (keeps "render, don't
  reason"). Tapping = the **fold gesture** (container-transform card→detail).
  *Why not checkable here:* the card is a summary, not the live list; a check on
  the card would desync the snapshot until the loop re-emits, and keeping the
  **one** checkable surface = Detail/Hub-block gives a single write path to reason
  about. (A "quick-check from the card" is a clean post-M0 extension.)
- **Hub block — the canonical interactive surface.** The existing `ChecklistRow`
  list inside a hub dossier, made tappable. Each row: filled-coral-✓ when done /
  warm-ring when not, strike-through + `onSurfaceVariant` when done,
  `due · assignee · ✓ Dad` subline.
- **Detail — full interactive list.** Constant Detail skeleton (hero → metadata →
  actions → provenance+privacy → related); for a checklist the **hero is the list
  itself**.

**The check *is* a fold (the differentiated, on-brand move).** A completed item
doesn't vanish (jarring; you may want to un-tap) and doesn't clutter. After a
brief dwell, completed items **fold into a collapsed "▸ 3 done" foldaway** at the
bottom, using the same container/`AnimatedContent` vocabulary as the card→detail
fold, at row scale. So an active list of 5 with 3 done reads as **2 live rows +
one "3 done" line** — calm, glanceable, honest. "Content folded away until it
matters" enacted literally.

**Tap micro-interaction (≤200ms, M3 Expressive emphasized-decelerate):** box
fills coral with a quick scale-overshoot, ✓ draws in, strike-through wipes
left-to-right (animate between `ChecklistRow`'s two states, never snap); **one**
light haptic tick (never a pattern — a buzz pattern reads as a game/notification;
honor OS haptics-off); settle to de-emphasized; **debounce the fold ~1.5–2.5s**
after the last toggle so rapid multi-checking doesn't make rows leap mid-tap
(this also batches the outbox flush, §5.3). Whole-row 48dp hit target. Un-check is
symmetric; **no confirmation dialog, ever.**

## 5. Sync & conflict resolution

### 5.1 The merge primitive: per-item LWW-register, resolved client-side

For a **family** (n ≤ ~6, ADR 0015), **rare-but-real** concurrency, offline-first
(ADR 0020), zero-knowledge server (M1) — the right primitive is **per-item,
per-field Last-Writer-Wins registers keyed by stable item id**, merged by a pure
client function `merge(local, remote) → state`. Items are a **LWW-element-set**
(add-wins membership via a per-item tombstone register); each mutable field is an
**LWW-register**. This *is* a CRDT — the minimal one — and needs **no external
library** (~100 lines of KMP `commonMain`).

**Rejected alternatives:** (a) whole-block LWW (today) — *is the bug*; one tick
re-PUTs the whole array and the last writer erases every concurrent change; fine
only for single-writer server-authored content. (c) off-the-shelf CRDT
(Automerge/Yjs/Loro) — **no native KMP binding exists** (JS/WASM/Rust/Swift only;
hand-rolled FFI across 3 targets is a large unaudited surface for a project that
nervously pins one pre-1.0 crypto lib) **and over-modeled** (sequence-CRDT
character-merge machinery a family checklist never needs) — keep as a *revisit
trigger* if collaborative rich-text (`body_md`) is ever wanted. (d) server
total-order op-log — the server would sequence opaque ciphertext ops it can't
causally order at M1; keep the server's existing **row-level keyset order** as the
opaque relay order, but convergence comes from the per-item stamp, not the
server's sequence.

### 5.2 The stamp / ordering — *recommend wall-clock + actor tiebreak; HLC reserved*

Each register write carries a stamp so clients converge deterministically without
the server reading content. Two candidates:

- **Recommended (M0): `(doneAt | rev-bumped-updatedAt, actorId)` — a wall-clock
  LWW-register with a stable actor-id tiebreak.** `actorId` = a per-installation
  client id minted on first run (device-grain, not user-grain: one user's two
  devices can edit offline independently). Compare by timestamp, then `actorId`
  lexically. Simple, human-meaningful, adequate when phones are NTP-synced
  (skew typically < 1s) and the **done-wins bias (§5.4)** already removes the most
  common same-item race from depending on clock precision at all.
- **Reserved upgrade: Hybrid Logical Clock (HLC) `(wall, counter, actorId)`.**
  HLC is the textbook local-first primitive; its advantage is causality-correct
  ordering under clock skew / backward clocks. **Adversarial-review verdict:
  overkill for the M0 family workload** — the counter-management + merge-time
  clock-bump complexity buys little when concurrency is rare and the done-wins
  bias dominates. Adopt HLC only if dogfooding surfaces clock-skew anomalies. The
  field shape (`doneAt`/`rev` per item) is forward-compatible — swapping in an HLC
  stamp is a field-format change inside the same ciphertext payload, **not a
  re-model.**

The stamp lives **inside the (M1-ciphertext) payload** — it is *content*, so the
server can never order by `done`-time. Distinguish two clocks: the **in-payload
stamp = the merge clock** (client-only); the **row `version`/`updated_at` = the
relay clock** (server-assigned, drives /sync order + tombstones). Two clocks, two
jobs — keep them separate.

### 5.3 The outbox lifecycle (preserves unidirectionality)

ADR 0020 reserved exactly this: *"the 2-way path adds a local outbox table
feeding the same unidirectional flow."* New client-side SQLDelight table:

```
outbox(op_id PK,            -- ULID = idempotency key
       family_id, block_id,
       payload_blob,        -- the new merged checklist payload (cipher at M1)
       base_version,        -- server version the op was computed against
       state,               -- pending | inflight | acked
       created_at)
```

Lifecycle: **(1) optimistic local apply** — the tap writes the local DB (still the
single writer of UI state) with a fresh stamp → reactive query → store → UI flips
instantly; the same transaction inserts the outbox row. **(2) push** — a sender
loop (sibling to the poll loop in `SyncEngine`) drains the outbox FIFO per block:
`PUT …/blocks/{id}` with `If-Match: base_version` + `Idempotency-Key: op_id`.
**(3) ack** — store the returned `version`, mark `acked`. **(4) precondition
failure** — see §6.2: refetch, re-run the client `merge()`, recompute against the
new base, re-enqueue; because the merge is deterministic this **always
converges** — the failure is a benign "merge & retry," never user-facing.

**Why unidirectionality holds:** the **render path is unchanged** (`DB → reactive
query → store → UI`; DB is still the only writer of displayed state). The outbox
is a strictly **write-only egress lane** (`DB → outbox → network`) that never
feeds the UI. Inbound, /sync still writes the DB in its one crash-safe
transaction; the only change is that `applyDelta` runs `merge()` *instead of blind
upsert* **for two-way block types only** (checklist/budget) — one-way blocks
(markdown/link/contact) keep blind upsert. No second reader, no second render
writer.

**Echo suppression (anti-flicker):** your own op returns via /sync. Two guards:
(i) the `merge()` is idempotent/commutative — re-applying your own already-applied
stamp is a no-op, so even an unsuppressed echo can't flicker the value; (ii)
belt-and-suspenders — when /sync delivers a block whose `version` ≤ a locally
`acked` outbox row's returned version, drop the row and skip the redundant write.

### 5.4 Family-friendly concurrency semantics (values-shaped — ratify)

Resolve by the *family mental model*, not CRDT theory. These are the
`[pending-ratify]` policy choices (one-line changes, not re-models):

- **`done` toggle → "done wins over not-done" (biased LWW).** If Mom unchecks the
  same minute Dad checks, **checked wins.** A false "still todo" that never gets
  surfaced is worse for a household than a false "done" (someone re-buys milk —
  mildly annoying, recoverable), and "someone says it's done" is the calm-positive
  read. Strict LWW is the one-line fallback if dogfooding finds the bias
  surprising.
- **`text`/`due`/`assignee` edit → strict LWW (whole field, by `rev`+stamp).**
  Concurrent same-line edits are rare and low-stakes; no character-merge.
- **Delete vs concurrent toggle → ADD-WINS (item survives).** Accidental
  disappearance of a member's item ("where did my thing go?") is the most alarming
  failure; making content sticky against concurrent deletion is the calm choice
  (OR-Set add-wins via the per-item tombstone register). NB: this is a
  **deliberate divergence** from the *block*-level soft-delete model, which stays
  remove-authoritative/tombstone-LWW — *item*-level deletes inside a checklist are
  add-wins; *block*-level deletes are not.

### 5.5 Freshness: keep ~45s poll at M0

A checkbox is calm, not chat — a co-present parent seeing a toggle land in ≤45s is
fine for a household, and the product is explicitly *not* real-time collaboration.
Keep the existing foreground poll; add **sync-after-push** (a successful PUT
triggers an immediate /sync — the client already holds the mutex). A server
"something changed" nudge (FCM/SSE) becomes the **first real justification** for
the push channel ADR 0020 deferred — flag as the *next* revisit trigger, not M0
work.

## 6. Server contract (zero-knowledge preserved)

### 6.1 Endpoint: reuse the whole-block PUT — no per-item PATCH, no op-log at M0

The member app toggles by reading its cached checklist block, flipping the item
in the (decrypted at M1) payload, and re-PUTting the **whole block** via the
existing route `PUT /families/{fid}/blocks/{id}`. **A granular
`PATCH …/items/{itemId}` is rejected** — it requires the server to read+splice the
ciphertext payload, which is *structurally impossible* under the AAD binding (the
decisive reason, not merely an ergonomic one). An append-only **`POST …/ops`
log is the correct CRDT-grade transport and is reserved** for M2 (Revisit
Trigger: whole-block re-send bandwidth on long lists, or contention the client
LWW-on-retry can't resolve calmly) — over-built for "≤6 adults toggle a shared
list."

### 6.2 What the server does vs must NOT

**Does (all cleartext, no payload introspection):** bump `version=version+1`;
set `updated_at` (DB trigger → surfaces the row past every member's keyset
cursor); enforce `If-Match` (a single **integer comparison**); stamp
server-owned provenance (§6.4); scope + tenancy + **visibility** checks on
cleartext keys (`family_id`, hub `id`/`created_by`/`visibility`); emit tombstones
on soft-delete. **Must NOT:** read `payload.items[]`, any `done`/`text`/per-item
stamp; diff old-vs-new to "merge"; validate item *structure* at M1 (the tolerant
M0 `arr("items")` check `content-validation.ts:75` must be **gated to plaintext-M0
only**, else it forces a decrypt the server can't do).

**Optimistic concurrency under concurrent toggles.** Today `version` is bumped but
`If-Match` is **not enforced** → silent lost-update (Dad's stale-v5 PUT erases
Mom's v6 toggle). **Enforce `If-Match`:** mismatch → **412 Precondition Failed**
(the RFC-correct code for a failed `If-Match`; note the repo currently returns 409
for generic conflicts — the ADR should standardize 412 for precondition failures
and keep 409 for other conflict classes, or consciously reuse 409 — a small
consistency call for the API spec). The server does **one** thing: compare two
integers. The client converts row-level LWW (lossy) into **item-level LWW resolved
on the client** (lossless for the common different-items case) by merge-and-retry
(§5.3 step 4), capped at ~3 attempts with jittered backoff; beyond the cap, a calm
"couldn't save — will retry on next sync" and /sync reconciles. Contention windows
are tiny at household scale, so retry storms are not realistic.

### 6.3 How merged state returns + the security gap to close

The existing keyset `/sync` carries the updated block back **unchanged** — no new
sync machinery; `version`/`updated_at` already ride on the row. **Echo-suppression
is a client contract** (§5.3) the server can't enforce; the server's only
obligation is **monotonic `version` per `(family_id,id)`, always returned** —
which it already guarantees. `version` is the echo discriminator.

> **NEW SECURITY REQUIREMENT (must-fix, surfaced in review):
> enforce hub visibility on the *write* path.** Today the block/section PUT
> checks scope but **not** hub visibility (`app.ts:563-565`) — safe only because
> *only the visibility-exempt household token writes today*. The moment members
> write, a member holding `content:write` could PUT into / probe a **restricted
> hub they cannot read** (ADR 0030). Add a `hubVisible(cred, hub)` check to the
> block + section write paths, mirroring the read routes. Test matrix: member
> writes to {own, family, restricted-visible, restricted-invisible} hub →
> expect 200/200/200/**404** (uniform absence, never 403, for the invisible
> case — no existence leak).

### 6.4 Provenance: server-attested block writer + client per-item byline

Reconcile "show *Dad checked it*" with "server can't read item bodies":

- **Server stamps a cleartext, un-forgeable `provenance.writer_user_id`** =
  the authenticated `sub` (`a.userId`), the same way `credential_id` is stamped
  today (client values stripped first via `SERVER_MANAGED_CONTENT_FIELDS`). This
  is the **attested truth** of "who produced this block version" — the server
  authenticated the JWT, so it *did* observe this, without decrypting anything.
- **Client writes per-item `doneBy`/`doneAt` *inside* the (M1-ciphertext)
  payload** — the **display/convenience** layer. Forgeable in principle, but these
  are co-trusting adults; the byline is UX, not a security boundary. In the common
  case (one member toggles one item per write) the block-level attested writer ==
  the item's `doneBy`, so the badge is trustworthy.

Honest E2EE claim: the server knows *that* Dad's credential wrote block X at time
T, never *what* it says.

### 6.5 Scopes, abuse, idempotency, item-tombstones

- **Member scope (ratify — §11):** recommend member app credentials get **global
  `content:write`**, and let the **visibility filter be the human boundary**
  ("visibility gates human reads/writes; scope gates credential writes"). Per-hub
  credential scoping (ADR 0029) was designed for least-privilege *automation*
  tokens, not for gating which box a *person* ticks; per-hub for a member's own
  phone is redundant with visibility and adds approval friction. **Open
  sub-question:** do we ever want a read-only member (e.g. an eldercare hub a
  member may see but not edit)? If yes, keep per-hub member scoping available.
- **Abuse (extends ADR 0025):** add the first **content-write** rate limits
  (none today) via the existing `ratelimit.hit`: ~60/min per credential, ~240/min
  per family (anti-amplification); 429 + `Retry-After` over cap. New constants →
  record in/extend ADR 0025. Client **coalesces** per-block before flushing an
  offline backlog (collapse N toggles on one block into one PUT carrying the final
  payload; ties to the §4 fold debounce).
- **Idempotency:** the PUT is already idempotent by `(family_id,id)`; `If-Match`
  makes retries safe (a retried original `If-Match` after a successful first
  attempt → 412 → reconcile via /sync). Optional `Idempotency-Key` table
  (`write_idempotency(family_id, key, result_version, created_at)` + TTL) is
  **reserved**, build only if version-churn hurts.
- **Item tombstones:** **none needed at M0.** An item lives in the block payload;
  deleting it = a new block version with a shorter `items[]`; clients replace the
  whole payload. Item-level wire tombstones only matter for the reserved op-log.

## 7. Multi-member awareness, conflict UX, assignment, a11y

- **Awareness = provenance, not presence.** No live cursors (none exist; ADR 0020)
  and don't fake them. "Dad checked the tent" shows as a **quiet byline on the
  row** (`Dad · just now` / a small avatar dot) — discovered on next open, never a
  toast/banner/"Dad is active." Reuses the "added by Claude" provenance pattern
  with a human actor. **Notification line (constitution-bound):** member activity
  is **never itself** a notification; the only legitimate push is the **trigger
  engine** (ADR 0014) firing *to spare effort* ("Groceries done — you can skip the
  store"), opt-in and on-device, **never to report activity** ("Mom checked an
  item" = naggy, forbidden). Push is deferred anyway, so M0 has **no remote-toggle
  notifications** — the correct calm floor; keep it even after push lands.
- **Conflict UX = resolve silently, explain with a byline, never a modal.** A
  family list is not a code merge; if both parents check "tent" they *agree*. A
  late remote change (poll ~30s later) animates between states with the **same
  ≤200ms transition** as a local tap (a calm self-animating row, not a glitch),
  with the byline updating to explain it. **Never move a row out from under a
  finger** — apply the *state* immediately (honest) but **defer the layout
  shift/fold** until interaction ends. The one race-loser case (a remote change
  overwrites your just-made edit) is **not** modaled — the row reconciles with a
  byline so you see *why*; re-tap if you disagree. **No "your change was
  discarded" dialog, ever.**
- **Assignment = display-only, AI-authored, coordination-not-chores (M0).** The
  `assignee` field renders as a quiet subline/avatar ("Theo's meds · Mom") and
  must read as *"Mom said she'd grab this,"* never *"Mom's chore — is she done?"*
  (the explicit chore/allowance/gamification NOT-line). Concretely: **no assign
  UI, no member-picker, no reassign** (the loop/curator stamps `assignee` from
  context); **no per-person progress/scoreboards/completion-rates** (progress is
  list-level "3 of 5," never person-level); **no "overdue, assigned to Dad" nag.**
  Any member may check any item — assignment suggests, never gates.
- **First member-write slice = TOGGLE-ONLY.** Check/un-check is the entire first
  write: no add, edit, delete, reorder, or assign. This is the smallest surface
  that proves the whole outbox→sync→reconcile→provenance loop end-to-end with real
  value, and it honors "render intelligence authored elsewhere" — members
  *interact* with authored content, they don't *author* it. A "+ add item" button
  makes members content-authors, which re-opens `created_by`/visibility/curator
  propose-confirm questions — **defer deliberately**; the absence of a creation
  affordance is *calm by design*, not unfinished. Each later slice (add/edit/
  delete → reorder → in-app assignment) gets its own ADR-class look.
- **A11y + honesty chips.** The interactive row must expose `Role.Checkbox` +
  state (`stateDescription`/`toggleableState`) — TalkBack/VoiceOver announces
  *"Pack sunscreen, checkbox, not checked. Double-tap to toggle."* (today's
  `ChecklistRow` ships **no semantics** — close that gap *the moment* it becomes
  interactive; an interactive control with no a11y state is worse than a static
  one). State is never color-alone (strike-through + text reinforce). Byline in
  the accessible label ("checked by Dad"). Foldaway is a `Role.Button` with
  expanded/collapsed state. Reduced-motion drops the overshoot; haptics honor the
  OS setting. **Honesty-chip wording (ADR 0022 D4 — a claim only where a boundary
  enforces it):** M0-plaintext, the *only* true claims are about **sharing scope**
  and **sync timing** — "**Shared with your family · Synced when online**" (not
  "Stored on your device" — the server holds it too at M0); restricted list →
  "**Shared with N people**" + the ADR 0030 "who can see this" sheet; offline →
  "**You're offline — saved, will sync**" (honest about the outbox). **Never**
  "Location never leaves"/"Matched on your device" on a checklist — those are
  trigger-engine claims.

## 8. M0 → M1 migration: proof of no re-model

The invariant: **the merge is client-side over a server-opaque payload in *both*
milestones.** The only M0→M1 change is whether `payload_blob` is plaintext JSON or
an `EncryptedEnvelope` ciphertext; the server's job (order rows by
`(updated_at,id)`, relay via /sync, never inspect) is **identical**.

- **M0 (ship):** whole-block PUT carrying full `items[]` with **stable `id` + per-
  item stamps**; client merges on inbound /sync; server blind-upserts + bumps
  `version`; payload plaintext. The **one schema reservation that must land now**
  is item `id` (+ `doneAt`/`rev`/`ord`) — cheap while M0 data is throwaway,
  painful after multiple two-way types diverge.
- **M1 (no re-model):** `payload` field-type flips plaintext→`EncryptedEnvelope`
  (a drop-in field swap already designed); AAD `(family_id,id,version)`; stamps
  ride inside ciphertext; **merge code unchanged** (runs on decrypted payload in
  `commonMain`). Seam to confirm with the E2EE build: a whole-block re-PUT bumps
  `version`, and the 412-merge-retry loop re-encrypts with the new `version` in
  AAD on retry.
- **Later (transport optimization, still no re-model):** upgrade to granular ops
  (`{item_id, field, value, stamp}`) when whole-block re-send gets wasteful — each
  op is already an independent idempotent stamped register write; the server
  relays it as one more opaque ordered row; the **merge function is identical.**
  This is the payoff of choosing register-level granularity now.

## 9. Build slices (post-ratify, post-design-gate)

1. **Schema + codegen:** add item `id`/`doneBy`/`doneAt`/`ord`/`rev` to
   `ChecklistPayload` (+ mirror onto `BudgetPayload` if the general primitive is
   ratified); regen TS/Kotlin; CLI/skill stamp-on-push + ULID minting.
2. **Server:** enforce `If-Match`→412; add the **visibility-on-write** check
   (§6.3, the security must-fix) + its test matrix; stamp `writer_user_id`;
   content-write rate limits; gate the item-structure validator to plaintext-M0.
3. **Client sync:** `outbox` SQLDelight table + sender loop in `SyncEngine`;
   per-block-type dispatch in `applyDelta` (merge for checklist/budget, blind
   upsert for one-way types); the `merge()` pure function + tests; echo
   suppression; offline coalescing; sync-after-push.
4. **Client UI (ADR 0008 design-gate first):** make `ChecklistRow` interactive
   (tap, haptic, animation, a11y semantics); the completed-items **foldaway**; the
   Now-card progress summary; conflict-byline + deferred-layout-shift; honesty
   chips.

The **client surfaces are new interactive UI → ADR 0008 applies**: a hi-fi mockup
of the interactive checklist (tap states, foldaway, byline, offline affordance,
restricted-list chip) must be authored in `designs/` and operator-signed-off
before the UI build.

## 10. ADRs to write / amend

- **NEW — ADR 0038 (Proposed): "Two-Way Collaborative Content — Direct Member
  Mutation Primitive."** Decides: the content-delta channel **distinct from**
  `intents`; client-side per-item LWW-register merge with server-as-opaque-relay;
  the whole-block-PUT transport + `If-Match`→412 + merge-and-retry; the
  visibility-on-write rule; server-attested `writer_user_id` + client per-item
  byline; the family-friendly semantics (done-wins, add-wins); the M0 stable-id +
  stamp reservation; member `content:write` scope; toggle-only first slice; the
  reserved op-log/granular-transport upgrade path. ADR-class (automation-autonomy
  boundary + customer-data write path + E2EE posture + scope).
- **Amend/compose ADR 0020** (still *Proposed*): activate the reserved outbox;
  record that the egress lane preserves unidirectionality.
- **Cross-reference ADR 0016** (still *Proposed*): one line — content-deltas are a
  sibling reverse-channel to `intents`.
- **Extend ADR 0025:** content-write rate-limit constants.
- **Spec deltas:** `content.schema.json` (item id + stamps); `03-api.md`
  (`If-Match`→412, `Idempotency-Key` reserved, visibility-on-write, the echo/
  version contract, member `content:write`); `02-data-model.md` (note the
  **client** `outbox` table — client-side only, not a server table).

## 11. Decisions the operator must ratify (→ operator-inbox INB-25)

Per the confidence protocol, these are MEDIUM / values-shaped / scope / ADR-class
— **never agent-decided**:

1. **Accept Proposed ADR 0038** (the primitive + architecture above). *(ADR-class
   — scope + automation-autonomy + E2EE posture.)*
2. **Build the generalized `AddressableItem` primitive now** (checklist `done` +
   budget `paid` + future RSVP) vs **todo-only first**. *Recommend: generalize* —
   cheap while M0 data is throwaway, painful after types diverge. *(Scope.)*
3. **Conflict semantics:** confirm **done-wins-over-not-done** + **add-wins-over-
   delete**, or prefer strict LWW / remove-wins. *Recommend the calm biases.*
   *(Values-shaped.)*
4. **Member write scope:** member app credentials get **global `content:write`**,
   visibility as the human boundary — or keep per-hub member scoping for a
   possible **read-only member** (eldercare). *Recommend global + revisit
   read-only-member if a real case appears.* *(Values/scope.)*
5. **Stamp:** ship **wall-clock + actor-id LWW** at M0 with **HLC reserved**, or
   adopt HLC up front. *Recommend wall-clock + actor; HLC only if dogfooding shows
   skew.* *(Technical — agent-leaning, surfaced for visibility.)*

## 12. Open questions & risks

1. **[high] Stable per-item id is the gate** for both merge correctness and the
   calm byline. Land it at M0 or the whole feature is unsafe to ship. Server
   **cannot** mint it at M1 (ciphertext) — CLI/skill/app own minting.
2. **[high — security] Visibility-on-write is unenforced today** (§6.3). Member
   writes open a restricted-hub write/probe hole until the `hubVisible` check +
   test matrix land. Must ship with slice 2.
3. **[med] Now-card snapshot staleness.** The card's "3 of 5" is loop-stamped;
   a member toggle updates the block but not the card until the loop re-emits.
   *Open:* does the client **live-recompute** the card's progress from the
   resolved `target.blockId` (crosses the "card is denormalized" line) or accept
   brief staleness? *Lean: accept staleness at M0; revisit.*
4. **[med] Untrusted client clocks.** A far-future stamp could pin a value as
   "always newest." Family-trust makes this low-severity; note a **client-side**
   bounded-drift clamp at merge time (the server can't see the stamp under E2EE).
5. **[med] `version`/AAD seam at M1.** Concurrent writers contend `version`; the
   412-merge-retry loop re-encrypts with the new `version` in AAD — confirm with
   whoever owns the E2EE build; it is the one real seam to ADR 0015/0017.
6. **[low] Item-tombstone GC** for add-wins resurrection — short family lists make
   unbounded retention fine; note a compaction policy as later work.
7. **[principle] Provenance-not-presence** should be written down as a durable
   principle (candidate for `context/operating-lessons` or the ADR) so a future
   push-enabled milestone doesn't drift into "Dad is viewing" presence.
8. **[discipline] One-haptic-tick / no person-level scoreboards** are easy to
   regress into gamification — guard in review.

---

### Provenance of this design
4-agent brainstorm (content primitive · sync/conflict · API/server · UX/
collaboration), each grounded in the repo + cited, then 2 adversarial review
rounds (correctness, then simplification). Convergence was strong and independent
on the load-bearing calls (stable item id, whole-block PUT, server-opaque
client-side merge, new-primitive-beside-intents, toggle-only first slice). The
chief review-driven simplifications: **wall-clock+actor LWW over HLC** at M0; the
**visibility-on-write security gap** elevated to a must-fix; **412** standardized
for precondition failures; the **op-log + granular transport** explicitly reserved
(not built) behind a Revisit Trigger.
