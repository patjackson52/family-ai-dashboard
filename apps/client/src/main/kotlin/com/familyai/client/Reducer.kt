package com.familyai.client

import org.reduxkotlin.Store
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.middleware
import org.reduxkotlin.threadsafe.createThreadSafeStore

// Hand-written root reducer (locked decision: no combineReducers). Applies a
// /sync delta: upsert changed cards by id (preserving order, newest wins),
// remove tombstoned ids, advance the cursor.
fun rootReducer(state: AppState, action: Any): AppState = when (action) {
  is SyncStarted -> state.copy(syncing = true, error = null)
  is SyncFailed -> state.copy(syncing = false, error = action.message)
  is SyncSucceeded -> {
    val byId = LinkedHashMap<String, Card>()
    state.cards.forEach { byId[it.id] = it }
    action.resp.changes.cards.forEach { byId[it.id] = it }            // upsert
    action.resp.tombstones.filter { it.type == "card" }
      .forEach { byId.remove(it.id) }                                  // delete
    state.copy(
      cards = byId.values.toList(),
      cursor = action.resp.nextCursor ?: state.cursor,
      syncing = false,
      error = null,
    )
  }
  else -> state
}

// Debug middleware — logs every action + the state delta. A stand-in for the
// redux-kotlin devtools module (not yet published); swap/augment with the real
// devtools enhancer when it ships. Gated off in release builds via `debug`.
private val loggingMiddleware = middleware<AppState> { store, next, action ->
  val before = store.state
  val result = next(action)
  val after = store.state
  println("[redux] ${action::class.simpleName}: cards ${before.cards.size}→${after.cards.size}, syncing=${after.syncing}, error=${after.error}")
  result
}

// [F5] thread-safe store: the SyncClient effect dispatches from Dispatchers.IO
// while the Compose UI reads on main — needs synchronized dispatch.
fun createAppStore(initial: AppState = AppState(), debug: Boolean = true): Store<AppState> =
  if (debug) createThreadSafeStore(::rootReducer, initial, applyMiddleware(loggingMiddleware))
  else createThreadSafeStore(::rootReducer, initial)
