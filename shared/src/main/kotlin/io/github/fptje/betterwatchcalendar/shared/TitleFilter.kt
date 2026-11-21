package io.github.fptje.betterwatchcalendar.shared

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.regex.PatternSyntaxException

/**
 * Strips a leading `your_name_here / ` or trailing ` / your_name_here`: the owner's name is in
 * nearly every 1:1 title, so the few characters the complication shows go to the other person.
 * Users replace the placeholder with their own name in the phone's Settings tab.
 */
const val DEFAULT_TITLE_FILTER = "(?i)^\\s*your_name_here\\s*/\\s*|\\s*/\\s*your_name_here\\s*$"

/** Why a title filter pattern doesn't compile. [index] is -1 when the position is unknown. */
data class TitleFilterError(val description: String, val pattern: String, val index: Int)

/** Compiles a title filter. A blank pattern is no filter: `null`. */
fun compileTitleFilter(pattern: String): Either<TitleFilterError, Regex?> =
  if (pattern.isBlank()) null.right()
  else
    try {
      Regex(pattern).right()
    } catch (e: PatternSyntaxException) {
      TitleFilterError(e.description, pattern, e.index).left()
    }

/**
 * The complication title: [title] with every match of [filter] removed. Only the title; the spoken
 * description stays whole. Falls back to [title] if nothing would be left.
 */
fun shortTitle(title: String, filter: Regex?): String =
  if (filter == null) title else title.replace(filter, "").ifBlank { title }

/** The snapshot's filter; an invalid one (which the phone never sends) filters nothing. */
internal fun ScheduleSnapshot.compiledTitleFilter(): Regex? =
  compileTitleFilter(titleFilter).getOrNull()
