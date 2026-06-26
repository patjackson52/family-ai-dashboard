# Dayfold Curator Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a `dayfold-curator` Claude Code skill that authors dayfold Hubs + BriefingCards from connected-MCP and pasted context, driving the existing `dayfold` CLI, propose-confirm before every push.

**Architecture:** A skill is a directory of Markdown instruction files under `.claude/skills/dayfold-curator/`. `SKILL.md` holds the triggering frontmatter and the 3-phase workflow (Onboard → Author → Enrich); `references/` holds the condensed content-model, CLI cheatsheet, and guardrails kept close to use; `install.sh` symlinks the skill into `~/.claude/skills/`. No code is compiled — verification is YAML validity, fidelity of documented CLI commands to the real CLI, and a manual dry-run.

**Tech Stack:** Markdown + YAML frontmatter (skill format), POSIX `sh` (install), the existing Kotlin `dayfold` CLI (unchanged), `python3` (YAML/JSON validation in verification steps).

## Global Constraints

- Skill source of truth lives in the dayfold repo at `.claude/skills/dayfold-curator/`. Copied verbatim from the design.
- Authoring is **propose → confirm → push**. The skill NEVER runs `dayfold push` before explicit operator approval.
- Email cards are authored over the **operator's own mail only** (Guardrail 3). No implication of a server-side restricted-scope Gmail read.
- `privacy.storage` chips must name a boundary the schema enforces: `on_device` for a cached copy, `location_local` only for live position (ADR 0014).
- Adults-only as account holders (Guardrail 1 / COPPA); children appear only as subjects in a parent's own data.
- Every authored card/block carries `provenance.source = "claude"` and an `at` timestamp; author from `dayfold template`, not bare stubs.
- The server is the authority; local `dayfold push --type` validation is a courtesy pre-check only.
- The skill assumes `dayfold` is on PATH and logged in; it verifies with `dayfold whoami` before any authoring.
- Hub `type` ∈ `vacation·starting-college·move·party-event·new-baby·medical·school-year`. Card `type` ∈ `file·link·invite·contact·geo·email`. Block `type` ∈ `text·markdown·link·checklist·document·milestone·contact·location·budget`.
- CLI subcommands that exist (verified against `apps/cli/src/main/kotlin/Main.kt`): `login`, `logout`, `whoami`, `pull`, `push`, `template`, `version`, `help`. The skill must reference ONLY these.

---

## File Structure

- Create `.claude/skills/dayfold-curator/SKILL.md` — frontmatter + 3-phase workflow.
- Create `.claude/skills/dayfold-curator/references/content-model.md` — card/hub/block field reference.
- Create `.claude/skills/dayfold-curator/references/cli.md` — `dayfold` command cheatsheet.
- Create `.claude/skills/dayfold-curator/references/guardrails.md` — privacy/consent/provenance rules.
- Create `.claude/skills/dayfold-curator/install.sh` — symlink installer + docs pointer.
- Modify `README.md` (repo root) — add a short "Curator skill" pointer + install line.

Each task below produces one or two of these files and ends with an independently checkable deliverable.

---

### Task 1: Scaffold the skill directory and SKILL.md frontmatter + workflow

**Files:**
- Create: `.claude/skills/dayfold-curator/SKILL.md`

**Interfaces:**
- Produces: the skill entrypoint. Its `description` frontmatter is what triggers the skill. Later reference files are linked from here by relative path (`references/content-model.md`, `references/cli.md`, `references/guardrails.md`).

- [ ] **Step 1: Write SKILL.md**

Create `.claude/skills/dayfold-curator/SKILL.md` with exactly this content:

````markdown
---
name: dayfold-curator
description: Use when setting up dayfold for a person/family, deciding what hubs to create, authoring dayfold content from email/calendar/files/notes, or asking "what should be on my dashboard" / "enrich my hubs". Analyzes context, runs an onboarding questionnaire, then authors Hubs + BriefingCards through the dayfold CLI — propose-confirm before every push.
---

# Dayfold Curator

Turn a person's scattered context (email, calendar, files, notes/second-brain)
into **dayfold content** — Hubs and BriefingCards — authored through the
`dayfold` CLI. The dashboard renders intelligence; this skill produces it. It is
not a chatbot.

## The one test for every piece of content

