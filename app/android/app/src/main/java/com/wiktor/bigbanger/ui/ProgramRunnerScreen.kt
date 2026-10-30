package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.ble.ConnectionState
import com.wiktor.bigbanger.training.GripVariant
import com.wiktor.bigbanger.training.ProgramDefinition
import com.wiktor.bigbanger.training.ProgramRunner
import com.wiktor.bigbanger.training.ProgramStep
import com.wiktor.bigbanger.training.RunnerPhase
import com.wiktor.bigbanger.training.StepKind
import com.wiktor.bigbanger.training.TrainingMode

/** ~1 WORK hold of past, ~1 REST of future preview, either side of the live point. */
private const val RUNNER_CHART_WINDOW_SECONDS = 30f

@Composable
fun ProgramRunnerScreen(
    viewModel: BigBangerViewModel,
    program: ProgramDefinition,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(TrainingMode.FULL) }
    var resolvedProgram by remember { mutableStateOf<ProgramDefinition?>(null) }
    var pendingMissingMax by remember { mutableStateOf<Pair<ProgramDefinition, List<BigBangerViewModel.MissingMax>>?>(null) }
    // Attempts recorded this run (Max Testing only -- every other program's WORK steps
    // have a target and never call onAttemptCompleted), so the session summary below
    // can show and let you clean up just this run's own readings, not every one ever.
    val recordedResultIds = remember { mutableStateListOf<String>() }
    var showSummary by remember { mutableStateOf(false) }
    val runningProgram = resolvedProgram

    if (showSummary) {
        MaxTestingSummaryScreen(viewModel = viewModel, resultIds = recordedResultIds, onDone = onFinished)
        return
    }

    if (runningProgram == null) {
        BackHandler(onBack = onFinished)
        ReadyGate(
            program = program,
            mode = mode,
            onModeChange = { mode = it },
            onTare = { viewModel.tare() },
            onStart = {
                val resolved = viewModel.resolveProgramForRun(program, mode)
                if (resolved != null) {
                    val missing = viewModel.missingMaxesFor(resolved)
                    if (missing.isEmpty()) resolvedProgram = resolved else pendingMissingMax = resolved to missing
                }
            },
        )
        pendingMissingMax?.let { (resolved, missing) ->
            MissingMaxDialog(
                missing = missing,
                onContinueAnyway = {
                    pendingMissingMax = null
                    resolvedProgram = resolved
                },
                onCancel = { pendingMissingMax = null },
            )
        }
        return
    }

    // Every step in a resolved program names its own grip via holdTypeName/gripLabel --
    // resolveProgramForRun always expands against a configured grip sequence.
    fun resolveVariant(step: ProgramStep): GripVariant? {
        val holdTypeName = step.holdTypeName ?: return null
        val label = step.gripLabel ?: return null
        val holdType = viewModel.trainingRepo.holdTypes.value.firstOrNull { it.name == holdTypeName } ?: return null
        return viewModel.trainingRepo.gripVariants.value.firstOrNull { it.holdTypeId == holdType.id && it.name == label }
    }

    val runner = remember(runningProgram) {
        ProgramRunner(
            program = runningProgram,
            maxKgForStep = { step: ProgramStep ->
                val hand = step.hand
                val variant = resolveVariant(step)
                if (variant != null && hand != null) viewModel.trainingRepo.currentMaxKg(variant.id, hand) else null
            },
            scope = scope,
            onSetMeasuring = { active -> if (active) viewModel.startMeasurement() else viewModel.stopMeasurement() },
            currentHistory = { viewModel.weightHistory.value },
            onAttemptCompleted = { step, peak ->
                val variant = resolveVariant(step)
                val hand = step.hand
                if (variant != null && hand != null) {
                    recordedResultIds.add(viewModel.trainingRepo.recordMaxTest(variant.id, hand, peak).id)
                }
            },
        )
    }

    LaunchedEffect(runner) { runner.start() }

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    LaunchedEffect(connectionState) {
        when (connectionState) {
            is ConnectionState.Reconnecting -> runner.pauseForDisconnect()
            is ConnectionState.Connected -> runner.resumeAfterReconnect()
            else -> Unit
        }
    }

    LaunchedEffect(runner.stepIndex) {
        viewModel.setSimulatedTargetBand(runner.targetRangeKg)
    }

    LaunchedEffect(runner.phase) {
        if (runner.phase is RunnerPhase.Finished) {
            viewModel.trainingRepo.logSession(runningProgram.id)
            if (recordedResultIds.isNotEmpty()) showSummary = true else onFinished()
        }
    }

    // Cancel goes through the same Finished handling above (just sets the phase rather
    // than also navigating directly) so an early stop with a few attempts already
    // recorded still gets the same chance to review/delete them.
    BackHandler { runner.cancel() }

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
        runningProgram.steps.getOrNull(runner.stepIndex + 1)?.let { upcoming -> gripHandLabel(upcoming)?.let { "Next: $it" } }
    } else {
        gripHandLabel(step)
    }
    val repCounterText = if (isRest) runningProgram.steps.getOrNull(runner.stepIndex - 1)?.label else step.label

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LinearProgressIndicator(
            progress = { (runner.stepIndex + 1f) / runningProgram.steps.size },
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
            Text(
                "${runner.remainingSeconds}s",
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
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
            isWorking = step.kind == StepKind.WORK,
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
                onClick = { runner.cancel() },
                modifier = Modifier.weight(1f),
            ) { Text("Cancel") }
        }
    }
}

