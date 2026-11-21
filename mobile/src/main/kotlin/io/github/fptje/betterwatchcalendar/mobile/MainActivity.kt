package io.github.fptje.betterwatchcalendar.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

const val TAG = "BWC"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    graph.workManager.schedulePeriodicSync()
    lifecycleScope.launch { graph.workManager.watchCalendarChanges() }
    // Not on recreation (rotation), only when the user actually opens the app.
    if (savedInstanceState == null && hasCalendarPermission(this)) {
      graph.workManager.syncNow(SyncTrigger.AppOpen)
    }
    setContent {
      val context = LocalContext.current
      val colors =
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
      MaterialTheme(colorScheme = colors) { App() }
    }
  }
}

private fun hasCalendarPermission(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
    PackageManager.PERMISSION_GRANTED

/** `package:<this app>`, which the per-app Settings screens expect. */
fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

@Composable
private fun App() {
  val context = LocalContext.current
  // Re-checked on resume: the permission can be revoked from Settings while the app is open.
  var granted by remember { mutableStateOf(hasCalendarPermission(context)) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCalendarPermission(context) }

  val launcher =
    rememberLauncherForActivityResult(RequestPermission()) { result ->
      granted = result
      if (result) context.graph.workManager.syncNow(SyncTrigger.AppOpen)
    }
  var asked by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (!granted && !asked) {
      asked = true
      launcher.launch(Manifest.permission.READ_CALENDAR)
    }
  }

  if (granted) MainScreen()
  else PermissionScreen(onGrant = { launcher.launch(Manifest.permission.READ_CALENDAR) })
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
  val context = LocalContext.current
  Scaffold { padding ->
    Column(
      Modifier.padding(padding).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("Calendar access needed", style = MaterialTheme.typography.headlineSmall)
      Text(
        "Better Watch Calendar reads the calendars of the Google accounts on this phone and " +
          "sends your upcoming meetings to your watch. It never changes your calendar and " +
          "never connects to the internet."
      )
      Button(onClick = onGrant) { Text("Grant calendar access") }
      // After repeated denials Android stops showing the dialog; Settings is the only way back.
      TextButton(
        onClick = {
          context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
          )
        }
      ) {
        Text("Open app settings")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen() {
  val context = LocalContext.current
  var tab by rememberSaveable { mutableIntStateOf(0) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Better Watch Calendar") },
        actions = {
          TextButton(onClick = { context.graph.workManager.syncNow(SyncTrigger.Manual) }) {
            Text("Sync now")
          }
        },
      )
    }
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      BatteryOptimizationBanner()
      AccountAccessBanner()
      PrimaryTabRow(selectedTabIndex = tab) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Calendars") })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Debug log") })
      }
      when (tab) {
        0 -> CalendarsScreen()
        else -> LogScreen()
      }
    }
  }
}
