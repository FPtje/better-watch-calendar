package io.github.fptje.betterwatchcalendar.mobile

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.WorkManager
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("calendars")

private val Context.logStore: DataStore<SyncLogFile> by
  dataStore(
    "sync_log.pb",
    SyncLogSerializer,
    // The log is diagnostics only; losing it beats crashing every sync.
    corruptionHandler = ReplaceFileCorruptionHandler { SyncLogFile() },
  )

/** Manual wiring of the phone app's long-lived dependencies. */
class AppGraph(val context: Context) {
  val resolver: ContentResolver = context.contentResolver
  val dataClient: DataClient = Wearable.getDataClient(context)
  val workManager: WorkManager = WorkManager.getInstance(context)
  val settings = AppSettings(context.settingsStore)
  val log = SyncLog(context.logStore)
}

class BetterWatchCalendarApp : Application() {
  val graph by lazy { AppGraph(this) }
}

val Context.graph: AppGraph
  get() = (applicationContext as BetterWatchCalendarApp).graph
