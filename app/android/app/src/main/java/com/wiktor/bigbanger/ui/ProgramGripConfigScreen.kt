package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.training.ProgramDefinition
import com.wiktor.bigbanger.training.ProgramId
import com.wiktor.bigbanger.training.ProgramTiming
import com.wiktor.bigbanger.training.Programs
import com.wiktor.bigbanger.training.TrainingMode
import kotlin.math.roundToInt

/**
 * A program's own settings: which grips it always runs against and in what order, its
 * sets/reps, and its work/rest timing -- set once here instead of picking a grip (and
 * living with built-in numbers) every single time. A program with nothing configured
 * here can't be run (see BigBangerViewModel.resolveProgramForRun); the Home screen
 * prompts the user back here if they try.
 */
@Composable
fun ProgramGripConfigScreen(viewModel: BigBangerViewModel, program: ProgramDefinition, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val holdTypes by viewModel.trainingRepo.holdTypes.collectAsStateWithLifecycle()
    val allVariants by viewModel.trainingRepo.gripVariants.collectAsStateWithLifecycle()
    val sequences by viewModel.trainingRepo.programGripSequences.collectAsStateWithLifecycle()
    val timings by viewModel.trainingRepo.programTiming.collectAsStateWithLifecycle()
    val disabledGrips by viewModel.trainingRepo.programDisabledGrips.collectAsStateWithLifecycle()
    val selectedIds = sequences[program.id] ?: emptyList()
    val timing = timings[program.id] ?: Programs.defaultTimingFor(program.id)
    val disabledIds = disabledGrips[program.id] ?: emptySet()

    fun setSelected(ids: List<String>) = viewModel.trainingRepo.setGripSequence(program.id, ids)
    fun setTiming(newTiming: ProgramTiming) = viewModel.trainingRepo.setTiming(program.id, newTiming)

    // Full mode (no volume cut) for this preview -- the Ready gate's own Fast/Express
    // choice is a per-run thing, not part of what's being configured here.
    val resolved = remember(selectedIds, timing, disabledIds) { viewModel.resolveProgramForRun(program, TrainingMode.FULL) }
    val cycleTimeLabel = resolved?.let { formatDuration(it.steps.sumOf { s -> s.durationSeconds }) }
        ?: "Enable a grip below to see the full cycle time"

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("${program.title} settings", style = MaterialTheme.typography.titleLarge)
        Text(
            "Full cycle: $cycleTimeLabel",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    "Grips",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    "Pick the grips to always use, in order. Long-press and drag to reorder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (selectedIds.isNotEmpty()) {
                item {
                    Text(
                        "Uncheck a grip to skip it for now (e.g. if you're injured) without losing its place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                item(key = "selected-list") {
                    ReorderableGripList(
                        ids = selectedIds,
                        labelFor = { id ->
                            val variant = allVariants.firstOrNull { it.id == id }
                            val holdName = variant?.let { v -> holdTypes.firstOrNull { it.id == v.holdTypeId }?.name } ?: ""
                            if (variant != null) "$holdName · ${variant.name}" else "(deleted grip)"
                        },
                        enabledFor = { id -> id !in disabledIds },
                        onToggleEnabled = { id, enabled -> viewModel.trainingRepo.setGripEnabled(program.id, id, enabled) },
                        onRemove = { id -> setSelected(selectedIds.filterNot { it == id }) },
                        onReorder = { newOrder -> setSelected(newOrder) },
                    )
                }
            }

            item {
                Text(
                    "Add a grip",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            holdTypes.forEach { holdType ->
                val available = allVariants.filter { it.holdTypeId == holdType.id && it.id !in selectedIds }
                if (available.isNotEmpty()) {
                    item(key = "hold-${holdType.id}") {
                        Text(
                            holdType.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                    items(available, key = { it.id }) { variant ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(variant.name, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { setSelected(selectedIds + variant.id) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add ${variant.name}")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Sets & reps",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
            }
            if (program.id == ProgramId.NO_HANGS) {
                item(key = "no-hangs-protocol-toggle") {
                    val useProtocolDefaults = timing.reps <= 0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Use the protocol's own reps per grip", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = useProtocolDefaults,
                            onCheckedChange = { checked -> setTiming(timing.copy(reps = if (checked) 0 else 4)) },
                        )
                    }
                    if (!useProtocolDefaults) {
                        StepperRow(
                            label = "Reps per grip",
                            value = timing.reps,
                            min = 1,
                            onValueChange = { setTiming(timing.copy(reps = it)) },
                        )
                    }
                }
            } else {
                item(key = "reps-stepper") {
                    StepperRow(
                        label = repsLabelFor(program.id),
                        value = timing.reps,
                        min = 1,
                        onValueChange = { setTiming(timing.copy(reps = it)) },
                    )
                }
            }

            item {
                Text(
                    "Timing",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
            }
            item(key = "work-stepper") {
                StepperRow(
                    label = "Work (seconds)",
                    value = timing.workSeconds,
                    min = 1,
                    step = 5,
                    onValueChange = { setTiming(timing.copy(workSeconds = it)) },
                )
            }
            item(key = "rest-stepper") {
                StepperRow(
                    label = "Rest (seconds)",
                    value = timing.restSeconds,
                    min = 0,
                    step = 10,
                    onValueChange = { setTiming(timing.copy(restSeconds = it)) },
                )
            }
        }
    }
}

private fun repsLabelFor(id: ProgramId): String = when (id) {
    ProgramId.MAX_HANGS -> "Reps per grip"
    ProgramId.MAX_TESTING -> "Attempts per grip"
    ProgramId.WARM_UP -> "Ramp repeats (sets)"
    ProgramId.NO_HANGS -> "Reps per grip"
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

/** [step] lets a field like Rest (seconds), which sits at 120 by default, move in
 * chunks instead of needing dozens of taps to get anywhere -- reps/attempts/sets fields
 * keep the default of 1, since those are usually small numbers where every unit counts. */
@Composable
private fun StepperRow(label: String, value: Int, min: Int, step: Int = 1, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - step).coerceAtLeast(min)) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = { onValueChange(value + step) }) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}

/**
 * A plain (non-lazy) drag-to-reorder list of fixed-height rows -- small enough (a
 * handful of grips) that manual offset math beats pulling in a reorder library.
 *
 * The drag gesture lives on the handle icon alone, via plain [detectDragGestures] (no
 * long-press wait): touching and moving the handle grabs the row immediately, the way a
 * dedicated handle is expected to behave, while the rest of the row (and the enclosing
 * LazyColumn's own scroll) is left completely alone since nothing there starts a drag.
 * The grabbed row pops visually -- it scales up slightly, lifts with a stronger shadow,
 * and tints -- and a short haptic tick on grab confirms it physically, not just visually.
 *
 * Each row is wrapped in [key] by its id: without it, Compose matches these rows to
 * their *slot position* in the Column rather than to the id they render, so mid-drag
 * reordering reassigned a row's remembered gesture/animation state to whatever id ended
 * up at that position instead of following the dragged item -- the drag visibly lost
 * track of itself. [key] is what `LazyColumn`'s own `items(key = ...)` gives for free;
 * a plain `Column.forEach` needs it spelled out.
 */
@Composable
private fun ReorderableGripList(
    ids: List<String>,
    labelFor: (String) -> String,
    enabledFor: (String) -> Boolean,
    onToggleEnabled: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val haptics = LocalHapticFeedback.current

    var order by remember(ids) { mutableStateOf(ids) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        order.forEach { id ->
            key(id) {
                val isDragging = id == draggingId
                val scale by animateFloatAsState(if (isDragging) 1.03f else 1f, label = "gripRowScale")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(vertical = 3.dp)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = if (isDragging) 16f else 0f
                        }
                        .zIndex(if (isDragging) 1f else 0f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragging) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            CardDefaults.cardColors().containerColor
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .pointerInput(id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = id
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                            onReorder(order)
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            dragOffset += delta.y
                                            val currentIndex = order.indexOf(id)
                                            val targetIndex = (currentIndex + (dragOffset / rowHeightPx).roundToInt())
                                                .coerceIn(0, order.lastIndex)
                                            if (targetIndex != currentIndex) {
                                                val reordered = order.toMutableList()
                                                val item = reordered.removeAt(currentIndex)
                                                reordered.add(targetIndex, item)
                                                order = reordered
                                                dragOffset -= (targetIndex - currentIndex) * rowHeightPx
                                            }
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val enabled = enabledFor(id)
                        Checkbox(checked = enabled, onCheckedChange = { onToggleEnabled(id, it) })
                        Text(
                            labelFor(id),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                        IconButton(onClick = { onRemove(id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}
