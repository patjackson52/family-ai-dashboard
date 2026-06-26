# ADR 0036: External-Image-URL Privacy Posture — HTTPS Host-Allowlist + Hardened Parser (Phase 1)

## Status

**Accepted** 2026-06-26 (operator-directed — "Accept — Wikimedia-only start";
**customer-data-handling class**, guardrail #3/#4). Cleared the BUILD gate for the
Hub & card visual-enrichment image render. Composes 0004/0006/0022/0029/0030; the
visual treatment it serves was signed off separately under ADR 0008 (hi-fi
mockup approved as-is, 2026-06-26). Spec: `specs/hub-card-visual-enrichment-design.md`.

**Accepted allowlist (Phase 1): exactly `upload.wikimedia.org`.** Additions are
one exact host at a time via the change process below — never a wildcard or a
broad "logo CDN" category — each operator-approved.

## Context

The visual-enrichment feature lets the Claude skill set explicit image URLs
(`Hub.media.heroUrl`/`thumbnailUrl`, `BriefingCard.media.thumbnailUrl`, block
`thumbnailUrl`/`avatarUrl`) that the Compose client fetches and renders. At MVP
(Phase 1) these are **public third-party URLs** — the family's devices fetch them
directly. That introduces handling concerns the rest of the stack deliberately
avoids:

- **Client→third-party exposure.** When a device renders `heroUrl`, it makes a
  direct request to that host, leaking the device IP + a coarse usage signal
  (which family is viewing which hub, when) to a server Dayfold does not control.
  This is new: today the client talks only to the Dayfold API.
- **Allowlist-evasion / parser-differential attacks.** A naive URL check (e.g.
  `host.endsWith("wikimedia.org")`, `scheme.startsWith("http")`, or trusting the
  server-side parser to match the client/Coil parser) is bypassable via
  `user@evil.com`, punycode/IDN homographs, trailing-dot hosts, alternate ports,
  suffix matches (`evil-wikimedia.org`), or `data:`/`javascript:` payloads.
- **SVG = XSS surface.** `image/svg+xml` can carry script; rendering author-set
  SVG is an injection vector the moment any web/email surface touches these
  fields.
- **Trademark/correctness.** An allowlisted host constrains *where* an image comes
  from, not whether the family has rights to it or whether it's the *right* entity
  (wrong university logo). That is the operator's call, not the renderer's.

A decision is needed now because the field shapes + validator land in this build;
the allowlist posture is the one piece that is customer-data-handling-class and
therefore operator-gated, not agent-decided.

## Decision

Adopt a **Phase-1 hardened-URL posture** for all author-supplied image fields,
enforced identically server-side (Zod refine in the API PUT path) and client/CLI
(Kotlin `Validate.kt` + the Coil load path), as a **single shared rule**:

1. **HTTPS only.** Scheme must be exactly `https` (pinned at write). Rejects
   `http`, `data:`, `javascript:`, `blob:`, relative, and scheme-relative URLs.
2. **No userinfo.** Reject any `@` in the authority (`user:pass@host`).
3. **Host normalization then exact match.** Lowercase, IDNA/punycode
   `toASCII`-normalize, strip a single trailing dot, then require an **exact host
   match** against the allowlist constant — never `endsWith`/suffix matching.
4. **Port.** Empty or `443` only.
5. **Reject SVG.** Reject `.svg` paths at validate time and `image/svg+xml` at
   render time; Coil decodes raster only.
6. **Length cap** on the URL; reject control chars / whitespace.
7. **Host allowlist (Phase 1 starter set):**
   - `upload.wikimedia.org` — Wikimedia Commons static file host (photos +
     institutional logos/mascots; HTTPS, stable, no query-driven redirects).
   - *(Additional hosts are added one exact host at a time — never a wildcard or a
     broad "any logo CDN" category — via the change process below.)*

The allowlist + curated icon set ship as a **generated/bundled constant**
(mirrors `ALLOWED_SCHEMES` in `CardRender.kt`); the skill reads it rather than
guessing, and **surfaces its chosen image to the operator for confirmation before
push** (the trademark/wrong-entity guard).

**Accepted, explicitly-temporary Phase-1 cost:** direct client→third-party image
fetches leak device IP + coarse usage to the allowlisted host(s). This is
bounded, disclosed here, and **eliminated in Phase 2** (self-host + CDN; see the
spec's Phase-2 section — object storage, content-addressed URLs, optional
ingest-proxy *only* with full SSRF guards).

**Change process for the allowlist (not agent-decided):** server telemetry counts
validation rejects by host → surfaces candidate hosts → the **operator** approves
each addition. Allowlist edits are access-controlled + auditable; no wildcards.

## Rationale

- **Why an allowlist, not "any HTTPS image":** an open image fetch makes every
  family device a request generator to arbitrary attacker-named hosts (IP/usage
  exfil, tracking pixels, decode-bomb delivery). A tight allowlist is the cheapest
  Phase-1 control that still ships the headline use case (university logo /
  vacation photo from Wikimedia).
- **Why exact-host, not registrable-domain:** registrable-domain (`wikimedia.org`)
  would admit any subdomain; exact-host (`upload.wikimedia.org`) is strictly
  tighter and Wikimedia serves files from exactly that host.
- **Why Wikimedia-only to start:** it is the single most useful, most stable
  source for the headline use case and carries the least abuse surface (no
  user-controlled redirects, static files). "Official logo CDNs" as a *category*
  is rejected for Phase 1 — most logo/asset CDNs also host arbitrary
  user-uploaded content, which re-opens the exfil/abuse hole an allowlist exists
  to close. Specific hosts can be added per the change process.
- **Why shared/identical validation server+client+CLI:** a parser differential
  (server accepts, Coil fetches something else) is itself the vulnerability; one
  rule, mirrored, closes it.
- **Alternatives rejected:** (B) open HTTPS image fetch — exfil/abuse hole; (C)
  registrable-domain suffix match — admits sibling-subdomain abuse; (D)
  server-side ingest-proxy now — moves SSRF risk server-side without the Phase-2
  guards, premature; (E) ship Phase-2 self-host first — delays the whole feature
  for infra that the dogfood doesn't yet need.

## Consequences

Positive:
- Headline use case ships (logo/photo enrichment) with a bounded, auditable
  exposure surface.
- Evasion vectors (userinfo, punycode, port, suffix, parser-differential,
  `data:`/SVG) are closed by construction and unit-tested incl. evasion vectors.
- No new authz surface — enrichment rides the already-authorized PUT
  (ADR 0029/0030); validation is additive.

Negative / accepted:
- Direct client→Wikimedia fetch leaks device IP + coarse usage until Phase 2
  self-host lands. Disclosed; temporary.
- The allowlist is a small ongoing operator touchpoint (approve host additions).
  Mitigated by telemetry-surfaced candidates + the no-wildcard rule.
- Trademark/usage-rights correctness remains the operator's responsibility; the
  skill's pre-push confirmation is the guard, not the allowlist.

## Revisit Trigger

- Phase 2 self-host + CDN lands → the client→third-party leak is eliminated; this
  ADR's exposure section is superseded by the self-host ADR.
- Any web/email surface begins rendering these fields → re-confirm the same
  validation + add contextual output-encoding (treat `icon`/`accentColor` as typed
  lookups, never raw interpolation) before that surface ships.
- The allowlist grows past a handful of hosts, or a candidate host serves
  user-uploaded content → re-evaluate whether self-host (Phase 2) should be pulled
  forward.
