package dev.falcop.betterwatchcalendar.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import dev.falcop.betterwatchcalendar.shared.DetailContent
import dev.falcop.betterwatchcalendar.shared.MeetingCard
import dev.falcop.betterwatchcalendar.shared.SyncFooter
import dev.falcop.betterwatchcalendar.shared.WAITING_FOR_PHONE

/**
 * The detail view opened from the complication: the shown and following meeting, then the sync
 * footer. Each meeting is split into several list items (header card, description, attendees) so
 * long descriptions scroll smoothly and the edge transformations stay item-sized.
 */
@Composable
fun DetailScreen(content: DetailContent) {
  MaterialTheme {
    AppScaffold {
      val listState = rememberTransformingLazyColumnState()
      val spec = rememberTransformationSpec()
      ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
          state = listState,
          contentPadding = contentPadding,
          modifier = Modifier.fillMaxWidth(),
        ) {
          when (content) {
            DetailContent.WaitingForPhone -> message(WAITING_FOR_PHONE, spec)
            is DetailContent.Schedule -> {
              content.emptyMessage?.let { message(it, spec) }
              content.cards.forEach { meeting(it, spec) }
              footer(content.footer, spec)
            }
          }
        }
      }
    }
  }
}

private fun TransformingLazyColumnScope.message(text: String, spec: TransformationSpec) {
  item("message") {
    Text(
      text,
      modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).padding(top = 8.dp),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyLarge,
    )
  }
}

private fun TransformingLazyColumnScope.meeting(card: MeetingCard, spec: TransformationSpec) {
  item("${card.id}/header") { MeetingHeader(card, spec) }
  if (card.description.isNotEmpty()) {
    item("${card.id}/description") {
      Text(
        card.description,
        modifier = Modifier.bodyItem(this, spec),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
  if (card.attendees.isNotEmpty() || card.moreAttendees != null) {
    item("${card.id}/attendees-header") {
      ListHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
      ) {
        Text("Attendees")
      }
    }
    // Names can repeat (two people with the same display name), so key by position.
    card.attendees.forEachIndexed { i, name ->
      item("${card.id}/attendee/$i") { Attendee(name, spec) }
    }
    card.moreAttendees?.let { more -> item("${card.id}/more") { Attendee(more, spec) } }
  }
}

@Composable
private fun TransformingLazyColumnItemScope.MeetingHeader(
  card: MeetingCard,
  spec: TransformationSpec,
) {
  // Purely informational; Wear's Card has no non-clickable variant.
  Card(
    onClick = {},
    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
    transformation = SurfaceTransformation(spec),
  ) {
    Text(
      card.title,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.titleMedium,
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(card.time, style = MaterialTheme.typography.bodyMedium)
      if (card.isCurrent) NowBadge()
    }
    if (card.room.isNotEmpty()) {
      Text(
        card.room,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun NowBadge() {
  Text(
    "now",
    modifier =
      Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
        .padding(horizontal = 6.dp),
    color = MaterialTheme.colorScheme.onPrimary,
    style = MaterialTheme.typography.labelSmall,
  )
}

@Composable
private fun TransformingLazyColumnItemScope.Attendee(name: String, spec: TransformationSpec) {
  Text(
    name,
    modifier = Modifier.bodyItem(this, spec),
    // Two lines, so the long addresses of attendees without a display name still fit.
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

private fun TransformingLazyColumnScope.footer(footer: SyncFooter, spec: TransformationSpec) {
  item("footer") {
    Text(
      listOfNotNull(footer.label, footer.staleWarning).joinToString("\n"),
      modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).padding(top = 8.dp),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.labelSmall,
      color =
        if (footer.staleWarning != null) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun Modifier.bodyItem(scope: TransformingLazyColumnItemScope, spec: TransformationSpec) =
  fillMaxWidth().transformedHeight(scope, spec).padding(horizontal = 12.dp)