> Imagine yourself in the user's position going about their day. What content,
> surfaced in dayfold, would stop them digging through multiple apps or searching
> their notes/second brain? **Link directly** to the app/content when possible;
> otherwise **embed** a snippet or the info itself.

## Before anything: read the references

- `references/cli.md` — the exact `dayfold` commands (the ONLY ones that exist).
- `references/content-model.md` — the card/hub/block shapes you author.
- `references/guardrails.md` — privacy/consent/provenance rules. **Binding.**

## Prereq gate (always first)

Run `dayfold whoami`. If it prints `(legacy)` with empty family, or errors, STOP
and tell the operator to run `dayfold login` first. Do not author without a
resolved family.

## Phase A — Onboard (first run per family)

1. **Ingest context.** Use what the operator pastes/points at, plus actively read
   their connected MCPs when available: Gmail (their OWN mail — see guardrails),
   Calendar (events, recurring commitments), Drive (documents/links they keep).
   If an MCP isn't connected, say so and continue with what you have.
2. **Deep-analyze → cluster** signals into candidate Hubs from the bounded catalog
   (`vacation, starting-college, move, party-event, new-baby, medical, school-year`).
   For each candidate name: the life-thread, the signals feeding it, why it matters
   now.
3. **Onboarding questionnaire — one question at a time.** Confirm: adult family
   members (account holders are adults only), which threads matter, hub priority
   order, privacy comfort (what may be read, what stays on-device).
4. **Output an agreed hub map.** Do NOT push in this phase.

## Phase B — Author (propose → confirm → push)

For each agreed hub:
- Start from `dayfold template hub` (and `section`, `block`), fill real fields,
  **show the operator the JSON**, push on approval:
  `dayfold push <id> hub.json --hub` (then `--section`, `--block` for children).

For each signal worth surfacing **now**:
- Author a BriefingCard of the right `type` from `dayfold template <type>`, set
  `target` to deep-link its hub, add `triggers` for time/place relevance, set an
  honest `privacy.storage` chip, `provenance.source = "claude"`. Validate + show
  JSON, push on approval: `dayfold push <cardId> card.json --type <type>`.

Batch a hub's whole tree (or a set of cards) into one approval, but NEVER push an
un-approved batch. If the server returns non-200, surface the body, fix, re-push.

## Phase C — Enrich (on-demand, over existing state)

1. `dayfold pull` (and `dayfold pull --hub <id>`) to read current hubs + cards.
2. **Empathy pass.** Walk the user's day against what exists. For each moment ask:
   *would they have to open another app or search notes to handle this?* Each "yes"
   is a gap.
3. For each gap, pick the surfacing form, in priority order:
   - **Link directly** — deep link / `location.mapUrl` / source email thread URL /
     document ref. Always preferred.
   - **Embed** — `body_md` snippet, `contact`/`checklist`/`milestone`/`budget`
     payload — when no direct link exists or the info is small and the point is to
     skip the click.
4. Propose new cards/blocks → confirm → push (same flow as Phase B). Only propose
   net-new content; do not duplicate what `pull` already returned.

## Always

- Propose-confirm before EVERY push.
- Honest privacy chips; own-mail-only email; adults-only accounts; `provenance` on
  everything. See `references/guardrails.md`.
````

- [ ] **Step 2: Verify the frontmatter is valid YAML with the required keys**

Run:
```bash
cd /Users/patrick/workspace/dayfold
python3 - <<'PY'
import sys, re
p = ".claude/skills/dayfold-curator/SKILL.md"
t = open(p).read()
m = re.match(r"^---\n(.*?)\n---\n", t, re.S)
assert m, "no frontmatter block"
import yaml  # pyyaml; if missing, fall back below
fm = yaml.safe_load(m.group(1)) if 'yaml' in sys.modules else None
PY
```
If `pyyaml` is unavailable, instead run:
```bash
cd /Users/patrick/workspace/dayfold
awk '/^---$/{c++; next} c==1{print}' .claude/skills/dayfold-curator/SKILL.md | grep -E '^(name|description):'
```
Expected: both a `name:` and a `description:` line print, `name: dayfold-curator`.

- [ ] **Step 3: Verify SKILL.md references only real CLI subcommands**

Run:
```bash
cd /Users/patrick/workspace/dayfold
grep -oE 'dayfold (login|logout|whoami|pull|push|template|version|help)' .claude/skills/dayfold-curator/SKILL.md | sort -u
```
Expected: only lines from the allowed set (`whoami`, `pull`, `push`, `template`, `login`). No other `dayfold <word>` invented commands.

