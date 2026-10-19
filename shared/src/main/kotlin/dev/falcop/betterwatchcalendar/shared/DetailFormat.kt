package dev.falcop.betterwatchcalendar.shared

import java.time.Duration
import java.time.Instant

/** `10:30–11:15`, prefixed with the weekday (`Tue 10:30–11:15`) when it doesn't start today. */
fun meetingTimeRange(meeting: Meeting, now: Instant, format: TimeFormat): String {
  val range = "${format.clock.at(meeting.start)}–${format.clock.at(meeting.end)}"
  return if (format.date(meeting.start) == format.date(now)) range
  else "${format.weekday.at(meeting.start)} $range"
}

/** Label for attendees beyond the shown list, or null when everyone is listed. */
fun moreAttendeesLabel(more: Int): String? = if (more > 0) "+$more more" else null

/** After this, the detail footer warns that the phone hasn't synced. */
val STALE_AFTER: Duration = Duration.ofHours(1)

data class SyncFooter(
  /** `Synced 14:02`, or `Synced Mon 14:02` when not today. */
  val label: String,
  /** `Phone not synced for 3 h`, or null while fresh. */
  val staleWarning: String?,
)

fun syncFooter(syncedAt: Long, now: Instant, format: TimeFormat): SyncFooter {
  val time = format.clock.at(syncedAt)
  val label =
    if (format.date(syncedAt) == format.date(now)) "Synced $time"
    else "Synced ${format.weekday.at(syncedAt)} $time"
  val age = Duration.between(Instant.ofEpochMilli(syncedAt), now)
  return SyncFooter(label, if (age > STALE_AFTER) "Phone not synced for ${shortAge(age)}" else null)
}

private const val HOURS_BEFORE_DAYS = 48L

private fun shortAge(age: Duration): String =
  if (age.toHours() < HOURS_BEFORE_DAYS) "${age.toHours()} h" else "${age.toDays()} d"
