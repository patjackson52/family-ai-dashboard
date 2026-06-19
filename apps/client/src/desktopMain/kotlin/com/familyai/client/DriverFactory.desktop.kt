package com.familyai.client

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.familyai.client.db.ContentDb

actual class DriverFactory {
  actual fun createDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ContentDb.Schema.create(it) }
}
