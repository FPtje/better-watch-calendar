package dev.falcop.betterwatchcalendar.shared

/** Why an instance is left out of the snapshot. Surfaced in the phone's debug log. */
enum class SkipReason {
  Canceled,
  AllDay,
  Declined,
  /** Only the user and room resources: focus time, out-of-office, personal blocks, reminders. */
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
): List<Meeting> {
  val calendarsById = calendars.associateBy { it.id }
  val attendeesByEvent = attendees.groupBy { it.eventId }
  return instances
    .mapNotNull { instance ->
      calendarsById[instance.calendarId]?.let { calendar ->
        Candidate(instance, calendar, attendeesByEvent[instance.eventId].orEmpty())
      }
    }
    .filter { skipReason(it.instance, it.calendar, it.attendees) == null }
    // Deduplication keeps the first copy, so the account order decides which calendar wins.
    .sortedWith(
      compareBy<Candidate, String>(String.CASE_INSENSITIVE_ORDER) { it.calendar.accountName }
        .thenBy { it.calendar.id.value }
        .thenBy { it.instance.begin }
    )
    .distinctBy { it.dedupKey }
    .map { it.toMeeting() }
    .sortedWith(MeetingOrder)
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
