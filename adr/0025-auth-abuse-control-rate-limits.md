# ADR 0025: Auth Abuse-Control — Rate Limits & Lockout Constants

**Status:** Accepted 2026-06-21 (operator-directed). Records the security
constants already implemented across AUTH-S3/S4 (`apps/api/src/app.ts`,
`apps/api/src/auth/ratelimit.ts`) so they're a reviewed, single source of truth
rather than scattered literals. Composes with ADR 0011 (hardened auth) and
ADR 0021 (S1–S6). Immutable — supersede, do not edit.

## Context

S3 (device grant) and S4 (invites) ship per-endpoint rate limits and per-account
lockouts as inline constants. The A8b `invitelocked` failure screen needs a
concrete cooldown to show the user, which forced the question "what is the
redeem lockout, exactly?" — and surfaced that none of these anti-abuse values
were recorded outside the code. This ADR fixes the values as the intended
posture so the UI copy, tests, and future tuning have an anchor.

## Decision — the constants (as implemented)

Fixed-window counter (`ratelimit.hit(key, windowSecs, cap)`) and
failure-lockout (`recordFailure(key, windowSecs, threshold, lockSecs)` +
`isLocked`), keyed in Postgres (`rate_limits`).

| Surface | Key | Window | Cap / threshold | Lockout |
|---|---|---|---|---|
| Device authorize | `ip:authorize:<ip>` | 600 s | 10 req | — (429 over cap) |
| Device token poll | `ip:token:<ip>` | 600 s | 600 req | — (anti-DoS, generous) |
| Device approve (failed) | `<user/code>` | 900 s | 5 failures | **900 s** |
| Owner mint invite | `owner:mint:<sub>` | 600 s | 20 req | — (429 over cap) |
| **Invite redeem (failed)** | `account:redeem:<sub>` | **900 s** | **5 failures** | **900 s** |
| Invite caps (per family) | — | — | ≤10 active invites · ≤20 pending members | — |

**User-facing value:** the **invite-redeem lockout = 5 failed attempts in 15
minutes → locked out for 15 minutes** (`app.ts:286`,
`recordFailure(key, 900, 5, 900)`). The `invitelocked` screen copy says "wait
about 15 minutes."

## Rationale

- **5 / 15 min** balances anti-enumeration (invite tokens are unguessable
  hashes; redeem failures are mostly typos/stale links) against not locking out
  a legitimate invitee who fat-fingers a couple of times. The uniform-404 on a
  bad/expired/exhausted token means failures don't leak which case occurred.
- Device-token poll is deliberately generous (600 / 10 min) — it's a tight CLI
  poll loop, wall-clock-bounded by the ~10-min device-code TTL anyway.
- IP-keyed caps blunt distributed abuse; per-account lockouts blunt targeted
  abuse. Both are cheap DB counters, swept with the other terminal rows
  (the tracked retention-sweep follow).

## Consequences

- The `invitelocked` UI cooldown is now exact, not assumed.
- Tuning any of these is a code + (this) ADR-supersession change, not a silent
  literal edit — they're security posture.
- Not changed: the refresh reuse-detection / ~20 s grace (ADR 0011 / S1-S3), the
  device-grant anti-phishing flow, or the uniform-404 behavior.

## Revisit Trigger

A measured abuse pattern that these values don't stop; a support signal that
legitimate invitees hit the redeem lockout; or adding Phone-OTP (ADR 0023),
which reintroduces the SMS-specific velocity/region/spend controls deferred
there.
