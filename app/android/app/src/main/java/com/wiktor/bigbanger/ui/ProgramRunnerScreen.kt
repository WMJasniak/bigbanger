package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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

/** ~1 WORK hold of past, ~1 REST of future preview, either side of the live point. */
private const val RUNNER_CHART_WINDOW_SECONDS = 30f

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

    // During REST, preview the *next* step's grip/hand (muted) instead of this step's
    // (there isn't one) -- and keep showing the *previous* step's rep counter instead of
    // letting it go blank. Both exist so nothing in the top block appears/disappears
    // between WORK and REST, which was what made the Pause/Cancel row and the chart
    // shift position every other step.
    val isRest = step.kind == StepKind.REST
    val gripHandText = if (isRest) {
        program.steps.getOrNull(runner.stepIndex + 1)?.let { upcoming -> gripHandLabel(upcoming)?.let { "Next: $it" } }
    } else {
        gripHandLabel(step)
    }
    val repCounterText = if (isRest) program.steps.getOrNull(runner.stepIndex - 1)?.label else step.label

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LinearProgressIndicator(
            progress = { (runner.stepIndex + 1f) / program.steps.size },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            when (step.kind) {
                StepKind.WORK -> "Work"
                StepKind.REST -> "Rest"
                StepKind.PREP -> "Get ready"
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )

        gripHandText?.let {
            Text(
                it,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = if (isRest) FontWeight.Normal else FontWeight.Bold,
                color = if (isRest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Box(modifier = Modifier.height(20.dp)) {
            repCounterText?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        if (phase == RunnerPhase.ConnectionLost) {
            Text(
                "Scale disconnected -- turn it back on to continue. Your progress is saved.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            // The text's own width is measured and used to offset it, so its right edge
            // lands exactly on the container's horizontal middle no matter how many
            // digits are showing -- a fractional-width box wasn't landing there reliably.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                var textWidthPx by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                Text(
                    "${runner.remainingSeconds}s",
                    style = MaterialTheme.typography.displayMedium,
                    onTextLayout = { textWidthPx = it.size.width },
                    modifier = Modifier.offset(
                        x = with(density) { (constraints.maxWidth / 2 - textWidthPx).toDp() },
                    ),
                )
            }
        }

        // Always mounted (never conditionally shown/hidden) so it keeps its slot in the
        // middle of the screen and the Pause/Cancel row below it never moves. The target
        // strip rolls at the same rate as the data and stays anchored a third of the way
        // across so the live point holds still while the data scrolls under it, instead
        // of chasing an edge.
        ForceChart(
            samples = history,
            targetTimeline = runner.targetTimeline(
                pastSeconds = RUNNER_CHART_WINDOW_SECONDS / 3f,
                futureSeconds = RUNNER_CHART_WINDOW_SECONDS * 2f / 3f,
                nowTimestampSeconds = history.lastOrNull()?.timestampSeconds ?: 0f,
            ),
            anchorFraction = 1f / 3f,
            windowSeconds = RUNNER_CHART_WINDOW_SECONDS,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

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

private fun gripHandLabel(step: ProgramStep): String? {
    val grip = step.gripLabel ?: return null
    return if (step.hand != null) "$grip — ${step.hand.displayLabel}" else grip
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
