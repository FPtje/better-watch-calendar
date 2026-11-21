package io.github.fptje.betterwatchcalendar.shared

/**
 * Where the meeting happens: booked room resources, else the event location, else the conferencing
 * service mentioned in the raw description, else empty.
 */
fun resolveRoom(resources: List<RawAttendee>, location: String?, rawDescription: String?): String =
  resources.mapNotNull { it.displayName() }.joinToString(", ").ifEmpty { null }
    ?: location?.trim()?.takeIf { it.isNotEmpty() }
    ?: rawDescription?.let(::detectConference)
    ?: ""

private val conferenceHosts =
  listOf(
    "meet.google.com" to "Google Meet",
    "zoom.us" to "Zoom",
    "teams.microsoft.com" to "Teams",
  )

/** Label of the first conferencing service whose link appears in [text], if any. */
fun detectConference(text: String): String? =
  conferenceHosts.firstOrNull { (host, _) -> text.contains(host, ignoreCase = true) }?.second
