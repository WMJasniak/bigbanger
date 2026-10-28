package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel

/** Top-level settings menu: a handful of consistently-styled rows (same Card shape as
 * Home's tiles), with holds/grips editing tucked one layer deeper -- see [HoldsSettingsScreen]. */
@Composable
fun SettingsScreen(
    viewModel: BigBangerViewModel,
    updateStatus: String?,
    onCheckUpdates: () -> Unit,
    onOpenHolds: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

        SettingsRow(
            title = "Dark theme",
            onClick = { viewModel.setDarkTheme(!isDarkTheme) },
            trailing = { Switch(checked = isDarkTheme, onCheckedChange = viewModel::setDarkTheme) },
        )
        SettingsRow(
            title = "Holds & grips",
            subtitle = "Add, edit or remove what you train",
            onClick = onOpenHolds,
        )
        SettingsRow(
            title = "Check for updates",
            subtitle = updateStatus,
            onClick = onCheckUpdates,
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing?.invoke()
        }
    }
}
