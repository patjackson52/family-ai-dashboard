package works.tether.cli

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val J = Json { ignoreUnknownKeys = true }
private fun http() = HttpClient.newHttpClient()

/** Low-level HTTP — Pair<status, body>. JDK-only, no extra deps. */
internal fun req(method: String, url: String, body: String?, token: String?): Pair<Int, String> {
  val b = HttpRequest.newBuilder(URI.create(url))
    .apply { if (body != null) header("content-type", "application/json") }
    .apply { if (token != null) header("authorization", "Bearer $token") }
    .method(method, if (body != null) HttpRequest.BodyPublishers.ofString(body) else HttpRequest.BodyPublishers.noBody())
    .build()
  val res = http().send(b, HttpResponse.BodyHandlers.ofString())
  return res.statusCode() to res.body()
}

/**
 * The generic authed transport: any request, with ONE transparent refresh on 401.
 * This is dayfold's `authedGet`/push-retry logic generalized to any method + path,
 * with the refresh serialized across processes via the file lock.
 *
 * Resource paths are built as `/<tenantPath>/<tenantId>/<resource>/<rid>` so a
 * caller says `client.put("cards", id, json)` and never hand-builds URLs.
 */
class TetherClient(
  private val cfg: TetherConfig,
  private val store: Credentials,
  private val keychain: SecretStore?,
) {
  private fun current(): Creds =
    loadCreds(store, keychain) ?: run { System.err.println("not signed in — run: login"); kotlin.system.exitProcess(1) }

  /** Refresh the access token (serialized across processes); returns the new access token. */
  private fun refresh(): String = store.withRefreshLock {
    val cur = loadCreds(store, keychain) ?: run { System.err.println("credentials removed — run: login"); kotlin.system.exitProcess(1) }
    val (rc, rt) = req("POST", "${cur.api}${cfg.endpoints.refresh}", """{"refresh":"${cur.refreshToken}"}""", null)
    if (rc != 200) { System.err.println("session expired — run: login"); kotlin.system.exitProcess(1) }
    val o = runCatching { J.parseToJsonElement(rt).jsonObject }.getOrNull()
    val na = o?.get("access")?.jsonPrimitive?.contentOrNull
    val nr = o?.get("refresh")?.jsonPrimitive?.contentOrNull
    if (na == null || nr == null) { System.err.println("session refresh failed — run: login"); kotlin.system.exitProcess(1) }
    saveCreds(store, keychain, cur.copy(accessToken = na, refreshToken = nr))
    na
  }

  /** Any method against an absolute path on the configured API, refreshing once on 401. */
  fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
    val c = current()
    var (code, resp) = req(method, "${c.api}$path", body, c.accessToken)
    if (code == 401) {
      val fresh = refresh()
      val retry = req(method, "${c.api}$path", body, fresh); code = retry.first; resp = retry.second
    }
    return code to resp
  }

  /** Convenience for the tenant-scoped resource convention. */
  fun put(resource: String, rid: String, body: String): Pair<Int, String> {
    val c = current()
    return call("PUT", "/${cfg.tenantPath}/${c.tenantId}/$resource/$rid", body)
  }

  fun get(resource: String): Pair<Int, String> {
    val c = current()
    return call("GET", "/${cfg.tenantPath}/${c.tenantId}/$resource")
  }

  fun whoami(): Pair<Int, String> = call("GET", cfg.endpoints.whoami)
}
