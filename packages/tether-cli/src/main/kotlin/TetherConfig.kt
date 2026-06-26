package works.tether.cli

import java.nio.file.Path

/**
 * The ONE thing a new project edits to adopt Tether's CLI auth.
 *
 * Everything dayfold hard-coded — the keychain service name, the credentials
 * file path, the default API host, the tenant path segment, the OAuth endpoint
 * paths — is lifted here. A new app provides a `TetherConfig`; the device-login
 * loop, keychain store, and refresh-on-401 client below are otherwise untouched.
 *
 * The flow itself is plain RFC 8628 (device authorization grant) + bearer tokens,
 * so this CLI works against ANY backend that speaks it — dayfold's own API,
 * Better Auth's device plugin, FusionAuth, Zitadel, WorkOS, etc. Only the paths
 * differ, and those are config.
 */
data class TetherConfig(
  /** Human brand, used in keychain labels and prompts. e.g. "Dayfold". */
  val appName: String,
  /** Keychain service id (OS keyring entry). e.g. "dayfold-cli". */
  val keychainService: String,
  /** Default API base if no env override is set. */
  val defaultApiBase: String,
  /** Env var that overrides the API base. e.g. "DAYFOLD_API". */
  val apiEnvVar: String,
  /** Where the 0600 credentials file lives. Default: ~/.config/<slug>/credentials.json */
  val credentialsFile: Path,
  /**
   * The tenant noun used to build resource paths: `/<tenantPath>/<id>/<resource>/<rid>`.
   * dayfold uses "families". A todo app might use "workspaces".
   */
  val tenantPath: String,
  /** OAuth/endpoint paths — standards-shaped defaults, override per backend. */
  val endpoints: Endpoints = Endpoints(),
) {
  data class Endpoints(
    val deviceAuthorize: String = "/device/authorize",
    val deviceToken: String = "/device/token",
    val refresh: String = "/auth/refresh",
    val whoami: String = "/auth/whoami",
    val signout: String = "/auth/signout",
  )

  companion object {
    /** Convenience builder using the conventional ~/.config/<slug>/credentials.json path. */
    fun of(
      appName: String,
      slug: String,
      defaultApiBase: String,
      tenantPath: String,
      apiEnvVar: String = "${slug.uppercase()}_API",
      endpoints: Endpoints = Endpoints(),
    ): TetherConfig = TetherConfig(
      appName = appName,
      keychainService = "$slug-cli",
      defaultApiBase = defaultApiBase,
      apiEnvVar = apiEnvVar,
      credentialsFile = Path.of(System.getProperty("user.home"), ".config", slug, "credentials.json"),
      tenantPath = tenantPath,
      endpoints = endpoints,
    )
  }
}
