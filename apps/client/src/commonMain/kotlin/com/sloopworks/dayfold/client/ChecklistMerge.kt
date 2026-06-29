package com.sloopworks.dayfold.client

import kotlin.time.Instant

// ADR 0038 §5 — the client-side merge that makes two-way checklists converge without
// the server ever reading the payload. PURE + deterministic: same inputs → same output,
// so two devices that exchange the same writes reach the same state regardless of order.
//
// The rules (§5.1 / §5.6):
//   * Membership + loop-authoritative fields (text/due/assignee/ord) → TAKE REMOTE. The
//     loop owns the item set and its non-`done` fields; members don't write them at M0.
//   * The member-mutable done-triple (done/doneBy/doneAt) → per-item STRICT LWW keyed by
//     the stable item `id`, compared by (doneAt, doneBy). Latest tap wins; un-check is
//     just a newer stamp (no done-wins bias). A tie breaks deterministically on doneBy.
//
// Idempotent by construction: re-applying your own already-applied stamp is a no-op
// (the local and remote done-triples are equal → remote is kept), which is what makes an
// unsuppressed /sync echo unable to flicker the value (§5.5).
object ChecklistMerge {

  /** Merge a local checklist payload against the remote (loop-authoritative) one. */
  fun mergeItems(local: List<ChecklistItem>, remote: List<ChecklistItem>): List<ChecklistItem> {
    if (local.isEmpty()) return remote
    val localById = HashMap<String, ChecklistItem>(local.size)
    for (it in local) it.id?.let { id -> localById[id] = it }
    return remote.map { r ->
      val id = r.id ?: return@map r                 // id-less remote item can't be matched → take remote
      val l = localById[id] ?: return@map r         // no local edit → remote as-is
      if (localDoneIsNewer(l, r)) {
        // keep all of remote's loop-authoritative fields; overlay only the done-triple
        r.copy(done = l.done, doneBy = l.doneBy, doneAt = l.doneAt)
      } else {
        r
      }
    }
  }

  /**
   * Merge two whole blocks. For a `checklist` block the item set is reconciled per the
   * rules above; every other block type is one-way → take remote unchanged. The returned
   * block is remote with (for checklists) the merged items overlaid — so the loop's
   * text/ord/structure always win while the member's toggles survive.
   */
  fun mergeBlock(local: HubBlock, remote: HubBlock): HubBlock {
    if (remote.type != "checklist") return remote
    val localItems = local.payload?.items ?: return remote
    val remoteItems = remote.payload?.items ?: return remote
    val merged = mergeItems(localItems, remoteItems)
    return remote.copy(payload = remote.payload.copy(items = merged))
  }

  // Strict LWW on the done-triple: is the LOCAL stamp newer than the REMOTE one?
  //   - no local stamp        → never newer (remote wins)
  //   - local stamp, no remote → local is newer
  //   - both                  → later doneAt wins; equal doneAt breaks on doneBy
  // Compare PARSED instants, not strings: kotlinx `Instant.toString()` trims trailing-zero
  // fractional groups, so lexicographic order is NOT chronological across mixed precision
  // (".123Z" sorts AFTER ".123000001Z" because "Z" > "0", yet it is 1 ns EARLIER). Cross-device
  // toggles — Android ms-clock vs desktop/iOS ns-clock — hit exactly this. Lexicographic is the
  // fallback only when a stamp is non-ISO (shouldn't happen: doneAt is a client now() stamp).
  private fun localDoneIsNewer(local: ChecklistItem, remote: ChecklistItem): Boolean {
    val la = local.doneAt ?: return false
    val ra = remote.doneAt ?: return true
    val li = runCatching { Instant.parse(la) }.getOrNull()
    val ri = runCatching { Instant.parse(ra) }.getOrNull()
    val cmp = if (li != null && ri != null) li.compareTo(ri) else la.compareTo(ra)
    return when {
      cmp > 0 -> true
      cmp < 0 -> false
      else -> (local.doneBy ?: "") > (remote.doneBy ?: "") // deterministic tiebreak on actor
    }
  }
}
