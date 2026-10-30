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
 * Hold types, their grip variants, max-test history, and a session log,
 * persisted as one JSON snapshot in app-private storage (house style -- see
 * update/Updater.kt's UpdateSettings and the sibling Morse app's Repo.kt; no
 * Room/DataStore).
 *
 * Saves on every mutation rather than batching (unlike Morse's Repo, which
 * saves only in onStop()): losing a recorded max test or a logged session to
 * a process kill is not acceptable, and mutations here are rare (seconds
 * apart at most), so the extra disk writes cost nothing in practice.
 */
class TrainingRepo(context: Context) {
    private val file = File(context.filesDir, "training.json")

    private val _holdTypes = MutableStateFlow<List<HoldType>>(emptyList())
    val holdTypes = _holdTypes.asStateFlow()

    private val _gripVariants = MutableStateFlow<List<GripVariant>>(emptyList())
    val gripVariants = _gripVariants.asStateFlow()

    private val _maxResults = MutableStateFlow<List<MaxTestResult>>(emptyList())
    val maxResults = _maxResults.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionLogEntry>>(emptyList())
    val sessions = _sessions.asStateFlow()

    /** Per-program, user-chosen and ordered grip variant IDs -- e.g. always running Max
     * Hangs against the same grips in the same order instead of picking them each time. */
    private val _programGripSequences = MutableStateFlow<Map<ProgramId, List<String>>>(emptyMap())
    val programGripSequences = _programGripSequences.asStateFlow()

    /** Per-program sets/reps and work/rest timing -- see [ProgramTiming]. Missing from
     * the map means "use that program's own default" ([Programs.defaultTimingFor]). */
    private val _programTiming = MutableStateFlow<Map<ProgramId, ProgramTiming>>(emptyMap())
    val programTiming = _programTiming.asStateFlow()

    /** Grip variant IDs temporarily excluded from a run without losing their place in
     * [programGripSequences] -- e.g. skipping an injured grip for a while, then
     * re-enabling it later with its ordering intact instead of re-adding it at the end. */
    private val _programDisabledGrips = MutableStateFlow<Map<ProgramId, Set<String>>>(emptyMap())
    val programDisabledGrips = _programDisabledGrips.asStateFlow()

    fun load() {
        if (!file.exists()) return
        var needsSaveAfterLoad = false
        try {
            val root = JSONObject(file.readText())

            val holds = root.optJSONArray("holdTypes") ?: JSONArray()
            _holdTypes.value = (0 until holds.length()).map {
                val h = holds.getJSONObject(it)
                HoldType(h.getString("id"), h.getString("name"))
            }

            val variants = root.optJSONArray("gripVariants") ?: JSONArray()
            _gripVariants.value = (0 until variants.length()).map {
                val v = variants.getJSONObject(it)
                GripVariant(v.getString("id"), v.getString("holdTypeId"), v.getString("name"))
            }

            val results = root.optJSONArray("maxResults") ?: JSONArray()
            val loadedResults = (0 until results.length()).map {
                val r = results.getJSONObject(it)
                MaxTestResult(
                    id = r.getString("id"),
                    gripVariantId = r.getString("gripVariantId"),
                    hand = Hand.valueOf(r.getString("hand")),
                    peakKg = r.getDouble("peakKg").toFloat(),
                    timestampMs = r.getLong("timestampMs"),
                )
            }
            // One-time cleanup: earlier versions seeded a placeholder 1kg "max" per
            // default grip/hand so a target band existed before any real Max Testing --
            // a real measurement is never going to land on exactly 1.000000kg, so this
            // is a safe way to drop the placeholders without touching real ones.
            _maxResults.value = loadedResults.filterNot { it.peakKg == 1f }
            if (_maxResults.value.size != loadedResults.size) needsSaveAfterLoad = true

            val sessionArray = root.optJSONArray("sessions") ?: JSONArray()
            _sessions.value = (0 until sessionArray.length()).map {
                val s = sessionArray.getJSONObject(it)
                SessionLogEntry(
                    id = s.getString("id"),
                    programId = ProgramId.valueOf(s.getString("programId")),
                    timestampMs = s.getLong("timestampMs"),
                )
            }

            val sequences = root.optJSONObject("programGripSequences")
            _programGripSequences.value = sequences?.keys()?.asSequence()?.mapNotNull { key ->
                val programId = runCatching { ProgramId.valueOf(key) }.getOrNull() ?: return@mapNotNull null
                val ids = sequences.getJSONArray(key)
                programId to (0 until ids.length()).map { ids.getString(it) }
            }?.toMap() ?: emptyMap()

            val timing = root.optJSONObject("programTiming")
            _programTiming.value = timing?.keys()?.asSequence()?.mapNotNull { key ->
                val programId = runCatching { ProgramId.valueOf(key) }.getOrNull() ?: return@mapNotNull null
                val t = timing.getJSONObject(key)
                programId to ProgramTiming(
                    reps = t.getInt("reps"),
                    workSeconds = t.getInt("workSeconds"),
                    restSeconds = t.getInt("restSeconds"),
                )
            }?.toMap() ?: emptyMap()

            val disabled = root.optJSONObject("programDisabledGrips")
            _programDisabledGrips.value = disabled?.keys()?.asSequence()?.mapNotNull { key ->
                val programId = runCatching { ProgramId.valueOf(key) }.getOrNull() ?: return@mapNotNull null
                val ids = disabled.getJSONArray(key)
                programId to (0 until ids.length()).map { ids.getString(it) }.toSet()
            }?.toMap() ?: emptyMap()
        } catch (_: Exception) {
            // Corrupt or unreadable state: start fresh rather than crash.
        }
        if (needsSaveAfterLoad) save()
    }

