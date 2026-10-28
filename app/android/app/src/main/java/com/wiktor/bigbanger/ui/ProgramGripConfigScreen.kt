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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlin.math.roundToInt

/**
 * A program's own settings: which grips it always runs against, and in what order --
 * set once here instead of picking a grip every single time. A program with nothing
 * configured here can't be run (see BigBangerViewModel.resolveProgramForRun); the Home
 * screen prompts the user back here if they try.
 */
@Composable
fun ProgramGripConfigScreen(viewModel: BigBangerViewModel, program: ProgramDefinition, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val holdTypes by viewModel.trainingRepo.holdTypes.collectAsStateWithLifecycle()
    val allVariants by viewModel.trainingRepo.gripVariants.collectAsStateWithLifecycle()
    val sequences by viewModel.trainingRepo.programGripSequences.collectAsStateWithLifecycle()
    val selectedIds = sequences[program.id] ?: emptyList()

    fun setSelected(ids: List<String>) = viewModel.trainingRepo.setGripSequence(program.id, ids)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("${program.title} grips", style = MaterialTheme.typography.titleLarge)
        Text(
            "Pick the grips to always use for ${program.title}, in order. Long-press and drag to reorder.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (selectedIds.isNotEmpty()) {
                item {
                    Text(
                        "Selected, in order",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
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
                        onRemove = { id -> setSelected(selectedIds.filterNot { it == id }) },
                        onReorder = { newOrder -> setSelected(newOrder) },
                    )
                }
            }

            item {
                Text(
                    "Add a grip",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
            holdTypes.forEach { holdType ->
                val available = allVariants.filter { it.holdTypeId == holdType.id && it.id !in selectedIds }
                if (available.isNotEmpty()) {
                    item(key = "hold-${holdType.id}") {
                        Text(
                            holdType.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
        }
    }
}

/**
 * A plain (non-lazy) drag-to-reorder list of fixed-height rows -- small enough (a
 * handful of grips) that manual offset math beats pulling in a reorder library. Drag
 * starts on long-press so it doesn't fight the enclosing LazyColumn's own scroll drag.
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
