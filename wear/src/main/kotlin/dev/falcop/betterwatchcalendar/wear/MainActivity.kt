package dev.falcop.betterwatchcalendar.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.gms.common.api.ApiException
import java.time.Instant

/**
 * Placeholder opened by the complication tap: shows a summary of the stored snapshot. Replaced by
 * the detail view in M5.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val graph = graph
    setContent {
      val snapshot by graph.snapshots.snapshot.collectAsStateWithLifecycle(initialValue = null)
      LaunchedEffect(Unit) {
        try {
          if (graph.snapshots.refreshFrom(graph.dataClient)) {
            graph.complicationUpdater.requestUpdateAll()
          }
        } catch (e: ApiException) {
          Log.w(TAG, "could not read the current snapshot", e)
        }
      }
      val summary =
        snapshot?.let {
          "${it.meetings.size} meetings, synced ${Instant.ofEpochMilli(it.syncedAt)}"
        } ?: "No snapshot"
      MaterialTheme {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          Text(summary, textAlign = TextAlign.Center)
        }
      }
    }
  }
}
