package dev.falcop.betterwatchcalendar.shared

const val MAX_DESCRIPTION_LENGTH = 2000

/**
 * Plain text for the watch: HTML converted, conferencing boilerplate removed (the room line already
 * says "Google Meet" etc.), blank lines collapsed, and capped at [maxLength] characters.
 */
fun cleanDescription(raw: String, maxLength: Int = MAX_DESCRIPTION_LENGTH): String =
  raw.let(::htmlToText).let(::removeConferencingBoilerplate).let(::collapseBlankLines).let {
    truncate(it, maxLength)
  }

// --- Conferencing boilerplate -----------------------------------------------------------------

/**
 * Google Calendar's "Please do not edit this section" block between two `-::~:~::~:~…::-` lines.
 */
private val meetDelimitedBlock = Regex("""-::~[:~]*::-[\s\S]*?(?:-::~[:~]*::-|\z)""")

/** A line that starts (after bullets and an optional `(US)` country tag) with an intl. number. */
private val phoneLine = Regex("""^[\s•*·-]*(?:\([A-Z]{2}\)\s*)?\+\d""")
private val ipLine = Regex("""^[\s•*·-]*\d{1,3}(?:\.\d{1,3}){3}\b""")

private class LineBlock(val starts: (String) -> Boolean, val continues: (String) -> Boolean)

private fun String.containsAny(needles: List<String>) = needles.any {
  contains(it, ignoreCase = true)
}

private const val MIN_SEPARATOR_LENGTH = 3
private const val MIN_TEAMS_RULE_LENGTH = 10

private fun String.isSeparator(): Boolean {
  val t = trim()
  return t.length >= MIN_SEPARATOR_LENGTH && t.all { it in "-_─━—=*~" }
}

private val meetBlock =
  LineBlock(
    starts = { it.trim().let { t -> t.startsWith("Join with Google Meet", ignoreCase = true) } },
    continues = { line ->
      line.containsAny(
        listOf(
          "meet.google.com",
          "tel.meet",
          "Join with Google Meet",
          "Join by phone",
          "Or dial",
          "More phone numbers",
          "Learn more about Meet",
          "PIN:",
          "Please do not edit this section",
        )
      ) || phoneLine.containsMatchIn(line)
    },
  )

private val meetPhoneBlock =
  LineBlock(
    starts = { it.trim().startsWith("Join by phone", ignoreCase = true) },
    continues = meetBlock.continues,
  )

private val zoomBlock =
  LineBlock(
    starts = { line ->
      line.contains("inviting you to a scheduled Zoom meeting", ignoreCase = true) ||
        line.trim().startsWith("Join Zoom Meeting", ignoreCase = true)
    },
    continues = { line ->
      line.containsAny(
        listOf(
          "zoom",
          "Meeting ID",
          "Passcode",
          "Password",
          "One tap mobile",
          "Dial by your location",
          "Find your local number",
          "Join by SIP",
          "Join by H.323",
          "Join by Skype",
          "Join instructions",
        )
      ) || phoneLine.containsMatchIn(line) || ipLine.containsMatchIn(line) || line.isSeparator()
    },
  )

internal fun removeConferencingBoilerplate(text: String): String {
  val lines = text.replace(meetDelimitedBlock, "\n").lines()
  return listOf(meetBlock, meetPhoneBlock, zoomBlock)
    .fold(lines) { acc, rule -> acc.removeBlocks(rule) }
    .let(::removeTeamsBlocks)
    .joinToString("\n")
}

/**
 * Removes each block that begins at a [LineBlock.starts] line and runs over following blank or
 * [LineBlock.continues] lines. Separator lines just above the block (an invite's "──────────"
 * frame) go too. Invites stop at the first ordinary line, so text written after them survives.
 */
private fun List<String>.removeBlocks(rule: LineBlock): List<String> {
  fun inBlock(line: String) = line.isBlank() || rule.continues(line) || rule.starts(line)
  val out = mutableListOf<String>()
  var i = 0
  while (i < size) {
    if (!rule.starts(this[i])) {
      out += this[i++]
      continue
    }
    while (out.isNotEmpty() && (out.last().isBlank() || out.last().isSeparator())) out.removeLast()
    i++
    while (i < size && inBlock(this[i])) i++
    out += ""
  }
  return out
}

private fun String.isUnderscoreRule(): Boolean =
  trim().let { it.length >= MIN_TEAMS_RULE_LENGTH && it.all { c -> c == '_' } }

private val teamsMarkers =
  listOf(
    "Microsoft Teams",
    "teams.microsoft.com",
    "aka.ms/JoinTeamsMeeting",
    "Conference ID",
    "Dial in by phone",
    "Call in (audio only)",
    "Meeting options",
  )

/**
 * Teams invites sit between long `____` rules, and newer ones add an inner rule before the dial-in
 * part. A rule only counts when the text after it is Teams boilerplate, because people also use
 * underscore lines as plain separators.
 */
private fun removeTeamsBlocks(lines: List<String>): List<String> {
  val rules = lines.indices.filter { lines[it].isUnderscoreRule() }
  fun segmentAfter(k: Int): List<String> =
    lines.subList(rules[k] + 1, rules.getOrNull(k + 1) ?: lines.size)
  fun List<String>.isTeams() = joinToString("\n").containsAny(teamsMarkers)

  val startK = rules.indices.firstOrNull { segmentAfter(it).isTeams() } ?: return lines
  val head = lines.subList(0, rules[startK]) + ""
  return if (startK + 1 >= rules.size) {
    head // Without a closing rule the invite runs to the end of the description.
  } else {
    val endK =
      generateSequence(startK + 1) { k ->
          (k + 1).takeIf { it < rules.size && segmentAfter(k).isTeams() }
        }
        .last()
    head + removeTeamsBlocks(lines.subList(rules[endK] + 1, lines.size))
  }
}

// --- Whitespace and length --------------------------------------------------------------------

private val invisibleChars = Regex("[\u200B\u200C\u200D\uFEFF]")
private val blankLineRuns = Regex("""\n{3,}""")

internal fun collapseBlankLines(text: String): String =
  text
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace('\u00A0', ' ')
    .replace(invisibleChars, "")
    .lines()
    .joinToString("\n") { it.trimEnd() }
    .replace(blankLineRuns, "\n\n")
    .trim()

private fun truncate(text: String, maxLength: Int): String {
  if (text.length <= maxLength) return text
  var cut = maxLength - 1
  if (Character.isHighSurrogate(text[cut - 1])) cut--
  return text.substring(0, cut).trimEnd() + "…"
}
