package com.caregiver.mobile.presentation.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.local.MedicationEntity
import com.caregiver.mobile.test.WithTestOwner
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * The card's guardrails: deleting a tracked medication asks first — a tap
 * on Delete erases the taken history only once the dialog is confirmed —
 * and with no person in the household the + button stays off, because a
 * medication cannot belong to nobody.
 */
@RunWith(AndroidJUnit4::class)
class MedicationsCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val med = MedicationEntity(
        id = "m1",
        recipientId = "r1",
        name = "Amlodipine",
        dose = null,
        schedule = null,
        active = true,
        createdAt = 1,
    )

    private fun medState(): MedicationsState = MedicationsState(meds = listOf(med))

    private fun render(
        people: List<MedicationsPerson> = listOf(MedicationsPerson("r1", "Mom")),
        onDelete: (String) -> Unit = {},
        onAdd: (String, String, String?, String?, Int) -> Unit = { _, _, _, _, _ -> },
    ) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    MedicationsCard(
                        state = medState(),
                        people = people,
                        onSetTaken = { _, _, _ -> },
                        onAdd = onAdd,
                        onUpdate = { _, _, _, _, _ -> },
                        onDelete = onDelete,
                    )
                }
            }
        }
    }

    @Test
    fun menuDeleteAsksTheDialogBeforeDeleting() {
        val deleted = AtomicBoolean(false)
        render(onDelete = { deleted.set(true) })

        compose.onNodeWithContentDescription(
            context.getString(R.string.home_medications_menu),
        ).performClick()
        compose.onNodeWithText(
            context.getString(R.string.home_medications_delete),
        ).performClick()

        // The menu tap alone must not delete anything: the dialog is up,
        // the history still intact.
        assertFalse(deleted.get())
        compose.onNodeWithText(
            context.getString(R.string.home_medications_delete_title),
        ).assertExists()

        compose.onNodeWithText(
            context.getString(R.string.home_medications_delete_confirm),
        ).performClick()
        assertTrue(deleted.get())
    }

    @Test
    fun cancelingTheDialogKeepsTheMedicationAndItsHistory() {
        val deleted = AtomicBoolean(false)
        render(onDelete = { deleted.set(true) })

        compose.onNodeWithContentDescription(
            context.getString(R.string.home_medications_menu),
        ).performClick()
        compose.onNodeWithText(
            context.getString(R.string.home_medications_delete),
        ).performClick()
        compose.onNodeWithText(
            context.getString(R.string.common_cancel),
        ).performClick()

        assertFalse(deleted.get())
    }

    @Test
    fun addingWithoutAnyPersonStaysOff() {
        val added = AtomicBoolean(false)
        render(
            people = emptyList(),
            onAdd = { _, _, _, _, _ -> added.set(true) },
        )

        compose.onNodeWithContentDescription(
            context.getString(R.string.home_medications_add),
        ).assertIsNotEnabled()
        assertFalse(added.get())
    }
}