- [ ] **Step 4: Commit**

```bash
cd /Users/patrick/workspace/dayfold
git add .claude/skills/dayfold-curator/SKILL.md
git commit -m "feat(skill): scaffold dayfold-curator SKILL.md (3-phase workflow)"
```

---

### Task 2: Write references/cli.md (the command cheatsheet)

**Files:**
- Create: `.claude/skills/dayfold-curator/references/cli.md`

**Interfaces:**
- Consumes: the real CLI surface in `apps/cli/src/main/kotlin/Main.kt` (commands `login/logout/whoami/pull/push/template/version/help`).
- Produces: the authoritative command reference SKILL.md points to. Later tasks rely on the exact `push`/`template` flag spellings documented here.

- [ ] **Step 1: Write cli.md**

Create `.claude/skills/dayfold-curator/references/cli.md` with exactly this content:

````markdown
# dayfold CLI — command cheatsheet

The skill drives ONLY these commands. Assume `dayfold` is on PATH and logged in.

## Prereq

```
dayfold whoami      # family=<id> api=<url> (device|legacy); prints scope=...
```
If it shows `(legacy)` with empty family or errors → operator must `dayfold login`.

## Read current state (Phase C, and to get ids before push)

```
dayfold pull                 # {"cards":[...],"hubs":[...]}
dayfold pull --hub <hubId>   # that hub's full section/block tree
```

## Get a starter body

```
dayfold template <type>      # prints starter JSON to stdout
```
`<type>` ∈ card types `file link invite contact geo email`
        + hub-tree bodies `hub section block`.
Redirect to a file to edit: `dayfold template invite > card.json`.

## Push (PUT) — card by default, hub tree with a flag

```
dayfold push <cardId> card.json --type <type>     # briefing card, local-validated
dayfold push <hubId> hub.json --hub               # hub
dayfold push <sectionId> section.json --section   # section (body carries hubId)
dayfold push <blockId> block.json --block         # block (body carries sectionId)
```
- `--type` runs local structural validation against the generated schema BEFORE
  the network — catches wrong payload variant / unknown field / type mismatch.
  Without `--type`, a card is sent unchanged (no local validation).
- The path `<id>` overwrites the body `id` server-side — the body `id` can stay
  `REPLACE_WITH_CARD_ID`.
- Output: `push <resource>/<id> -> <httpStatus>`. Non-200 prints the server body
  to stderr and exits 1 — the server is the authority; fix and re-push.

## Notes

- Generate stable ulids for new ids client-side (26-char Crockford base32). Reuse
  an existing id (from `dayfold pull`) to update rather than create.
- There is NO `dayfold delete` / `create` / `list`. Update-by-push only.
````

- [ ] **Step 2: Verify every command in cli.md exists in the real CLI**

Run:
```bash
cd /Users/patrick/workspace/dayfold
# extract `dayfold <word>` tokens from the doc, compare to dispatch in Main.kt
doc=$(grep -oE 'dayfold (login|logout|whoami|pull|push|template|version|help|[a-z]+)' .claude/skills/dayfold-curator/references/cli.md | awk '{print $2}' | sort -u)
real=$(grep -oE '"(login|logout|whoami|pull|push|template|version|help)"' apps/cli/src/main/kotlin/Main.kt | tr -d '"' | sort -u)
echo "DOC:"; echo "$doc"; echo "REAL:"; echo "$real"
comm -23 <(echo "$doc") <(echo "$real")
```
Expected: the final `comm -23` (commands in the doc NOT in the CLI) prints nothing.

- [ ] **Step 3: Verify the documented push flags match Main.kt's pushResource**

Run:
```bash
cd /Users/patrick/workspace/dayfold
grep -E '"--(hub|section|block)"' apps/cli/src/main/kotlin/Main.kt
grep -E '\-\-(hub|section|block|type)' .claude/skills/dayfold-curator/references/cli.md
```
Expected: `--hub`, `--section`, `--block` appear in both; `--type` appears in the doc (it maps to card validation in Main.kt).

- [ ] **Step 4: Commit**

```bash
cd /Users/patrick/workspace/dayfold
git add .claude/skills/dayfold-curator/references/cli.md
git commit -m "docs(skill): add dayfold CLI cheatsheet reference"
```

---

