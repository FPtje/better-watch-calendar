package dev.falcop.betterwatchcalendar.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.api.ApiException
import dev.falcop.betterwatchcalendar.shared.ScheduleSnapshot
import dev.falcop.betterwatchcalendar.shared.detailContent
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** The detail view, opened by tapping the complication or from the app launcher. */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val graph = graph
    setContent {
      // Null until DataStore has loaded, so "Waiting for phone…" doesn't flash on open.
      val stored by
        remember { graph.snapshots.snapshot.map(::Stored) }
          .collectAsStateWithLifecycle(initialValue = null)
      val now by
        remember { minuteTicks() }.collectAsStateWithLifecycle(initialValue = Instant.now())
      LaunchedEffect(Unit) {
        try {
          graph.snapshots.refreshFrom(graph.dataClient)
        } catch (e: ApiException) {
          Log.w(TAG, "could not read the current snapshot", e)
        }
      }
      stored?.let { DetailScreen(detailContent(it.snapshot, now, timeFormat(this))) }
    }
  }
}

private data class Stored(val snapshot: ScheduleSnapshot?)

/**
 * The current time, then again at each minute boundary. State changes (starts, the end of the "now"
 * phase, midnight) fall on whole minutes in practice, so the view switches on time.
 */
private fun minuteTicks(): Flow<Instant> = flow {
  while (true) {
    val now = Instant.now()
    emit(now)
    delay(MINUTE_MILLIS - now.toEpochMilli() % MINUTE_MILLIS)
  }
}

private const val MINUTE_MILLIS = 60_000L
