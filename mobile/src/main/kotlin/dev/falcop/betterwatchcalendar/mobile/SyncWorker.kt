package dev.falcop.betterwatchcalendar.mobile

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Clock
import java.time.Duration

private const val KEY_TRIGGER = "trigger"
private const val SYNC_NOW = "sync-now"
private const val SYNC_PERIODIC = "sync-periodic"

/**
 * Every 15 minutes rolls the 7-day window forward and catches anything the other triggers missed.
 */
private val PERIOD: Duration = Duration.ofMinutes(15)

/** Runs one [sync]. Failures are recorded in the debug log; the next trigger retries. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val trigger =
      inputData.getString(KEY_TRIGGER)?.let { name ->
        SyncTrigger.entries.firstOrNull { it.name == name }
      } ?: SyncTrigger.Periodic
    applicationContext.graph.sync(trigger, Clock.systemUTC())
    return Result.success()
  }
}

/** Syncs as soon as possible. A newer request replaces a pending one, so rapid toggles coalesce. */
fun WorkManager.syncNow(trigger: SyncTrigger) {
  enqueueUniqueWork(
    SYNC_NOW,
    ExistingWorkPolicy.REPLACE,
    OneTimeWorkRequestBuilder<SyncWorker>()
      .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
      .setInputData(workDataOf(KEY_TRIGGER to trigger.name))
      .build(),
  )
}

fun WorkManager.schedulePeriodicSync() {
  enqueueUniquePeriodicWork(
    SYNC_PERIODIC,
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<SyncWorker>(PERIOD).build(),
  )
}
