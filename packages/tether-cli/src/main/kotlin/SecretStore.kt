package works.tether.cli

// Generified from dayfold's apps/cli/.../SecretStore.kt. The long-lived refresh
// token belongs in the OS keychain, not a plaintext file. Access token + non-secret
// config stay in the 0600 file. On a host with no keychain (headless/CI) the file
// fallback is allowed only behind an explicit opt-in.
//
// The ONLY change from dayfold: the keychain service id comes from TetherConfig
// instead of a hard-coded constant.
//
// Plain-JVM (one target) → runtime OS detection. macOS = Keychain via
// `/usr/bin/security`; Linux = libsecret via `secret-tool`; other/absent = none.

internal const val KEYCHAIN_ACCOUNT = "refresh-token"

interface SecretStore {
  fun get(account: String): String?
  fun put(account: String, secret: String)
  fun delete(account: String)
}

internal data class ProcResult(val code: Int, val stdout: String)

internal fun interface CommandRunner {
  fun run(cmd: List<String>, stdin: String?): ProcResult
}

internal val realRunner = CommandRunner { cmd, stdin ->
  val p = ProcessBuilder(cmd).redirectErrorStream(false).start()
  if (stdin != null) p.outputStream.use { it.write(stdin.toByteArray()); it.flush() }
  val out = p.inputStream.readBytes().decodeToString()
  p.errorStream.readBytes() // drain so the child never blocks on a full stderr pipe
  ProcResult(p.waitFor(), out)
}

private fun commandExists(name: String, runner: CommandRunner): Boolean =
  runCatching { runner.run(listOf("/bin/sh", "-c", "command -v $name"), null).code == 0 }.getOrDefault(false)

internal class MacKeychain(
  private val service: String,
  private val runner: CommandRunner = realRunner,
) : SecretStore {
  fun available(): Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Mac") &&
      java.io.File("/usr/bin/security").exists()

  override fun get(account: String): String? {
    val r = runner.run(listOf("/usr/bin/security", "find-generic-password", "-s", service, "-a", account, "-w"), null)
    return if (r.code == 0) r.stdout.trim().ifEmpty { null } else null
  }

  override fun put(account: String, secret: String) {
    val r = runner.run(listOf("/usr/bin/security", "add-generic-password", "-U", "-s", service, "-a", account, "-w", secret), null)
    if (r.code != 0) error("keychain write failed (security exit ${r.code})")
  }

  override fun delete(account: String) {
    runner.run(listOf("/usr/bin/security", "delete-generic-password", "-s", service, "-a", account), null)
  }
}

internal class LibSecret(
  private val service: String,
  private val label: String,
  private val runner: CommandRunner = realRunner,
) : SecretStore {
  fun available(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().let { it.contains("linux") || it.contains("nix") } &&
      commandExists("secret-tool", runner)

  override fun get(account: String): String? {
    val r = runner.run(listOf("secret-tool", "lookup", "service", service, "account", account), null)
    return if (r.code == 0) r.stdout.trim().ifEmpty { null } else null
  }

  override fun put(account: String, secret: String) {
    val r = runner.run(listOf("secret-tool", "store", "--label=$label", "service", service, "account", account), secret)
    if (r.code != 0) error("keychain write failed (secret-tool exit ${r.code})")
  }

  override fun delete(account: String) {
    runner.run(listOf("secret-tool", "clear", "service", service, "account", account), null)
  }
}

/** The OS keychain for this host, or null if none is available. */
internal fun resolveKeychain(cfg: TetherConfig, runner: CommandRunner = realRunner): SecretStore? =
  MacKeychain(cfg.keychainService, runner).takeIf { it.available() }
    ?: LibSecret(cfg.keychainService, "${cfg.appName} CLI", runner).takeIf { it.available() }

// ── Creds ⇄ keychain glue (keeps the refresh token out of the file) ──

internal fun saveCreds(store: Credentials, keychain: SecretStore?, creds: Creds) {
  if (keychain != null) {
    keychain.put(KEYCHAIN_ACCOUNT, creds.refreshToken)
    store.save(creds.copy(refreshToken = "")) // file keeps access + config only
  } else {
    store.save(creds)
  }
}

internal fun loadCreds(store: Credentials, keychain: SecretStore?): Creds? {
  val c = store.load() ?: return null
  if (keychain == null) return c
  val refresh = keychain.get(KEYCHAIN_ACCOUNT)?.takeIf { it.isNotEmpty() } ?: c.refreshToken
  return c.copy(refreshToken = refresh)
}

internal fun deleteCreds(store: Credentials, keychain: SecretStore?) {
  store.delete()
  keychain?.delete(KEYCHAIN_ACCOUNT)
}
