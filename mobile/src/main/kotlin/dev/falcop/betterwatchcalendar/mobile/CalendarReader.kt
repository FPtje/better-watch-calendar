package dev.falcop.betterwatchcalendar.mobile

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import dev.falcop.betterwatchcalendar.shared.AttendeeRelationship
import dev.falcop.betterwatchcalendar.shared.AttendeeStatus
import dev.falcop.betterwatchcalendar.shared.AttendeeType
import dev.falcop.betterwatchcalendar.shared.CalendarId
import dev.falcop.betterwatchcalendar.shared.CalendarInfo
import dev.falcop.betterwatchcalendar.shared.EventId
import dev.falcop.betterwatchcalendar.shared.EventStatus
import dev.falcop.betterwatchcalendar.shared.RawAttendee
import dev.falcop.betterwatchcalendar.shared.RawCalendar
import dev.falcop.betterwatchcalendar.shared.RawInstance

/** Expected failures when reading the calendar provider (SPEC §4). */
sealed interface ReadError {
  data object PermissionDenied : ReadError

  data object NoGoogleCalendars : ReadError

  /** A null cursor or an unexpected `SecurityException`. */
  data class ProviderFailure(val message: String) : ReadError
}

/** Only Google accounts are supported; other account types are ignored. */
const val GOOGLE_ACCOUNT_TYPE = "com.google"

// SQLite's bound-parameter limit is 999 on older builds; stay well below it.
private const val IN_CHUNK = 500

/** Every Google calendar on the phone, synced or not. */
fun readCalendars(resolver: ContentResolver): Either<ReadError, List<CalendarInfo>> = either {
  val calendars =
    query(
      resolver,
      Calendars.CONTENT_URI,
      arrayOf(
        Calendars._ID,
        Calendars.ACCOUNT_NAME,
        Calendars.OWNER_ACCOUNT,
        Calendars.CALENDAR_DISPLAY_NAME,
        Calendars.SYNC_EVENTS,
        Calendars.CALENDAR_COLOR,
      ),
      "${Calendars.ACCOUNT_TYPE} = ?",
      arrayOf(GOOGLE_ACCOUNT_TYPE),
    ) {
      CalendarInfo(
        raw =
          RawCalendar(
            id = CalendarId(long(Calendars._ID)),
            accountName = string(Calendars.ACCOUNT_NAME).orEmpty(),
            ownerAccount = string(Calendars.OWNER_ACCOUNT).orEmpty(),
          ),
        displayName = string(Calendars.CALENDAR_DISPLAY_NAME)?.trim().orEmpty(),
        synced = int(Calendars.SYNC_EVENTS) == 1,
        color = int(Calendars.CALENDAR_COLOR),
      )
    }
  ensure(calendars.isNotEmpty()) { ReadError.NoGoogleCalendars }
  calendars
}

/** Occurrences overlapping `[begin, end)` in [calendars], recurring events expanded. */
fun readInstances(
  resolver: ContentResolver,
  calendars: List<CalendarId>,
  begin: Long,
  end: Long,
): Either<ReadError, List<RawInstance>> = either {
  if (calendars.isEmpty()) return@either emptyList()
  val uri =
    Instances.CONTENT_URI.buildUpon()
      .also { ContentUris.appendId(it, begin) }
      .also { ContentUris.appendId(it, end) }
      .build()
  query(
    resolver,
    uri,
    arrayOf(
      Instances.EVENT_ID,
      Instances.BEGIN,
      Instances.END,
      Instances.TITLE,
      Instances.EVENT_LOCATION,
      Instances.DESCRIPTION,
      Instances.ALL_DAY,
      Instances.STATUS,
      Instances.SELF_ATTENDEE_STATUS,
      Instances.CALENDAR_ID,
      Instances.UID_2445,
    ),
    inClause(Instances.CALENDAR_ID, calendars.size),
    calendars.map { it.value.toString() }.toTypedArray(),
  ) {
    RawInstance(
      eventId = EventId(long(Instances.EVENT_ID)),
      begin = long(Instances.BEGIN),
      end = long(Instances.END),
      title = string(Instances.TITLE),
      location = string(Instances.EVENT_LOCATION),
      description = string(Instances.DESCRIPTION),
      allDay = int(Instances.ALL_DAY) == 1,
      status = intOrNull(Instances.STATUS)?.let(::eventStatus),
      selfAttendeeStatus = attendeeStatus(int(Instances.SELF_ATTENDEE_STATUS)),
      calendarId = CalendarId(long(Instances.CALENDAR_ID)),
      uid2445 = string(Instances.UID_2445),
    )
  }
}

