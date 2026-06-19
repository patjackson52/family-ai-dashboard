package com.familyai.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.reduxkotlin.Store

// f(store.state) -> UI. The store is the single state source; the feed is a
// pure projection of it. Every client shell (desktop, Android, iOS) renders
// this one connected composable — they own only the store + the sync effect.
//
// NOTE: this is the hand-rolled store→Compose binding. Swap the body for
// redux-kotlin-compose `selectorState(store){…}` / `fieldState` once that module
// is republished with Kotlin-2.2-compatible metadata (alpha01 ships 2.3 metadata
// that 2.2.20 can't read) — the FeedApp boundary stays identical.
@Composable
fun FeedApp(store: Store<AppState>) {
  var state by remember { mutableStateOf(store.state) }
  DisposableEffect(store) {
    val unsub = store.subscribe { state = store.state }
    onDispose { unsub() }
  }
  FeedScreen(state)
}
