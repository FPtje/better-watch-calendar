package dev.falcop.betterwatchcalendar.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant

/**
 * Rebuilds the complication timeline. Triggered by:
 * - The alarm at the next state change. The timeline alone isn't enough: when the watch face falls
 *   back to the timeline's default data (for example after it restarts), that default is the state
 *   at the last request, such as "now", and it would stay until the next phone sync. Refreshing at
 *   each transition makes the default correct again right when it matters.
 * - A time zone, clock, or locale change, because the entries hold formatted local times.
 * - An app update, which cancels the tap action's PendingIntent that the watch face still holds, so
 *   taps would do nothing until the next update.
 */
class ComplicationRefreshReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    context.graph.complicationUpdater.requestUpdateAll()
  }
}

/** Replaces the pending refresh with one at [at], or cancels it when [at] is null. */
fun scheduleComplicationRefresh(context: Context, at: Instant?) {
  val alarms = context.getSystemService(AlarmManager::class.java)
  val intent =
    PendingIntent.getBroadcast(
      context,
      0,
      Intent(context, ComplicationRefreshReceiver::class.java),
      PendingIntent.FLAG_IMMUTABLE,
    )
  when {
    at == null -> alarms.cancel(intent)
    // USE_EXACT_ALARM is granted at install; the check guards against it being revoked.
    alarms.canScheduleExactAlarms() ->
      alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
    else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
  }
}
