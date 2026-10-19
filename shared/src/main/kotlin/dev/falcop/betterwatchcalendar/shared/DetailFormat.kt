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

/** One meeting in the detail view, already formatted. */
data class MeetingCard(
  val id: String,
  val title: String,
  /** From [meetingTimeRange]. */
  val time: String,
  /** Within its first 10 minutes, like the complication's "now"; shows the "now" badge. */
  val isCurrent: Boolean,
  /** Empty when absent. */
  val room: String,
  val description: String,
  val attendees: List<String>,
  /** From [moreAttendeesLabel]. */
  val moreAttendees: String?,
)

const val WAITING_FOR_PHONE = "Waiting for phone… open Better Watch Calendar on your phone"
const val NO_MEETINGS = "No meetings in the next 7 days"

/** What the detail view shows at a given moment. */
sealed interface DetailContent {
  /** No snapshot was ever received. */
  data object WaitingForPhone : DetailContent

  data class Schedule(
    /** The shown meeting and the one after it; empty when there is nothing to show. */
    val cards: List<MeetingCard>,
    /** Shown instead of the cards when [cards] is empty. */
    val emptyMessage: String?,
    val footer: SyncFooter,
  ) : DetailContent
}

/**
 * The detail view for [snapshot] at [now]: the same meeting as the complication, then the following
 * one. An expired snapshot shows the waiting message, never "no meetings".
 */
fun detailContent(snapshot: ScheduleSnapshot?, now: Instant, format: TimeFormat): DetailContent {
  if (snapshot == null) return DetailContent.WaitingForPhone
  val footer = syncFooter(snapshot.syncedAt, now, format)
  val selection =
    if (now.toEpochMilli() >= snapshot.windowEnd) null else select(snapshot.meetings, now)
  return when (selection) {
    null -> DetailContent.Schedule(emptyList(), WAITING_FOR_PHONE, footer)
    Selection.Free -> DetailContent.Schedule(emptyList(), NO_MEETINGS, footer)
    is Selection.Shown -> {
      val cards =
        listOfNotNull(selection.meeting, selection.following).map { it.toCard(now, format) }
      DetailContent.Schedule(cards, null, footer)
    }
  }
}

private fun Meeting.toCard(now: Instant, format: TimeFormat) =
  MeetingCard(
    id = id,
    title = title,
    time = meetingTimeRange(this, now, format),
    isCurrent = isCurrentAt(now.toEpochMilli()),
    room = room,
    description = description,
    attendees = attendees,
    moreAttendees = moreAttendeesLabel(moreAttendees),
  )
