package dev.falcop.betterwatchcalendar.mobile

import dev.falcop.betterwatchcalendar.shared.Meeting
import dev.falcop.betterwatchcalendar.shared.ScheduleProtocol
import dev.falcop.betterwatchcalendar.shared.ScheduleSnapshot
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Hard-coded M1 test data, placed relative to [now] so it stays plausible whenever it's sent. */
fun sampleSnapshot(now: Instant): ScheduleSnapshot {
  fun at(offset: Duration) = now.toEpochMilli() + offset.inWholeMilliseconds

  return ScheduleSnapshot(
    schemaVersion = ScheduleProtocol.SCHEMA_VERSION,
    syncedAt = now.toEpochMilli(),
    windowEnd = at(7.days),
    meetings =
      listOf(
        Meeting(
          id = "1:101:${at(30.minutes)}",
          title = "Design review",
          start = at(30.minutes),
          end = at(75.minutes),
          room = "Orion",
          description = "Walk through the complication states.",
          attendees = listOf("Ada Lovelace", "Alan Turing"),
          moreAttendees = 0,
        ),
        Meeting(
          id = "1:102:${at(3.hours)}",
          title = "Weekly sync",
          start = at(3.hours),
          end = at(3.hours + 30.minutes),
          room = "Google Meet",
          description = "",
          attendees = listOf("Grace Hopper"),
          moreAttendees = 4,
        ),
      ),
  )
}
