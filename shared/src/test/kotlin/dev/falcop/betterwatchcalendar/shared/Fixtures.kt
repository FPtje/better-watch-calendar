package dev.falcop.betterwatchcalendar.shared

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE

val amsterdam: ZoneId = ZoneId.of("Europe/Amsterdam")
val format24 = TimeFormat(amsterdam, Locale.US, is24Hour = true)
val format12 = TimeFormat(amsterdam, Locale.US, is24Hour = false)

/** Monday 2026-10-19 in Amsterdam. */
fun at(hour: Int, minute: Int = 0, day: Int = 19, month: Int = 10): Instant =
  ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, amsterdam).toInstant()

fun meeting(
  start: Instant,
  minutes: Long = 30,
  title: String = "Meeting",
  id: String = "1:${start.toEpochMilli()}:${start.toEpochMilli()}",
  room: String = "",
) =
  Meeting(
    id = id,
    title = title,
    start = start.toEpochMilli(),
    end = start.toEpochMilli() + minutes * MINUTE,
    room = room,
    description = "",
    attendees = emptyList(),
    moreAttendees = 0,
  )

fun snapshotOf(vararg meetings: Meeting, syncedAt: Instant = at(8)) =
  ScheduleSnapshot(
    schemaVersion = ScheduleProtocol.SCHEMA_VERSION,
    syncedAt = syncedAt.toEpochMilli(),
    windowEnd = syncedAt.toEpochMilli() + LOOKAHEAD.toMillis(),
    meetings = meetings.sortedWith(MeetingOrder),
  )

val work = RawCalendar(CalendarId(29), accountName = "me@work.example", "me@work.example")
val personal = RawCalendar(CalendarId(5), accountName = "me@gmail.example", "me@gmail.example")

fun instance(
  eventId: Long,
  calendar: RawCalendar = work,
  begin: Instant = at(10),
  minutes: Long = 30,
  title: String? = "Design review",
  location: String? = null,
  description: String? = null,
  allDay: Boolean = false,
  status: EventStatus? = EventStatus.Confirmed,
  selfStatus: AttendeeStatus = AttendeeStatus.Accepted,
  uid: String? = "uid-$eventId",
) =
  RawInstance(
    eventId = EventId(eventId),
    begin = begin.toEpochMilli(),
    end = begin.toEpochMilli() + minutes * MINUTE,
    title = title,
    location = location,
    description = description,
    allDay = allDay,
    status = status,
    selfAttendeeStatus = selfStatus,
    calendarId = calendar.id,
    uid2445 = uid,
  )

fun attendee(
  eventId: Long,
  email: String?,
  name: String? = null,
  type: AttendeeType = AttendeeType.Required,
  relationship: AttendeeRelationship = AttendeeRelationship.Attendee,
  status: AttendeeStatus = AttendeeStatus.Accepted,
) = RawAttendee(EventId(eventId), name, email, type, relationship, status)

fun room(eventId: Long, name: String?, email: String = "c_123@resource.calendar.google.com") =
  attendee(eventId, email, name, type = AttendeeType.Resource)

/** The user plus one colleague: the minimum for an instance to count as a meeting. */
fun meetingAttendees(eventId: Long, calendar: RawCalendar = work) =
  listOf(
    attendee(eventId, calendar.ownerAccount, relationship = AttendeeRelationship.Organizer),
    attendee(eventId, "ada@work.example", "Ada Lovelace"),
  )

/** Meetings within a few days of [base], including overlaps, zero-length and very short ones. */
fun meetingsArb(base: Instant = at(0)): Arb<List<Meeting>> =
  Arb.list(
      arbitrary {
        val start = base.toEpochMilli() + Arb.long(0L..72 * HOUR).bind() / MINUTE * MINUTE
        val minutes = Arb.int(0..180).bind()
        Meeting(
          id = "",
          title = "T",
          start = start,
          end = start + minutes * MINUTE,
          room = "",
          description = "",
          attendees = emptyList(),
          moreAttendees = 0,
        )
      },
      0..12,
    )
    .map { list -> list.mapIndexed { i, m -> m.copy(id = "m$i") } }

fun instantArb(base: Instant = at(0)): Arb<Instant> =
  Arb.long(0L..72 * HOUR).map { Instant.ofEpochMilli(base.toEpochMilli() + it) }
