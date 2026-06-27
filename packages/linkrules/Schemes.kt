package com.sloopworks.dayfold.client

// SHARED (packages/linkrules, srcDir'd into client commonMain + the CLI). The ONE
// outbound-handoff scheme allowlist — the body-link renderer, the card action
// effect layer, AND the author-side linkifier all read this. Stdlib-only.
// `sms` is included per the contact-action finding; https only (never plain http).
val ALLOWED_SCHEMES = setOf("https", "mailto", "tel", "geo", "sms")
fun schemeOf(url: String) = url.trim().substringBefore(":", "").lowercase()
