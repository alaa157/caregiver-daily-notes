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
import retrofit2.HttpException

enum class RecipientChip { DoneToday, MissedToday, HighPain, FallFlag, NoNote }

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
    data class Content(val content: HomeContent, val refreshing: Boolean = false) : HomeState
    data class Error(val lastContent: HomeContent? = null) : HomeState
}

/**
 * Home dashboard (board 2): greeting, today progress over active recipients,
 * per-recipient status chips, and fall safety flags.
 *
 * Bounded by construction: exactly two API calls per load — the recipient
 * list plus ONE notes-history call over the dashboard window, grouped in
 * memory. No per-recipient fan-out. Fall flags and the pain threshold come
 * verbatim from that response (same >= 7 rule the backend's own HIGH_PAIN
 * flag uses); anything needing a dedicated safety aggregate is a backend
 * gap, not client logic.
 *
 * Refresh never hides known flags: a refresh over content keeps the content
 * visible with a progress marker, and failures carry the last content so the
 * banner survives. A 401 signs out upstream and flips the root nav to login.
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
        execScope.launch { load(preserve = false) }
    }

    fun refresh() {
        val current = (_state.value as? HomeState.Content)?.content
        _state.value = if (current != null) {
            HomeState.Content(current, refreshing = true)
        } else {
            HomeState.Loading
        }
        execScope.launch { load(preserve = current != null) }
    }

    private suspend fun load(preserve: Boolean) {
        try {
            val today = LocalDate.now(clock).toString()
            val windowStart = LocalDate.now(clock).minusDays(HOME_WINDOW_DAYS - 1).toString()
            val email = settings.email.first()
            val recipients = repository.authorized { recipients().list() }
            val active = recipients.filter { it.active }
            val notes = repository.authorized { notes().history(null, windowStart, today) }
            val byRecipient = notes.groupBy { it.recipientId }
            val cards = active.map { recipient ->
                val mine = byRecipient[recipient.id].orEmpty()
                val latest = mine.maxByOrNull { it.date }
                val chips = buildList {
                    if (latest == null) {
                        add(RecipientChip.NoNote)
                    } else {
                        if (latest.date == today) {
                            add(RecipientChip.DoneToday)
                        } else {
                            add(RecipientChip.MissedToday)
                        }
                        if (latest.pain >= HIGH_PAIN_THRESHOLD) {
                            add(RecipientChip.HighPain)
                        }
                    }
                    if (mine.any { it.fall }) {
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
                byRecipient[recipient.id].orEmpty()
                    .filter { it.fall }
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
            if (!preserve) {
                _state.value = HomeState.Idle
            } else {
                (_state.value as? HomeState.Content)?.let {
                    _state.value = it.copy(refreshing = false)
                }
            }
        } catch (e: HttpException) {
            _state.value = HomeState.Error(
                if (preserve) (_state.value as? HomeState.Content)?.content else null,
            )
        } catch (e: IOException) {
            _state.value = HomeState.Error(
                if (preserve) (_state.value as? HomeState.Content)?.content else null,
            )
        }
    }

    companion object {
        const val HIGH_PAIN_THRESHOLD = 7
        const val NOON_HOUR = 12

        /** Dashboard window in days, inclusive of today. */
        const val HOME_WINDOW_DAYS = 7L

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
