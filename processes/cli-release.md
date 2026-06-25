# CLI Release & Homebrew Distribution (ADR 0031)

How `dayfold` ships to users via `brew install` and how to cut a release.

```
brew install sloopworks/tap/dayfold      # one line; brew installs Java for the user
brew upgrade dayfold                       # update
```

## One-time operator setup (the ADR 0031 gates)

These need a human (license decision + an external repo + a secret) — they are not
agent-buildable:

1. **License / distribution decision.** The repo is unlicensed; a *public* tap
   distributes the CLI publicly. Pick a license (or use a **private** tap) and set it
   in the formula (`apps/cli/homebrew/dayfold.rb`, currently `license :cannot_represent`).
2. **Create the tap repo** `SloopWorks/homebrew-tap` (a normal GitHub repo named
   `homebrew-tap`). Copy `apps/cli/homebrew/dayfold.rb` to `Formula/dayfold.rb`.
   (Recommended: add a `brew test-bot` / `brew audit --strict dayfold` CI on the tap
   so a bad bump can't reach users.)
3. **Add the `HOMEBREW_TAP_TOKEN` secret** to this repo — a fine-scoped PAT (or a
   GitHub App token) with **write to only `SloopWorks/homebrew-tap`** (least
   privilege — it should not grant write to the main repo). Until it exists, the
   release workflow publishes the GitHub Release but **skips** the formula bump.
4. **Harden the release trigger** (from the security review — a `cli-v*` tag push runs
   with `contents: write` + the tap token):
   - Restrict who can push `cli-v*` tags (a tag-protection rule, or limit write
     access); a tag alone publishes a release.
   - CODEOWNERS-protect `.github/workflows/release-cli.yml` so the pipeline can't be
     weakened without review.
   - Optionally gate the release job behind a GitHub Environment with a required
     reviewer (a tag alone otherwise publishes).
   - (Already done in the workflow: `actions/checkout` + `actions/setup-java` are
     SHA-pinned; the release upload uses the pre-installed `gh` CLI — no third-party
     action; the tap token flows via scoped `GIT_CONFIG_*` env, never a URL/argv/
     `.git/config`; the untrusted tag is strict-semver-validated before any use.)

## Cutting a release

1. Decide the version (semver). Tag from `main`:
   ```
   git tag cli-v0.1.0 && git push origin cli-v0.1.0
   ```
2. `.github/workflows/release-cli.yml` then (on the `cli-v*` tag):
   - validates the tag is `cli-v<semver>`,
   - builds the dist — `apps/cli` is a **standalone Gradle build**:
     `cd apps/cli && ./gradlew distTar -PcliVersion=<version>` → `dayfold-<version>.tar`
     (a `dayfold-<version>/bin/dayfold` launcher + `lib/*.jar`),
   - publishes a **GitHub Release** with that tarball,
   - **bumps the tap formula** (`url` + `sha256`) and pushes to `homebrew-tap`
     (skipped if `HOMEBREW_TAP_TOKEN` is unset).
3. Users get it via `brew install sloopworks/tap/dayfold` / `brew upgrade dayfold`.

## How it works (ADR 0031, Option A)

- **Packaging:** the Gradle `application` plugin's `distTar` ships a runnable tree
  (launcher + jars). The formula `depends_on "openjdk"` so brew installs Java; because
  brew's openjdk is keg-only, `write_env_script` pins `JAVA_HOME` into `bin/dayfold` →
  **zero user configuration**. One platform-independent artifact (no per-arch matrix).
- **Why not jpackage/.app:** the `reduxkotlin/homebrew-tap` (`rk`) tool nested its
  launcher in a macOS `.app` → empty keg `bin/` → it never linked onto PATH (INB-19).
  We ship a plain top-level `bin/dayfold` and the formula's `test do` block exercises
  it, so that class of bug fails the tap CI rather than the user.

## Versioning notes

- The git tag is the source of truth; CI passes it to Gradle as `-PcliVersion`. Local
  builds default to `0.0.0-dev`.
- Homebrew derives the version from the `dayfold-<version>.tar` URL, so the bump only
  rewrites `url` + `sha256`.

## Future (recorded in ADR 0031, not adopted)

- **jlink** self-contained image (no openjdk dependency) if the ~300 MB JDK pull or
  JVM startup becomes a complaint — at the cost of a per-platform release matrix.
- A **native (Go/Rust) rewrite** would simplify packaging further (single static
  binary; GoReleaser/cargo-dist bump the formula for free) but must regenerate the
  shared `packages/schema/kotlin-gen` types into the new language.
