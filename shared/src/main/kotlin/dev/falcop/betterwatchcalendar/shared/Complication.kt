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
        title = shortTitle(m.title),
        contentDescription =
          "Current meeting: ${m.title}, started at ${format.spokenClock.at(m.start)}${m.inRoom()}",
      )
    }
    is Selection.Next -> {
      val m = selection.meeting
      val today = format.date(m.start) == format.date(now)
      val time = format.spokenClock.at(m.start)
      ComplicationContent(
        text =
          if (showsClock(m, now, today)) format.clock.at(m.start) else format.weekday.at(m.start),
        title = shortTitle(m.title),
        contentDescription =
          if (today) "Next meeting: ${m.title} at $time${m.inRoom()}"
          else
            "Next meeting: ${m.title} on ${format.fullWeekday.at(m.start)} at $time${m.inRoom()}",
      )
    }
  }
}

/**
 * The owner's name, left out of the complication title: it is in nearly every 1:1 title. TODO if I
 * ever want to release this: make this a setting in the Android app.
 */
const val OWN_NAME = "Falco"

private val ownNameSegment =
  Regex(
    "^\\s*${Regex.escape(OWN_NAME)}\\s*/\\s*|\\s*/\\s*${Regex.escape(OWN_NAME)}\\s*$",
    RegexOption.IGNORE_CASE,
  )

/**
 * The title without a leading `Falco / ` or trailing ` / Falco`, so the few characters the
 * complication shows go to the other person. Only the title; the spoken description stays whole.
 */
internal fun shortTitle(title: String): String = title.replace(ownNameSegment, "").ifBlank { title }

/**
 * Within this time a bare clock time is unambiguous, so a meeting tomorrow morning shows `10:00`
 * instead of `Wed`.
 */
val CLOCK_WINDOW: Duration = Duration.ofHours(24)

/** Today's meetings always show a time, even on a 25-hour DST day. */
private fun showsClock(m: Meeting, now: Instant, today: Boolean): Boolean =
  today || m.start - now.toEpochMilli() <= CLOCK_WINDOW.toMillis()

/** How far ahead the complication timeline is built. */
val TIMELINE_HORIZON: Duration = Duration.ofHours(24)

/**
 * Moments in `(now, now + horizon]` where [complicationContent] may change: each meeting's start,
 * the end of its "now" phase, when it enters the [LOOKAHEAD], when it enters the [CLOCK_WINDOW],
 * and each local midnight (the description drops the weekday).
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
    listOf(
      it.start,
      minOf(it.currentUntil, it.end),
      it.start - LOOKAHEAD.toMillis(),
      it.start - CLOCK_WINDOW.toMillis(),
    )
  }
  return (meetingEdges + midnights)
    .filter { it in (from + 1)..until }
    .distinct()
    .sorted()
    .map(Instant::ofEpochMilli)
}

/** A complication state that holds from [from] until the next entry's [from]. */
data class TimelineState(val from: Instant, val content: ComplicationContent)

/**
 * The complication's states from [now] through [horizon]: the current state first, then one per
 * change. Includes the snapshot's `windowEnd`, where the content turns into "sync". Consecutive
 * transitions with equal content are merged.
 */
fun complicationTimeline(
  snapshot: ScheduleSnapshot?,
  now: Instant,
  format: TimeFormat,
  horizon: Duration = TIMELINE_HORIZON,
): List<TimelineState> {
  val windowEnd =
    snapshot?.windowEnd?.let(Instant::ofEpochMilli)?.takeIf { it > now && it <= now.plus(horizon) }
  val transitions =
    snapshot?.let { complicationTransitions(it.meetings, now, format.zone, horizon) }.orEmpty()
  val moments = (listOf(now) + transitions + listOfNotNull(windowEnd)).distinct().sorted()
  return moments
    .map { TimelineState(it, complicationContent(snapshot, it, format)) }
    .fold(emptyList()) { acc, state ->
      if (acc.lastOrNull()?.content == state.content) acc else acc + state
    }
}

internal fun DateTimeFormatter.at(epochMillis: Long): String =
  format(Instant.ofEpochMilli(epochMillis))

private fun Meeting.inRoom(): String = if (room.isEmpty()) "" else " in $room"
