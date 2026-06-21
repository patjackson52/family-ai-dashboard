package com.familyai.client.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyai.client.Card

// CL-6 — full-screen per-type detail (mockup designs/content/Detail-Phone.dc.html).
// Colored hero header + per-type hero media + safe actions row + DETAILS list +
// provenance/privacy. Read-only (ADR 0020): every action is an OS handoff via the
// vetted CardAction seam (CL-PLAT). RELATED rows = CL-8; the container-transform =
// CL-7 (M0 is a plain feed↔detail swap). Reuses the CL-5 chrome (internal).

@Composable
fun DetailScreen(card: Card, onBack: () -> Unit, onAction: (CardAction) -> Unit) {
  // M0: back is the on-screen "← Back" only. HARDWARE/gesture back (Android) +
  // interactive-pop (iOS) → NavBack is deferred to CL-7, which wires
  // PredictiveBackHandler (present at Compose-MP 1.9.3; the `ui` module has no
  // plain BackHandler composable at this version). Tracked as a CL-7 prerequisite.
  val accent = accentFor(card.type)
  val (heroBg, heroFg) = accentColors(accent, solid = false) // container + onContainer (AA)
  Column(Modifier.fillMaxSize()) {
    HeroHeader(card, accent, heroBg, heroFg, onBack, onAction)
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item { HeroMedia(card, onAction) }
      item { ActionsRow(detailActions(card), onAction) }
      detailMeta(card).takeIf { it.isNotEmpty() }?.let { rows -> item { DetailsCard(rows) } }
      item { ProvenancePrivacy(card) }
    }
  }
}

@Composable
private fun HeroHeader(
  card: Card, accent: CardAccent, heroBg: androidx.compose.ui.graphics.Color,
  heroFg: androidx.compose.ui.graphics.Color, onBack: () -> Unit, onAction: (CardAction) -> Unit,
) {
  Column(Modifier.fillMaxWidth().background(heroBg).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 18.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back to feed" }) {
        Text("← Back", color = heroFg)
      }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = { onAction(CardAction.Share(card.title)) }) { Text("Share", color = heroFg) }
    }
    Row(Modifier.padding(start = 4.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      AccentTile(typeMonogram(card), accent, solid = true) // solid → contrasts on the container hero bg
      KickerChip(kickerFor(card), accent, solid = true)
    }
    Text(
      card.title, color = heroFg, style = MaterialTheme.typography.headlineSmall,
      maxLines = 3, overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
  }
}

private fun typeMonogram(card: Card): String = when (card.type) {
  "file" -> "F"; "link" -> "L"; "invite" -> "!"; "contact" -> "C"; "geo" -> "G"; "email" -> "@"; else -> "•"
}

// ── per-type hero media ───────────────────────────────────────────────────────

@Composable
private fun HeroMedia(card: Card, onAction: (CardAction) -> Unit) {
  val p = card.payload ?: return
  when (card.type) {
    "file" -> InfoPanel(listOfNotNull(p.file?.filename, p.file?.pages?.let { "$it pages" }, p.file?.mime))
    "link" -> InfoPanel(listOfNotNull(p.link?.domain, p.link?.title, p.link?.ogDesc))
    "invite" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      InfoPanel(listOfNotNull(p.invite?.startAt, p.invite?.place, p.invite?.host))
      RsvpDisplayRow(p.invite?.rsvpState)
    }
    "contact" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      InfoPanel(listOfNotNull(p.contact?.name, p.contact?.role, p.contact?.company))
      ContactReachRow(card, onAction)
    }
    "geo" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      MapStrip()
      InfoPanel(listOfNotNull(p.geo?.label, p.geo?.etaMin?.let { "$it min away" }, p.geo?.address))
    }
    "email" -> InfoPanel(listOfNotNull(
      p.email?.from, p.email?.bodyExcerpt,
      p.email?.attachments?.takeIf { it.isNotEmpty() }?.let { "${it.size} attachment(s)" },
    ))
    else -> {}
  }
}

/** A neutral surface panel of lines — the M0 hero-media placeholder (no async media,
 *  no fetch). Richer per-type media (PDF thumb, real map) are CL-9/later follows. */
@Composable
private fun InfoPanel(lines: List<String>) {
  if (lines.isEmpty()) return
  Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      lines.forEachIndexed { i, line ->
        Text(
          line,
          style = if (i == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
          color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ContactReachRow(card: Card, onAction: (CardAction) -> Unit) {
  val c = card.payload?.contact ?: return
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    c.phone?.let { p ->
      OutlinedButton(onClick = { onAction(CardAction.Call(p)) }, modifier = Modifier.heightIn48()) { Text("Call") }
      OutlinedButton(onClick = { onAction(CardAction.Message(p)) }, modifier = Modifier.heightIn48()) { Text("Text") }
    }
    c.email?.let { e ->
      OutlinedButton(onClick = { onAction(CardAction.Email("mailto:$e")) }, modifier = Modifier.heightIn48()) { Text("Email") }
    }
  }
}

// ── actions row + details + provenance/privacy ────────────────────────────────

@Composable
private fun ActionsRow(actions: List<DetailAction>, onAction: (CardAction) -> Unit) {
  if (actions.isEmpty()) return
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    actions.forEach { a ->
      when (a.style) {
        ActionStyle.Filled -> Button(onClick = { onAction(a.action) }, modifier = Modifier.heightIn48()) { Text(a.label) }
        ActionStyle.Tonal -> FilledTonalButton(onClick = { onAction(a.action) }, modifier = Modifier.heightIn48()) { Text(a.label) }
        ActionStyle.Outlined -> OutlinedButton(onClick = { onAction(a.action) }, modifier = Modifier.heightIn48()) { Text(a.label) }
      }
    }
  }
}

@Composable
private fun DetailsCard(rows: List<MetaRow>) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("DETAILS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(horizontal = 16.dp)) {
        rows.forEachIndexed { i, row ->
          Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(row.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
            Text(row.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
          }
          if (i < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
      }
    }
  }
}

@Composable
private fun ProvenancePrivacy(card: Card) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
    ProvenanceChip(card.provenance?.source)
    PrivacyChip(card.privacy?.storage)
  }
}

// 48dp min touch target (WCAG-AA).
private fun Modifier.heightIn48(): Modifier = this.heightIn(min = 48.dp)
