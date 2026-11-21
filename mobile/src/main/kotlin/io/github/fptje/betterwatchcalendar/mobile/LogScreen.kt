package io.github.fptje.betterwatchcalendar.mobile

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val KB = 1024.0

/** The last [LOG_ENTRIES] sync attempts, newest first (SPEC P8). */
@Composable
fun LogScreen() {
  val context = LocalContext.current
  val entries by context.graph.log.entries.collectAsStateWithLifecycle(initialValue = null)
  val formats = remember { LogFormats(ZoneId.systemDefault(), DateFormat.is24HourFormat(context)) }

  val list = entries ?: return
  if (list.isEmpty()) {
    Message("No syncs yet.")
    return
  }
  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    itemsIndexed(list, key = { _, e -> "${e.at}:${e.trigger}" }) { index, entry ->
      LogCard(entry, formats, initiallyExpanded = index == 0)
    }
  }
}

private class LogFormats(zone: ZoneId, is24Hour: Boolean) {
  private val clock = if (is24Hour) "HH:mm" else "h:mm"
  val stamp: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM $clock:ss", Locale.getDefault()).withZone(zone)
  val meetingStart: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE $clock", Locale.getDefault()).withZone(zone)
  val meetingEnd: DateTimeFormatter =
    DateTimeFormatter.ofPattern(clock, Locale.getDefault()).withZone(zone)
}

@Composable
private fun LogCard(entry: SyncLogEntry, formats: LogFormats, initiallyExpanded: Boolean) {
  var expanded by rememberSaveable(entry.at) { mutableStateOf(initiallyExpanded) }
  val canExpand = entry.meetings.isNotEmpty()
  Card(Modifier.fillMaxWidth()) {
    Column(
      Modifier.clickable(enabled = canExpand) { expanded = !expanded }.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        "${formats.stamp.format(Instant.ofEpochMilli(entry.at))} · ${entry.trigger}",
        fontWeight = FontWeight.Bold,
      )
      Text(
        listOfNotNull(
            entry.payloadBytes?.let { "%.1f KB sent".format(Locale.ROOT, it / KB) },
            "${entry.durationMs} ms",
          )
          .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
      )
      entry.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      entry.accounts.forEach { Text("${it.account}: ${plural(it.meetings, "meeting")}") }
      skippedSummary(entry)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      if (canExpand) {
        Text(
          (if (expanded) "Hide " else "Show ") + plural(entry.meetings.size, "meeting") + " sent",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      if (expanded) entry.meetings.forEach { LoggedMeetingRow(it, formats) }
    }
  }
}

private fun skippedSummary(entry: SyncLogEntry): String? =
  (entry.skipped.entries.sortedBy { it.key }.map { "${it.value} ${it.key}" } +
      listOfNotNull(entry.duplicates.takeIf { it > 0 }?.let { "$it Duplicate" }))
    .takeIf { it.isNotEmpty() }
    ?.joinToString(", ", prefix = "Skipped: ")

@Composable
private fun LoggedMeetingRow(meeting: LoggedMeeting, formats: LogFormats) {
  Column(Modifier.padding(top = 6.dp)) {
    val start = formats.meetingStart.format(Instant.ofEpochMilli(meeting.start))
    val end = formats.meetingEnd.format(Instant.ofEpochMilli(meeting.end))
    Text("$start–$end  ${meeting.title}", fontWeight = FontWeight.Medium)
    Text(
      listOfNotNull(
          meeting.room.ifEmpty { null },
          if (meeting.attendees == 1) "1 person" else "${meeting.attendees} people",
          "${meeting.descriptionChars} chars",
          meeting.account,
        )
        .joinToString(" · "),
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
