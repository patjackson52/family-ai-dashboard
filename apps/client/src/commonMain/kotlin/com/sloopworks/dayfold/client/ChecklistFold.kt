package com.sloopworks.dayfold.client

import kotlin.time.Instant

// Slice 4 (ADR 0038 §4) — the PURE burst-fold presentation model for an interactive
// checklist. The done-triple in the DB is the source of truth; THIS layer decides only
// *timing*: a freshly-toggled row stays in the live list (struck) through one shared
// burst debounce, then the whole batch folds together into a collapsed "N done" section.
// Kept pure (no Compose, no clock) so the fold behaviour is testable headlessly; the
// composable wires the ~2s debounce + touch-end gate on top.

/**
 * The transient set of item ids checked *this burst* that haven't folded yet. An item
 * in [checking] is rendered live-and-struck (never yanked from under a finger); the burst
 * timer clears the set so the batch folds together. Immutable — every transition returns
 * a new value so it drops straight into `remember { mutableStateOf(...) }`.
 */
data class ChecklistFold(val checking: Set<String> = emptySet()) {
  /** A tap committed: a done toggle joins the current burst; an un-check leaves it. */
  fun toggled(id: String, nowDone: Boolean): ChecklistFold =
    if (nowDone) ChecklistFold(checking + id) else ChecklistFold(checking - id)

  /** The burst debounce elapsed (and touch ended): fold the whole batch at once. */
  fun burstFolded(): ChecklistFold = ChecklistFold(emptySet())
}

/** Pure partition + collapse rules over a checklist's items given the live burst set. */
object ChecklistFoldView {
  /** Past this many done items the "N done" line is count-only (no expand) — stays calm. */
  const val DONE_COLLAPSE_THRESHOLD = 20

  /** Rows shown in the live list: not-done, OR done-but-still-in-this-burst (struck). */
  fun activeItems(items: List<ChecklistItem>, checking: Set<String>): List<ChecklistItem> =
    items.filter { !it.done || it.id in checking }

  /**
   * Rows folded into "N done": done and settled (out of the burst), newest-completion first.
   * Sort by the PARSED instant, not the raw string — kotlinx `Instant.toString()` trims
   * trailing-zero fractions, so lexicographic order isn't chronological across mixed precision
   * (same bug fixed in ChecklistMerge). null/unparseable `doneAt` sorts last (DISTANT_PAST).
   */
  fun doneItems(items: List<ChecklistItem>, checking: Set<String>): List<ChecklistItem> =
    items.filter { it.done && it.id !in checking }
      .sortedByDescending { it.doneAt?.let { s -> runCatching { Instant.parse(s) }.getOrNull() } ?: Instant.DISTANT_PAST }

  /** Whether the done section is a count-only line (past the calm threshold). */
  fun doneCollapsedOnly(doneCount: Int): Boolean = doneCount > DONE_COLLAPSE_THRESHOLD
}
