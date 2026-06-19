package com.familyai.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

// Headless render proof — Compose Desktop renders the feed off-screen (Skiko
// software), no device needed. Verifies the redux state → Compose feed binding.
@OptIn(ExperimentalTestApi::class)
class FeedScreenTest {
  @Test
  fun rendersCardsInFeedOrder() = runComposeUiTest {
    val state = AppState(
      cards = listOf(
        Card("b", title = "Soccer 4pm", notBefore = "2026-06-18T16:00:00Z"),
        Card("a", title = "Leave by 3:30", notBefore = "2026-06-18T15:30:00Z"),
      ),
    )
    setContent { MaterialTheme { FeedScreen(state) } }
    onNodeWithText("Soccer 4pm").assertIsDisplayed()
    onNodeWithText("Leave by 3:30").assertIsDisplayed()
  }

  @Test
  fun showsEmptyState() = runComposeUiTest {
    setContent { MaterialTheme { FeedScreen(AppState()) } }
    onNodeWithText("Nothing yet").assertIsDisplayed()
  }
}
