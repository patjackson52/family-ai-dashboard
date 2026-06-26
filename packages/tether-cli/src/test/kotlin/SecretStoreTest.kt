package works.tether.cli

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretStoreTest {
  private fun cfg() = TetherConfig.of(
    appName = "Test", slug = "tether-test", defaultApiBase = "http://localhost", tenantPath = "families",
  )

  /** In-memory keychain so the glue is testable without an OS keyring. */
  private class FakeKeychain : SecretStore {
    val m = HashMap<String, String>()
    override fun get(account: String) = m[account]
    override fun put(account: String, secret: String) { m[account] = secret }
    override fun delete(account: String) { m.remove(account) }
  }

  private fun creds() = Creds(
    api = "http://localhost", accessToken = "atk", refreshToken = "rtk", tenantId = "fam_1", obtainedAt = "now",
  )

  @Test fun configBuilderDerivesConventions() {
    val c = TetherConfig.of(appName = "Dayfold", slug = "dayfold", defaultApiBase = "https://api", tenantPath = "families")
    assertEquals("dayfold-cli", c.keychainService)
    assertEquals("DAYFOLD_API", c.apiEnvVar)
    assertTrue(c.credentialsFile.toString().endsWith("/.config/dayfold/credentials.json"))
    assertEquals("/device/authorize", c.endpoints.deviceAuthorize)
  }

  @Test fun keychainHoldsRefreshTokenOutOfFile() {
    val tmp = Files.createTempDirectory("tether").resolve("credentials.json")
    val store = Credentials(tmp)
    val kc = FakeKeychain()

    saveCreds(store, kc, creds())

    // refresh token went to the keychain, NOT the on-disk file
    assertEquals("rtk", kc.get(KEYCHAIN_ACCOUNT))
    assertEquals("", store.load()!!.refreshToken)

    // load() re-joins keychain + file into a full Creds
    val loaded = loadCreds(store, kc)!!
    assertEquals("rtk", loaded.refreshToken)
    assertEquals("atk", loaded.accessToken)
    assertEquals("fam_1", loaded.tenantId)

    deleteCreds(store, kc)
    assertNull(kc.get(KEYCHAIN_ACCOUNT))
    assertNull(store.load())
  }

  @Test fun fileFallbackKeepsRefreshTokenWhenNoKeychain() {
    val tmp = Files.createTempDirectory("tether").resolve("credentials.json")
    val store = Credentials(tmp)

    saveCreds(store, null, creds())
    assertEquals("rtk", store.load()!!.refreshToken) // no keychain → stays in file
    assertEquals("rtk", loadCreds(store, null)!!.refreshToken)
  }
}
