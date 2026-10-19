package dev.falcop.betterwatchcalendar.shared

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The system settings that affect how times are shown, read at the edge and passed in. */
data class TimeFormat(val zone: ZoneId, val locale: Locale, val is24Hour: Boolean) {
  /** Compact clock time: `10:30`, `14:05`, or `2:05` in 12-hour mode. */
  internal val clock: DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", locale).withZone(zone)

  /** Unambiguous clock time for screen readers: adds AM/PM in 12-hour mode. */
  internal val spokenClock: DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale).withZone(zone)

  internal val weekday: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", locale).withZone(zone)
  internal val fullWeekday: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE", locale).withZone(zone)

  internal fun date(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

  internal fun date(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()
}

/** One `SHORT_TEXT` complication state. */
data class ComplicationContent(
  /** Top line, about 7 characters. */
  val text: String,
  /** Bottom line; the watch face truncates it. */
  val title: String,
  val contentDescription: String,
)

const val NO_TIME = "—"

/** Complication content at [now]. A missing or expired snapshot reads "sync", never "free". */
fun complicationContent(
  snapshot: ScheduleSnapshot?,
  now: Instant,
  format: TimeFormat,
): ComplicationContent {
  if (snapshot == null || now.toEpochMilli() >= snapshot.windowEnd) {
    return ComplicationContent(NO_TIME, "sync", "Waiting for the phone to sync")
  }
  return when (val selection = select(snapshot.meetings, now)) {
    Selection.Free -> ComplicationContent(NO_TIME, "free", "No meetings in the next 7 days")
    is Selection.Current -> {
      val m = selection.meeting
      ComplicationContent(
        text = "now",
        title = m.title,
        contentDescription =
          "Current meeting: ${m.title}, started at ${format.spokenClock.at(m.start)}${m.inRoom()}",
      )
    }
    is Selection.Next -> {
      val m = selection.meeting
      val today = format.date(m.start) == format.date(now)
      val time = format.spokenClock.at(m.start)
      ComplicationContent(
        text = if (today) format.clock.at(m.start) else format.weekday.at(m.start),
        title = m.title,
        contentDescription =
          if (today) "Next meeting: ${m.title} at $time${m.inRoom()}"
          else
            "Next meeting: ${m.title} on ${format.fullWeekday.at(m.start)} at $time${m.inRoom()}",
      )
    }
  }
}

/** How far ahead the complication timeline is built. */
val TIMELINE_HORIZON: Duration = Duration.ofHours(24)

/**
 * Moments in `(now, now + horizon]` where [complicationContent] may change: each meeting's start,
 * the end of its "now" phase, when it enters the [LOOKAHEAD], and each local midnight (a weekday
 * label turns into a time).
 */
fun complicationTransitions(
  meetings: List<Meeting>,
  now: Instant,
  zone: ZoneId,
  horizon: Duration = TIMELINE_HORIZON,
): List<Instant> {
  val from = now.toEpochMilli()
  val until = from + horizon.toMillis()
  val midnights =
    generateSequence(now.atZone(zone).toLocalDate().plusDays(1)) { it.plusDays(1) }
      .map { it.atStartOfDay(zone).toInstant().toEpochMilli() }
      .takeWhile { it <= until }
  val meetingEdges = meetings.flatMap {
    listOf(it.start, minOf(it.currentUntil, it.end), it.start - LOOKAHEAD.toMillis())
  }
  return (meetingEdges + midnights)
    .filter { it in (from + 1)..until }
    .distinct()
    .sorted()
    .map(Instant::ofEpochMilli)
}

internal fun DateTimeFormatter.at(epochMillis: Long): String =
  format(Instant.ofEpochMilli(epochMillis))

private fun Meeting.inRoom(): String = if (room.isEmpty()) "" else " in $room"