### Task 3: Write references/content-model.md (card/hub/block field reference)

**Files:**
- Create: `.claude/skills/dayfold-curator/references/content-model.md`

**Interfaces:**
- Consumes: `specs/domain-model/schemas/content.schema.json` (source of truth) and the starter templates in `apps/cli/src/main/resources/templates/`.
- Produces: the condensed field reference the author phases use to fill real fields. Must stay consistent with the schema's enums (already pinned in Global Constraints).

- [ ] **Step 1: Write content-model.md**

Create `.claude/skills/dayfold-curator/references/content-model.md` with exactly this content:

````markdown
# dayfold content model (authoring reference)

Source of truth: `specs/domain-model/schemas/content.schema.json`. This is a
condensed authoring view. When in doubt, `dayfold template <type>` prints a valid
starter; edit that rather than hand-writing.

## BriefingCard — the "Now" feed surface

Required: `id`, `kind`, `title`, `provenance`.
- `kind` ∈ `action | info | weather | countdown` (default `info`).
- `type` ∈ `file | link | invite | contact | geo | email` — drives the card
  layout. Payload is `payload.<type>` (single variant key == `type`).
- `body_md` — limited inline markdown (snippet/embed).
- `target` — deep link `{hubId, sectionId?, blockId?}` into a hub.
- `hubRef` — parent hub id (the "PART OF THIS HUB" pane).
- `triggers[]` — relevance: `{ "when": { "at": <ts>, "alert_offset": "-PT1H" } }`
  or `{ "geo": { "lat","lng","radius_m","label" } }` (geo matched on-device).
- `related[]` — cross-links to other cards in THIS family.
- `not_before` / `expires_at` — show/hide window (ISO-8601).
- `privacy.storage` — honest chip (see guardrails).
- `provenance` — `{ "source": "claude", "at": <ISO-8601> }`.

Per-type payload keys (the common ones):
- `link`: `{ url, label?, source? }`
- `invite`: `{ eventName, host?, startAt, place?, rsvpBy?, rsvpState?, guestCount?, notes? }`
- `contact`: `{ name, role?, phone?, email? }`
- `geo`: `{ label, address?, mapUrl?, lat?, lng? }`
- `email`: `{ subject?, from?, bodyExcerpt?, threadUrl? }` (own mail only)
- `file`: `{ ref, label?, kind? }` (url | fileRef)

## Hub → Section → Block — project/event containers

**Hub** — required `id`, `type`, `title`.
- `type` ∈ `vacation | starting-college | move | party-event | new-baby | medical | school-year`.
- `status` ∈ `planning | active | archived` (default `active`).
- `start_at` / `end_at` / `countdown_to` (ISO-8601). `sections[]`.

**Section** — required `id`. `title`, `ord`, `blocks[]`. Body carries `hubId`.

**Block** — required `id`, `type`, `provenance`. Body carries `sectionId`.
- `type` ∈ `text | markdown | link | checklist | document | milestone | contact | location | budget`.
- `text`/`markdown` use `body_md` (no payload). Others use `payload`:
  - `link`: `{ url, label?, source? }`
  - `checklist`: `{ items: [{ text, done?, due?, assignee? }] }`
  - `document`: `{ ref, label?, kind? }`
  - `milestone`: `{ date, label }`
  - `contact`: `{ name, role?, phone?, email? }`
  - `location`: `{ label, address?, mapUrl? }`
  - `budget`: `{ items: [{ label, amount, paid? }] }`

## Choosing card vs hub content

- **BriefingCard** = surfaces NOW in the feed (time/place-relevant, short-lived).
- **Hub block** = the durable reference body the card deep-links into.
- A good pattern: author the hub (the dossier), then a few cards that point into it
  at the right moment ("RSVP by Thursday" card → invite section of the party hub).

## ids

26-char Crockford base32 ULIDs (`^[0-9A-HJKMNP-TV-Z]{26}$`). New content → new id;
update → reuse the id from `dayfold pull`.
````

- [ ] **Step 2: Verify the documented enums match the schema**

