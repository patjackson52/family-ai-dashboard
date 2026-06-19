package com.familyai.client

import app.cash.sqldelight.db.SqlDriver
import com.familyai.client.db.ContentDb

// The local SQLDelight DB = the single source of truth (ADR 0020). The sync
// engine writes here; the UI projects from here. Driver is injected per platform
// (JdbcSqliteDriver desktop/test · AndroidSqliteDriver · NativeSqliteDriver iOS).
class ContentStore(driver: SqlDriver) {
  private val q = ContentDb(driver).contentQueries

  /** Apply one /sync page atomically: upsert changes, tombstone deletes, advance cursor. */
  fun applyDelta(changed: List<Card>, tombstoneIds: List<String>, nextCursor: String?, nowIso: String) {
    q.transaction {
      changed.forEach { c ->
        q.upsertCard(c.id, c.kind, c.title, c.bodyMd, c.provenance?.source, c.notBefore, c.expiresAt, nowIso)
      }
      tombstoneIds.forEach { q.markDeleted(nowIso, it) }
      if (nextCursor != null) q.setCursor(nextCursor, nowIso)
    }
  }

  /** Feed projection: live cards, not_before NULLS LAST then id (the API contract). */
  fun activeCards(): List<Card> = q.activeCards().executeAsList().map {
    Card(
      id = it.id, kind = it.kind, title = it.title, bodyMd = it.body_md,
      provenance = it.source?.let { s -> Provenance(s) },
      notBefore = it.not_before, expiresAt = it.expires_at,
    )
  }

  fun cursor(): String? = q.getCursor().executeAsOneOrNull()?.cursor

  companion object {
    fun create(driver: SqlDriver): ContentStore {
      ContentDb.Schema.create(driver)
      return ContentStore(driver)
    }
  }
}
