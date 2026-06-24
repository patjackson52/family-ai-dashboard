# TASK-AUTH-CONTENT — Content-API + CLI Verbs + Per-Hub Scoping + Per-Member Visibility

> Implementation design **v2** (2026-06-24, post 3-agent review: security +
> simplification + correctness). Branch `claude/auth-content-slice` (off `main`
> cacdf43). Lands **ADR 0029** (credential resource-scoped grants) + **ADR 0030**
> (per-member visibility) on the existing typed-card content API. Both ADRs
> Accepted. No outstanding gates for the API+data+CLI scope (design-first ADR 0008
> governs only UI surfaces, which are OUT). Review verdict folded in §11.

## 1. Scope

**In (agent-buildable, headless/CI-tested — TS/Hono/Postgres + Kotlin CLI):**
1. **ADR 0029 grants** — `credential_grants` table + central `requireScope` gate
   that resolves grants **from the table** (closes the read-enforcement gap;
   replaces the flat `credentials.scopes[]` reads). Existing card routes rewired
   through it at behaviour parity.
2. **ADR 0030 visibility (cards + hubs, read path)** — hub `visibility`/`created_by`,
   hubs-only `resource_visibility` allow-list + `→hubs.updated_at` touch-trigger,
   card `visibility`/`audience[]`, a read filter applied to cards/hubs **and to
   sections/blocks via their parent hub**, client cache-wipe on tenancy 401/404.
3. **Hub/section/block WRITE API + plain visibility-filtered GET reads.**
4. **CLI** — `pull` + `whoami` (shows resolved grants). Existing `push` reused.

