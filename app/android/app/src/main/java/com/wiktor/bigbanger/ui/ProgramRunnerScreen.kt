package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.ble.ConnectionState
import com.wiktor.bigbanger.training.GripType
import com.wiktor.bigbanger.training.ProgramDefinition
import com.wiktor.bigbanger.training.ProgramRunner
import com.wiktor.bigbanger.training.ProgramStep
import com.wiktor.bigbanger.training.RunnerPhase
import com.wiktor.bigbanger.training.StepKind

@Composable
fun ProgramRunnerScreen(
    viewModel: BigBangerViewModel,
    program: ProgramDefinition,
    gripType: GripType?,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val runner = remember(program, gripType) {
        ProgramRunner(
            program = program,
            maxKgForStep = { step: ProgramStep ->
                val fixedGripId = step.gripTypeName?.let { name ->
                    viewModel.trainingRepo.gripTypes.value.firstOrNull { it.name == name }?.id
                }
                (fixedGripId ?: gripType?.id)?.let { viewModel.trainingRepo.currentMaxKg(it) }
            },
            scope = scope,
            onSetMeasuring = { active -> if (active) viewModel.startMeasurement() else viewModel.stopMeasurement() },
            currentHistory = { viewModel.weightHistory.value },
        )
    }
    var isReady by remember { mutableStateOf(false) }

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    LaunchedEffect(connectionState) {
        when (connectionState) {
            is ConnectionState.Reconnecting -> runner.pauseForDisconnect()
            is ConnectionState.Connected -> runner.resumeAfterReconnect()
            else -> Unit
        }
    }

    LaunchedEffect(runner.stepIndex, isReady) {
        if (isReady) viewModel.setSimulatedTargetBand(runner.targetRangeKg)
    }

    LaunchedEffect(runner.phase) {
        if (runner.phase is RunnerPhase.Finished) {
            viewModel.trainingRepo.logSession(program.id)
            onFinished()
        }
    }

    BackHandler {
        runner.cancel()
        onFinished()
    }

    if (!isReady) {
        ReadyGate(
            program = program,
            onTare = { viewModel.tare() },
            onStart = {
                isReady = true
                runner.start()
            },
            onCancel = onFinished,
        )
        return
    }

    val history by viewModel.weightHistory.collectAsStateWithLifecycle()
    val step = runner.currentStep
    val phase = runner.phase

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LinearProgressIndicator(
            progress = { (runner.stepIndex + 1f) / program.steps.size },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            if (step.kind == StepKind.WORK) "Work" else "Rest",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        step.label?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        when (phase) {
            RunnerPhase.ConnectionLost -> {
                Text(
                    "Scale disconnected -- turn it back on to continue. Your progress is saved.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            else -> {
                Text(
                    "${runner.remainingSeconds}s",
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                if (step.kind == StepKind.WORK) {
                    ForceChart(samples = history, targetRangeKg = runner.targetRangeKg)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = { runner.togglePause() },
                enabled = phase == RunnerPhase.Running || phase == RunnerPhase.Paused,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (phase == RunnerPhase.Paused) "Resume" else "Pause")
            }
            Button(
                onClick = { runner.cancel(); onFinished() },
                modifier = Modifier.weight(1f),
            ) { Text("Cancel") }
        }
    }

    val decision = phase as? RunnerPhase.AwaitingAttemptDecision
    if (decision != null && gripType != null) {
        AttemptDecisionDialog(
            peakKg = decision.peakKg,
            currentMaxKg = viewModel.trainingRepo.currentMaxKg(gripType.id),
            onSave = {
                runner.resolveAttempt(save = true) { peak -> viewModel.trainingRepo.recordMaxTest(gripType.id, peak) }
            },
            onRetry = { runner.retryAttempt() },
            onSkip = { runner.resolveAttempt(save = false) {} },
            onFinishEarly = { runner.finishEarly() },
        )
    }
}

@Composable
private fun ReadyGate(program: ProgramDefinition, onTare: () -> Unit, onStart: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onCancel) { Text("Back") }
        Text(
            program.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        Text(
            "Tare the scale with no load on it, then start.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(onClick = onTare, modifier = Modifier.weight(1f)) { Text("Tare") }
            Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Start") }
        }
    }
}

@Composable
private fun AttemptDecisionDialog(
    peakKg: Float,
    currentMaxKg: Float?,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    onFinishEarly: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Attempt result") },
        text = {
            Column {
                Text("Peak: ${"%.1f".format(peakKg)} kg")
                Text(
                    if (currentMaxKg != null) "Current max: ${"%.1f".format(currentMaxKg)} kg" else "No max recorded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onSkip) { Text("Skip") }
                TextButton(onClick = onFinishEarly) { Text("Finish testing") }
            }
        },
    )
}
