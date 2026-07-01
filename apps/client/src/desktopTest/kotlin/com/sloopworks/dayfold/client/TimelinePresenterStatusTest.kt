package com.sloopworks.dayfold.client
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelinePresenterStatusTest {
  private val ny = TimeZone.of("America/New_York")
  private fun s(at: String, done: Boolean = false) = Stop(at = at, title = at, done = done)
  @Test fun `past is done, first future is next, rest upcoming`() {
    val stops = listOf(s("2026-08-24T08:00:00-04:00"), s("2026-08-24T09:50:00-04:00"),
                       s("2026-08-24T11:00:00-04:00"), s("2026-08-24T14:00:00-04:00"))
    val out = stopStatuses(stops, "2026-08-24T10:40:00-04:00", ny)
    assertEquals(listOf(StopStatus.Done, StopStatus.Done, StopStatus.Next, StopStatus.Upcoming), out.map { it.status })
  }
  @Test fun `author done overrides even a future date`() {
    assertEquals(StopStatus.Done, stopStatuses(listOf(s("2026-12-01", done = true)), "2026-08-24T10:00:00-04:00", ny).first().status)
  }
  @Test fun `not-today makes everything upcoming, no next-before-now`() {
    val out = stopStatuses(listOf(s("2026-09-19"), s("2026-09-20")), "2026-08-24T10:00:00-04:00", ny)
    assertEquals(listOf(StopStatus.Next, StopStatus.Upcoming), out.map { it.status })
  }

  // ── tz-aware display labels ─────────────────────────────────────────────────
  @Test fun `timeLabel is 12h with am pm, date-only stop is null`() {
    val out = stopStatuses(
      listOf(s("2026-08-24T14:05:00-04:00"), s("2026-08-24T00:00:00-04:00"), s("2026-08-24")),
      "2026-08-24T10:40:00-04:00", ny,
    )
    assertEquals("2:05 PM", out[0].timeLabel)
    assertEquals("12:00 AM", out[1].timeLabel)
    assertEquals(null, out[2].timeLabel)   // date-only → no time
  }

  @Test fun `timeLabel honors injected tz over the authored offset`() {
    // authored in UTC, timeline tz is NY: 11:00Z == 7:00 AM local
    val out = stopStatuses(listOf(s("2026-08-24T11:00:00Z")), "2026-08-24T10:40:00-04:00", ny)
    assertEquals("7:00 AM", out[0].timeLabel)
  }

  @Test fun `dateLabel and tile month day are tz-aware`() {
    // 02:00Z Aug 24 == 22:00 Aug 23 in NY → the local date is the 23rd
    val out = stopStatuses(listOf(s("2026-08-24T02:00:00Z"), s("2026-08-24")), "2026-08-01", ny)
    assertEquals("Aug 23", out[0].dateLabel)
    assertEquals("AUG", out[1].monthUpper)
    assertEquals("24", out[1].dayOfMonth)
    assertEquals("Aug 24", out[1].dateLabel)
  }

  @Test fun `unparseable at falls back to raw for dateLabel, null time`() {
    val out = stopStatuses(listOf(s("not-a-date")), "2026-08-24", ny)
    assertEquals(null, out[0].timeLabel)
    assertEquals("not-a-date", out[0].dateLabel)
  }
}
