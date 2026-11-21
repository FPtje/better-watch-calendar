package io.github.fptje.betterwatchcalendar.shared

private const val HEX_RADIX = 16

private val htmlComment = Regex("""<!--[\s\S]*?(?:-->|\z)""")
private val lineBreakTags =
  Regex(
    """<br\s*/?>|</?(?:p|div|ul|ol|h[1-6]|tr|blockquote)(?:\s[^>]*)?>""",
    RegexOption.IGNORE_CASE,
  )
private val listItemTag = Regex("""<li(?:\s[^>]*)?>""", RegexOption.IGNORE_CASE)

// Requires a tag name followed by whitespace, `/` or `>`, so the `<https://…>` links and `<a@b.c>`
// addresses that plain-text invites contain are not mistaken for tags.
private val anyTag = Regex("""</?[a-zA-Z][a-zA-Z0-9-]*(?:\s[^>]*)?/?>""")
private val entity = Regex("""&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]+);""")
private val namedEntities =
  mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "ndash" to "–",
    "mdash" to "—",
    "hellip" to "…",
    "bull" to "•",
    "lsquo" to "‘",
    "rsquo" to "’",
    "ldquo" to "“",
    "rdquo" to "”",
    "euro" to "€",
  )

/** Google sync stores rich-text descriptions as HTML (often wrapped in `<html-blob>`). */
internal fun htmlToText(text: String): String =
  text
    .replace(htmlComment, "")
    .replace(lineBreakTags, "\n")
    .replace(listItemTag, "\n• ")
    .replace(anyTag, "")
    .replace(entity) { match -> decodeEntity(match.groupValues[1]) ?: match.value }

private fun decodeEntity(body: String): String? =
  when {
    body.startsWith("#x") || body.startsWith("#X") -> codePoint(body.drop(2).toIntOrNull(HEX_RADIX))
    body.startsWith("#") -> codePoint(body.drop(1).toIntOrNull())
    else -> namedEntities[body.lowercase()]
  }

private fun codePoint(value: Int?): String? =
  value?.takeIf { Character.isValidCodePoint(it) && it != 0 }?.let { String(Character.toChars(it)) }
