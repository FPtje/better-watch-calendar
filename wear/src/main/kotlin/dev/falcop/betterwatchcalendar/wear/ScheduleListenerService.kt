package dev.falcop.betterwatchcalendar.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dev.falcop.betterwatchcalendar.shared.ScheduleProtocol
import kotlinx.coroutines.runBlocking

/**
 * Receives snapshots from the phone, stores them, and refreshes the complication. The manifest
 * filter already limits it to the schedule path.
 */
class ScheduleListenerService : WearableListenerService() {
  override fun onDataChanged(dataEvents: DataEventBuffer) {
    val payloads =
      dataEvents
        .filter {
          it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == ScheduleProtocol.PATH
        }
        .mapNotNull { it.dataItem.data }
    // Runs on a binder worker thread; blocking keeps the service alive until the write lands.
    runBlocking {
      payloads.forEach { bytes ->
        logSnapshot("received", bytes)
        graph.snapshots.save(bytes)
      }
    }
  }
}
