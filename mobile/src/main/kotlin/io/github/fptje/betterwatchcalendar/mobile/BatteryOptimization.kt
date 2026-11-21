package io.github.fptje.betterwatchcalendar.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
  context
    .getSystemService(PowerManager::class.java)
    .isIgnoringBatteryOptimizations(context.packageName)

/**
 * Asks for the battery optimization exemption (SPEC P9). An app nobody opens sinks to the RARE
 * standby bucket, where job quota holds the 15-minute sync back for hours.
 */
@SuppressLint("BatteryLife")
@Composable
fun BatteryOptimizationBanner() {
  val context = LocalContext.current
  var exempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
  // Re-checked on resume: the result of the system dialog arrives that way, and the exemption
  // can be revoked from Settings.
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    exempt = isIgnoringBatteryOptimizations(context)
  }
  if (exempt) return
  Banner(
    "Background sync is limited",
    "Android delays background work of apps you rarely open, so your watch can miss " +
      "calendar changes for hours. Allow unrestricted battery use to sync every 15 minutes.",
  ) {
    Button(
      onClick = {
        context.startActivity(
          Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
        )
      }
    ) {
      Text("Allow")
    }
  }
}
