package com.caregiver.mobile.data.demo

import com.caregiver.mobile.data.api.AddendumDto
import com.caregiver.mobile.data.api.AppendVersionRequest
import com.caregiver.mobile.data.api.AuthApi
import com.caregiver.mobile.data.api.AuthResponse
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.CreateNoteRequest
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.NoteApi
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.PlanApi
import com.caregiver.mobile.data.api.PlanDto
import com.caregiver.mobile.data.api.PlanVersionDto
import com.caregiver.mobile.data.api.RecipientApi
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.api.RegisterRequest
import com.caregiver.mobile.data.api.SignalDto
import com.caregiver.mobile.data.api.SuggestPlanRequest
import com.caregiver.mobile.data.api.SummaryApi
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.api.SummaryRequest
import com.caregiver.mobile.data.api.UpdateRecipientRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Fully offline demo backend behind the [BackendApis] seam.
 *
 * Used only when `DEMO_BACKEND` is true (debug and minified builds). All dates are
 * relative to today so chips, windows and history behave correctly whenever
 * the APK is installed. Writes (recipients, notes, addenda, plan
 * transitions) mutate in-memory state, so create/edit flows can be clicked
 * through end to end; nothing leaves the device. Unknown ids raise a 404
 * [HttpException] so screens render their normal error states.
 */
class DemoBackendApis : BackendApis {
    private val lock = Any()
    private val ids = AtomicInteger(0)

    private val recipients = mutableListOf(
        RecipientDto(id = "r1", name = "ليلى حسن", active = true),
        RecipientDto(id = "r2", name = "John Carter", active = true),
        RecipientDto(id = "r3", name = "سميرة علي", active = false),
    )

    private val notes = mutableListOf(
        NoteDto(
            id = "n1", recipientId = "r1", date = daysAgo(0),
            mood = "good", appetite = "good", sleep = "good", mobility = "walks",
            medicationTaken = "taken", pain = 2, fall = false,
            text = "يوم جيد — appetite good, slept well. Moral is high today.",
        ),
        NoteDto(
            id = "n2", recipientId = "r1", date = daysAgo(2),
            mood = "fair", appetite = "reduced", sleep = "broken", mobility = "assisted",
            medicationTaken = "missed", pain = 8, fall = true,
            text = "سقوط بسيط في الحمام — minor fall in the bathroom, pain 8/10.",
        ),
        NoteDto(
            id = "n3", recipientId = "r2", date = daysAgo(3),
            mood = "good", appetite = "good", sleep = "good", mobility = "walks",
            medicationTaken = "taken", pain = 3, fall = false,
            text = "Steady day. Went for a short walk outside.",
        ),
        NoteDto(
            id = "n4", recipientId = "r2", date = daysAgo(6),
            mood = "bad", appetite = "poor", sleep = "poor", mobility = "bed",
            medicationTaken = "unsure", pain = 5, fall = false,
            text = "Tired day, stayed in bed. Evening dose uncertain.",
        ),
        NoteDto(
            id = "n5", recipientId = "r3", date = daysAgo(10),
            mood = "fair", appetite = "reduced", sleep = "good", mobility = "assisted",
            medicationTaken = "taken", pain = 4, fall = false,
            text = "Routine visit. All stable.",
        ),
    )

    private val addenda = mutableListOf(
        AddendumDto(
            id = "a1", noteId = "n2", createdAt = daysAgo(2) + "T18:30:00",
            text = "تصحيح: لم يحدث كسر، مجرد كدمة — correction: bruise only, no fracture.",
        ),
    )

    private val plans = mutableListOf(
        PlanDto(
            id = "p1",
            recipientId = "r1",
            versions = listOf(
                PlanVersionDto(
                    version = 1, status = "Accepted",
                    items = listOf("Short supervised walk after lunch", "Night light in the hallway"),
                    createdAt = daysAgo(5), reason = "",
                ),
                PlanVersionDto(
                    version = 2, status = "Suggested",
                    items = listOf("Non-slip mat in the bathroom", "Review pain relief with the pharmacist"),
                    createdAt = daysAgo(1), reason = "بناءً على السقوط قبل يومين — based on the fall two days ago",
                ),
            ),
        ),
    )

