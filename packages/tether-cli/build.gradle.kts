// SPIKE — extraction PoC. Proves dayfold's CLI auth lifts into a standalone,
// config-driven module with the dayfold specifics parameterized into TetherConfig.
// Mirrors apps/cli's toolchain so behavior is identical.

plugins {
  kotlin("jvm") version "2.3.20"
  kotlin("plugin.serialization") version "2.3.20"
  application
}

repositories { mavenCentral() }

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
  implementation("com.google.zxing:core:3.5.3")
  testImplementation(kotlin("test"))
}

// dayfold's apps/cli pins jvmToolchain(17); this spike targets 21 so it builds on
// the host JDK without toolchain auto-provisioning. Production adoption would match
// the consuming app's pin.
kotlin { jvmToolchain(21) }

application {
  mainClass.set("works.tether.cli.MainKt")
  applicationName = "tether"
}

tasks.test { useJUnitPlatform() }
