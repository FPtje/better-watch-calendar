package dev.falcop.betterwatchcalendar.mobile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.falcop.betterwatchcalendar.shared.CalendarId
import dev.falcop.betterwatchcalendar.shared.CalendarOverrides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val INCLUDE_PREFIX = "include_"

/**
 * Explicit include toggles, one key per calendar. Only calendars the user touched are stored, so
 * new calendars pick up the default.
 */
class CalendarSettings(private val store: DataStore<Preferences>) {
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
}
