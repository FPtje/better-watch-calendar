package dev.falcop.betterwatchcalendar.shared

/** A Google calendar as listed in the phone's settings screen. */
data class CalendarInfo(
  val raw: RawCalendar,
  val displayName: String,
  /** `SYNC_EVENTS`: unsynced calendars have no events on the phone, so they can't be included. */
  val synced: Boolean,
  /** `CALENDAR_COLOR` as ARGB. */
  val color: Int,
) {
  val id: CalendarId
    get() = raw.id

  /** The account's own calendar. Google can list more than one; only synced ones matter. */
  val isPrimary: Boolean
    get() = raw.ownerAccount.equals(raw.accountName, ignoreCase = true)
}

/** The user's explicit toggles; calendars without one fall back to [defaultIncluded]. */
typealias CalendarOverrides = Map<CalendarId, Boolean>

fun defaultIncluded(calendar: CalendarInfo): Boolean = calendar.isPrimary

/** What the toggle shows. Unsynced calendars still show their setting, greyed out. */
fun toggleState(calendar: CalendarInfo, overrides: CalendarOverrides): Boolean =
  overrides[calendar.id] ?: defaultIncluded(calendar)

/** Whether the calendar's events go into the snapshot. */
fun isIncluded(calendar: CalendarInfo, overrides: CalendarOverrides): Boolean =
  calendar.synced && toggleState(calendar, overrides)

data class AccountCalendars(val accountName: String, val calendars: List<CalendarInfo>)

/**
 * Groups calendars by account, accounts alphabetically (the same order deduplication uses). Within
 * an account: synced first, then primary, then by name.
 */
fun groupByAccount(calendars: List<CalendarInfo>): List<AccountCalendars> =
  calendars
    .groupBy { it.raw.accountName }
    .map { (account, list) ->
      AccountCalendars(
        account,
        list.sortedWith(
          compareBy<CalendarInfo>({ !it.synced }, { !it.isPrimary }).thenBy(
            String.CASE_INSENSITIVE_ORDER
          ) {
            it.displayName
          }
        ),
      )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.accountName })