Run:
```bash
cd /Users/patrick/workspace/dayfold
echo "--- hub types in schema ---"
grep -oE 'vacation\|starting-college\|move\|party-event\|new-baby\|medical\|school-year' specs/domain-model/schemas/content.schema.json
echo "--- card types in schema ---"
grep -oE '"file", "link", "invite", "contact", "geo", "email"' specs/domain-model/schemas/content.schema.json
echo "--- block types in schema ---"
grep -oE '"text", "markdown", "link", "checklist", "document", "milestone", "contact", "location", "budget"' specs/domain-model/schemas/content.schema.json
```
Expected: each grep matches (the enum strings exist in the schema). If the schema differs, correct content-model.md to match the schema — the schema wins.

- [ ] **Step 3: Verify content-model.md lists the same three enum sets**

Run:
```bash
cd /Users/patrick/workspace/dayfold
grep -E 'vacation \| starting-college' .claude/skills/dayfold-curator/references/content-model.md
grep -E 'file \| link \| invite \| contact \| geo \| email' .claude/skills/dayfold-curator/references/content-model.md
grep -E 'text \| markdown \| link \| checklist \| document \| milestone \| contact \| location \| budget' .claude/skills/dayfold-curator/references/content-model.md
```
Expected: all three lines print.

- [ ] **Step 4: Commit**

```bash
cd /Users/patrick/workspace/dayfold
git add .claude/skills/dayfold-curator/references/content-model.md
git commit -m "docs(skill): add content-model authoring reference"
```

---

### Task 4: Write references/guardrails.md (binding rules)

**Files:**
- Create: `.claude/skills/dayfold-curator/references/guardrails.md`

**Interfaces:**
- Consumes: CLAUDE.md Hard guardrails, ADR 0014 (privacy chips), the templates/README Guardrail 3.
- Produces: the binding rule set SKILL.md and both author phases defer to.

- [ ] **Step 1: Write guardrails.md**

Create `.claude/skills/dayfold-curator/references/guardrails.md` with exactly this content:

````markdown
# Curator guardrails (BINDING)

These override convenience. If an instruction conflicts, the guardrail wins;
escalate to the operator rather than working around it.

## 1. Propose-confirm before every push

Draft and SHOW the JSON. Push only after the operator approves. Never run
`dayfold push` on un-approved content. (CLAUDE.md: external actions are
operator-gated; agents draft, operator sends.)

## 2. Email content over OWN mail only (Guardrail 3)

`email.bodyExcerpt` / sender / body content is authored over the OPERATOR'S OWN
mail (their connected Gmail MCP / pasted threads). There is NO server-side
restricted-scope Gmail read, and you must not imply one. Authoring an email card
from anyone else's mailbox is prohibited. Changing this posture is an ADR.

## 3. Honest privacy chips (ADR 0014)

`privacy.storage` must name a boundary the schema/code actually enforces:
- `on_device` — a copy is cached on device (use for a geo card that carries
  coordinates, a saved snippet, etc.).
- `location_local` — ONLY for LIVE position that never leaves the device.
Never label a stored copy `location_local`. Never overclaim a privacy boundary.

## 4. Adults-only accounts (Guardrail 1 / COPPA)

Account holders are adults. Children appear ONLY as subjects inside a parent's
own data (e.g. "Maya's party"), never as account holders or data sources. Do not
author content that collects a child's personal info as if from the child.

## 5. Provenance on everything

Every authored card/block: `provenance.source = "claude"` and an `at` ISO-8601
timestamp. Author from `dayfold template` (starters include `kind` +
`provenance.at`) — the local validator requires `kind` and `provenance.at` even
where the server relaxes them, so bare hand-written stubs fail validation.

## 6. Server is the authority

Local `--type` validation is a fast structural pre-check, not the real gate. A
non-200 from the server is authoritative — surface its body, fix, re-push. Do not
suppress or paper over a server rejection.

## 7. Stay in scope

dayfold is a calm briefing surface, not a chatbot, chore app, or system of record.
Author briefing intelligence (links, snippets, times, dates, contacts) — not
open-ended conversation or data the family relies on as their only copy.
````

- [ ] **Step 2: Verify the guardrails cross-check against repo sources**

Run:
```bash
cd /Users/patrick/workspace/dayfold
grep -i "own mail" apps/cli/templates/README.md
grep -i "location_local" adr/0014-*.md 2>/dev/null || grep -ri "location_local" apps/cli/templates/README.md
grep -i "adults-only\|under-13\|COPPA" CLAUDE.md | head -2
```
Expected: each grep returns at least one line — confirming the guardrails restate real repo rules (own-mail, on_device vs location_local, adults-only).

- [ ] **Step 3: Commit**

