package works.tether.cli

import kotlin.system.exitProcess

// Demo entrypoint. A new project's CLI is THIS small: define a TetherConfig,
// then delegate to the shared verbs. login / logout / whoami / call come free,
// backed by RFC 8628 device grant + OS-keychain refresh + refresh-on-401.
//
// Swapping backends (dayfold API ↔ Better Auth ↔ FusionAuth ↔ Zitadel) is a
// config edit — the protocol is identical.

private val CONFIG = TetherConfig.of(
  appName = "Demo",
  slug = "tether-demo",
  defaultApiBase = "http://localhost:8787",
  tenantPath = "families", // a workspaces app would say "workspaces"
)

fun main(args: Array<String>) {
  val cfg = CONFIG
  val store = Credentials(cfg.credentialsFile)
  when (args.getOrNull(0)) {
    "login" -> deviceLogin(cfg, allowFileFallback = args.contains("--allow-file-key"))

    "logout" -> {
      val keychain = resolveKeychain(cfg)
      loadCreds(store, keychain)?.let { c -> runCatching { req("POST", "${c.api}${cfg.endpoints.signout}", "{}", c.accessToken) } }
      deleteCreds(store, keychain)
      println("logged out")
    }

    "whoami" -> {
      val keychain = resolveKeychain(cfg)
      val creds = loadCreds(store, keychain)
      if (creds == null) { println("not signed in — run: login"); return }
      println("tenant=${creds.tenantId} api=${creds.api}")
      val (code, body) = TetherClient(cfg, store, keychain).whoami()
      if (code == 200) println(body)
    }

    // call <METHOD> <path> [bodyFile]   e.g. call PUT /families/$f/cards/$id card.json
    "call" -> {
      val method = args.getOrNull(1) ?: usage()
      val path = args.getOrNull(2) ?: usage()
      val body = args.getOrNull(3)?.let { java.nio.file.Files.readString(java.nio.file.Path.of(it)) }
      val keychain = resolveKeychain(cfg)
      val (code, resp) = TetherClient(cfg, store, keychain).call(method.uppercase(), path, body)
      println("$method $path -> $code")
      if (code >= 400) { System.err.println(resp); exitProcess(1) }
      println(resp)
    }

    else -> usage()
  }
}

private fun usage(): Nothing {
  System.err.println(
    "usage: tether <command>\n" +
      "  login [--allow-file-key]   device-grant login (owner approves in the app)\n" +
      "  logout                     revoke + clear local creds\n" +
      "  whoami                     show tenant + resolved scopes\n" +
      "  call <METHOD> <path> [file]  authed request, auto refresh-on-401",
  )
  exitProcess(2)
}
