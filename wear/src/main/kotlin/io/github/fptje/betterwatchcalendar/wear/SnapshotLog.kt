package io.github.fptje.betterwatchcalendar.wear

import android.util.Log
import io.github.fptje.betterwatchcalendar.shared.decodeSnapshot
import java.time.Instant

const val TAG = "BWC"

/** Logs a received snapshot payload for debugging over `just log-watch`. */
fun logSnapshot(source: String, bytes: ByteArray) {
  decodeSnapshot(bytes)
    .onLeft { Log.w(TAG, "$source: bad snapshot: $it") }
    .onRight { snapshot ->
      Log.i(
        TAG,
        "$source: ${bytes.size} bytes, synced ${Instant.ofEpochMilli(snapshot.syncedAt)}, " +
          "${snapshot.meetings.size} meetings",
      )
      snapshot.meetings.forEach { m ->
        Log.i(TAG, "  ${Instant.ofEpochMilli(m.start)} ${m.title} @ ${m.room}")
      }
    }
}
