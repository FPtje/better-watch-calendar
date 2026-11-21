package io.github.fptje.betterwatchcalendar.mobile

import android.util.Log
import arrow.core.Either
import arrow.core.raise.either
import io.github.fptje.betterwatchcalendar.shared.BuildReport
import io.github.fptje.betterwatchcalendar.shared.LOOKAHEAD
import io.github.fptje.betterwatchcalendar.shared.ScheduleProtocol
import io.github.fptje.betterwatchcalendar.shared.ScheduleSnapshot
import io.github.fptje.betterwatchcalendar.shared.buildReport
import io.github.fptje.betterwatchcalendar.shared.fitToBudget
import io.github.fptje.betterwatchcalendar.shared.isIncluded
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** How far back instances are read, so a meeting that just started is still in the snapshot. */
private val LOOKBEHIND: Duration = Duration.ofHours(1)

/** Reads the included calendars, pushes a snapshot to the watch, and records the attempt. */
suspend fun AppGraph.sync(trigger: SyncTrigger, clock: Clock): SyncLogEntry {
  val now = clock.millis()
  if (trigger == SyncTrigger.Manual)
    withContext(Dispatchers.IO) { requestGoogleSync(context, resolver) }
  val windowEnd = now + LOOKAHEAD.toMillis()
  val read = withContext(Dispatchers.IO) { readSchedule(now - LOOKBEHIND.toMillis(), windowEnd) }
  val entry =
    read.fold(
      ifLeft = { error ->
        SyncLogEntry(now, trigger, clock.millis() - now, error = error.describe())
      },
      ifRight = { report ->
        val snapshot =
          fitToBudget(
            ScheduleSnapshot(
              schemaVersion = ScheduleProtocol.SCHEMA_VERSION,
              syncedAt = now,
              windowEnd = windowEnd,
              meetings = report.meetings.map { it.meeting },
              titleFilter = settings.titleFilter.first(),
            )
          )
        val sent = sendSnapshot(dataClient, snapshot)
        logEntry(trigger, now, clock.millis() - now, report, snapshot, sent)
      },
    )
  log.add(entry)
  Log.i(TAG, "sync $trigger: ${entry.payloadBytes ?: "-"} bytes, ${entry.error ?: "ok"}")
  return entry
}

private suspend fun AppGraph.readSchedule(from: Long, to: Long): Either<ReadError, BuildReport> =
  either {
    val overrides = settings.current()
    val included =
      readCalendars(resolver).bind().filter { isIncluded(it, overrides) }.map { it.raw }
    val instances = readInstances(resolver, included.map { it.id }, from, to).bind()
    val attendees = readAttendees(resolver, instances.map { it.eventId }).bind()
    buildReport(included, instances, attendees)
  }

private fun logEntry(
  trigger: SyncTrigger,
  at: Long,
  durationMs: Long,
  report: BuildReport,
  snapshot: ScheduleSnapshot,
  sent: Either<SendError, Int>,
): SyncLogEntry {
  val accountOf = report.meetings.associate { it.meeting.id to it.accountName }
  return SyncLogEntry(
    at = at,
    trigger = trigger,
    durationMs = durationMs,
    accounts =
      report.perAccount.entries
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
        .map { AccountCount(it.key, it.value) },
    payloadBytes = sent.getOrNull(),
    error = sent.leftOrNull()?.message,
    skipped = report.skipped.mapKeys { it.key.name },
    duplicates = report.duplicates,
    // Logged as sent, so trimming by the size budget shows up here too.
    meetings =
      snapshot.meetings.map {
        LoggedMeeting(
          start = it.start,
          end = it.end,
          title = it.title,
          room = it.room,
          account = accountOf[it.id].orEmpty(),
          attendees = it.attendees.size + it.moreAttendees,
          descriptionChars = it.description.length,
        )
      },
  )
}

fun ReadError.describe(): String =
  when (this) {
    ReadError.PermissionDenied -> "Calendar permission not granted"
    ReadError.NoGoogleCalendars -> "No Google calendars on this phone"
    is ReadError.ProviderFailure -> "Calendar provider: $message"
  }
