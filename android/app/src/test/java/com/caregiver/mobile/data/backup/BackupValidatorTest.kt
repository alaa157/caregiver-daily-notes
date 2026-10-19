package com.caregiver.mobile.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Semantic validation: ids, relationships, dates, options and the plan JSON.
 * Every rule is deterministic and read-only.
 */
class BackupValidatorTest {

    private fun issues(data: BackupData): List<BackupIssue> = BackupValidator.validate(data)

    private fun kinds(data: BackupData, kind: BackupIssueKind) =
        issues(data).filter { it.kind == kind }

    @Test
    fun aWellFormedBackupHasNoIssues() {
        assertTrue(issues(backupData()).isEmpty())
    }

    @Test
    fun emptyCollectionsAreValid() {
        assertTrue(issues(backupData(emptyList(), emptyList(), emptyList(), emptyList())).isEmpty())
    }

    @Test
    fun duplicateRecipientIdsAreReported() {
        val data = backupData(
            recipients = listOf(backupRecipient("r1"), backupRecipient("r1", name = "Other")),
        )
        assertEquals(1, kinds(data, BackupIssueKind.DUPLICATE_ID).size)
        assertEquals(BackupEntity.RECIPIENT, kinds(data, BackupIssueKind.DUPLICATE_ID).single().entity)
    }

    @Test
    fun duplicateNoteIdsAreReported() {
        val data = backupData(notes = listOf(backupNote("n1"), backupNote("n1", date = "2026-10-02")))
        assertEquals(BackupEntity.NOTE, kinds(data, BackupIssueKind.DUPLICATE_ID).single().entity)
    }

    @Test
    fun duplicateAddendumAndPlanIdsAreReported() {
        val data = backupData(
            addenda = listOf(backupAddendum("a1"), backupAddendum("a1")),
            plans = listOf(backupPlan("p1"), backupPlan("p1")),
        )
        val duplicates = kinds(data, BackupIssueKind.DUPLICATE_ID)
        assertEquals(2, duplicates.size)
        assertTrue(duplicates.any { it.entity == BackupEntity.ADDENDUM })
        assertTrue(duplicates.any { it.entity == BackupEntity.PLAN })
    }

    @Test
    fun aNoteReferencingAMissingRecipientIsReported() {
        val data = backupData(notes = listOf(backupNote(recipientId = "gone")))
        assertEquals(BackupEntity.NOTE, kinds(data, BackupIssueKind.BROKEN_REFERENCE).single().entity)
    }

    @Test
    fun anAddendumReferencingAMissingNoteIsReported() {
        val data = backupData(addenda = listOf(backupAddendum(noteId = "gone")))
        assertEquals(BackupEntity.ADDENDUM, kinds(data, BackupIssueKind.BROKEN_REFERENCE).single().entity)
    }

    @Test
    fun aPlanReferencingAMissingRecipientIsReported() {
        val data = backupData(plans = listOf(backupPlan(recipientId = "gone")))
        assertEquals(BackupEntity.PLAN, kinds(data, BackupIssueKind.BROKEN_REFERENCE).single().entity)
    }

    @Test
    fun blankIdsAndNamesAreReported() {
        val data = backupData(recipients = listOf(backupRecipient(serverId = "", name = " ")))
        val issues = issues(data)
        assertTrue(issues.any { it.entity == BackupEntity.RECIPIENT && it.kind == BackupIssueKind.MISSING_ID })
        assertTrue(issues.any { it.entity == BackupEntity.RECIPIENT && it.kind == BackupIssueKind.MISSING_VALUE })
    }

    @Test
    fun anInvalidNoteDateIsReported() {
        val data = backupData(notes = listOf(backupNote(date = "01/10/2026")))
        assertEquals(BackupEntity.NOTE, kinds(data, BackupIssueKind.INVALID_DATE).single().entity)
    }

    @Test
    fun anInvalidAddendumTimestampIsReported() {
        val data = backupData(addenda = listOf(backupAddendum(createdAt = "yesterday")))
        assertEquals(BackupEntity.ADDENDUM, kinds(data, BackupIssueKind.INVALID_DATE).single().entity)
    }

    @Test
    fun painOutsideZeroToTenIsReported() {
        assertTrue(kinds(backupData(notes = listOf(backupNote(pain = 11))), BackupIssueKind.INVALID_PAIN).isNotEmpty())
        assertTrue(kinds(backupData(notes = listOf(backupNote(pain = -1))), BackupIssueKind.INVALID_PAIN).isNotEmpty())
    }

    @Test
    fun aBlankMoodIsReportedAsMissingValue() {
        val data = backupData(notes = listOf(backupNote(mood = " ")))
        assertEquals(BackupEntity.NOTE, kinds(data, BackupIssueKind.MISSING_VALUE).single().entity)
    }

    @Test
    fun overlongOptionGroupsAreReported() {
        val data = backupData(notes = listOf(backupNote(appetite = "x".repeat(33))))
        assertEquals(BackupEntity.NOTE, kinds(data, BackupIssueKind.INVALID_OPTION).single().entity)
    }

    @Test
    fun noteTextIsNeverLengthRestricted() {
        // Notes are free text; only option groups have a wire cap.
        val data = backupData(notes = listOf(backupNote(text = "ط".repeat(5_000))))
        assertTrue(issues(data).isEmpty())
    }

    @Test
    fun planVersionsMustBeAJsonArray() {
        val notJson = backupData(plans = listOf(backupPlan(versionsJson = "not json")))
        assertEquals(BackupEntity.PLAN, kinds(notJson, BackupIssueKind.INVALID_PLAN_JSON).single().entity)

        val notAnArray = backupData(plans = listOf(backupPlan(versionsJson = """{"version":1}""")))
        assertEquals(BackupEntity.PLAN, kinds(notAnArray, BackupIssueKind.INVALID_PLAN_JSON).single().entity)
    }

    @Test
    fun aHostilePlanJsonLengthIsRejectedBeforeParsing() {
        val data = backupData(plans = listOf(backupPlan(versionsJson = "[" + "1,".repeat(200_000))))
        assertEquals(BackupEntity.PLAN, kinds(data, BackupIssueKind.INVALID_PLAN_JSON).single().entity)
    }
}