```bash
cd /Users/patrick/workspace/dayfold
git add .claude/skills/dayfold-curator/references/guardrails.md
git commit -m "docs(skill): add binding guardrails reference"
```

---

### Task 5: Write install.sh and document install in README

**Files:**
- Create: `.claude/skills/dayfold-curator/install.sh`
- Modify: `README.md` (repo root) — add a "Curator skill" pointer.

**Interfaces:**
- Consumes: the completed skill directory from Tasks 1–4.
- Produces: a documented global install path (`~/.claude/skills/dayfold-curator` symlink) and a README pointer.

- [ ] **Step 1: Write install.sh**

Create `.claude/skills/dayfold-curator/install.sh` with exactly this content:

```sh
#!/usr/bin/env sh
# Install the dayfold-curator skill globally by symlinking it into
# ~/.claude/skills/ (available in every project on this machine).
#
# Per-project install instead: copy this directory into <repo>/.claude/skills/.
set -eu

SRC="$(cd "$(dirname "$0")" && pwd)"
DEST_DIR="${HOME}/.claude/skills"
DEST="${DEST_DIR}/dayfold-curator"

mkdir -p "$DEST_DIR"

if [ -e "$DEST" ] || [ -L "$DEST" ]; then
  printf 'refusing to overwrite existing %s\n' "$DEST" >&2
  printf 'remove it first: rm -rf %s\n' "$DEST" >&2
  exit 1
fi

ln -s "$SRC" "$DEST"
printf 'linked %s -> %s\n' "$DEST" "$SRC"
printf 'restart Claude Code to pick up the skill.\n'
```

- [ ] **Step 2: Make it executable and verify it runs (dry, without clobbering)**

Run:
```bash
cd /Users/patrick/workspace/dayfold
chmod +x .claude/skills/dayfold-curator/install.sh
sh -n .claude/skills/dayfold-curator/install.sh && echo "syntax OK"
```
Expected: `syntax OK` (the `-n` flag checks shell syntax without executing).

- [ ] **Step 3: Functionally verify the symlink logic in a temp HOME**

Run:
```bash
cd /Users/patrick/workspace/dayfold
TMPHOME="$(mktemp -d)"
HOME="$TMPHOME" sh .claude/skills/dayfold-curator/install.sh
ls -l "$TMPHOME/.claude/skills/dayfold-curator"
# second run must refuse
HOME="$TMPHOME" sh .claude/skills/dayfold-curator/install.sh; echo "exit=$?"
rm -rf "$TMPHOME"
```
Expected: first run prints `linked ...`; `ls -l` shows a symlink to the repo skill dir; second run prints `refusing to overwrite ...` and `exit=1`.

- [ ] **Step 4: Add the README pointer**

Add this section to the repo root `README.md` (append after the existing content):

```markdown
## Curator skill (Claude Code)

`.claude/skills/dayfold-curator/` is the authoring wedge — a Claude Code skill
that analyzes your context, runs an onboarding questionnaire, and authors dayfold
Hubs + BriefingCards through the `dayfold` CLI (propose-confirm before every push).

Install globally (all projects on this machine):

```
sh .claude/skills/dayfold-curator/install.sh
```

Or per-project: copy `.claude/skills/dayfold-curator/` into another repo's
`.claude/skills/`. Requires `dayfold` on PATH and `dayfold login` done first.
```

- [ ] **Step 5: Commit**

```bash
cd /Users/patrick/workspace/dayfold
git add .claude/skills/dayfold-curator/install.sh README.md
git commit -m "feat(skill): add installer + README pointer for dayfold-curator"
```

---

### Task 6: End-to-end dry-run verification

**Files:**
- No new files. Verifies the assembled skill.

**Interfaces:**
- Consumes: the full skill directory from Tasks 1–5.
- Produces: confidence that the skill is internally consistent and the documented authoring flow validates against the real CLI (without pushing).

- [ ] **Step 1: Verify the skill directory is complete**

Run:
```bash
cd /Users/patrick/workspace/dayfold
ls -1 .claude/skills/dayfold-curator .claude/skills/dayfold-curator/references
```
Expected: `SKILL.md`, `install.sh`, `references/` listed; references contains `cli.md`, `content-model.md`, `guardrails.md`.

- [ ] **Step 2: Verify SKILL.md links resolve to real reference files**

