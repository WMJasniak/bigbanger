package com.wiktor.bigbanger.training

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.nio.file.Files

/**
 * Covers the export/import backup path added after a "keep only today's data" feature
 * destroyed real user data: importBackup must either fully replace every field, or --
 * on any parse failure, including one buried deep in the JSON -- leave the existing
 * state completely untouched. A partial apply here would be the same class of bug.
 */
class TrainingRepoTest {
    private lateinit var repo: TrainingRepo

    @Before
    fun setUp() {
        val dir = Files.createTempDirectory("trainingrepo_test").toFile()
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(dir)
        repo = TrainingRepo(context)
    }

    private fun seedSampleData(repo: TrainingRepo) {
        val hold = repo.addHoldType("20mm Edge")
        val variant = repo.addGripVariant(hold.id, "Front-2 Open")
        repo.recordMaxTest(variant.id, Hand.LEFT, 42.5f)
        repo.logSession(ProgramId.MAX_HANGS)
        repo.setGripSequence(ProgramId.MAX_HANGS, listOf(variant.id))
        repo.setTiming(ProgramId.MAX_HANGS, ProgramTiming(reps = 6, workSeconds = 10, restSeconds = 120))
        repo.setGripEnabled(ProgramId.MAX_HANGS, variant.id, enabled = false)
    }

    @Test
    fun roundTrip_exportThenImport_reproducesEveryField() {
        seedSampleData(repo)
        val backup = repo.exportBackup()

        val otherDir = Files.createTempDirectory("trainingrepo_test_other").toFile()
        val otherContext = mock(Context::class.java)
        `when`(otherContext.filesDir).thenReturn(otherDir)
        val freshRepo = TrainingRepo(otherContext)

        assertTrue(freshRepo.importBackup(backup))

        assertEquals(repo.holdTypes.value, freshRepo.holdTypes.value)
        assertEquals(repo.gripVariants.value, freshRepo.gripVariants.value)
        assertEquals(repo.maxResults.value, freshRepo.maxResults.value)
        assertEquals(repo.sessions.value, freshRepo.sessions.value)
        assertEquals(repo.programGripSequences.value, freshRepo.programGripSequences.value)
        assertEquals(repo.programTiming.value, freshRepo.programTiming.value)
        assertEquals(repo.programDisabledGrips.value, freshRepo.programDisabledGrips.value)
    }

    @Test
    fun importBackup_malformedJson_returnsFalseAndLeavesStateUntouched() {
        seedSampleData(repo)
        val holdTypesBefore = repo.holdTypes.value
        val maxResultsBefore = repo.maxResults.value

        assertFalse(repo.importBackup("this is not json"))

        assertEquals(holdTypesBefore, repo.holdTypes.value)
        assertEquals(maxResultsBefore, repo.maxResults.value)
    }

    @Test
    fun importBackup_validJsonButCorruptPartway_doesNotPartiallyApply() {
        seedSampleData(repo)
        val holdTypesBefore = repo.holdTypes.value
        val gripVariantsBefore = repo.gripVariants.value
        val maxResultsBefore = repo.maxResults.value

        // holdTypes parses fine on its own, but maxResults is missing "hand" --
        // a naive implementation that assigns each field as it parses would end up
        // with the new holdTypes and the old maxResults, half backup/half original.
        val partiallyCorrupt = """
            {
              "holdTypes": [{"id": "new-hold", "name": "Imported Hold"}],
              "gripVariants": [],
              "maxResults": [{"id": "bad", "gripVariantId": "x", "peakKg": 10.0, "timestampMs": 1}],
              "sessions": []
            }
        """.trimIndent()

        assertFalse(repo.importBackup(partiallyCorrupt))

        assertEquals(holdTypesBefore, repo.holdTypes.value)
        assertEquals(gripVariantsBefore, repo.gripVariants.value)
        assertEquals(maxResultsBefore, repo.maxResults.value)
    }

    @Test
    fun commitProgramConfig_persistsAllThreeFieldsTogetherAndSurvivesReload() {
        val dir = Files.createTempDirectory("trainingrepo_test_commit").toFile()
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(dir)
        val repo = TrainingRepo(context)
        val hold = repo.addHoldType("20mm Edge")
        val variant = repo.addGripVariant(hold.id, "Front-2 Open")

        val timing = ProgramTiming(reps = 5, workSeconds = 8, restSeconds = 90)
        repo.commitProgramConfig(ProgramId.WARM_UP, listOf(variant.id), setOf(variant.id), timing)

        // A Save button's whole point is that this survives past the in-memory
        // StateFlow -- reload from the same file a fresh TrainingRepo would read on
        // the next app start, the same way the backup round-trip test above does.
        val reloaded = TrainingRepo(context)
        reloaded.load()
        assertEquals(listOf(variant.id), reloaded.gripSequenceFor(ProgramId.WARM_UP).map { it.id })
        assertEquals(setOf(variant.id), reloaded.disabledGripsFor(ProgramId.WARM_UP))
        assertEquals(timing, reloaded.timingFor(ProgramId.WARM_UP))
    }

    @Test
    fun load_corruptFileOnDisk_doesNotCrashAndKeepsRepoUsable() {
        val dir = Files.createTempDirectory("trainingrepo_test_corrupt").toFile()
        File(dir, "training.json").writeText("{ not valid json at all")
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(dir)
        val corruptRepo = TrainingRepo(context)

        corruptRepo.load()

        assertTrue(corruptRepo.holdTypes.value.isEmpty())
        val hold = corruptRepo.addHoldType("Still works")
        assertEquals("Still works", hold.name)
    }
}
