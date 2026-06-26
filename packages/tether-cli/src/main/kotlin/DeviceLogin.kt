package works.tether.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.system.exitProcess

private val J = Json { ignoreUnknownKeys = true }

/**
 * RFC 8628 device authorization grant — the headline flow, generalized from
 * dayfold's `deviceLogin`. The owner approves THIS cli inside the mobile app
 * (and, on a Tether-style backend, picks its scopes); the CLI polls until then.
 *
 * Differences from dayfold: brand strings, endpoint paths, keychain service, and
 * the tenant id field all come from TetherConfig. The protocol is unchanged.
 */
fun deviceLogin(cfg: TetherConfig, allowFileFallback: Boolean) {
  val api = System.getenv(cfg.apiEnvVar) ?: cfg.defaultApiBase
  val store = Credentials(cfg.credentialsFile)
  val keychain = keychainForLogin(cfg, allowFileFallback)

  val (ac, atxt) = req("POST", "$api${cfg.endpoints.deviceAuthorize}", "{}", null)
  if (ac != 200) {
    System.err.println("login failed: the server couldn't start a device login (HTTP $ac).")
    System.err.println("  " + atxt.trim().take(300).ifEmpty { "(no response body)" })
    exitProcess(1)
  }
  val auth = runCatching { J.parseToJsonElement(atxt).jsonObject }.getOrNull()
  val deviceCode = auth?.get("device_code")?.jsonPrimitive?.contentOrNull
  val userCode = auth?.get("user_code")?.jsonPrimitive?.contentOrNull
  val verifyUri = auth?.get("verification_uri")?.jsonPrimitive?.contentOrNull
  if (deviceCode == null || userCode == null || verifyUri == null) {
    System.err.println("login failed: unexpected response from ${cfg.endpoints.deviceAuthorize}."); exitProcess(1)
  }
  var interval = auth["interval"]?.jsonPrimitive?.intOrNull ?: 5
  val expiresIn = auth["expires_in"]?.jsonPrimitive?.intOrNull ?: 600
  val verifyComplete = auth["verification_uri_complete"]?.jsonPrimitive?.contentOrNull ?: "$verifyUri?user_code=$userCode"

  if (System.console() != null) runCatching { print("\n" + Qr.render(verifyComplete) + "\n") }
  println("To authorize this ${cfg.appName} CLI, an owner must approve code:\n\n    $userCode\n\nScan the QR above, or go to $verifyUri")

  val deadline = System.currentTimeMillis() + (expiresIn + 30) * 1000L
  while (System.currentTimeMillis() < deadline) {
    Thread.sleep(interval * 1000L)
    val body = """{"grant_type":"urn:ietf:params:oauth:grant-type:device_code","device_code":"$deviceCode"}"""
    val (code, txt) = req("POST", "$api${cfg.endpoints.deviceToken}", body, null)
    val obj = runCatching { J.parseToJsonElement(txt).jsonObject }.getOrNull()

    if (code == 200 && obj != null) {
      val accessToken = obj["access_token"]?.jsonPrimitive?.contentOrNull
      val refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull
      if (accessToken == null || refreshToken == null) {
        System.err.println("login failed: unexpected response from ${cfg.endpoints.deviceToken}."); exitProcess(1)
      }
      // Resolve the active tenant from whoami so later calls need no env/flags.
      val (wc, wt) = req("GET", "$api${cfg.endpoints.whoami}", null, accessToken)
      val tenantId = if (wc == 200) {
        runCatching {
          val o = J.parseToJsonElement(wt).jsonObject
          (o["tenant_id"] ?: o["family_id"])?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        }.getOrNull()
      } else null
      if (tenantId.isNullOrEmpty()) {
        System.err.println("login succeeded but could not resolve a tenant — the approving owner needs one set up first.")
        exitProcess(1)
      }
      saveCreds(
        store, keychain,
        Creds(api = api, accessToken = accessToken, refreshToken = refreshToken, tenantId = tenantId, obtainedAt = java.time.Instant.now().toString()),
      )
      println("logged in")
      return
    }

    if (obj == null) {
      if (code in 500..599) continue
      System.err.println("login failed: unexpected response from ${cfg.endpoints.deviceToken} (HTTP $code)."); exitProcess(1)
    }
    when (obj["error"]?.jsonPrimitive?.contentOrNull) {
      "authorization_pending" -> {}
      "slow_down" -> interval += 5
      "access_denied" -> { System.err.println("login denied: the owner declined this device."); exitProcess(1) }
      "expired_token" -> { System.err.println("login expired: the code timed out — run login again."); exitProcess(1) }
      else -> { System.err.println("login failed: ${obj["error"]?.jsonPrimitive?.contentOrNull ?: "server error (HTTP $code)"}"); exitProcess(1) }
    }
  }
  System.err.println("login timed out — run login to try again."); exitProcess(1)
}

/** Keychain to store the refresh token in; file fallback only behind explicit opt-in. */
internal fun keychainForLogin(cfg: TetherConfig, allowFileFallback: Boolean): SecretStore? {
  resolveKeychain(cfg)?.let { return it }
  if (allowFileFallback) {
    System.err.println("warning: no OS keychain found — storing the refresh token in plaintext ${cfg.credentialsFile} (0600).")
    return null
  }
  System.err.println(
    "error: no OS keychain found to store the refresh token securely.\n" +
      "  Re-run with --allow-file-key to keep it in a 0600 file (headless/CI),\n" +
      "  or sign in on a machine with macOS Keychain / libsecret.",
  )
  exitProcess(2)
}
