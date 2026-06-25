package com.sloopworks.dayfold.cli

import com.sloopworks.dayfold.schema.BlockType
import com.sloopworks.dayfold.schema.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Fast pre-check for push --hub/--section/--block bodies (server stays the authority).
class HubTreeValidateTest {
  private fun ok(errs: List<String>) = assertTrue(errs.isEmpty(), "expected valid, got: $errs")
  private fun bad(errs: List<String>, needle: String) =
    assertTrue(errs.any { it.contains(needle) }, "expected an error mentioning \"$needle\", got: $errs")

  @Test fun `valid hub passes, bad type or status or missing title fail`() {
    ok(validateHubTree("hubs", """{"type":"party-event","title":"Maya's birthday","status":"planning"}"""))
    bad(validateHubTree("hubs", """{"type":"party-event"}"""), "title")
    bad(validateHubTree("hubs", """{"type":"birthday-bash","title":"x"}"""), "catalog key")
    bad(validateHubTree("hubs", """{"type":"medical","title":"x","status":"pending"}"""), "status")
  }

  @Test fun `section requires hubId`() {
    ok(validateHubTree("sections", """{"hubId":"h1","title":"Shopping","ord":0}"""))
    bad(validateHubTree("sections", """{"title":"Shopping"}"""), "hubId")
  }

  @Test fun `block requires sectionId and a valid type (catches typos)`() {
    ok(validateHubTree("blocks", """{"sectionId":"s1","type":"checklist","ord":0}"""))
    bad(validateHubTree("blocks", """{"sectionId":"s1","type":"checlist"}"""), "checlist")   // typo caught
    bad(validateHubTree("blocks", """{"type":"text"}"""), "sectionId")
  }

  @Test fun `malformed JSON is reported, not thrown`() {
    bad(validateHubTree("hubs", """{not json"""), "invalid hubs JSON")
  }

  // The block-type + hub-status accept-lists are DERIVED from the generated schema
  // enums (Content.kt). This locks that derivation: every value the schema declares
  // must validate. If the schema adds/removes a value, this passes automatically —
  // and it fails loudly if anyone re-hardcodes the lists and misses one (drift).
  @Test fun `every generated BlockType and hub Status is accepted (no schema drift)`() {
    for (t in BlockType.entries)
      ok(validateHubTree("blocks", """{"sectionId":"s1","type":"${t.value}"}"""))
    for (s in Status.entries)
      ok(validateHubTree("hubs", """{"type":"party-event","title":"x","status":"${s.value}"}"""))
  }
}
