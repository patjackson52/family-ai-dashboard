package com.sloopworks.dayfold.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardRenderTest {
  @Test fun `allowlisted link is tappable`() {
    assertTrue(hasActionLinks("Party Sat — [order groceries](https://instacart.com)?"))
    assertTrue(hasActionLinks("[reply](mailto:school@x.edu) by Thu"))
  }

  @Test fun `disallowed scheme is dropped (xss-safe)`() {
    assertFalse(hasActionLinks("[x](javascript:alert(1))"))
    assertFalse(hasActionLinks("[x](file:///etc/passwd)"))
  }

  @Test fun `body text inlines the label, not the url`() {
    val out = renderCardBody("Tap [list](https://store) now")
    assertEquals("Tap list now", out.text) // label kept; url not in visible text
  }

  // hasActionLinks (above) reports the disallowed case; this locks the ACTUAL render
  // path — a javascript:/file: link must render as PLAIN TEXT with no clickable link
  // annotation (xss-safe), while an allowlisted link stays clickable.
  @Test fun `renderCardBody strips a disallowed-scheme link to plain text (no link annotation)`() {
    val bad = renderCardBody("Tap [x](file:///etc/passwd) now")
    assertEquals("Tap x now", bad.text)                                  // label kept as text
    assertTrue(bad.getLinkAnnotations(0, bad.length).isEmpty())          // NOT tappable
    val ok = renderCardBody("Tap [list](https://store) now")
    assertEquals(1, ok.getLinkAnnotations(0, ok.length).size)            // allowlisted → tappable
  }

  @Test fun `kind + source labels`() {
    assertEquals("Action", kindLabel("action"))
    assertNull(kindLabel("info"))
    assertEquals("Added by Claude", sourceLabel("claude"))
    assertEquals("From your email", sourceLabel("email"))
    assertNull(sourceLabel("user"))
    assertNull(sourceLabel(null))
  }
}
