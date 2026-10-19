package dev.falcop.betterwatchcalendar.shared

/** Display names of the other attendees, plus how many didn't fit. */
data class AttendeeList(val names: List<String>, val more: Int)

const val MAX_ATTENDEES = 30

/**
 * Names of everyone except the user ([ownerAccount]) and room resources: organizer first, then
 * accepted, then alphabetical. Falls back to the email address when there is no name.
 */
fun attendeeList(
  attendees: List<RawAttendee>,
  ownerAccount: String,
  cap: Int = MAX_ATTENDEES,
): AttendeeList {
  val people =
    attendees
      .filter { it.type != AttendeeType.Resource && !it.isSelf(ownerAccount) }
      .mapNotNull { attendee -> attendee.displayName()?.let { attendee to it } }
      .sortedWith(
        compareBy<Pair<RawAttendee, String>> {
            it.first.relationship != AttendeeRelationship.Organizer
          }
          .thenBy { it.first.status != AttendeeStatus.Accepted }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { it.second }
      )
      .map { it.second }
  return AttendeeList(people.take(cap), (people.size - cap).coerceAtLeast(0))
}

internal fun RawAttendee.displayName(): String? =
  name?.trim()?.takeIf { it.isNotEmpty() } ?: email?.trim()?.takeIf { it.isNotEmpty() }

/** The user appears as the attendee whose email is the calendar's `OWNER_ACCOUNT`. */
internal fun RawAttendee.isSelf(ownerAccount: String): Boolean =
  email?.trim().equals(ownerAccount.trim(), ignoreCase = true)
