# ADR 0035: Block-Payload Schema Reconciliation — One Source of Truth

## Status

**Accepted** 2026-06-26 (operator: "I accept 0035, 0033").

**Implementation refinement → Option C (2026-06-26, operator-confirmed).** Building
it surfaced three facts the Proposed ADR did not account for: (a) there is **no
Kotlin codegen** yet (`codegen.mjs` emits TS only), so `Content.kt` can't be
regenerated; (b) `content.schema.json` is declared **FROZEN for M0**; (c) the
schema's per-type block payloads are **clean + well-designed**, and the renderer
genuinely uses fields the schema lacks (`lat`/`lng` map, `total`/`spent` budget bar)
— so the original Option A (edit schema → client) would break the freeze and degrade
the design for **zero current benefit** (no structured-payload content exists yet).
Adopt **Option C** instead: keep the frozen schema canonical; **add block-payload
validation** (CLI + server) that is *tolerant of both* the schema and current
client-render field names; make the renderer read the canonical names alongside its
current ones; document the schema as canonical. The single-representation unification
(rename client → schema, decide the location/budget representations, build Kotlin
codegen) is deferred to **M1** — tracked in `OQ-block-payload-schema`. Delivered in
steps: **CLI `validateHubTree` block-payload pre-check (this PR)** → server validation
→ renderer tolerance → authoring-doc update.

Originally **Proposed** 2026-06-26 (agent-drafted from the authoring-doc audit behind
PR #134 / `OQ-block-payload-schema`; **operator-gated** — it changes the content
schema, a source-of-truth spec). Resolves `OQ-block-payload-schema`.

## Context

Hub **block** `payload` has **two disagreeing definitions**:

| | source | fields (representative) |
|---|---|---|
| client render model | `apps/client/.../Model.kt` `BlockPayload` | `items[{text,done,due,assignee}]`, `url`/`label`/`domain`/`docRef`, `name`/`role`/`phone`/`email`, `address`/`lat`/`lng`, `date`, `total`/`spent` |
| generated schema | `packages/schema/kotlin-gen/Content.kt` ← `content.schema.json` | `ref`/`source`/`kind`/`mapUrl`, `Item{amount,label,paid,…}` |

Key facts established by the audit:

1. The server stores block `payload` as **JSONB verbatim** (`content/hubs.ts` — `J(b.payload)`)
   and returns it via `SELECT *`. **Pure passthrough** — the server never reshapes or
   validates the block payload against the schema.
2. The CLI's `validateHubTree` checks only block **`type`** (one of 9), **not** payload
   fields. So the generated `BlockPayload` is **not actually consumed** by the CLI or
   server today — it is documentation that has drifted.
3. The **client renderer** (`HubBlockCard` + the row composables) reads the **Model.kt**
   shape. That is the *de facto* contract: a structured block authored per the *schema*
   (`document.ref`, `location.mapUrl`, a `budget` via item `amount`) would store fine but
   **silently not render**.
4. **Not yet hit** — all live content is `body_md`-only (which is unaffected; it renders
   richly per the markdown work in #114–#132). **No structured-payload content exists**, so
   there is **no data migration** either way.
5. Contrast: the **card** payloads have full client/schema parity (audited + locked,
   PR #135). Cards stayed in sync because the CLI validates them against the schema;
   blocks drifted because nothing did.

## Decision

**Adopt Option A — make `content.schema.json`'s block payload match the client render
model, and regenerate `Content.kt`.** The generated schema becomes accurate to what
actually renders; the client and renderer are unchanged; no content migrates.

Concretely (operator-accepted, then agent-buildable):
- Edit `content.schema.json`'s block-payload definition to the Model.kt field set
  (`items[{text,done,due,assignee}]`, `url`/`label`/`domain`/`docRef`,
  `name`/`role`/`phone`/`email`, `address`/`lat`/`lng`, `date`, `total`/`spent`).
- Regenerate `packages/schema/kotlin-gen/Content.kt`.
- Add block-payload structural validation to `validateHubTree` (CLI) **and** the server,
  so blocks get the same schema-enforcement cards have — closing the "nothing guards it"
  root cause (mirrors PR #135's card parity lock, extended to blocks).
- The `apps/cli/templates/README.md` payload table (#134) and `OQ-block-payload-schema`
  become resolved/authoritative.

## Options considered

- **A — schema → client model (recommended).** Spec follows the working render contract.
  *Pros:* zero client/renderer churn; zero content migration; the field names authors were
  already told to use (#134) become canonical; aligns the unused-and-drifted generated type
  to reality. *Cons:* edits a source-of-truth spec to match hand-written code (governance
  normally flows spec→code) — acceptable here because the spec's block payload is unused and
  the client model encodes the actual UI need (lat/lng for a map, total/spent for a budget
  bar).
- **B — client model → schema.** Change Model.kt + the renderer row composables to read
  `ref`/`mapUrl`/item-`amount`. *Pros:* preserves spec-as-source-of-truth direction.
  *Cons:* churns working render code + the #134 doc; the schema's shape (`mapUrl` vs
  `lat`/`lng`) may not match what the UI renders (a map needs coordinates); higher risk for
  no functional gain.
- **C — compatibility shim (read both).** Renderer reads `docRef ?? ref`, derives location
  from `lat`/`lng` *or* `mapUrl`, etc. *Pros:* tolerant of either author style.
  *Cons:* entrenches the ambiguity instead of resolving it; two ways to author the same
  block; more code paths to test. Rejected as a permanent answer (fine only as a transition
  if structured content already existed — it doesn't).

## Consequences

- **Positive:** one source of truth for block payloads; structured-payload authoring becomes
  reliable (renders as authored); blocks gain schema validation (CLI + server) like cards;
  the drifted generated type stops being a trap.
- **Cost:** a schema edit + codegen run + adding block-payload validation in two places
  (CLI `validateHubTree` + server). Bounded; no migration.
- **Risk:** low — no live structured content; `body_md` authoring (the only style in use) is
  untouched; card validation is unaffected (separate payload).
- **Reversibility:** the schema/codegen change is forward-only but trivially re-editable; no
  data is rewritten.

This composes ADR 0006 (hub content model) and ADR 0022 (typed content); it does not change
auth, transport, pricing, or scope. **Until accepted**, authors use the client-model field
names documented in `apps/cli/templates/README.md`, or `body_md` (always safe).
