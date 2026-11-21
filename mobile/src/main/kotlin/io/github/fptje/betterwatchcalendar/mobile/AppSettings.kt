package io.github.fptje.betterwatchcalendar.mobile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.fptje.betterwatchcalendar.shared.CalendarId
import io.github.fptje.betterwatchcalendar.shared.CalendarOverrides
import io.github.fptje.betterwatchcalendar.shared.DEFAULT_TITLE_FILTER
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val INCLUDE_PREFIX = "include_"

private val TITLE_FILTER = stringPreferencesKey("title_filter")

/**
 * The phone app's settings. Calendar include toggles are one key per calendar; only calendars the
 * user touched are stored, so new calendars pick up the default.
 */
class AppSettings(private val store: DataStore<Preferences>) {
  val overrides: Flow<CalendarOverrides> =
    store.data.map { prefs ->
      prefs
        .asMap()
        .entries
        .mapNotNull { (key, value) ->
          val id = key.name.removePrefix(INCLUDE_PREFIX).toLongOrNull()
          if (key.name.startsWith(INCLUDE_PREFIX) && id != null && value is Boolean)
            CalendarId(id) to value
          else null
        }
        .toMap()
    }

  suspend fun current(): CalendarOverrides = overrides.first()

  suspend fun setIncluded(id: CalendarId, included: Boolean) {
    store.edit { it[booleanPreferencesKey("$INCLUDE_PREFIX${id.value}")] = included }
  }

  /** The complication title filter (see `shortTitle`); [DEFAULT_TITLE_FILTER] until saved. */
  val titleFilter: Flow<String> = store.data.map { it[TITLE_FILTER] ?: DEFAULT_TITLE_FILTER }

  suspend fun setTitleFilter(pattern: String) {
    store.edit { it[TITLE_FILTER] = pattern }
  }
}
