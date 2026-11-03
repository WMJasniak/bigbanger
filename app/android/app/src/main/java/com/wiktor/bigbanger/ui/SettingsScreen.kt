package com.wiktor.bigbanger.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current

    var backupStatus by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupStatus = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.trainingRepo.exportBackup().toByteArray()) }
            "Backup saved."
        } catch (_: Exception) {
            "Couldn't save the backup."
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImportUri = uri
    }

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
            title = "Backup data",
            subtitle = "Save everything to a file you pick -- a cloud storage app if you have one installed",
            onClick = {
                backupStatus = null
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exportLauncher.launch("bigbanger-backup-$stamp.json")
            },
        )
        SettingsRow(
            title = "Restore backup",
            subtitle = "Replace all current data with a backup file",
            onClick = {
                backupStatus = null
                importLauncher.launch(arrayOf("application/json"))
            },
        )
        backupStatus?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
        SettingsRow(
            title = "Check for updates",
            subtitle = updateStatus,
            onClick = onCheckUpdates,
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "This replaces everything currently on this device -- every hold, grip, max test and session -- " +
                        "with what's in the chosen file. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    backupStatus = try {
                        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        if (text != null && viewModel.trainingRepo.importBackup(text)) {
                            "Backup restored."
                        } else {
                            "That file isn't a valid backup -- nothing was changed."
                        }
                    } catch (_: Exception) {
                        "That file isn't a valid backup -- nothing was changed."
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            },
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
