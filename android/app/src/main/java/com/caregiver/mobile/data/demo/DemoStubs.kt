package com.caregiver.mobile.data.demo

import kotlinx.coroutines.flow.first

/**
 * Offline stand-ins for the AI backend (Step 6). Both return FIXED sample
 * content labeled "Demo" in the UI. They never generate medical advice and
 * never decide safety: the only flag source is [DemoSafetyRule] applied to
 * recorded notes. The real backend and AI safety logic are untouched.
 */

data class DemoEvidence(val quote: String, val noteId: String)

data class DemoSummary(
    val text: String,
    val evidence: List<DemoEvidence>,
    val uncertainties: List<String>,
    /** Backend-style flag codes; only "FALL_REPORTED", only via DemoSafetyRule. */
    val redFlags: List<String>,
)

class DemoSummaryStub {

    /**
     * Fixed descriptive body per locale (no advice, no inference); evidence
     * quotes come from the recorded notes; flags come only from the demo
     * fall rule. Pure function of its inputs for unit testing; callers pass
     * the notes already scoped to the recipient/period.
     */
    fun summarize(notes: List<NoteEntity>, periodDays: Int, arabic: Boolean): DemoSummary {
        val flags = if (DemoSafetyRule.hasFallFlag(notes)) listOf("FALL_REPORTED") else emptyList()
        val evidence = notes.take(2).map { DemoEvidence(it.text, it.id) }
        val body = if (arabic) {
            "ملخص تجريبي: تصف الملاحظات مزاجًا مستقرًا في معظم الأيام، مع انخفاض الشهية أحيانًا والحاجة أحيانًا إلى مساعدة بسيطة في الحركة."
        } else {
            "Demo summary: notes describe a mostly steady mood, with appetite dipping on some days and occasional need for a little help moving around."
        }
        val uncertainties = if (arabic) {
            listOf("الدواء: سُجّل تناوله على أنه غير مؤكد في بعض الأيام.")
        } else {
            listOf("Medication: recorded as unsure on some days.")
        }
        return DemoSummary(body, evidence, uncertainties, flags)
    }
}

data class DemoPlanItem(
    val text: String,
    val rationale: String,
    /** Medication items are read-only: never editable in the UI. */
    val medication: Boolean,
)

data class DemoPlanVersion(
    val version: Int,
    /** Raw server-style status data (flagged: needs copy keys from owner). */
    val status: String,
    val items: List<DemoPlanItem>,
    val reason: String,
)

class DemoPlanStub {
    private var status = "Suggested"
    private val extraVersions = mutableListOf<DemoPlanVersion>()

    /** Fixed sample proposal; medication item is read-only. */
    fun proposal(arabic: Boolean): List<DemoPlanItem> = if (arabic) {
        listOf(
            DemoPlanItem("وجبات صغيرة متكررة", "الشهية انخفضت في عدة أيام.", false),
            DemoPlanItem("مشي لطيف يوميًا", "الحركة تحتاج أحيانًا إلى مساعدة بسيطة.", false),
            DemoPlanItem("دواء المساء — للقراءة فقط", "الذكاء الاصطناعي لا يغيّر الأدوية.", true),
        )
    } else {
        listOf(
            DemoPlanItem("Small frequent meals", "Appetite dipped on several days.", false),
            DemoPlanItem("Gentle daily walk", "Movement sometimes needed a little help.", false),
            DemoPlanItem("Evening medication — read-only", "AI never changes medication.", true),
        )
    }

    fun versions(arabic: Boolean): List<DemoPlanVersion> {
        val base = listOf(
            DemoPlanVersion(2, status, proposal(arabic), if (arabic) "السبب: إضافة مساعدة" else "Reason: added assistance"),
            DemoPlanVersion(1, "Suggested", proposal(arabic), if (arabic) "الاقتراح الأصلي محفوظ" else "Original suggestion kept"),
        )
        return (extraVersions + base).sortedByDescending { it.version }
    }

    fun accept() {
        status = "Accepted"
    }

    fun dismiss() {
        status = "Dismissed"
    }

    /** Edit-then-accept appends a new in-memory version; medication untouched. */
    fun editAccept(items: List<DemoPlanItem>, arabic: Boolean) {
        val nonMeds = items.filter { !it.medication }
        val meds = proposal(arabic).filter { it.medication }
        val merged = nonMeds + meds
        val next = ((extraVersions + versions(arabic)).maxOfOrNull { it.version } ?: 2) + 1
        extraVersions.add(0, DemoPlanVersion(next, "Edited-and-Accepted", merged, "Edited"))
        status = "Edited-and-Accepted"
    }

    fun reset() {
        status = "Suggested"
        extraVersions.clear()
    }
}
