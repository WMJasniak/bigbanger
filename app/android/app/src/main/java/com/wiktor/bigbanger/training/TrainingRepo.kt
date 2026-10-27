package com.wiktor.bigbanger.training

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Grip types, max-test history, and a session log, persisted as one JSON
 * snapshot in app-private storage (house style -- see update/Updater.kt's
 * UpdateSettings and the sibling Morse app's Repo.kt; no Room/DataStore).
 *
 * Saves on every mutation rather than batching (unlike Morse's Repo, which
 * saves only in onStop()): losing a recorded max test or a logged session to
 * a process kill is not acceptable, and mutations here are rare (seconds
 * apart at most), so the extra disk writes cost nothing in practice.
 */
class TrainingRepo(context: Context) {
    private val file = File(context.filesDir, "training.json")

    private val _gripTypes = MutableStateFlow<List<GripType>>(emptyList())
    val gripTypes = _gripTypes.asStateFlow()

    private val _maxResults = MutableStateFlow<List<MaxTestResult>>(emptyList())
    val maxResults = _maxResults.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionLogEntry>>(emptyList())
    val sessions = _sessions.asStateFlow()

    fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())

            val grips = root.optJSONArray("gripTypes") ?: JSONArray()
            _gripTypes.value = (0 until grips.length()).map {
                val g = grips.getJSONObject(it)
                GripType(g.getString("id"), g.getString("name"))
            }

            val results = root.optJSONArray("maxResults") ?: JSONArray()
            _maxResults.value = (0 until results.length()).map {
                val r = results.getJSONObject(it)
                MaxTestResult(
                    id = r.getString("id"),
                    gripTypeId = r.getString("gripTypeId"),
                    peakKg = r.getDouble("peakKg").toFloat(),
                    timestampMs = r.getLong("timestampMs"),
                )
            }

            val sessionArray = root.optJSONArray("sessions") ?: JSONArray()
            _sessions.value = (0 until sessionArray.length()).map {
                val s = sessionArray.getJSONObject(it)
                SessionLogEntry(
                    id = s.getString("id"),
                    programId = ProgramId.valueOf(s.getString("programId")),
                    timestampMs = s.getLong("timestampMs"),
                )
            }
        } catch (_: Exception) {
            // Corrupt or unreadable state: start fresh rather than crash.
        }
    }

    private fun save() {
        try {
            val root = JSONObject().apply {
                put(
                    "gripTypes",
                    JSONArray().apply {
                        _gripTypes.value.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
                    },
                )
                put(
                    "maxResults",
                    JSONArray().apply {
                        _maxResults.value.forEach {
                            put(
                                JSONObject()
                                    .put("id", it.id)
                                    .put("gripTypeId", it.gripTypeId)
                                    .put("peakKg", it.peakKg.toDouble())
                                    .put("timestampMs", it.timestampMs),
                            )
                        }
                    },
                )
                put(
                    "sessions",
                    JSONArray().apply {
                        _sessions.value.forEach {
                            put(
                                JSONObject()
                                    .put("id", it.id)
                                    .put("programId", it.programId.name)
                                    .put("timestampMs", it.timestampMs),
                            )
                        }
                    },
                )
            }
            file.writeText(root.toString())
        } catch (_: Exception) {
            // Best-effort; nothing useful to do if the write fails.
        }
    }

    fun addGripType(name: String): GripType {
        val grip = GripType(UUID.randomUUID().toString(), name)
        _gripTypes.update { it + grip }
        save()
        return grip
    }

    fun deleteGripType(id: String) {
        _gripTypes.update { list -> list.filterNot { it.id == id } }
        _maxResults.update { list -> list.filterNot { it.gripTypeId == id } }
        save()
    }

    fun recordMaxTest(gripTypeId: String, peakKg: Float): MaxTestResult {
        val result = MaxTestResult(UUID.randomUUID().toString(), gripTypeId, peakKg, System.currentTimeMillis())
        _maxResults.update { it + result }
        save()
        return result
    }

    fun logSession(programId: ProgramId) {
        _sessions.update { it + SessionLogEntry(UUID.randomUUID().toString(), programId, System.currentTimeMillis()) }
        save()
    }

    /** Average of up to the last 3 results (newest-first) for this grip; null if none exist. */
    fun currentMaxKg(gripTypeId: String): Float? {
        val recent = _maxResults.value
            .filter { it.gripTypeId == gripTypeId }
            .sortedByDescending { it.timestampMs }
            .take(3)
        if (recent.isEmpty()) return null
        return recent.sumOf { it.peakKg.toDouble() }.toFloat() / recent.size
    }

    fun resultsFor(gripTypeId: String): List<MaxTestResult> =
        _maxResults.value.filter { it.gripTypeId == gripTypeId }.sortedByDescending { it.timestampMs }
}
