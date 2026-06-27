package com.sloopworks.dayfold.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// content.db is a disposable server-content cache. Installing an OLDER build over a
// NEWER one left the on-disk schema at a higher version than the build expected, and
// AndroidSqliteDriver crashed the app on launch ("Can't downgrade database from
// version 4 to 3"). cacheNeedsWipe drives the guard that drops the stale cache.
class DriverCacheWipeTest {
  private val build = 3L   // this build's ContentDb.Schema.version

  @Test fun `wipe ONLY when the on-disk cache is newer than this build (the crash case)`() {
    assertTrue(cacheNeedsWipe(4L, build))    // device v4, build v3 → downgrade → wipe
    assertTrue(cacheNeedsWipe(99L, build))   // far newer → wipe
  }

  @Test fun `keep the cache on same version, upgrade, or a fresh-or-unreadable file`() {
    assertFalse(cacheNeedsWipe(3L, build))   // same version → keep
    assertFalse(cacheNeedsWipe(2L, build))   // upgrade → SQLDelight migrations handle it
    assertFalse(cacheNeedsWipe(null, build)) // missing/unreadable → let the driver create it
  }
}
