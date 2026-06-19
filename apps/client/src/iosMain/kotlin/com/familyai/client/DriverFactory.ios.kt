package com.familyai.client

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.familyai.client.db.ContentDb

actual class DriverFactory {
  actual fun createDriver(): SqlDriver =
    NativeSqliteDriver(ContentDb.Schema, "content.db")
}
