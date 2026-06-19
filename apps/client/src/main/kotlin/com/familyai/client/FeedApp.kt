package com.familyai.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.reduxkotlin.Store
import org.reduxkotlin.compose.selectorState

// f(store.state) -> UI via redux-kotlin-compose `store.selectorState { }` — a
// reactive Compose projection of the single state source (the whole AppState
// here; swap to per-field `fieldState`/narrower selectors to scope recomposition).
// Every shell (desktop, Android, iOS) renders this one connected composable.
@Composable
fun FeedApp(store: Store<AppState>) {
  val state by store.selectorState { it }
  FeedScreen(state)
}
