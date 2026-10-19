package dev.falcop.betterwatchcalendar.mobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import kotlinx.coroutines.launch

const val TAG = "BWC"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val context = LocalContext.current
      val colors =
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
      MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) { SendScreen() }
      }
    }
  }
}

@Composable
private fun SendScreen() {
  val dataClient = Wearable.getDataClient(LocalContext.current)
  val scope = rememberCoroutineScope()
  var status by remember { mutableStateOf("Not sent yet") }

  suspend fun send() {
    val snapshot = sampleSnapshot(Instant.now())
    status =
      sendSnapshot(dataClient, snapshot)
        .fold(
          ifLeft = { "Failed: ${it.message}".also { msg -> Log.w(TAG, msg) } },
          ifRight = { bytes ->
            "Sent ${snapshot.meetings.size} meetings ($bytes bytes)".also { msg -> Log.i(TAG, msg) }
          },
        )
  }

  LaunchedEffect(Unit) { send() }

  Column(
    Modifier.safeDrawingPadding().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Better Watch Calendar", style = MaterialTheme.typography.headlineSmall)
    Text(status)
    Button(onClick = { scope.launch { send() } }) { Text("Send test snapshot") }
  }
}
