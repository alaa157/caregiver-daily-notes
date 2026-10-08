package com.caregiver.mobile.core.navigation

import androidx.annotation.StringRes
import com.caregiver.mobile.R

/**
 * Bottom tabs, in design order: الرئيسية، الأشخاص، الملاحظات، السجل.
 * Plans deliberately have no tab: they live under recipient detail and the
 * summary follow-up, exactly like the design boards.
 */
enum class MainTab(val route: String, @StringRes val titleRes: Int) {
    Home("home", R.string.tab_home),
    People("people", R.string.tab_people),
    Notes("notes", R.string.tab_notes),
    History("history", R.string.tab_history),
}

/** Every destination the app can navigate to. Feature tasks fill in screens. */
data class Destination(val base: String)

object AppDestinations {
    val Login = Destination("login")
    val Register = Destination("register")
    val ServerUrl = Destination("server-url")

    val Home = Destination("home")
    val People = Destination("people")
    val Notes = Destination("notes")
    val History = Destination("history")

    val RecipientDetail = Destination("recipient/{recipientId}")
    val NoteEditor = Destination("note-editor/{recipientId}")
    val NoteSaved = Destination("note-saved/{noteId}")
    val NoteDetail = Destination("note/{noteId}")
    val Addendum = Destination("addendum/{noteId}")

    val Summary = Destination("summary/{recipientId}")
    val SummaryResult = Destination("summary-result")

    val Plans = Destination("plans")
    val PlanProposal = Destination("plan-proposal/{planId}")
    val PlanEdit = Destination("plan-edit/{planId}")
    val PlanVersions = Destination("plan-versions/{planId}")

    val Settings = Destination("settings")

    val all: List<Destination> = listOf(
        Login, Register, ServerUrl,
        Home, People, Notes, History,
        RecipientDetail, NoteEditor, NoteSaved, NoteDetail, Addendum,
        Summary, SummaryResult,
        Plans, PlanProposal, PlanEdit, PlanVersions,
        Settings,
    )
}
