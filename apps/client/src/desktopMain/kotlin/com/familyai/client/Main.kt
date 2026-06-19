package com.familyai.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// Desktop shell — lets the operator preview the feed on a laptop (no device).
// UI = f(store.state) via FeedApp; the shell only owns the store + the sync
// effect. The Android/iOS shells do the same.
fun main() = application {
  val store = remember { createAppStore() }
  LaunchedEffect(Unit) {
    val api = System.getenv("FAMILYAI_API")
    val fam = System.getenv("FAMILY_ID")
    val sec = System.getenv("HOUSEHOLD_SECRET")
    if (api != null && fam != null && sec != null) {
      SyncClient(api, fam, sec).sync(store)
    }
  }
  Window(onCloseRequest = ::exitApplication, title = "family-ai-dashboard") {
    MaterialTheme { FeedApp(store) }
  }
}
