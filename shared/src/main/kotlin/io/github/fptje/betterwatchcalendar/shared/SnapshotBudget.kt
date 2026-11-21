package io.github.fptje.betterwatchcalendar.shared

/** Target payload size; the Data Layer's hard limit is 100 KB. */
const val SNAPSHOT_BUDGET_BYTES = 80_000

/** Upcoming meetings that always keep their description and attendees. */
const val FULL_DETAIL_COUNT = 3

/**
 * Shrinks [snapshot] until [sizeOf] is within [budgetBytes]: first strips descriptions and
 * attendees from the latest meetings, then, as a last resort, drops the latest meetings and pulls
 * [ScheduleSnapshot.windowEnd] in so the watch treats that time as unknown rather than free. The
 * next [FULL_DETAIL_COUNT] meetings (as of `syncedAt`) are never touched.
 */
fun fitToBudget(
  snapshot: ScheduleSnapshot,
  budgetBytes: Int = SNAPSHOT_BUDGET_BYTES,
  sizeOf: (ScheduleSnapshot) -> Int = { it.encode().size },
): ScheduleSnapshot {
  val candidates = shrinkSteps(snapshot)
  return candidates.firstOrNull { sizeOf(it) <= budgetBytes } ?: candidates.last()
}

/** [snapshot] itself, then each progressively smaller variant [fitToBudget] may settle on. */
private fun shrinkSteps(snapshot: ScheduleSnapshot): Sequence<ScheduleSnapshot> = sequence {
  val meetings = snapshot.meetings
  val keepFull =
    meetings.indices.filter { meetings[it].end > snapshot.syncedAt }.take(FULL_DETAIL_COUNT).toSet()

  var current = snapshot
  yield(current)
  for (i in meetings.indices.reversed().filterNot { it in keepFull }) {
    current = current.copy(meetings = current.meetings.updated(i) { it.stripped() })
    yield(current)
  }

  val keepAtLeast = (keepFull.maxOrNull() ?: -1) + 1
  for (count in meetings.indices.reversed().filter { it >= keepAtLeast }) {
    current =
      current.copy(
        meetings = current.meetings.take(count),
        windowEnd = minOf(snapshot.windowEnd, meetings[count].start),
      )
    yield(current)
  }
}

private fun Meeting.stripped(): Meeting =
  copy(description = "", attendees = emptyList(), moreAttendees = moreAttendees + attendees.size)

private fun <T> List<T>.updated(index: Int, transform: (T) -> T): List<T> = mapIndexed { i, item ->
  if (i == index) transform(item) else item
}
