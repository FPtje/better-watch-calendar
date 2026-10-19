package dev.falcop.betterwatchcalendar.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dev.falcop.betterwatchcalendar.shared.ScheduleProtocol

/**
 * Receives snapshots from the phone. The manifest filter already limits it to the schedule path.
 */
class ScheduleListenerService : WearableListenerService() {
  override fun onDataChanged(dataEvents: DataEventBuffer) {
    dataEvents
      .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == ScheduleProtocol.PATH }
      .forEach { event -> logSnapshot("received", event.dataItem.data) }
  }
}
