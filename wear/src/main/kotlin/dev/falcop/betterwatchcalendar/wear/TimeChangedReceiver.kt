package dev.falcop.betterwatchcalendar.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Rebuilds the complication timeline after a time zone, clock, or locale change, because its
 * entries hold formatted local times. Also after an app update, which cancels the tap action's
 * PendingIntent that the watch face still holds, so taps would do nothing until the next update.
 */
class TimeChangedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action !in ACTIONS) return
    context.graph.complicationUpdater.requestUpdateAll()
  }

  private companion object {
    val ACTIONS =
      setOf(
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_LOCALE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
      )
  }
}