**Out (deferred — review-driven, with ADR cover):**
- **Hub `/sync` keyset + tombstone** — hub render is design-gated OUT (ADR 0008);
  nothing caches hubs yet, so there is no incremental stream to feed and nothing to
  revoke. Hub reads are plain `GET`. The touch-trigger still ships (cheap DDL,
  captures ADR 0030's revocation mechanic for when hub-sync lands with render).
- **Approve→grant-set plumbing / per-hub picker UI** — ADR 0029 §5 interim posture
  ships the default `content:read+write` grant; `redeem` writes the default rows,
  `whoami` flat-reads them. The picker UI + grant-carrying approve land with the
  mobile toolchain session.
- **CLI `hub get|archive|rm` / `status` / `push --dry-run|--diff`** — operator has
  direct SQL + existing `push --type` local validation; add when a dogfood session
  needs one.
- **Hub render** (Compose UI) — ADR 0008 design gate. E2EE (0015/0017), in-app
  authoring, 2-way actions (0016).

## 2. Data model (migration `0008` — verified next free number; 0001-0007 exist)

DDL authoritative in the migration; `specs/prototype/02-data-model.md` updated in
lockstep.

### 2.1 ADR 0029 — grants
```sql
CREATE TABLE credential_grants (
  credential_id text NOT NULL REFERENCES credentials(id) ON DELETE CASCADE,
  scope         text NOT NULL,   -- 'content:read'|'content:write'|'hub:<id>:read'|'hub:<id>:write'
  created_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (credential_id, scope)
);
CREATE INDEX ON credential_grants (credential_id);

-- Backfill: mirror each LIVE credential's actual scopes (not a blanket r+w), so a
-- write-only cred stays write-only. Single migration; aborts if any live cred ends
-- with zero grants (the row-count guard, not just a test).
INSERT INTO credential_grants (credential_id, scope)
  SELECT id, unnest(scopes) FROM credentials WHERE revoked_at IS NULL;
-- guard (in the same tx): assert COUNT(distinct live cred w/ >=1 grant) == COUNT(live creds), else RAISE.
```
`credentials.scopes[]` becomes vestigial (feeds backfill only); a later migration
drops it once nothing reads it. **Runtime never reads both** (§11 R-C-P1).

### 2.2 ADR 0030 — visibility
```sql
ALTER TABLE hubs           ADD COLUMN visibility text NOT NULL DEFAULT 'family'
  CHECK (visibility IN ('family','restricted'));
ALTER TABLE hubs           ADD COLUMN created_by text REFERENCES users(id);  -- NULL = legacy/M0 token
ALTER TABLE briefing_cards ADD COLUMN visibility text NOT NULL DEFAULT 'family'
  CHECK (visibility IN ('family','restricted'));
ALTER TABLE briefing_cards ADD COLUMN audience   text[];   -- permitted user ids when restricted

CREATE TABLE resource_visibility (             -- hubs-only allow-list; rows IMMUTABLE (insert/delete to grant/revoke)
  family_id  text NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  hub_id     text NOT NULL,
  user_id    text NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (family_id, hub_id, user_id),
  FOREIGN KEY (family_id, hub_id) REFERENCES hubs(family_id, id) ON DELETE CASCADE
);
CREATE INDEX ON resource_visibility (family_id, user_id);

-- Touch the hub on allow-list churn so the keyset cursor re-surfaces it as a
-- tombstone to a dropped member WHEN hub-sync exists (deferred). Cheap now, correct later.
CREATE FUNCTION touch_hub_from_visibility() RETURNS trigger AS $$
BEGIN
  UPDATE hubs SET updated_at = now()
   WHERE id = COALESCE(NEW.hub_id, OLD.hub_id)
     AND family_id = COALESCE(NEW.family_id, OLD.family_id);
  RETURN NULL;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_resvis_touch_hub
  AFTER INSERT OR DELETE ON resource_visibility   -- rows immutable, so no UPDATE path
  FOR EACH ROW EXECUTE FUNCTION touch_hub_from_visibility();

-- Defense-in-depth: a non-legacy credential MUST carry a real user (ADR 0030 §4b).
-- (Enforced at insert paths; documented as the invariant the visibility-exempt bypass relies on.)
-- CHECK lives on credentials: kind IN ('app','cli') => user_id IS NOT NULL  (added if not already implied).
```
Hub `family→restricted` flip advances `hubs.updated_at` via the existing
`touch_updated_at` trigger (column write) — covered independently of the resvis
trigger.

## 3. Scope resolution & `requireScope` (ADR 0029) — fixes R-S-P1.1/P1.2, R-C-P1

- **Resolution:** `requireScope` issues its **own** `SELECT scope FROM
  credential_grants WHERE credential_id=$cid` per request (after `authorizeTenant`
  resolves membership + not-revoked). It does **not** read `authorizeTenant`'s
  returned `scopes` (that field is removed/ignored). Never from the token.
- **Gate:** pass iff grants hold `content:<action>` **OR** `hub:<thatId>:<action>`.
  Match by **constructing** `hub:${hubId}:${action}` and comparing for equality —
  never `split(':')` (hub ids are free text; a `:` would mis-parse). Constrain new
  hub ids to `[A-Za-z0-9_-]` at write time.
- **LIST `GET /hubs`:** passes if grants hold `content:read` **or any**
  `hub:*:read`. Returned set = (visibility-visible hubs) **∩** (grant set: all
  family hubs if `content:read`, else only granted `hub:<id>`s). Scope can only
  **restrict**, never widen, the visibility result.
- **Reads compose AND:** a `hub:H:read` grant lets a credential *attempt* H, but
  the row is still subject to the visibility filter against the credential's
  `user_id`. Scope ∧ visibility, never scope-overrides-visibility.
- Helper `apps/api/src/auth/scope.ts`; consumed by every content route.

## 4. Visibility enforcement (ADR 0030) — fixes R-C-P0 (section/block), R-S-P0-1/P0-2

- **Filter (`apps/api/src/content/visibility.ts`):** a row is visible iff
  `visibility='family'` OR caller permitted. Per type:
  - **hub:** caller ∈ `resource_visibility(hub)` OR caller = `hubs.created_by`.
  - **card:** caller ∈ `audience[]`.
  - **section:** its parent **hub** is visible (join `sections→hubs`).
  - **block:** its parent **hub** is visible (join `blocks→sections→hubs`).
  Sections/blocks have **no** own visibility — they inherit the hub. This closes
  the §11 R-C-P0 leak (a restricted hub's blocks carry the sensitive `body_md`).
- **Exempt = legacy credential only**, keyed on the middleware `legacy===true`
  flag (the household/M0 token), **not** on `user_id IS NULL` (R-S-P0-2). The
  filter predicate `caller = created_by` is only reached for non-NULL callers; the
  legacy token short-circuits to see-all before the predicate.
- **Omit-don't-403:** invisible rows are absent, not forbidden. `GET /hubs/:id`
  returns a **uniform 404** for not-exist / not-visible / **and scope-miss on a
  hub-scoped token** (R-S-P2-1) — no 403/404 split that lets a prober enumerate
  real hub ids.
- **Cursor safety (cards `/sync`, R-S-P0-1):** the visibility filter is an in-SQL
  `WHERE` predicate, but `next_cursor`/`has_more` are computed from the **raw
  fetched keyset window before** dropping invisible rows — so a page that is
  entirely restricted-invisible still **advances the cursor** (no stall) and never
  discloses existence by count. Test: all-restricted page → cursor advances,
  `has_more` honest.
- **Client cache-wipe (R1 P0-2):** KMP client hard-wipes local SQLDelight content
  cache on any tenancy 401/404 for the active family (a removed member gets no
  server tombstone). Desktop-testable.

## 5. API endpoints (new; all `authorizeTenant` + `requireScope`)

| Method | Path | Scope | Notes |
|---|---|---|---|
| GET | `/families/:fid/hubs` | read | visibility ∩ grant-set filtered list (plain, **no keyset**) |
| GET | `/families/:fid/hubs/:id` | read/`hub:id:read` | uniform 404 if not-exist/not-visible/scope-miss |
| PUT | `/families/:fid/hubs/:id` | write/`hub:id:write` | upsert; sets `created_by`; carries `visibility` + allow-list authoring (gated per §6) |
| POST | `/families/:fid/hubs/:id/archive` | write | status→archived. **Use Hono wildcard dispatch** (`:id:archive` colon-suffix won't route — mirror the `members/*` pattern in `app.ts`) |
| DELETE | `/families/:fid/hubs/:id` | write | soft-delete; cascade sections/blocks **in one transaction** |
| GET | `/families/:fid/hubs/:id/tree` | read | hub+sections+blocks, visibility-filtered (CLI `pull`) |
| PUT | `/families/:fid/sections/:id` | write | parent hub must exist **AND `deleted_at IS NULL`** |
| PUT | `/families/:fid/blocks/:id` | write | parent section must exist AND not soft-deleted |

Card routes (`PUT/DELETE/GET /cards`, `/sync`) gain `visibility`/`audience`
handling + the `requireScope` gate (replacing the inline scope check), and the
cursor-safe visibility filter (§4).

## 6. Allow-list authoring authority (ADR 0030 §6) — R-S-P1.3

Writing a hub's `visibility`/allow-list requires `content:write` **or**
`hub:<id>:write` AND — per ADR 0030 §6 — the acting member must already be
permitted on the hub (author or allow-listed). At MVP (operator/legacy token only)
this collapses, but the route enforces it now so it doesn't ship the permissive
version where any `content:write` cred rewrites any hub's allow-list.

## 7. CLI verbs (Kotlin, `apps/cli`)

| Verb | Behavior |
|---|---|
| `pull [--hub <id>]` | content read (via `GET /hubs` + `/hubs/:id/tree`) → write local files; proves the author→read-back loop |
| `whoami` | family + resolved grants (flat read of `credential_grants`) + label |

Existing `push` reused (already validates locally via `--type`). Consumes generated
`com.sloopworks.dayfold.schema.*` types.

## 8. Build order — 3 sequenced PRs (subagent-driven, TDD, 2 reviews + final each)

- **PR 1 — grants + gate (de-risk refactor, no new surface).** Migration `0008`
  `credential_grants` + backfill (mirror live scopes, tx-guarded) + `requireScope`
  (reads grants, structural matching) + rewire **card** routes at behaviour parity.
  Tests: gate truth-table; cred with `scopes[]` but no grant row → **denied**
  (catches the dual-read bug); backfill row-count.
- **PR 2 — ADR 0030 schema + card visibility (real reader = M0 feed).** Visibility
  cols, `resource_visibility`, touch-trigger, `kind⇒user_id` CHECK; card read
  filter + cursor-safe `/sync` + `audience` authoring; client cache-wipe. Tests:
  member A/B divergence; legacy-token exempt; all-restricted-page cursor-advance;
  membership-removal cache-wipe.
- **PR 3 — hub/section/block write API + plain GET + CLI.** Reuse card
  upsert/`stripServerManaged`/`stampProvenance`/validation; parent-exists-&-not-deleted;
  one-tx cascade delete; visibility filter incl. **section/block-by-parent-hub**;
  uniform-404; `pull` + `whoami` grants line. Tests: hub CRUD round-trip; restricted
  hub's sections/blocks invisible to non-member (the P0 leak test); tenancy-404.

Each PR green + CI-enforced before the next.

## 9. Test plan highlights (CI, headless)
Scope gate (truth table + dual-read denial) · visibility (A sees / B omitted /
author / legacy-exempt / **section+block by parent hub**) · cursor-advance over
all-restricted page · membership-removal cache-wipe · hub CRUD keyset-free GET +
parent-must-exist + one-tx cascade · CLI pull/whoami · migration backfill row-count
+ idempotent re-apply + added to api/auth-e2e/device-approve harnesses.

## 10. Reuse (don't rebuild)
`stripServerManaged` + `stampProvenance` (mass-assignment + provenance) · the card
upsert shape (`ON CONFLICT (family_id,id) … version+1, deleted_at=NULL`) for
`upsertHub/Section/Block` · existing `(family_id, updated_at, id)` indexes +
`touch_updated_at` triggers (already on hubs/sections/blocks, migration 0001) ·
existing keyset/tombstone machinery for cards.

## 11. Review findings folded (3-agent, 2026-06-24)

**Security:** R-S-P0-2 exempt-on-legacy-not-NULL + `kind⇒user_id` CHECK (§2.2/§4) ·
R-S-P0-1 cursor-from-raw-window (§4) · R-S-P1.1 scope∧visibility (§3) · R-S-P1.2
atomic backfill, mirror scopes, live-only, tx-guard (§2.1) · R-S-P1.3 allow-list
authoring authority (§6) · R-S-P2-1 uniform-404 on scope-miss (§4) · R-S-P2-2
structural grant matching + hub-id charset (§3). Sound: tenancy isolation, scope
from rows, cache-wipe, author-trusted card audience (accepted posture).

**Correctness:** R-C-P0 section/block visibility-by-parent-hub (§4) — the
confirmed leak · R-C-P0 subtree tombstone — **dissolved** by deferring hub `/sync`
(§1; nothing caches hubs) · R-C-P1 requireScope reads `credential_grants` not
`cred.scopes` (§3) + dual-read denial test (§8) · R-C-P1 LIST scope semantics (§3) ·
R-C-P2 backfill predicate (§2.1) · R-C-P2 parent-not-soft-deleted + one-tx cascade
(§5) · R-C-P2 `:id:archive` Hono routing → wildcard dispatch (§5) · sync payload
shape moot (hub-sync deferred). Verified correct: FK composite, touch-trigger
COALESCE, migration number 0008.

**Simplification (reshaped the slice):** defer hub `/sync` keyset+tombstone (plain
GET) — biggest cut, removes ~70% of risk, dissolves a P0 · cut approve→grant-set
plumbing (ADR 0029 interim default) · trim CLI to `pull`+`whoami` · keep
`credential_grants`+`requireScope` (the read-gate fix, single-pass replace, no
dual-read) · reuse card upsert/provenance/validation for hub writes · 3-PR
sequence with card visibility tested against the real feed reader.

## 12. Cross-references
ADR 0029, ADR 0030, ADR 0011, ADR 0022 · `specs/domain-model/scope-and-access-model.md`
· `specs/prototype/02-data-model.md` (DDL home), `03-api.md`, `07-cli.md`.