    private val authApi = object : AuthApi {
        override suspend fun login(body: LoginRequest): AuthResponse = AuthResponse("demo-token")
        override suspend fun register(body: RegisterRequest): AuthResponse = AuthResponse("demo-token")
    }

    private val recipientApi = object : RecipientApi {
        override suspend fun list(): List<RecipientDto> = synchronized(lock) { recipients.toList() }

        override suspend fun create(body: CreateRecipientRequest): RecipientDto = synchronized(lock) {
            RecipientDto(id = "r-demo-${ids.incrementAndGet()}", name = body.name, active = true)
                .also { recipients.add(it) }
        }

        override suspend fun get(id: String): RecipientDto = synchronized(lock) {
            recipients.firstOrNull { it.id == id } ?: throw notFound("recipient")
        }

        override suspend fun update(id: String, body: UpdateRecipientRequest): RecipientDto =
            synchronized(lock) {
                val index = recipients.indexOfFirst { it.id == id }
                if (index < 0) throw notFound("recipient")
                recipients[index].copy(active = body.active).also { recipients[index] = it }
            }
    }

    private val noteApi = object : NoteApi {
        override suspend fun create(body: CreateNoteRequest): NoteDto = synchronized(lock) {
            NoteDto(
                id = "n-demo-${ids.incrementAndGet()}", recipientId = body.recipientId,
                date = LocalDate.now().toString(), mood = body.mood, appetite = body.appetite,
                sleep = body.sleep, mobility = body.mobility, medicationTaken = body.medicationTaken,
                pain = body.pain, fall = body.fall, text = body.text,
            ).also { notes.add(it) }
        }

        override suspend fun detail(id: String): NoteDetailDto = synchronized(lock) {
            val note = notes.firstOrNull { it.id == id } ?: throw notFound("note")
            NoteDetailDto(
                id = note.id, recipientId = note.recipientId, date = note.date, mood = note.mood,
                appetite = note.appetite, sleep = note.sleep, mobility = note.mobility,
                medicationTaken = note.medicationTaken, pain = note.pain, fall = note.fall,
                text = note.text, addenda = addenda.filter { it.noteId == id },
            )
        }

        override suspend fun append(id: String, body: CreateAddendumRequest): AddendumDto =
            synchronized(lock) {
                if (notes.none { it.id == id }) throw notFound("note")
                AddendumDto(
                    id = "a-demo-${ids.incrementAndGet()}", noteId = id,
                    createdAt = LocalDateTime.now().toString(), text = body.text,
                ).also { addenda.add(it) }
            }

        override suspend fun history(
            recipientId: String?,
            from: String?,
            to: String?,
        ): List<NoteDto> = synchronized(lock) {
            notes.filter { note ->
                (recipientId == null || note.recipientId == recipientId) &&
                    (from == null || note.date >= from) &&
                    (to == null || note.date <= to)
            }.sortedByDescending { it.date }
        }
    }