private fun gripHandLabel(step: ProgramStep): String? {
    val grip = step.gripLabel ?: return null
    return if (step.hand != null) "$grip — ${step.hand.displayLabel}" else grip
}

@Composable
private fun ReadyGate(
    program: ProgramDefinition,
    mode: TrainingMode,
    onModeChange: (TrainingMode) -> Unit,
    onTare: () -> Unit,
    onStart: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            program.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        Text("Volume", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainingMode.entries.forEach { entry ->
                FilterChip(
                    selected = entry == mode,
                    onClick = { onModeChange(entry) },
                    label = { Text(entry.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(
            "Tare the scale with no load on it, then start.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 24.dp),
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
private fun MissingMaxDialog(
    missing: List<BigBangerViewModel.MissingMax>,
    onContinueAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("No max recorded") },
        text = {
            Column {
                Text("Run Max Testing first for an accurate target for:")
                missing.forEach { m ->
                    Text(
                        "${m.holdTypeName} · ${m.gripLabel} — ${m.hand.displayLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    "Continuing now runs those with a plain timer and no target band.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onContinueAnyway) { Text("Continue anyway") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** Shown right after a Max Testing run (finished or cancelled early) that recorded at
 * least one attempt -- scoped to just this run's own readings via [resultIds], not
 * every max test ever recorded for these grips, so there is one clear place to catch a
 * slipped grip or false start before it skews the rolling average in Stats. Deleting
 * here uses the same TrainingRepo call Stats itself uses, so either place stays correct. */
@Composable
private fun MaxTestingSummaryScreen(viewModel: BigBangerViewModel, resultIds: List<String>, onDone: () -> Unit) {
    BackHandler(onBack = onDone)

    val allResults by viewModel.trainingRepo.maxResults.collectAsStateWithLifecycle()
    val variants by viewModel.trainingRepo.gripVariants.collectAsStateWithLifecycle()
    val holdTypes by viewModel.trainingRepo.holdTypes.collectAsStateWithLifecycle()
    val results = resultIds.mapNotNull { id -> allResults.firstOrNull { it.id == id } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Session results", style = MaterialTheme.typography.titleLarge)
        Text(
            "Delete any reading that shouldn't count (slipped grip, false start, ...).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (results.isEmpty()) {
            Text("Nothing left -- everything was deleted.", style = MaterialTheme.typography.bodyMedium)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results, key = { it.id }) { result ->
                val variant = variants.firstOrNull { it.id == result.gripVariantId }
                val holdName = variant?.let { v -> holdTypes.firstOrNull { it.id == v.holdTypeId }?.name }
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "${holdName ?: "?"} · ${variant?.name ?: "?"} — ${result.hand.displayLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text("${"%.1f".format(result.peakKg)} kg", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { viewModel.trainingRepo.deleteMaxResult(result.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete this reading")
                        }
                    }
                }
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Done") }
    }
}