/** Attendees of [events], fetched in a few batched queries rather than one per event. */
fun readAttendees(
  resolver: ContentResolver,
  events: Collection<EventId>,
): Either<ReadError, List<RawAttendee>> = either {
  events.distinct().chunked(IN_CHUNK).flatMap { chunk ->
    query(
      resolver,
      Attendees.CONTENT_URI,
      arrayOf(
        Attendees.EVENT_ID,
        Attendees.ATTENDEE_NAME,
        Attendees.ATTENDEE_EMAIL,
        Attendees.ATTENDEE_TYPE,
        Attendees.ATTENDEE_RELATIONSHIP,
        Attendees.ATTENDEE_STATUS,
      ),
      inClause(Attendees.EVENT_ID, chunk.size),
      chunk.map { it.value.toString() }.toTypedArray(),
    ) {
      RawAttendee(
        eventId = EventId(long(Attendees.EVENT_ID)),
        name = string(Attendees.ATTENDEE_NAME),
        email = string(Attendees.ATTENDEE_EMAIL),
        type = attendeeType(int(Attendees.ATTENDEE_TYPE)),
        relationship = attendeeRelationship(int(Attendees.ATTENDEE_RELATIONSHIP)),
        status = attendeeStatus(int(Attendees.ATTENDEE_STATUS)),
      )
    }
  }
}

private fun inClause(column: String, count: Int) =
  "$column IN (${List(count) { "?" }.joinToString(",")})"

/** Runs a query and maps every row; the cursor never escapes this function. */
private fun <T> Raise<ReadError>.query(
  resolver: ContentResolver,
  uri: Uri,
  projection: Array<String>,
  selection: String,
  args: Array<String>,
  row: Row.() -> T,
): List<T> {
  val cursor =
    try {
      resolver.query(uri, projection, selection, args, null)
    } catch (_: SecurityException) {
      raise(ReadError.PermissionDenied)
    } ?: raise(ReadError.ProviderFailure("null cursor for $uri"))
  return cursor.use { c ->
    val r = Row(c)
    buildList(c.count) { while (c.moveToNext()) add(r.row()) }
  }
}

/** Column access by name for the current row. */
private class Row(private val cursor: Cursor) {
  private fun index(column: String) = cursor.getColumnIndexOrThrow(column)

  fun long(column: String): Long = cursor.getLong(index(column))

  fun int(column: String): Int = cursor.getInt(index(column))

  fun intOrNull(column: String): Int? =
    index(column).takeUnless(cursor::isNull)?.let(cursor::getInt)

  fun string(column: String): String? = cursor.getString(index(column))
}

private fun eventStatus(value: Int): EventStatus? =
  when (value) {
    Events.STATUS_TENTATIVE -> EventStatus.Tentative
    Events.STATUS_CONFIRMED -> EventStatus.Confirmed
    Events.STATUS_CANCELED -> EventStatus.Canceled
    else -> null
  }

private fun attendeeStatus(value: Int): AttendeeStatus =
  when (value) {
    Attendees.ATTENDEE_STATUS_ACCEPTED -> AttendeeStatus.Accepted
    Attendees.ATTENDEE_STATUS_DECLINED -> AttendeeStatus.Declined
    Attendees.ATTENDEE_STATUS_INVITED -> AttendeeStatus.Invited
    Attendees.ATTENDEE_STATUS_TENTATIVE -> AttendeeStatus.Tentative
    else -> AttendeeStatus.None
  }

private fun attendeeType(value: Int): AttendeeType =
  when (value) {
    Attendees.TYPE_REQUIRED -> AttendeeType.Required
    Attendees.TYPE_OPTIONAL -> AttendeeType.Optional
    Attendees.TYPE_RESOURCE -> AttendeeType.Resource
    else -> AttendeeType.None
  }

private fun attendeeRelationship(value: Int): AttendeeRelationship =
  when (value) {
    Attendees.RELATIONSHIP_ATTENDEE -> AttendeeRelationship.Attendee
    Attendees.RELATIONSHIP_ORGANIZER -> AttendeeRelationship.Organizer
    Attendees.RELATIONSHIP_PERFORMER -> AttendeeRelationship.Performer
    Attendees.RELATIONSHIP_SPEAKER -> AttendeeRelationship.Speaker
    else -> AttendeeRelationship.None
  }
