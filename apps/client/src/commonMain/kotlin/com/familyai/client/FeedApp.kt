package com.familyai.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.familyai.client.cards.CardAction
import com.familyai.client.cards.DetailScreen
import com.familyai.client.theme.DayfoldTheme
import org.reduxkotlin.Store
import org.reduxkotlin.compose.selectorState

// f(store.state) -> UI via redux-kotlin-compose `store.selectorState { }` — a
// reactive Compose projection of the single state source (the whole AppState
// here; swap to per-field `fieldState`/narrower selectors to scope recomposition).
// Every shell (desktop, Android, iOS) renders this one connected composable,
// wrapped once in the Dayfold theme (ADR 0022 D5).
@Composable
fun FeedApp(store: Store<AppState>, onPlatformAction: (CardAction) -> Unit = {}) {
  val state by store.selectorState { it }
  // One stable handler (remembered so feed/detail stay skippable): OpenDetail is
  // in-app nav → dispatched to the store; every other CardAction is an OS handoff
  // → the shell's PlatformActions. NOTE: the whole-state `selectorState { it }`
  // subscription is the pre-existing M0 pattern; scoping it (feedCards vs
  // currentDetailCard) to shrink recomposition is a tracked perf follow.
  val handle = remember(store, onPlatformAction) {
    fun(action: CardAction) { // anonymous fun → Unit return (dispatch returns the action)
      if (action is CardAction.OpenDetail) store.dispatch(NavToDetail(action.cardId))
      else onPlatformAction(action)
    }
  }
  DayfoldTheme {
    val detail = currentDetailCard(state)
    if (detail != null) DetailScreen(detail, onBack = { store.dispatch(NavBack) }, onAction = handle)
    else FeedScreen(state, onAction = handle)
  }
}
