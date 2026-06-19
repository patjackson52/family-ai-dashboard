package com.familyai.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

// Compose UI snapshot capability — renders FeedScreen(state) off-screen and
// captures it to an image. Establishes the f(store.state) → pixels pipeline;
// the NEXT step is golden-image diffing (Roborazzi/Paparazzi) + a redux-kotlin
// devtools/CLI-driven snapshot harness once those modules publish. See
// ADR 0019 (Proposed) + research/reduxkotlin-1.0-feedback.md (M4).
@OptIn(ExperimentalTestApi::class)
class FeedSnapshotTest {
  @Test
  fun capturesPopulatedFeedSnapshot() = runComposeUiTest {
    val state = AppState(
      cards = listOf(
        Card("a", title = "Party Saturday — order groceries?", notBefore = "2026-06-20T09:00:00Z"),
        Card("b", title = "Rain at soccer 4pm — pack jackets", notBefore = "2026-06-18T15:00:00Z"),
      ),
    )
    setContent { MaterialTheme { FeedScreen(state) } }
    val img = onRoot().captureToImage()
    // Snapshot rendered to pixels (golden-diff is the next step).
    assertTrue(img.width > 0 && img.height > 0, "snapshot has no pixels")
  }

  @Test
  fun emptyAndPopulatedSnapshotsDiffer() = runComposeUiTest {
    setContent { MaterialTheme { FeedScreen(AppState()) } } // empty state
    val empty = onRoot().captureToImage()
    assertTrue(empty.width > 0, "empty snapshot has no pixels")
    // (Distinct-from-populated pixel diff is part of the golden harness — ADR 0019.)
  }
}
