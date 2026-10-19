package dev.falcop.betterwatchcalendar.wear

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import dev.falcop.betterwatchcalendar.shared.ScheduleSnapshot

private val Context.snapshotStore: DataStore<ScheduleSnapshot?> by
  dataStore("snapshot.pb", SnapshotSerializer)

/** Manual wiring of the watch app's long-lived dependencies. */
class AppGraph(context: Context) {
  val dataClient: DataClient = Wearable.getDataClient(context)
  val snapshots = SnapshotStore(context.snapshotStore)
  val complicationUpdater: ComplicationDataSourceUpdateRequester =
    ComplicationDataSourceUpdateRequester.create(
      context,
      ComponentName(context, NextMeetingComplicationService::class.java),
    )
}

class BetterWatchCalendarApp : Application() {
  val graph by lazy { AppGraph(this) }
}

val Context.graph: AppGraph
  get() = (applicationContext as BetterWatchCalendarApp).graph
