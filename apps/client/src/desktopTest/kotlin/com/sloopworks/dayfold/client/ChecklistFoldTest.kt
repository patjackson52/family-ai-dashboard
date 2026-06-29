package com.sloopworks.dayfold.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Slice 4 (ADR 0038 §4) — the PURE burst-fold presentation model. The check IS a fold:
// a freshly-checked row stays in the live list (struck) through a single shared burst
// debounce, then the whole batch folds together into the collapsed "N done" section
// (sorted newest-first, count-only past ~20). Un-check returns a row to active. Items
// that are already done when first observed render folded immediately (no burst).
class ChecklistFoldTest {
  private fun item(id: String?, text: String, done: Boolean = false, doneBy: String? = null, doneAt: String? = null) =
    ChecklistItem(id = id, text = text, done = done, doneBy = doneBy, doneAt = doneAt)

  // ── the checking set transitions ────────────────────────────────────────────
  @Test fun `toggling an item done puts it in the checking set`() {
    val fold = ChecklistFold().toggled("a", nowDone = true)
    assertTrue("a" in fold.checking)
  }

  @Test fun `un-checking an item removes it from the checking set`() {
    val fold = ChecklistFold().toggled("a", nowDone = true).toggled("a", nowDone = false)
    assertFalse("a" in fold.checking)
  }

  @Test fun `the burst fold clears the whole checking batch at once`() {
    val fold = ChecklistFold().toggled("a", true).toggled("b", true).burstFolded()
    assertTrue(fold.checking.isEmpty())
  }

  // ── the partition (active list vs the folded "N done" section) ────────────────
  @Test fun `an already-done item with an empty burst is folded immediately`() {
    val items = listOf(item("a", "Pack", done = true), item("b", "Buy", done = false))
    val checking = emptySet<String>()
    assertEquals(listOf("Buy"), ChecklistFoldView.activeItems(items, checking).map { it.text })
    assertEquals(listOf("Pack"), ChecklistFoldView.doneItems(items, checking).map { it.text })
  }

  @Test fun `a freshly-checked item stays in the active list until the burst folds`() {
    val items = listOf(item("a", "Pack", done = true))
    val checking = setOf("a")                                   // just tapped, burst not elapsed
    assertEquals(1, ChecklistFoldView.activeItems(items, checking).size)   // still live, struck
    assertTrue(ChecklistFoldView.doneItems(items, checking).isEmpty())     // not folded yet
  }

  @Test fun `after the burst the freshly-checked item folds into done`() {
    val items = listOf(item("a", "Pack", done = true))
    val checking = emptySet<String>()                          // burst elapsed → checking cleared
    assertTrue(ChecklistFoldView.activeItems(items, checking).isEmpty())
    assertEquals(1, ChecklistFoldView.doneItems(items, checking).size)
  }

  @Test fun `an undone item is always in the active list`() {
    val items = listOf(item("a", "Buy", done = false))
    assertEquals(1, ChecklistFoldView.activeItems(items, emptySet()).size)
    assertTrue(ChecklistFoldView.doneItems(items, emptySet()).isEmpty())
  }

  // ── the "N done" section ordering + collapse ─────────────────────────────────
  @Test fun `done items sort by completion time newest-first`() {
    val items = listOf(
      item("a", "First", done = true, doneAt = "2026-06-29T10:00:00Z"),
      item("b", "Second", done = true, doneAt = "2026-06-29T10:05:00Z"),
      item("c", "Third", done = true, doneAt = "2026-06-29T10:03:00Z"),
    )
    val order = ChecklistFoldView.doneItems(items, emptySet()).map { it.text }
    assertEquals(listOf("Second", "Third", "First"), order)
  }

  @Test fun `done items sort newest-first across variable fractional precision`() {
    // Same bug class as ChecklistMerge (#250): lexicographic order != chronological once
    // kotlinx Instant.toString() trims trailing-zero fraction groups. ".123Z" is EARLIER than
    // ".123000001Z" yet sorts LATER lexically ("Z" > "0"). Cross-device done stamps (Android ms
    // vs desktop/iOS ns) hit this; newest-first must be chronological, not string order.
    val items = listOf(
      item("a", "Earlier", done = true, doneAt = "2026-06-29T10:00:00.123Z"),
      item("b", "Later", done = true, doneAt = "2026-06-29T10:00:00.123000001Z"),   // 1 ns later
    )
    val order = ChecklistFoldView.doneItems(items, emptySet()).map { it.text }
    assertEquals(listOf("Later", "Earlier"), order)   // chronologically-newest first
  }

  @Test fun `the done section collapses to a count-only line past the threshold`() {
    assertFalse(ChecklistFoldView.doneCollapsedOnly(20))
    assertTrue(ChecklistFoldView.doneCollapsedOnly(21))
  }

  @Test fun `partition is exhaustive and disjoint across all states`() {
    val items = listOf(
      item("a", "done-folded", done = true),                   // done, not checking → done section
      item("b", "done-checking", done = true),                 // done, checking → active (struck)
      item("c", "undone", done = false),                       // active
    )
    val checking = setOf("b")
    val active = ChecklistFoldView.activeItems(items, checking).map { it.text }.toSet()
    val done = ChecklistFoldView.doneItems(items, checking).map { it.text }.toSet()
    assertEquals(setOf("done-checking", "undone"), active)
    assertEquals(setOf("done-folded"), done)
    assertTrue((active intersect done).isEmpty())              // disjoint
    assertEquals(3, active.size + done.size)                   // exhaustive
  }

  @Test fun `a done item with a null id still folds into the done section`() {
    val items = listOf(item(null, "legacy-done", done = true))
    assertEquals(1, ChecklistFoldView.doneItems(items, emptySet()).size)
    assertTrue(ChecklistFoldView.activeItems(items, emptySet()).isEmpty())
  }
}
