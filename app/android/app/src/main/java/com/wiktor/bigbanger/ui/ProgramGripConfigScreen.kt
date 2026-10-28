package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.training.ProgramDefinition
import com.wiktor.bigbanger.training.ProgramId
import com.wiktor.bigbanger.training.ProgramTiming
import com.wiktor.bigbanger.training.Programs
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
    val selectedIds = sequences[program.id] ?: emptyList()
    val timing = timings[program.id] ?: Programs.defaultTimingFor(program.id)

    fun setSelected(ids: List<String>) = viewModel.trainingRepo.setGripSequence(program.id, ids)
    fun setTiming(newTiming: ProgramTiming) = viewModel.trainingRepo.setTiming(program.id, newTiming)

    val resolved = remember(selectedIds, timing) { viewModel.resolveProgramForRun(program) }
    val cycleTimeLabel = resolved?.let { formatDuration(it.steps.sumOf { s -> s.durationSeconds }) }
        ?: "Add a grip below to see the full cycle time"

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
                item(key = "selected-list") {
                    ReorderableGripList(
                        ids = selectedIds,
                        labelFor = { id ->
                            val variant = allVariants.firstOrNull { it.id == id }
                            val holdName = variant?.let { v -> holdTypes.firstOrNull { it.id == v.holdTypeId }?.name } ?: ""
                            if (variant != null) "$holdName · ${variant.name}" else "(deleted grip)"
                        },
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
                    onValueChange = { setTiming(timing.copy(workSeconds = it)) },
                )
            }
            item(key = "rest-stepper") {
                StepperRow(
                    label = "Rest (seconds)",
                    value = timing.restSeconds,
                    min = 0,
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

@Composable
private fun StepperRow(label: String, value: Int, min: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(min)) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = { onValueChange(value + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}

/**
 * A plain (non-lazy) drag-to-reorder list of fixed-height rows -- small enough (a
 * handful of grips) that manual offset math beats pulling in a reorder library. Drag
 * starts on long-press so it doesn't fight the enclosing LazyColumn's own scroll drag.
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
    onRemove: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    var order by remember(ids) { mutableStateOf(ids) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        order.forEach { id ->
            key(id) {
                val isDragging = id == draggingId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(vertical = 3.dp)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            shadowElevation = if (isDragging) 6f else 0f
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = id
                                    dragOffset = 0f
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
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            labelFor(id),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
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
