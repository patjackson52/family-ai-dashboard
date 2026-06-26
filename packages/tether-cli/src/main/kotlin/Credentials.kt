package works.tether.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission

// Generified from dayfold's apps/cli/.../Credentials.kt. Identical mechanics
// (0600 file, atomic tmp+move, cross-process refresh lock); the file path is now
// injected from TetherConfig instead of hard-coded to ~/.config/dayfold.
//
// `tenantId` is the generic rename of dayfold's `familyId`.

@Serializable
data class Creds(
  val v: Int = 1,
  val api: String,
  val accessToken: String,
  val refreshToken: String,
  val tenantId: String,
  val obtainedAt: String,
)

class Credentials(private val file: Path) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  fun path(): Path = file

  fun load(): Creds? =
    if (Files.exists(file)) runCatching { json.decodeFromString<Creds>(Files.readString(file)) }.getOrNull() else null

  fun save(c: Creds) {
    Files.createDirectories(file.parent)
    val tmp = file.resolveSibling("credentials.json.tmp")
    Files.writeString(tmp, json.encodeToString(Creds.serializer(), c))
    runCatching { Files.setPosixFilePermissions(tmp, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
    Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    runCatching { Files.setPosixFilePermissions(file, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
  }

  fun delete() {
    Files.deleteIfExists(file)
  }

  fun <T> withRefreshLock(block: () -> T): T {
    Files.createDirectories(file.parent)
    val lock = file.resolveSibling("refresh.lock")
    FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { ch ->
      ch.lock().use { return block() }
    }
  }
}
