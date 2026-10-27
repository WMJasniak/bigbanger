package com.wiktor.bigbanger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wiktor.bigbanger.update.ReleaseInfo

/** Phase of an in-progress update, once the user has tapped "Update". */
sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data class Downloading(val progress: Float) : UpdatePhase
    data class Error(val message: String) : UpdatePhase
}

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    phase: UpdatePhase,
    onUpdate: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = phase is UpdatePhase.Downloading
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Update available — ${release.versionName}") },
        text = {
            Column {
                when (phase) {
                    is UpdatePhase.Downloading -> {
                        Text("Downloading… ${(phase.progress * 100).toInt()}%")
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { phase.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is UpdatePhase.Error -> {
                        Text(
                            "Couldn't download the update: ${phase.message}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    UpdatePhase.Idle -> {
                        Text(
                            release.notes.ifBlank { "A new version is ready to install." },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!downloading) {
                TextButton(onClick = onUpdate) { Text("Update") }
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = onSkip) { Text("Skip this version") }
            }
        },
    )
}
