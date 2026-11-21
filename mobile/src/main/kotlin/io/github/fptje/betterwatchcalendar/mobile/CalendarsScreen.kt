package io.github.fptje.betterwatchcalendar.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arrow.core.Either
import io.github.fptje.betterwatchcalendar.shared.AccountCalendars
import io.github.fptje.betterwatchcalendar.shared.CalendarInfo
import io.github.fptje.betterwatchcalendar.shared.CalendarOverrides
import io.github.fptje.betterwatchcalendar.shared.groupByAccount
import io.github.fptje.betterwatchcalendar.shared.toggleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UNSYNCED_ALPHA = 0.5f

/** Google calendars grouped by account, each with an include toggle (SPEC P2, P3). */
@Composable
fun CalendarsScreen() {
  val graph = LocalContext.current.graph
  val scope = rememberCoroutineScope()
  var calendars by remember { mutableStateOf<Either<ReadError, List<CalendarInfo>>?>(null) }
  // Accounts can be added or removed in Android Settings while the app is in the background.
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    scope.launch { calendars = withContext(Dispatchers.IO) { readCalendars(graph.resolver) } }
  }
  val overrides by graph.settings.overrides.collectAsStateWithLifecycle(initialValue = null)

  val loaded = calendars
  val currentOverrides = overrides
  when {
    loaded == null || currentOverrides == null -> Unit
    loaded is Either.Left -> Message(calendarsErrorText(loaded.value))
    loaded is Either.Right ->
      LazyColumn(Modifier.fillMaxWidth()) {
        groupByAccount(loaded.value).forEach { account ->
          accountSection(account, currentOverrides) { calendar, included ->
            scope.launch {
              graph.settings.setIncluded(calendar.id, included)
              graph.workManager.syncNow(SyncTrigger.Toggle)
            }
          }
        }
      }
  }
}

private fun calendarsErrorText(error: ReadError): String =
  when (error) {
    ReadError.NoGoogleCalendars ->
      "No Google calendars found. Add a Google account in Android Settings → Accounts and " +
        "turn on calendar sync for it."
    else -> error.describe()
  }

private fun LazyListScope.accountSection(
  account: AccountCalendars,
  overrides: CalendarOverrides,
  onToggle: (CalendarInfo, Boolean) -> Unit,
) {
  val (synced, unsynced) = account.calendars.partition { it.synced }
  item(key = "account:${account.accountName}") {
    Text(
      account.accountName,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
  }
  items(synced, key = { "calendar:${it.id.value}" }) {
    CalendarRow(it, toggleState(it, overrides)) { included -> onToggle(it, included) }
  }
  // Room resources and old shared calendars are usually unsynced; folded away to cut the noise.
  if (unsynced.isNotEmpty()) {
    item(key = "unsynced:${account.accountName}") {
      var expanded by rememberSaveable { mutableStateOf(false) }
      Column {
        Text(
          (if (expanded) "Hide " else "Show ") +
            if (unsynced.size == 1) "1 calendar that isn't synced"
            else "${unsynced.size} calendars that aren't synced",
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
        )
        if (expanded) unsynced.forEach { CalendarRow(it, toggleState(it, overrides), null) }
      }
    }
  }
  item(key = "divider:${account.accountName}") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
}

@Composable
private fun CalendarRow(calendar: CalendarInfo, checked: Boolean, onToggle: ((Boolean) -> Unit)?) {
  Row(
    Modifier.fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .alpha(if (calendar.synced) 1f else UNSYNCED_ALPHA),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(Modifier.size(12.dp).background(Color(calendar.color), CircleShape))
    Column(Modifier.weight(1f)) {
      Text(calendar.displayName.ifEmpty { calendar.raw.ownerAccount })
      if (!calendar.synced) {
        Text(
          "Not synced. Enable it in Google Calendar settings",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    Switch(checked = checked, onCheckedChange = onToggle, enabled = onToggle != null)
  }
}
