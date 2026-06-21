# CL-3 — CLI + skill: typed authoring (design)

**Epic:** `planning/content-detail-epic.md` (CL-3) · **ADR:** 0022 (typed content)
/ Guardrail 3 (email = operator's OWN mail, no server Gmail read) · **Depends:**
CL-1 (generated `com.familyai.schema` Kotlin types), CL-2 (server typed validation
+ the type↔payload-key rule) — both on `cl-next`. **Operator-authorized
2026-06-20** (deferred per INB-18 → unblocked by operator choice).

## Goal

Let the operator + Claude Code author all 6 typed content cards from the CLI with
**local validation** (fail fast with field errors, not a server 422) and starter
**templates**. This is the M0 authoring half of the content-API wedge.

## Scope

1. **Consume the generated types.** The CLI is a standalone Gradle project that
   doesn't yet see `packages/schema/kotlin-gen`. Add it as a `srcDir` so the CLI
   compiles `com.familyai.schema.{BriefingCard, BriefingCardPayload, …}` — the
   single source of truth (no hand-dup, no drift).
2. **Local validation** (`Validate.kt`, pure): `validateCard(assertType, json)` →
   list of human errors (empty = ok). A **structural** pre-check (NOT a full
   server replica): strictly decodes the JSON into the generated `BriefingCard`
   (catches wrong types / unknown payload fields), then applies the **CL-2
   cross-check** (type present iff payload present; payload's single variant key ==
   `type`); if `--type` was passed, assert it equals the card's `type`. **Does not**
   re-implement the server's format refinements (`url()`, ISO datetimes, length/int
   caps) — the server (CL-2) stays the authority for those. Two codegen
   asymmetries (validator stricter than server): `kind` and `provenance.at` are
   required locally though the server defaults/relaxes them → author from
   `template`. (Documented in the README, not silently divergent.)
3. **`push … --type <t>`** (opt-in, back-compat): when `--type` is present, run
   `validateCard` BEFORE the PUT; on failure print the field errors and exit
   non-zero **without hitting the server**. Without `--type`, push is unchanged.
4. **`template <type>`**: print a starter JSON for the type to stdout (from
   `apps/cli/templates/<type>.json`), so authoring starts from a valid skeleton.
5. **Templates**: `apps/cli/templates/{file,link,invite,contact,geo,email}.json`
   — minimal valid cards (each validates clean).
6. **Authoring doc**: `apps/cli/templates/README.md` — the typed-authoring loop
   for the operator + Claude (the "skill/doc" the epic asks for): the 6 types,
   the Guardrail-3 email constraint, `template` → edit → `push --type`.

## Security / privacy

- **Guardrail 3** restated in the doc + the email template: `email.bodyExcerpt`
  is authored over the operator's OWN mail (CLI / Claude reasoning), **never a
  server-side Gmail restricted-scope read**. CL-3 adds no mail integration.
- Validation is **local only** (no network); it mirrors the server gate so the
  server stays the real authority (CL-2). No secrets in templates.
- No change to auth/credentials/transport (CL-3 is authoring-side only).

## Files

- `apps/cli/build.gradle.kts` — `sourceSets["main"].kotlin.srcDir("../../packages/
  schema/kotlin-gen")`.
- `apps/cli/src/main/kotlin/Validate.kt` (new, pure).
- `apps/cli/src/main/kotlin/Main.kt` — `--type` on push; `template` command; usage.
- `apps/cli/templates/*.json` (6) + `README.md`.
- `apps/cli/src/test/kotlin/com/familyai/cli/ValidateTest.kt` (new).

## Test plan (`apps/cli` gradle test)

1. `validateCard` — a valid card per type passes (empty errors); a payload-key↔type
   mismatch is rejected with a clear message; an unknown payload field is rejected
   (strict decode); `--type` mismatch is rejected; a legacy kind-only card (no
   type/payload) passes.
2. Every shipped `templates/<type>.json` parses + `validateCard("<type>", it)` is
   clean (golden — guarantees the starters stay valid as the schema evolves).
3. Existing `CredentialsTest` stays green; `apps/cli` builds.

## DoD

All 6 types authorable from the CLI with local validation that catches bad
payloads before the server; `template <type>` emits a valid starter; templates +
doc committed; `apps/cli` test green. (Server remains the authority — CL-2.)

## Risks

- srcDir-ing the generated file pulls ALL generated types into the CLI (Block/Hub/
  …) — harmless (compile-only, unused). Keeps one source of truth.
- kotlinx-serialization version skew (CLI 1.8.1 vs codegen) — generated code is
  plain `@Serializable`; no version-specific API. Verify by compiling.
- Strict decode could reject loose-but-valid authoring JSON → that's the point
  (catch typos); `--type` is opt-in so existing untyped push is unaffected.
