package io.github.fptje.betterwatchcalendar.shared

/** Why an instance is left out of the snapshot. Surfaced in the phone's debug log. */
enum class SkipReason {
  Canceled,
  AllDay,
  Declined,
  /**
   * Only the user and room resources: focus time, out-of-office, personal blocks, reminders.
   * Applies to primary calendars only; every event on a secondary calendar the user included is
   * shown.
   */
  NoOtherAttendees,
}

/** Returns why [instance] should be skipped, or null if it is a real meeting. */
fun skipReason(
  instance: RawInstance,
  calendar: RawCalendar,
  attendees: List<RawAttendee>,
): SkipReason? =
  when {
    instance.status == EventStatus.Canceled -> SkipReason.Canceled
    instance.allDay -> SkipReason.AllDay
    instance.selfAttendeeStatus == AttendeeStatus.Declined -> SkipReason.Declined
    calendar.isPrimary &&
      attendees.none { it.type != AttendeeType.Resource && !it.isSelf(calendar.ownerAccount) } ->
      SkipReason.NoOtherAttendees
    else -> null
  }

/**
 * Turns provider rows into the meetings the watch shows: filtered, deduplicated, normalized and
 * sorted by [MeetingOrder]. Instances whose calendar is not in [calendars] are dropped, so the
 * caller passes only the calendars the user included.
 */
fun buildMeetings(
  calendars: List<RawCalendar>,
  instances: List<RawInstance>,
  attendees: List<RawAttendee>,
): List<Meeting> = buildReport(calendars, instances, attendees).meetings.map { it.meeting }

/** A built meeting plus the account whose copy survived deduplication. */
data class SourcedMeeting(val meeting: Meeting, val accountName: String)

/** [buildMeetings] with the counts the phone's debug log shows. */
data class BuildReport(
  val meetings: List<SourcedMeeting>,
  /** Every account in the input calendars, including those left with no meetings. */
  val perAccount: Map<String, Int>,
  val skipped: Map<SkipReason, Int>,
  /** Copies of an invite that another account already contributed. */
  val duplicates: Int,
)

fun buildReport(
  calendars: List<RawCalendar>,
  instances: List<RawInstance>,
  attendees: List<RawAttendee>,
): BuildReport {
  val calendarsById = calendars.associateBy { it.id }
  val attendeesByEvent = attendees.groupBy { it.eventId }
  val candidates = instances.mapNotNull { instance ->
    calendarsById[instance.calendarId]?.let { calendar ->
      Candidate(instance, calendar, attendeesByEvent[instance.eventId].orEmpty())
    }
  }
  val (kept, skipped) =
    candidates
      .map { it to skipReason(it.instance, it.calendar, it.attendees) }
      .partition { it.second == null }
  val unique =
    kept
      .map { it.first }
      // Deduplication keeps the first copy, so the account order decides which calendar wins.
      .sortedWith(
        compareBy<Candidate, String>(String.CASE_INSENSITIVE_ORDER) { it.calendar.accountName }
          .thenBy { it.calendar.id.value }
          .thenBy { it.instance.begin }
      )
      .distinctBy { it.dedupKey }
  val meetings =
    unique
      .map { SourcedMeeting(it.toMeeting(), it.calendar.accountName) }
      .sortedWith(compareBy(MeetingOrder) { it.meeting })
  return BuildReport(
    meetings = meetings,
    perAccount =
      calendars.associate { it.accountName to 0 } +
        meetings.groupingBy { it.accountName }.eachCount(),
    skipped = skipped.groupingBy { checkNotNull(it.second) }.eachCount(),
    duplicates = kept.size - unique.size,
  )
}

/** Shown when an event has no title, matching Google Calendar. */
const val UNTITLED = "(No title)"

private data class Candidate(
  val instance: RawInstance,
  val calendar: RawCalendar,
  val attendees: List<RawAttendee>,
) {
  val id = "${calendar.id.value}:${instance.eventId.value}:${instance.begin}"

  /**
   * Events without a UID can't be matched across accounts, so they only collide with themselves.
   */
  val dedupKey: Any =
    instance.uid2445?.takeIf { it.isNotBlank() }?.let { it to instance.begin } ?: id

  fun toMeeting(): Meeting {
    val people = attendeeList(attendees, calendar.ownerAccount)
    return Meeting(
      id = id,
      title = instance.title?.trim().orEmpty().ifEmpty { UNTITLED },
      start = instance.begin,
      end = instance.end,
      room =
        resolveRoom(
          attendees.filter { it.type == AttendeeType.Resource },
          instance.location,
          instance.description,
        ),
      description = cleanDescription(instance.description.orEmpty()),
      attendees = people.names,
      moreAttendees = people.more,
    )
  }
}
