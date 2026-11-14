package dev.falcop.betterwatchcalendar.wear

import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.google.android.gms.wearable.DataClient
import dev.falcop.betterwatchcalendar.shared.ScheduleProtocol
import dev.falcop.betterwatchcalendar.shared.ScheduleSnapshot
import dev.falcop.betterwatchcalendar.shared.decodeSnapshot
import dev.falcop.betterwatchcalendar.shared.encode
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * The last snapshot received from the phone, or null before the first one. Calls [onChanged] after
 * each save that changes it.
 */
class SnapshotStore(
  private val store: DataStore<ScheduleSnapshot?>,
  private val onChanged: () -> Unit,
) {
  val snapshot: Flow<ScheduleSnapshot?> = store.data

  suspend fun current(): ScheduleSnapshot? = snapshot.first()

  /**
   * Decodes and stores a Data Layer payload. Bad or unknown-version payloads are logged and ignored
   * so the last good snapshot stays in use.
   */
  suspend fun save(bytes: ByteArray) {
    val decoded =
      decodeSnapshot(bytes).onLeft { Log.w(TAG, "ignoring snapshot: $it") }.getOrNull() ?: return
    var changed = false
    store.updateData {
      changed = it != decoded
      decoded
    }
    if (changed) onChanged()
  }

  /** Catches up on a snapshot delivered while the listener wasn't running. */
  suspend fun refreshFrom(dataClient: DataClient) {
    val uri = Uri.Builder().scheme("wear").path(ScheduleProtocol.PATH).build()
    val items = dataClient.getDataItems(uri).await()
    val bytes =
      try {
        items.firstOrNull()?.data
      } finally {
        items.release()
      }
    if (bytes != null) save(bytes)
  }
}

/** Stores the snapshot in its wire format; an empty file means "none yet". */
object SnapshotSerializer : Serializer<ScheduleSnapshot?> {
  override val defaultValue: ScheduleSnapshot? = null

  override suspend fun readFrom(input: InputStream): ScheduleSnapshot? {
    val bytes = input.readBytes()
    // A snapshot from an older schema is treated like none; the phone sends a fresh one soon.
    return if (bytes.isEmpty()) null else decodeSnapshot(bytes).getOrNull()
  }

  override suspend fun writeTo(t: ScheduleSnapshot?, output: OutputStream) {
    if (t != null) output.write(t.encode())
  }
}
