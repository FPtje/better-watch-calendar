package dev.falcop.betterwatchcalendar.shared

// Immutable mirrors of CalendarContract rows. The phone's cursor layer maps the provider's integer
// constants to these enums, so everything downstream is plain Kotlin with no Android dependency.

@JvmInline value class CalendarId(val value: Long)

@JvmInline value class EventId(val value: Long)

/** The calendar-level facts that filtering and deduplication need. */
data class RawCalendar(
  val id: CalendarId,
  /** `ACCOUNT_NAME`: the Google account the calendar syncs through. */
  val accountName: String,
  /** `OWNER_ACCOUNT`: the user's own email address as an attendee of this calendar's events. */
  val ownerAccount: String,
) {
  /** The account's own calendar. Google can list more than one; only synced ones matter. */
  val isPrimary: Boolean
    get() = ownerAccount.equals(accountName, ignoreCase = true)
}

enum class EventStatus {
  Tentative,
  Confirmed,
  Canceled,
}

enum class AttendeeStatus {
  None,
  Accepted,
  Declined,
  Invited,
  Tentative,
}

enum class AttendeeType {
  None,
  Required,
  Optional,
  Resource,
}

enum class AttendeeRelationship {
  None,
  Attendee,
  Organizer,
  Performer,
  Speaker,
}

/** One row of `CalendarContract.Instances`: a single occurrence of a (possibly recurring) event. */
data class RawInstance(
  val eventId: EventId,
  /** Epoch millis. */
  val begin: Long,
  /** Epoch millis. */
  val end: Long,
  val title: String?,
  val location: String?,
  /** As stored by Google sync: plain text or HTML, including conferencing boilerplate. */
  val description: String?,
  val allDay: Boolean,
  val status: EventStatus?,
  val selfAttendeeStatus: AttendeeStatus,
  val calendarId: CalendarId,
  /** iCalendar UID, shared by copies of the same invite across accounts. */
  val uid2445: String?,
)

/** One row of `CalendarContract.Attendees`. */
data class RawAttendee(
  val eventId: EventId,
  val name: String?,
  val email: String?,
  val type: AttendeeType,
  val relationship: AttendeeRelationship,
  val status: AttendeeStatus,
)
