package io.github.fptje.betterwatchcalendar.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A full-screen explanation in place of a list, such as an empty or error state. */
@Composable
fun Message(text: String) {
  Text(text, Modifier.padding(24.dp))
}

/** A card above the tabs asking the user to fix something, with [actions] below the text. */
@Composable
fun Banner(title: String, text: String, actions: @Composable ColumnScope.() -> Unit) {
  Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(text)
      actions()
    }
  }
}
