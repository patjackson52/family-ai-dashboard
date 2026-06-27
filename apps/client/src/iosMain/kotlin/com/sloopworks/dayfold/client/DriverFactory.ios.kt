package com.sloopworks.dayfold.client

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.sloopworks.dayfold.client.db.ContentDb

actual class DriverFactory {
  // TODO(ios-host): apply the same disposable-cache downgrade guard as Android/desktop
  // (DriverFactory.android / .desktop). content.db is a re-syncable cache; an older
  // build over a newer on-disk version risks a "can't downgrade" crash. The decision
  // helper `cacheNeedsWipe(onDisk, ContentDb.Schema.version)` is shared and ready — this
  // just needs a native `PRAGMA user_version` read (platform sqlite3) + file delete,
  // wired + verified on an Xcode host (CI has none today).
  actual fun createDriver(): SqlDriver =
    NativeSqliteDriver(ContentDb.Schema, "content.db")
}