    private fun save() {
        try {
            val root = JSONObject().apply {
                put(
                    "holdTypes",
                    JSONArray().apply {
                        _holdTypes.value.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
                    },
                )
                put(
                    "gripVariants",
                    JSONArray().apply {
                        _gripVariants.value.forEach {
                            put(JSONObject().put("id", it.id).put("holdTypeId", it.holdTypeId).put("name", it.name))
                        }
                    },
                )
                put(
                    "maxResults",
                    JSONArray().apply {
                        _maxResults.value.forEach {
                            put(
                                JSONObject()
                                    .put("id", it.id)
                                    .put("gripVariantId", it.gripVariantId)
                                    .put("hand", it.hand.name)
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
                put(
                    "programGripSequences",
                    JSONObject().apply {
                        _programGripSequences.value.forEach { (programId, variantIds) ->
                            put(programId.name, JSONArray(variantIds))
                        }
                    },
                )
                put(
                    "programTiming",
                    JSONObject().apply {
                        _programTiming.value.forEach { (programId, timing) ->
                            put(
                                programId.name,
                                JSONObject()
                                    .put("reps", timing.reps)
                                    .put("workSeconds", timing.workSeconds)
                                    .put("restSeconds", timing.restSeconds),
                            )
                        }
                    },
                )
                put(
                    "programDisabledGrips",
                    JSONObject().apply {
                        _programDisabledGrips.value.forEach { (programId, ids) ->
                            put(programId.name, JSONArray(ids.toList()))
                        }
                    },
                )
            }
            file.writeText(root.toString())
        } catch (_: Exception) {
            // Best-effort; nothing useful to do if the write fails.
        }
    }

    fun addHoldType(name: String): HoldType {
        val holdType = HoldType(UUID.randomUUID().toString(), name)
        _holdTypes.update { it + holdType }
        save()
        return holdType
    }

    /** Deletes a hold type along with every grip variant under it and their max results. */
    fun deleteHoldType(id: String) {
        val variantIds = _gripVariants.value.filter { it.holdTypeId == id }.map { it.id }.toSet()
        _holdTypes.update { list -> list.filterNot { it.id == id } }
        _gripVariants.update { list -> list.filterNot { it.holdTypeId == id } }
        _maxResults.update { list -> list.filterNot { it.gripVariantId in variantIds } }
        save()
    }

    fun addGripVariant(holdTypeId: String, name: String): GripVariant {
        val variant = GripVariant(UUID.randomUUID().toString(), holdTypeId, name)
        _gripVariants.update { it + variant }
        save()
        return variant
    }

    fun deleteGripVariant(id: String) {
        _gripVariants.update { list -> list.filterNot { it.id == id } }
        _maxResults.update { list -> list.filterNot { it.gripVariantId == id } }
        _programGripSequences.update { map -> map.mapValues { (_, ids) -> ids.filterNot { it == id } } }
        _programDisabledGrips.update { map -> map.mapValues { (_, ids) -> ids - id } }
        save()
    }

    /** The ordered grips a program should always use, as set via its own settings
     * screen; empty if the user hasn't configured one (fall back to picking each run). */
    fun gripSequenceFor(programId: ProgramId): List<GripVariant> {
        val ids = _programGripSequences.value[programId] ?: return emptyList()
        return ids.mapNotNull { id -> _gripVariants.value.firstOrNull { it.id == id } }
    }

    fun setGripSequence(programId: ProgramId, variantIds: List<String>) {
        _programGripSequences.update { it + (programId to variantIds) }
        save()
    }

    /** Grips in [gripSequenceFor] that are temporarily skipped for this program (e.g. an
     * injured grip) -- still shown in its settings, in their place, just excluded from a run. */
    fun disabledGripsFor(programId: ProgramId): Set<String> = _programDisabledGrips.value[programId] ?: emptySet()

    fun setGripEnabled(programId: ProgramId, variantId: String, enabled: Boolean) {
        _programDisabledGrips.update { map ->
            val current = map[programId] ?: emptySet()
            map + (programId to if (enabled) current - variantId else current + variantId)
        }
        save()
    }

    /** A program's configured sets/reps and timing, or its own default if never changed. */
    fun timingFor(programId: ProgramId): ProgramTiming = _programTiming.value[programId] ?: Programs.defaultTimingFor(programId)

    fun setTiming(programId: ProgramId, timing: ProgramTiming) {
        _programTiming.update { it + (programId to timing) }
        save()
    }

    /**
     * No Hangs ships with a default grip order (see [Programs.NO_HANGS_DEFAULT_GRIPS]) so
     * it works before the user ever opens its settings, instead of starting out empty like
     * every other program. Seeded once, only the first time its sequence is missing
     * entirely -- not reseeded if the user later clears it on purpose (an explicitly empty
     * list stays empty). Does not invent a max for any of them -- a real one, or an
     * explicit choice to continue without one, is required before training (see
     * BigBangerViewModel.missingMaxesFor).
     */
    fun ensureNoHangsDefaultSeeded() {
        if (_programGripSequences.value.containsKey(ProgramId.NO_HANGS)) return
        val ids = Programs.NO_HANGS_DEFAULT_GRIPS.map { (gripName, _) -> ensureGripVariant(Programs.NO_HANGS_HOLD_TYPE, gripName).id }
        setGripSequence(ProgramId.NO_HANGS, ids)
    }

    fun recordMaxTest(gripVariantId: String, hand: Hand, peakKg: Float): MaxTestResult {
        val result = MaxTestResult(UUID.randomUUID().toString(), gripVariantId, hand, peakKg, System.currentTimeMillis())
        _maxResults.update { it + result }
        save()
        return result
    }

    fun deleteMaxResult(id: String) {
        _maxResults.update { list -> list.filterNot { it.id == id } }
        save()
    }

    fun logSession(programId: ProgramId) {
        _sessions.update { it + SessionLogEntry(UUID.randomUUID().toString(), programId, System.currentTimeMillis()) }
        save()
    }

    /** Average of up to the last 3 results (newest-first) for this variant+hand; null if none exist. */
    fun currentMaxKg(gripVariantId: String, hand: Hand): Float? {
        val recent = _maxResults.value
            .filter { it.gripVariantId == gripVariantId && it.hand == hand }
            .sortedByDescending { it.timestampMs }
            .take(3)
        if (recent.isEmpty()) return null
        return recent.sumOf { it.peakKg.toDouble() }.toFloat() / recent.size
    }

    fun resultsFor(gripVariantId: String, hand: Hand): List<MaxTestResult> =
        _maxResults.value.filter { it.gripVariantId == gripVariantId && it.hand == hand }.sortedByDescending { it.timestampMs }

    /** Looks up a hold type and grip variant by exact name, creating either if missing --
     * used to seed No Hangs' default grip catalog entries without inventing a max for them. */
    fun ensureGripVariant(holdTypeName: String, variantName: String): GripVariant {
        val holdType = _holdTypes.value.firstOrNull { it.name == holdTypeName } ?: addHoldType(holdTypeName)
        return _gripVariants.value.firstOrNull { it.holdTypeId == holdType.id && it.name == variantName }
            ?: addGripVariant(holdType.id, variantName)
    }
}
