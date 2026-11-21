package io.github.fptje.betterwatchcalendar.mobile

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
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
import kotlinx.coroutines.flow.first

private const val KEY_TRIGGER = "trigger"
private const val KEY_SLOT = "slot"
private const val SYNC_NOW = "sync-now"
private const val SYNC_PERIODIC = "sync-periodic"
private const val SYNC_ON_CHANGE = "sync-on-change"

/**
 * Google's sync writes an update as a burst of rows; waiting for the database to settle turns the
 * burst into one sync. The maximum bounds the wait during a long initial sync.
 */
private val CHANGE_SETTLE: Duration = Duration.ofSeconds(5)
private val CHANGE_MAX_DELAY: Duration = Duration.ofSeconds(30)

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
    val graph = applicationContext.graph
    if (trigger == SyncTrigger.CalendarChange) {
      // Armed before syncing, so a change that lands during the sync triggers another one.
      graph.workManager.watchCalendar(slot = 1 - inputData.getInt(KEY_SLOT, 0))
    } else {
      // Repairs the chain if it broke, for example when a run was cancelled before re-arming.
      graph.workManager.watchCalendarChanges()
    }
    graph.sync(trigger, Clock.systemUTC())
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

/**
 * Makes sure a sync runs whenever the calendar database changes, even when the app isn't running
 * (SPEC P5a). A content-URI trigger fires once, so each run arms the next one.
 */
suspend fun WorkManager.watchCalendarChanges() {
  val armed =
    (0..1).any { slot ->
      getWorkInfosForUniqueWorkFlow(changeWorkName(slot)).first().any { !it.state.isFinished }
    }
  if (!armed) watchCalendar(slot = 0)
}

/**
 * The trigger alternates between two unique names: a running request can't replace itself, and
 * appending the next one would leave changes made during the sync unobserved.
 */
private fun WorkManager.watchCalendar(slot: Int) {
  enqueueUniqueWork(
    changeWorkName(slot),
    ExistingWorkPolicy.REPLACE,
    OneTimeWorkRequestBuilder<SyncWorker>()
      .setConstraints(
        Constraints.Builder()
          .addContentUriTrigger(CalendarContract.CONTENT_URI, triggerForDescendants = true)
          .setTriggerContentUpdateDelay(CHANGE_SETTLE)
          .setTriggerContentMaxDelay(CHANGE_MAX_DELAY)
          .build()
      )
      .setInputData(workDataOf(KEY_TRIGGER to SyncTrigger.CalendarChange.name, KEY_SLOT to slot))
      .build(),
  )
}

private fun changeWorkName(slot: Int) = "$SYNC_ON_CHANGE-$slot"
