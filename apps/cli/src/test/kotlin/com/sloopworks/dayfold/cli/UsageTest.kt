package com.sloopworks.dayfold.cli

import kotlin.test.Test
import kotlin.test.assertTrue

// `dayfold help` / -h / --help prints USAGE to stdout + exits 0 (help is not an error);
// misuse prints it to stderr + exits 2. The text must list every command.
class UsageTest {
  @Test fun `USAGE lists the commands incl help`() {
    listOf("login", "logout", "whoami", "push", "pull", "template", "version", "help")
      .forEach { assertTrue(USAGE.contains(it), "USAGE missing \"$it\"") }
    assertTrue(USAGE.startsWith("usage: dayfold"))
  }
}
