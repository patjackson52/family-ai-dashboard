package com.sloopworks.dayfold.client

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// Slice 4 (ADR 0038) — the client-side ULID minter for op_ids (and any client-minted
// item ids). Mirrors the CLI minter: Crockford base32, 26 chars (10-char 48-bit time
// prefix + 16-char random), matching the schema `$defs.ulid` pattern. Pure `encode`
// is deterministic for a fixed (timeMs, random) so the seam is testable headlessly.
class UlidTest {
  private val ulidPattern = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")

  @Test fun `encode produces a 26-char Crockford base32 string`() {
    val s = Ulid.encode(1_700_000_000_000L, Random(42))
    assertEquals(26, s.length)
    assertTrue(ulidPattern.matches(s), "not a valid ULID: $s")
  }

  @Test fun `encode is deterministic for a fixed time and seed`() {
    assertEquals(Ulid.encode(1_700_000_000_000L, Random(7)), Ulid.encode(1_700_000_000_000L, Random(7)))
  }

  @Test fun `the time prefix makes ids lexicographically time-sortable`() {
    val earlier = Ulid.encode(1_700_000_000_000L, Random(1))
    val later = Ulid.encode(1_700_000_001_000L, Random(1))
    assertTrue(earlier < later, "$earlier should sort before $later")
  }

  @Test fun `the random tail differs across mints at the same instant`() {
    assertNotEquals(Ulid.encode(1_700_000_000_000L, Random(1)), Ulid.encode(1_700_000_000_000L, Random(2)))
  }

  @Test fun `next mints a valid ULID from the wall clock`() {
    assertTrue(ulidPattern.matches(Ulid.next()))
  }
}
