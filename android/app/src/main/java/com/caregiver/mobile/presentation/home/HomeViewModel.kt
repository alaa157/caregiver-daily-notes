package com.caregiver.mobile.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.SettingsStore
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class RecipientChip { DoneToday, HighPain, FallFlag, NoNote }

data class LastNote(val date: String, val mood: String, val appetite: String, val sleep: String)

data class HomeCard(
    val recipientId: String,
    val name: String,
    val chips: List<RecipientChip>,
    val lastNote: LastNote?,
)

data class SafetyFlag(val recipientName: String, val date: String)

data class Greeting(val morning: Boolean, val name: String?)

data class HomeContent(
    val greeting: Greeting,
    val done: Int,
    val total: Int,
    val cards: List<HomeCard>,
    val flags: List<SafetyFlag>,
)

sealed interface HomeState {
    data object Loading : HomeState
    data object Idle : HomeState
    data class Content(val content: HomeContent) : HomeState
    data object Error : HomeState
}

/**
 * Home dashboard (board 2): greeting, today progress over active recipients,
 * per-recipient status chips, and fall safety flags. A 401 signs out upstream
 * and flips the root nav to login, so [LoggedOutException] is swallowed here
 * after the repository has cleared the session.
 */
class HomeViewModel(
    private val repository: AuthRepository,
    private val settings: SettingsStore,
    private val clock: Clock = Clock.systemDefaultZone(),
    scope: CoroutineScope? = null,
) : ViewModel() {
    // Production uses viewModelScope; tests inject their TestScope.
    private val execScope: CoroutineScope = scope ?: viewModelScope
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state

    init {
        execScope.launch { load() }
    }

    fun refresh() {
        _state.value = HomeState.Loading
        execScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val today = LocalDate.now(clock).toString()
            val email = settings.email.first()
            val recipients = repository.authorized { recipients().list() }
            val active = recipients.filter { it.active }
            val cards = active.map { recipient ->
                val notes = repository.authorized { notes().history(recipient.id, null, null) }
                val latest = notes.maxByOrNull { it.date }
                val weekAgo = LocalDate.now(clock).minusDays(7).toString()
                val signals = repository.authorized {
                    summaries().signals(recipient.id, weekAgo, today)
                }
                val chips = buildList {
                    if (latest == null) {
                        add(RecipientChip.NoNote)
                    } else {
                        if (latest.date == today) {
                            add(RecipientChip.DoneToday)
                        }
                        if (latest.pain >= HIGH_PAIN_THRESHOLD) {
                            add(RecipientChip.HighPain)
                        }
                    }
                    if (signals.any { it.fallReported }) {
                        add(RecipientChip.FallFlag)
                    }
                }
                HomeCard(
                    recipientId = recipient.id,
                    name = recipient.name,
                    chips = chips,
                    lastNote = latest?.let {
                        LastNote(it.date, it.mood, it.appetite, it.sleep)
                    },
                )
            }
            val flags = active.flatMap { recipient ->
                val weekAgo = LocalDate.now(clock).minusDays(7).toString()
                repository.authorized { summaries().signals(recipient.id, weekAgo, today) }
                    .filter { it.fallReported }
                    .map { SafetyFlag(recipient.name, it.date) }
            }
            val done = cards.count { RecipientChip.DoneToday in it.chips }
            _state.value = HomeState.Content(
                HomeContent(
                    greeting = Greeting(
                        morning = LocalTime.now(clock).hour < NOON_HOUR,
                        name = displayName(email),
                    ),
                    done = done,
                    total = active.size,
                    cards = cards,
                    flags = flags,
                ),
            )
        } catch (e: LoggedOutException) {
            _state.value = HomeState.Idle
        } catch (e: IOException) {
            _state.value = HomeState.Error
        }
    }

    companion object {
        const val HIGH_PAIN_THRESHOLD = 7
        const val NOON_HOUR = 12

        /** "ahmed@example.com" → "Ahmed"; blank → nameless greeting. */
        fun displayName(email: String?): String? {
            val local = email?.substringBefore("@").orEmpty()
            if (local.isBlank()) {
                return null
            }
            return local.replaceFirstChar { it.uppercase() }
        }
    }
}
