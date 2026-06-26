# tether-cli — extraction spike (PoC)

**Status: spike / proof-of-concept.** Not wired into dayfold's build. It exists
to answer one question: *can dayfold's working CLI auth be lifted into a
standalone, config-driven module that any future app reuses?* Answer: yes — and
this compiles + tests green to prove it.

## What this is

The "Tether" hypothesis is that the genuinely reusable, **nobody-sells-it** part
of dayfold's auth is the **client side**: a Kotlin/JVM CLI that logs in via the
RFC 8628 device-authorization grant (owner approves the device in the mobile
app), stashes the refresh token in the **OS keychain**, and makes authed calls
with transparent **refresh-on-401**. The backend can be anything that speaks the
same standard endpoints (dayfold's own API, Better Auth's device plugin,
FusionAuth, Zitadel, WorkOS…).

This module is dayfold's `apps/cli` auth code with every dayfold-specific value
hoisted into one `TetherConfig`.

## The whole adoption surface

```kotlin
val config = TetherConfig.of(
  appName        = "Demo",
  slug           = "tether-demo",          // → keychain "tether-demo-cli", env TETHER_DEMO_API,
                                           //   ~/.config/tether-demo/credentials.json
  defaultApiBase = "http://localhost:8787",
  tenantPath     = "families",             // resource paths: /families/<id>/<resource>/<rid>
)
```

That's it. `login` / `logout` / `whoami` / `call <METHOD> <path> [file]` then work
unchanged. Swapping backends = overriding `TetherConfig.Endpoints` paths.

## What was parameterized out of dayfold

| dayfold hard-coded | now config |
|---|---|
| keychain service `dayfold-cli` | `keychainService` (from `slug`) |
| `~/.config/dayfold/credentials.json` | `credentialsFile` |
| `DAYFOLD_API` env, `https://…` default | `apiEnvVar`, `defaultApiBase` |
| `familyId` field, `/families/:id/…` paths | `tenantId`, `tenantPath` |
| `/device/authorize`, `/auth/refresh`, … | `TetherConfig.Endpoints` |

Unchanged from dayfold (deliberately — the value is that it's already battle-tested):
the device-grant polling loop + `slow_down`/`access_denied`/`expired_token`
handling, the macOS Keychain / libsecret detection, the 0600 atomic file write,
the cross-process refresh lock, and the QR rendering.

## Files

- `TetherConfig.kt` — the one thing a new app edits.
- `DeviceLogin.kt` — RFC 8628 device-grant loop.
- `SecretStore.kt` — OS keychain (macOS/libsecret) + creds⇄keychain glue.
- `Credentials.kt` — 0600 file, atomic write, refresh lock.
- `TetherClient.kt` — authed transport, refresh-on-401, tenant path builder.
- `Qr.kt` — terminal QR (verbatim from dayfold).
- `Main.kt` — demo entrypoint showing how small a consumer is.

## Build / test

```sh
./gradlew test   # compiles + runs the keychain-glue + config tests, no network needed
./gradlew run --args="login"
```

## What this spike does NOT decide

The **backend** foundation (extract dayfold's TS vs. Better Auth vs. hybrid) is
still open and is ADR-class. This spike is the no-regrets half: the client/CLI is
worth packaging regardless of which backend any project picks, because the device
grant + bearer tokens are identical across all of them.
