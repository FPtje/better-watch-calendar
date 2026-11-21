package io.github.fptje.betterwatchcalendar.mobile

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google accounts the app may see. Android ignores `requestSync` for any other account, and the
 * calendar permission doesn't make an account visible; picking it in the system account chooser
 * does. Visibility reveals only the account name, never its tokens.
 */
private fun visibleGoogleAccounts(context: Context): Set<String> =
  AccountManager.get(context).getAccountsByType(GOOGLE_ACCOUNT_TYPE).map { it.name }.toSet()

/** Accounts with calendars on the phone that `requestSync` can't reach yet. */
private fun hiddenGoogleAccounts(context: Context, resolver: ContentResolver): List<String> {
  val visible = visibleGoogleAccounts(context)
  return calendarAccounts(resolver).filterNot { it in visible }
}

private fun calendarAccounts(resolver: ContentResolver): List<String> =
  readCalendars(resolver).getOrNull().orEmpty().map { it.raw.accountName }.distinct().sorted()

/**
 * Asks Google's sync adapter to refresh the visible accounts' calendars from the server now (SPEC
 * P6). Best effort: the resulting database change starts a [SyncTrigger.CalendarChange] sync, and
 * the caller pushes what the phone already has in the meantime.
 */
fun requestGoogleSync(context: Context, resolver: ContentResolver) {
  val visible = visibleGoogleAccounts(context)
  val (reachable, hidden) = calendarAccounts(resolver).partition { it in visible }
  val extras =
    Bundle().apply {
      putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
      putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
    }
  reachable.forEach {
    ContentResolver.requestSync(
      Account(it, GOOGLE_ACCOUNT_TYPE),
      CalendarContract.AUTHORITY,
      extras,
    )
  }
  Log.i(TAG, "requested Google sync for ${reachable.size} accounts, ${hidden.size} not visible")
}

/** Offers the account chooser for each account that "Sync now" can't refresh from Google. */
@Composable
fun AccountAccessBanner() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var hidden by remember { mutableStateOf(emptyList<String>()) }
  val refresh = {
    scope.launch {
      hidden = withContext(Dispatchers.IO) { hiddenGoogleAccounts(context, context.graph.resolver) }
    }
  }
  // Re-checked on resume: accounts can be added or removed in Settings meanwhile.
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
  val chooser = rememberLauncherForActivityResult(StartActivityForResult()) { refresh() }
  if (hidden.isEmpty()) return
  Banner(
    "Refresh from Google",
    "Sync now can ask Google to fetch the latest events right away, but Android only " +
      "allows that for accounts you pick. The app learns only the account name.",
  ) {
    hidden.forEach { name ->
      OutlinedButton(
        onClick = {
          chooser.launch(
            AccountManager.newChooseAccountIntent(
              null,
              listOf(Account(name, GOOGLE_ACCOUNT_TYPE)),
              arrayOf(GOOGLE_ACCOUNT_TYPE),
              null,
              null,
              null,
              null,
            )
          )
        }
      ) {
        Text(name)
      }
    }
  }
}