    private val summaryApi = object : SummaryApi {
        override suspend fun signals(
            id: String,
            from: String?,
            to: String?,
        ): List<SignalDto> = synchronized(lock) {
            notes.filter { note ->
                note.recipientId == id &&
                    (from == null || note.date >= from) &&
                    (to == null || note.date <= to)
            }.sortedBy { it.date }.map { note ->
                SignalDto(
                    noteId = note.id, recipientId = note.recipientId, date = note.date,
                    fallReported = note.fall, pain = note.pain,
                    medication = note.medicationTaken,
                    medicationUnverified = note.medicationTaken == "unsure",
                    poorAppetite = note.appetite == "poor", text = note.text,
                )
            }
        }

        override suspend fun summarize(body: SummaryRequest): SummaryDto = synchronized(lock) {
            // ISO-8601 dates sort lexicographically — the same convention
            // the history/signals filters already use. `periodDays` counts
            // back from today, so a 7-day summary never sees last month's
            // notes while describing them as recent.
            val cutoff = LocalDate.now().minusDays(body.periodDays.toLong()).toString()
            val inScope = notes.filter {
                it.recipientId == body.recipientId && it.date >= cutoff
            }.sortedByDescending { it.date }
            val flags = buildList {
                if (inScope.any { it.fall }) add("FALL_REPORTED")
                if (inScope.any { it.pain >= 7 }) add("HIGH_PAIN")
            }
            SummaryDto(
                id = "sum-demo", recipientId = body.recipientId, periodDays = body.periodDays,
                text = if (inScope.isEmpty()) {
                    "No demo notes in the last ${body.periodDays} days. " +
                        "لا توجد ملاحظات تجريبية في هذه الفترة."
                } else {
                    "Demo summary for the last ${body.periodDays} days " +
                        "from ${inScope.size} note(s). ملخص تجريبي — " +
                        "Generated offline; connect a backend for real AI summaries."
                },
                redFlags = flags,
                evidence = inScope.take(2).map {
                    SummaryDto.EvidenceDto(noteId = it.id, quote = it.text.take(80))
                },
                uncertainties = if (inScope.any { it.medicationTaken == "unsure" }) {
                    listOf(
                        SummaryDto.UncertaintyDto(
                            topic = "medication",
                            detail = "An evening dose was marked unsure in the demo notes.",
                        ),
                    )
                } else {
                    emptyList()
                },
            )
        }
    }

    private val planApi = object : PlanApi {
        override suspend fun list(): List<PlanDto> = synchronized(lock) {
            plans.map { it.copy(versions = it.versions.toList()) }
        }

        override suspend fun suggest(body: SuggestPlanRequest): PlanDto = synchronized(lock) {
            PlanDto(
                id = "p-demo-${ids.incrementAndGet()}", recipientId = body.recipientId,
                versions = listOf(
                    PlanVersionDto(
                        version = 1, status = "Suggested", items = body.items,
                        createdAt = LocalDate.now().toString(), reason = "",
                    ),
                ),
            ).also { plans.add(it) }
        }

        override suspend fun versions(id: String): List<PlanVersionDto> = synchronized(lock) {
            findPlan(id).versions.sortedByDescending { it.version }
        }

        override suspend fun appendVersion(id: String, body: AppendVersionRequest): PlanDto =
            synchronized(lock) {
                val plan = findPlan(id)
                val next = (plan.versions.maxOfOrNull { it.version } ?: 0) + 1
                updatePlan(
                    plan.copy(
                        versions = plan.versions + PlanVersionDto(
                            version = next, status = body.status ?: "Edited-and-Accepted",
                            items = body.items.orEmpty(),
                            createdAt = LocalDate.now().toString(), reason = "",
                        ),
                    ),
                )
            }

        override suspend fun accept(id: String): PlanDto = transition(id, "Accepted")
        override suspend fun editAccept(id: String): PlanDto = transition(id, "Edited-and-Accepted")
        override suspend fun dismiss(id: String): PlanDto = transition(id, "Dismissed")
        override suspend fun archive(id: String): PlanDto = transition(id, "Archived")

        private fun transition(id: String, status: String): PlanDto = synchronized(lock) {
            val plan = findPlan(id)
            val latest = plan.versions.maxByOrNull { it.version } ?: throw notFound("plan")
            updatePlan(
                plan.copy(
                    versions = plan.versions.map {
                        if (it.version == latest.version) it.copy(status = status) else it
                    },
                ),
            )
        }

        private fun findPlan(id: String): PlanDto =
            plans.firstOrNull { it.id == id } ?: throw notFound("plan")

        private fun updatePlan(updated: PlanDto): PlanDto {
            plans[plans.indexOfFirst { it.id == updated.id }] = updated
            return updated
        }
    }

    override suspend fun auth(): AuthApi = authApi
    override suspend fun recipients(): RecipientApi = recipientApi
    override suspend fun notes(): NoteApi = noteApi
    override suspend fun summaries(): SummaryApi = summaryApi
    override suspend fun plans(): PlanApi = planApi

    companion object {
        private fun daysAgo(n: Long): String = LocalDate.now().minusDays(n).toString()

        private fun notFound(what: String): HttpException = HttpException(
            Response.error<String>(
                404,
                """{"code":"NOT_FOUND","message":"Demo $what not found."}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
    }
}
