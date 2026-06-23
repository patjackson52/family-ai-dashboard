package com.sloopworks.debugdrawer

/**
 * Top-level entry/seam for the debug drawer. The app reads its backend URL through
 * here so the Backend-switch panel can override it (persisted-override + restart).
 *
 * Foundation scaffold: [backendUrl] returns the default. Real override resolution
 * (cached observable state seeded from durable [DebugStore]) lands in Task 6 (R1/R2),
 * along with eager [install] decoupled from Compose.
 */
object DebugDrawer {
  fun backendUrl(default: String): String = default
}
