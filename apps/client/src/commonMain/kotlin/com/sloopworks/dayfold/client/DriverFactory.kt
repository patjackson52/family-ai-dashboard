package com.sloopworks.dayfold.client

import app.cash.sqldelight.db.SqlDriver

// Per-platform SQLDelight driver (ADR 0020). The sync engine (TASK-SYNC) builds a
// ContentStore from this; nothing wires it into the store at runtime yet — this
// just gives each target a driver path so the shared ContentStore + generated DB
// compile on every platform.
expect class DriverFactory {
  fun createDriver(): SqlDriver
}

// content.db is a DISPOSABLE cache of server content (re-synced on launch). If an
// OLDER app build runs over a NEWER one, the on-disk schema version is higher than
// this build expects and the SQLite driver crashes on the downgrade ("Can't downgrade
// database from version N to M" → launch crash). In that case the cache must be wiped
// + re-created; an upgrade (on-disk < current) is handled by SQLDelight migrations, and
// a fresh/unreadable file (null) is left for the driver to create. Pure → unit-tested.
internal fun cacheNeedsWipe(onDiskVersion: Long?, schemaVersion: Long): Boolean =
  onDiskVersion != null && onDiskVersion > schemaVersion
