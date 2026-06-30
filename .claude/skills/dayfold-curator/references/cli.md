# dayfold CLI — command reference

The skill drives ONLY these commands. Assume `dayfold` is on PATH and logged in.

## Auth & account

```
dayfold login [--allow-env-key]
```
RFC 8628 device-authorization flow. Prints a QR code + user code; the family
owner approves in-app. Stores access + refresh tokens in the OS keychain.
`--allow-env-key` falls back to a 0600 plaintext file (headless/CI environments).

```
dayfold logout
```
Revokes the server session + clears local tokens.

```
dayfold whoami
```
Prints `family=<id> api=<url> (device|legacy)` + the credential's resolved
scope from the server (`scope=content:read,content:write,...`). If it shows
`(legacy)` with empty family, or errors → operator must run `dayfold login`.

## Read current state (Phase C — also to get ids before push)

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
- Hub/section/block pushes run an always-on structural pre-check (no flag needed).
- By default `push` auto-links bare phone/email in every `body_md` to tappable
  `tel:`/`mailto:` links and prints a diff of what changed — write plain text,
  not hand-rolled markdown links. `--no-linkify` stores the body verbatim.
- The path `<id>` overwrites the body `id` server-side — the body `id` can stay
  `REPLACE_WITH_CARD_ID`.
- Output: `push <resource>/<id> -> <httpStatus>`. Non-200 prints the server body
  to stderr and exits 1 — the server is the authority; fix and re-push.

## Delete

```
dayfold delete <id>          # delete a hub (cascades its sections + blocks)
dayfold delete <id> --card   # delete a briefing card
dayfold rm <id>              # alias for delete
```
- There is no section or block delete route at MVP. To remove a stray block,
  delete its hub and re-push the tree.
- The server enforces author-only authz: non-authors (and owners without
  explicit author grant) cannot delete.

## Update & version

```
dayfold update               # update to the latest stable dayfold (brew upgrade)
dayfold version              # print the installed CLI version
```
`update` delegates to `brew upgrade dayfold` when brew-managed, else prints
install/upgrade instructions. A throttled (once/24h) version nudge is also
printed after interactive `push`/`pull` when a newer stable version is available.

## Notes

- Generate stable ULIDs for new ids client-side (26-char Crockford base32). Reuse
  an existing id (from `dayfold pull`) to update rather than create.
- Checklist items get ULID stamps auto-applied on push (ADR 0038 — members need
  a stable per-item id to toggle). Idempotent; existing ids are preserved on re-push.
- There is NO `dayfold create` or `dayfold list` — update-by-push + pull-to-read only.