Run:
```bash
cd /Users/patrick/workspace/dayfold
for f in $(grep -oE 'references/[a-z-]+\.md' .claude/skills/dayfold-curator/SKILL.md | sort -u); do
  test -f ".claude/skills/dayfold-curator/$f" && echo "OK $f" || echo "MISSING $f"
done
```
Expected: every referenced file prints `OK` (no `MISSING`).

- [ ] **Step 3: Validate a sample authored card against the real CLI validator (no network)**

Build the CLI once, render a template, and run local `--type` validation. This
exercises the exact `template` + `push --type` flow the skill documents. Use a
bogus API so a *validation* failure surfaces before any network call; a clean
validation proceeds to the network and fails at connect — that's the success
signal (validation passed).

Run:
```bash
cd /Users/patrick/workspace/dayfold/apps/cli
./gradlew -q installDist 2>/dev/null || ./gradlew -q assemble
DAYFOLD="$(find . -path '*/install/*/bin/cli' -o -path '*/install/*/bin/dayfold' 2>/dev/null | head -1)"
"$DAYFOLD" template invite > /tmp/curator-card.json
# inject a bad field to prove the validator catches it:
python3 - <<'PY'
import json
c=json.load(open('/tmp/curator-card.json'))
c['payload']['invite']['NOT_A_FIELD']=1
json.dump(c, open('/tmp/curator-bad.json','w'))
PY
DAYFOLD_API="http://127.0.0.1:1" FAMILY_ID="x" HOUSEHOLD_SECRET="x" \
  "$DAYFOLD" push 01J0000000000000000000000X /tmp/curator-bad.json --type invite; echo "exit=$?"
```
Expected: prints `validation failed:` with a field error mentioning the unknown
field and `exit=1` — the local `--type` pre-check rejects the bad card BEFORE the
network, exactly as the skill relies on.

- [ ] **Step 4: Validate that a clean template passes local validation**

Run:
```bash
cd /Users/patrick/workspace/dayfold/apps/cli
DAYFOLD="$(find . -path '*/install/*/bin/cli' -o -path '*/install/*/bin/dayfold' 2>/dev/null | head -1)"
DAYFOLD_API="http://127.0.0.1:1" FAMILY_ID="x" HOUSEHOLD_SECRET="x" \
  "$DAYFOLD" push 01J0000000000000000000000X /tmp/curator-card.json --type invite 2>&1 | head -3; echo "exit=$?"
```
Expected: NO `validation failed:` line — instead a connection error to `127.0.0.1:1`
(validation passed, network refused). That proves the documented authoring flow
produces a card the local validator accepts.

- [ ] **Step 5: Final commit (verification notes, if any cleanup needed)**

If steps 1–4 required any fix to the skill files, the fix was committed in its
task above. If everything passed clean, there is nothing to commit here — record
the dry-run as done.

```bash
cd /Users/patrick/workspace/dayfold
git log --oneline -6
```
Expected: the six skill commits (Tasks 1–5) are present.

---

## Self-Review

**Spec coverage:**
- Phase A Onboard → Task 1 SKILL.md Phase A. ✓
- Phase B Author (propose-confirm-push) → Task 1 Phase B + Task 2 push docs. ✓
- Phase C Enrich (on-demand, pull + empathy + link-vs-embed) → Task 1 Phase C. ✓
- Active MCP read (Gmail own-mail / Calendar / Drive) → Task 1 Phase A step 1 + Task 4 guardrail 2. ✓
- Content model (card/hub/block) → Task 3. ✓
- CLI drive, assume-on-PATH, whoami gate → Task 1 prereq gate + Task 2. ✓
- Guardrails (confirm, own-mail, privacy chips, adults-only, provenance, server-authority) → Task 4. ✓
- Install (global/per-project/plugin-deferred) → Task 5 + README. ✓
- Testing/verification (dry-run authoring, guardrail checks, no-push) → Task 6. ✓
- Out of scope (no scheduled run, no auto-push, no new CLI commands) → honored; no task adds these. ✓

**Placeholder scan:** No TBD/TODO; every file's full content is inline; every verification step has an exact command + expected output. ✓

**Type/name consistency:** CLI subcommands (`whoami/pull/push/template`) and flags (`--type/--hub/--section/--block`) are spelled identically across SKILL.md, cli.md, and the Task 6 checks, and match `Main.kt`. Enum sets match between content-model.md and Global Constraints. ✓
