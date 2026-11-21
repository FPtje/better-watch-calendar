package io.github.fptje.betterwatchcalendar.shared

import java.time.Duration
import java.time.Instant

/** How long a started meeting stays on the complication instead of the next one. */
internal val CURRENT_WINDOW: Duration = Duration.ofMinutes(10)

/** How far ahead the watch looks for the next meeting. */
val LOOKAHEAD: Duration = Duration.ofDays(7)

/** Canonical meeting order: by start, then end, then id. */
internal val MeetingOrder: Comparator<Meeting> =
  compareBy<Meeting> { it.start }.thenBy { it.end }.thenBy { it.id }

/** Which meetings the watch shows at a given moment. */
internal sealed interface Selection {
  /** Nothing starts within the lookahead. */
  data object Free : Selection

  sealed interface Shown : Selection {
    val meeting: Meeting
    /** The second card in the detail view. */
    val following: Meeting?
  }

  /** [meeting] started less than [CURRENT_WINDOW] ago and hasn't ended. */
  data class Current(override val meeting: Meeting, override val following: Meeting?) : Shown

  data class Next(override val meeting: Meeting, override val following: Meeting?) : Shown
}

/** Lookahead-limited choice of the current (or else next) meeting and the one after it. */
internal fun select(events: List<Meeting>, now: Instant): Selection {
  val t = now.toEpochMilli()
  val horizon = t + LOOKAHEAD.toMillis()
  val sorted = events.sortedWith(MeetingOrder)

  val currentIndex = sorted.indexOfFirst { it.isCurrentAt(t) }
  val shownIndex =
    if (currentIndex >= 0) currentIndex
    else sorted.indexOfFirst { it.start > t && it.start <= horizon }
  if (shownIndex < 0) return Selection.Free

  // A short meeting that started after the current one may already be over; skip it.
  val following = sorted.drop(shownIndex + 1).firstOrNull { it.end > t && it.start <= horizon }
  val shown = sorted[shownIndex]
  return if (currentIndex >= 0) Selection.Current(shown, following)
  else Selection.Next(shown, following)
}

internal fun Meeting.isCurrentAt(epochMillis: Long): Boolean =
  start <= epochMillis && epochMillis < minOf(currentUntil, end)

/** When the meeting stops being "current" on the complication, ignoring its end. */
internal val Meeting.currentUntil: Long
  get() = start + CURRENT_WINDOW.toMillis()
