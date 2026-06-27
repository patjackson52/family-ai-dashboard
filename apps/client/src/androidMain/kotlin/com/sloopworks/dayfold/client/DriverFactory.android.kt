package com.sloopworks.dayfold.client

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sloopworks.dayfold.client.db.ContentDb

actual class DriverFactory(private val context: Context) {
  actual fun createDriver(): SqlDriver {
    // Guard against a downgrade crash when an older build runs over a newer on-disk
    // cache (SQLiteException: Can't downgrade …). content.db is disposable — if its
    // schema version is newer than this build, drop it + let it re-create + re-sync.
    val dbFile = context.getDatabasePath("content.db")
    if (dbFile.exists()) {
      val onDisk = runCatching {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
          .use { it.version.toLong() }
      }.getOrNull()
      if (cacheNeedsWipe(onDisk, ContentDb.Schema.version)) context.deleteDatabase("content.db")
    }
    return AndroidSqliteDriver(ContentDb.Schema, context, "content.db")
  }
}
