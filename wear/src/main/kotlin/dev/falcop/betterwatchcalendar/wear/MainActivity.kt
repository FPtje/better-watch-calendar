package dev.falcop.betterwatchcalendar.wear

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.Wearable
import dev.falcop.betterwatchcalendar.shared.ScheduleProtocol
import kotlinx.coroutines.tasks.await

/**
 * M1 placeholder: shows a summary of the current snapshot item. Replaced by the detail view in M5.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val dataClient = Wearable.getDataClient(this)
    setContent {
      var summary by remember { mutableStateOf("Loading…") }
      LaunchedEffect(Unit) {
        val uri = Uri.Builder().scheme("wear").path(ScheduleProtocol.PATH).build()
        val items = dataClient.getDataItems(uri).await()
        summary =
          try {
            logSnapshot("on start", items.firstOrNull()?.data)
          } finally {
            items.release()
          }
      }
      MaterialTheme {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          Text(summary, textAlign = TextAlign.Center)
        }
      }
    }
  }
}
