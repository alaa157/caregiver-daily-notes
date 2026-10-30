package com.caregiver.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.api.AppendVersionRequest
import com.caregiver.mobile.data.api.SuggestPlanRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException

/**
 * The plan workflow in the local backend: only the moves the state machine
 * allows, and one live version per plan.
 */
@RunWith(RobolectricTestRunner::class)
class PlanTransitionTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        runBlocking {
            db.recipientDao().insertAll(
                listOf(RecipientEntity(serverId = "r1", name = "R1", active = true)),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun statuses(planId: String, apis: LocalBackendApis): Map<Int, String> =
        apis.plans().versions(planId).associate { it.version to it.status }

    private suspend fun illegalTransition(block: suspend () -> Unit): Int {
        try {
            block()
            throw AssertionError("expected an illegal transition to be rejected")
        } catch (e: HttpException) {
            return e.code()
        }
    }

    @Test
    fun aStaleVersionChainCannotOverwriteANewerOne() = runTest {
        val original = PlanEntity(id = "p1", recipientId = "r1", versionsJson = "[1]", updatedAt = 1)
        db.planDao().upsert(original)
        db.planDao().upsert(original.copy(versionsJson = "[2]", updatedAt = 2))

        val changed = db.planDao().updateIfUnchanged(
            id = "p1",
            expectedVersionsJson = original.versionsJson,
            versionsJson = "[stale]",
            updatedAt = 3,
        )

        assertEquals(0, changed)
        assertEquals("[2]", db.planDao().byId("p1")?.versionsJson)
    }

    @Test
    fun suggestingForAMissingRecipientFailsWithoutWriting() = runTest {
        val apis = LocalBackendApis(db)
        try {
            apis.plans().suggest(SuggestPlanRequest("missing", listOf("item1")))
            throw AssertionError("expected a missing recipient to be rejected")
        } catch (e: NoSuchElementException) {
            assertTrue(e.message.orEmpty().contains("missing"))
        }
        assertTrue(apis.plans().list().isEmpty())
    }

    @Test
    fun acceptingASuggestedPlanWorks() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().accept(plan.id)
        assertEquals(mapOf(1 to "Accepted"), statuses(plan.id, apis))
    }

    @Test
    fun suggestingAgainAddsAVersionToTheExistingSuggestedPlan() = runTest {
        val apis = LocalBackendApis(db)
        val original = apis.plans().suggest(SuggestPlanRequest("r1", listOf("first")))
        val updated = apis.plans().suggest(SuggestPlanRequest("r1", listOf("second")))

        assertEquals(original.id, updated.id)
        assertEquals(1, apis.plans().list().size)
        assertEquals(listOf("first", "second"), apis.plans().versions(original.id).map { it.items.single() })
    }

    @Test
    fun acceptingADismissedPlanIsRejected() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().dismiss(plan.id)
        assertEquals(422, illegalTransition { apis.plans().accept(plan.id) })
        assertEquals(mapOf(1 to "Dismissed"), statuses(plan.id, apis))
    }

    @Test
    fun acceptingAnArchivedPlanIsRejected() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().archive(plan.id)
        assertEquals(422, illegalTransition { apis.plans().accept(plan.id) })
        assertEquals(422, illegalTransition { apis.plans().dismiss(plan.id) })
        assertEquals(422, illegalTransition { apis.plans().editAccept(plan.id) })
        assertEquals(mapOf(1 to "Archived"), statuses(plan.id, apis))
    }

    @Test
    fun dismissingAnAcceptedPlanIsRejected() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().accept(plan.id)
        assertEquals(422, illegalTransition { apis.plans().dismiss(plan.id) })
        assertEquals(mapOf(1 to "Accepted"), statuses(plan.id, apis))
    }

    @Test
    fun acceptingAnEditedPlanIsAllowed() = runTest {
        // The server accepts a plan that was already edited and accepted.
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().editAccept(plan.id)
        apis.plans().accept(plan.id)
        assertEquals(mapOf(1 to "Accepted"), statuses(plan.id, apis))
    }

    @Test
    fun anEditedVersionSupersedesTheAcceptedOne() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().accept(plan.id)
        apis.plans().appendVersion(plan.id, AppendVersionRequest(status = "Edited-and-Accepted"))
        assertEquals(
            mapOf(1 to "Archived", 2 to "Edited-and-Accepted"),
            statuses(plan.id, apis),
        )
    }

    @Test
    fun twoVersionsAreNeverBothAccepted() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().accept(plan.id)
        apis.plans().appendVersion(plan.id, AppendVersionRequest(status = "Accepted"))
        val all = apis.plans().versions(plan.id)
        assertEquals(1, all.count { it.status == "Accepted" })
        assertEquals(
            mapOf(1 to "Archived", 2 to "Accepted"),
            statuses(plan.id, apis),
        )
    }

    @Test
    fun appendingToAnArchivedPlanIsRejected() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().archive(plan.id)
        assertEquals(
            422,
            illegalTransition {
                apis.plans().appendVersion(plan.id, AppendVersionRequest(status = "Edited-and-Accepted"))
            },
        )
        assertEquals(mapOf(1 to "Archived"), statuses(plan.id, apis))
    }

    @Test
    fun appendingADismissedVersionToAnAcceptedPlanIsRejected() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().accept(plan.id)
        assertEquals(
            422,
            illegalTransition {
                apis.plans().appendVersion(plan.id, AppendVersionRequest(status = "Dismissed"))
            },
        )
    }

    @Test
    fun aNewVersionCannotBeSuggested() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        assertEquals(
            422,
            illegalTransition {
                apis.plans().appendVersion(plan.id, AppendVersionRequest(status = "Suggested"))
            },
        )
    }

    @Test
    fun aDismissedPlanCanStillBeEditedAndAccepted() = runTest {
        val apis = LocalBackendApis(db)
        val plan = apis.plans().suggest(SuggestPlanRequest("r1", listOf("item1")))
        apis.plans().dismiss(plan.id)
        apis.plans().editAccept(plan.id)
        assertEquals(mapOf(1 to "Edited-and-Accepted"), statuses(plan.id, apis))
    }
}
