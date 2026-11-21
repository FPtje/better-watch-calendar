package io.github.fptje.betterwatchcalendar.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arrow.core.Either
import io.github.fptje.betterwatchcalendar.shared.DEFAULT_TITLE_FILTER
import io.github.fptje.betterwatchcalendar.shared.TitleFilterError
import io.github.fptje.betterwatchcalendar.shared.compileTitleFilter
import io.github.fptje.betterwatchcalendar.shared.shortTitle
import kotlinx.coroutines.launch

private const val EXAMPLES = 5

/** Characters of the pattern shown on each side of a syntax error's position. */
private const val ERROR_CONTEXT = 14

/** The complication title filter, with a live preview on recently synced meetings. */
@Composable
fun SettingsScreen() {
  val graph = LocalContext.current.graph
  val saved by graph.settings.titleFilter.collectAsStateWithLifecycle(initialValue = null)
  val entries by graph.log.entries.collectAsStateWithLifecycle(initialValue = null)
  val savedPattern = saved ?: return
  val loggedEntries = entries ?: return
  TitleFilterEditor(savedPattern, upcomingTitles(loggedEntries, System.currentTimeMillis()))
}

@Composable
private fun TitleFilterEditor(saved: String, examples: List<String>?) {
  val graph = LocalContext.current.graph
  val scope = rememberCoroutineScope()
  // Keyed on the saved value, so a save elsewhere resets the field instead of leaving a stale edit.
  var text by rememberSaveable(saved) { mutableStateOf(saved) }
  val compiled = remember(text) { compileTitleFilter(text) }

  Column(
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Complication title filter", style = MaterialTheme.typography.titleMedium)
    Text(
      "The meeting bubble on your watch face shows only a few characters of the meeting " +
        "title. This filter removes text that you do not need, so that the important words " +
        "are visible. For example, many 1:1 meetings have titles such as \"Alex / Sam\". If " +
        "your name is Alex, the filter removes \"Alex / \" and the meeting bubble shows " +
        "\"Sam\". To use this example, replace \"your_name_here\" with your name. The filter " +
        "is a regular expression. The app removes each part of the title that matches it.",
      style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
      value = text,
      onValueChange = { text = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Regular expression") },
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
      isError = compiled.isLeft(),
      supportingText = (compiled as? Either.Left)?.let { { PatternError(it.value) } },
      keyboardOptions =
        KeyboardOptions(
          capitalization = KeyboardCapitalization.None,
          autoCorrectEnabled = false,
          keyboardType = KeyboardType.Ascii,
        ),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        enabled = compiled.isRight() && text != saved,
        onClick = {
          scope.launch {
            graph.settings.setTitleFilter(text)
            graph.workManager.syncNow(SyncTrigger.SettingsChange)
          }
        },
      ) {
        Text("Save")
      }
      TextButton(
        enabled = text != DEFAULT_TITLE_FILTER,
        onClick = { text = DEFAULT_TITLE_FILTER },
      ) {
        Text("Reset to default")
      }
    }
    if (compiled is Either.Right) Examples(examples, compiled.value)
  }
}

@Composable
private fun PatternError(error: TitleFilterError) {
  Column {
    Text(
      when {
        error.index < 0 -> error.description
        // Java reports unclosed groups and the like just past the last character.
        error.index >= error.pattern.length -> "${error.description} at the end of the pattern"
        else -> "${error.description} at position ${error.index + 1}"
      }
    )
    if (error.index >= 0) {
      val (snippet, caret) = errorSnippet(error.pattern, error.index)
      Text(
        "$snippet\n${" ".repeat(caret)}^",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
      )
    }
  }
}

/** A window of [pattern] around [index], and the caret's column within it. */
private fun errorSnippet(pattern: String, index: Int): Pair<String, Int> {
  val from = (index - ERROR_CONTEXT).coerceAtLeast(0)
  val to = (index + ERROR_CONTEXT).coerceAtMost(pattern.length)
  val prefix = if (from > 0) "…" else ""
  val suffix = if (to < pattern.length) "…" else ""
  return "$prefix${pattern.substring(from, to)}$suffix" to prefix.length + index - from
}

@Composable
private fun Examples(titles: List<String>?, filter: Regex?) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Upcoming meetings", style = MaterialTheme.typography.titleSmall)
    when {
      titles == null -> Text("Sync once to see examples from your calendar.")
      titles.isEmpty() -> Text("The last sync found no upcoming meetings.")
      else -> titles.forEach { ExampleRow(it, shortTitle(it, filter)) }
    }
  }
}

@Composable
private fun ExampleRow(before: String, after: String) {
  Column {
    Text(
      before,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      "→ $after",
      fontWeight = if (after != before) FontWeight.Medium else null,
    )
  }
}

/**
 * Titles of the next [EXAMPLES] meetings in the latest successful sync, current ones included. Null
 * when nothing was ever sent.
 */
private fun upcomingTitles(entries: List<SyncLogEntry>, now: Long): List<String>? =
  entries
    .firstOrNull { it.error == null && it.payloadBytes != null }
    ?.meetings
    ?.filter { it.end > now }
    ?.take(EXAMPLES)
    ?.map { it.title }
