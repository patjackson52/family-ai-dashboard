package com.familyai.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

// iOS entry — hosts the SHARED FeedApp in a ComposeUIViewController for a Swift
// @main app to embed (like the desktop/Android shells). The sync effect + config
// plumbing (api/family/secret, the iOS analogue of Android BuildConfig) is the
// TASK-SYNC iOS step; this just proves the shared UI renders from the framework.
fun MainViewController(): UIViewController = ComposeUIViewController {
  val store = remember { createAppStore() }
  MaterialTheme { FeedApp(store) }
}
