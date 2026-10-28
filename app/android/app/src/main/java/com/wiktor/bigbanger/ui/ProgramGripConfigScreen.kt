package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.training.ProgramDefinition

/**
 * A program's own settings: which grips it always runs against, and in what order --
 * set once here instead of picking a grip every single time. Empty selection falls
 * back to today's "pick a grip each run" flow (see BigBangerViewModel.resolveProgramForRun).
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("${program.title} grips", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Pick the grips to always use for ${program.title}, in order. Leave empty to pick one each time instead.",
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
                items(selectedIds, key = { "selected-$it" }) { id ->
                    val variant = allVariants.firstOrNull { it.id == id }
                    val holdName = variant?.let { v -> holdTypes.firstOrNull { it.id == v.holdTypeId }?.name } ?: ""
                    val index = selectedIds.indexOf(id)
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (variant != null) "$holdName · ${variant.name}" else "(deleted grip)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { setSelected(moved(selectedIds, index, index - 1)) }, enabled = index > 0) {
                                Text("Up")
                            }
                            TextButton(
                                onClick = { setSelected(moved(selectedIds, index, index + 1)) },
                                enabled = index < selectedIds.lastIndex,
                            ) { Text("Down") }
                            TextButton(onClick = { setSelected(selectedIds.filterNot { it == id }) }) { Text("Remove") }
                        }
                    }
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
                            TextButton(onClick = { setSelected(selectedIds + variant.id) }) { Text("Add") }
                        }
                    }
                }
            }
        }
    }
}

private fun <T> moved(list: List<T>, from: Int, to: Int): List<T> {
    if (to < 0 || to >= list.size) return list
    val mutable = list.toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}
