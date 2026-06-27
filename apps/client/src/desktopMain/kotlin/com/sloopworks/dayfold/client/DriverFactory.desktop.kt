package com.sloopworks.dayfold.client

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sloopworks.dayfold.client.db.ContentDb
import java.io.File

actual class DriverFactory {
  actual fun createDriver(): SqlDriver {
    val dir = File(System.getProperty("user.home"), ".dayfold").apply { mkdirs() }
    val dbFile = File(dir, "content.db")
    // Same disposable-cache downgrade guard as Android (DriverFactory.android): if an
    // older build opens a newer on-disk cache, drop it + re-sync instead of risking a
    // "can't downgrade" failure. Reads user_version via a raw JDBC connection first.
    if (dbFile.exists()) {
      val onDisk = runCatching {
        java.sql.DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { c ->
          c.createStatement().use { st ->
            st.executeQuery("PRAGMA user_version").use { rs -> if (rs.next()) rs.getLong(1) else null }
          }
        }
      }.getOrNull()
      if (cacheNeedsWipe(onDisk, ContentDb.Schema.version)) dbFile.delete()
    }
    val driver = JdbcSqliteDriver(
      "jdbc:sqlite:${dbFile.absolutePath}",
      schema = ContentDb.Schema,   // create/migrate handled by the driver
    )
    driver.execute(null, "PRAGMA journal_mode=WAL", 0)
    return driver
  }
}
