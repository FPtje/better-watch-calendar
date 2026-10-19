package dev.falcop.betterwatchcalendar.wear

import android.util.Log
import dev.falcop.betterwatchcalendar.shared.decodeSnapshot
import java.time.Instant

const val TAG = "BWC"

/** Decodes and logs a snapshot payload; returns a one-line summary for display. */
fun logSnapshot(source: String, bytes: ByteArray?): String {
  if (bytes == null) return "No snapshot".also { Log.i(TAG, "$source: no snapshot") }
  return decodeSnapshot(bytes)
    .fold(
      ifLeft = { error -> "Bad snapshot: $error".also { Log.w(TAG, "$source: $it") } },
      ifRight = { snapshot ->
        Log.i(
          TAG,
          "$source: ${bytes.size} bytes, synced ${Instant.ofEpochMilli(snapshot.syncedAt)}, " +
            "${snapshot.meetings.size} meetings",
        )
        snapshot.meetings.forEach { m ->
          Log.i(TAG, "  ${Instant.ofEpochMilli(m.start)} ${m.title} @ ${m.room}")
        }
        "${snapshot.meetings.size} meetings, synced ${Instant.ofEpochMilli(snapshot.syncedAt)}"
      },
    )
}
