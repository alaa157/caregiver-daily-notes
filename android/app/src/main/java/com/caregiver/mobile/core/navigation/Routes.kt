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
    val AddRecipient = Destination("add-recipient")
    val NoteEditor = Destination("note-editor/{recipientId}")
    val NoteSaved = Destination("note-saved/{noteId}")
    val NoteDetail = Destination("note/{noteId}")
    val Addendum = Destination("addendum/{noteId}")

    val Summary = Destination("summary/{recipientId}")
    val SummaryResult = Destination("summary-result/{recipientId}/{periodDays}")

    val Plans = Destination("plans")
    val PlanProposal = Destination("plan-proposal/{planId}")
    val PlanEdit = Destination("plan-edit/{planId}")
    val PlanVersions = Destination("plan-versions/{planId}")

    val Settings = Destination("settings")

    val all: List<Destination> = listOf(
        Login, Register, ServerUrl,
        Home, People, Notes, History,
        RecipientDetail, AddRecipient, NoteEditor, NoteSaved, NoteDetail, Addendum,
        Summary, SummaryResult,
        Plans, PlanProposal, PlanEdit, PlanVersions,
        Settings,
    )
}

/**
 * Typed route builders: the single place that turns destination templates
 * into concrete routes, so callers never hand-concatenate argument strings.
 * Templates in [AppDestinations] stay as the registration contract.
 */
object AppRoutes {
    fun tab(tab: MainTab): String = tab.route

    fun recipientDetail(recipientId: String): String = "recipient/$recipientId"
    fun noteEditor(recipientId: String): String = "note-editor/$recipientId"
    fun noteSaved(noteId: String): String = "note-saved/$noteId"
    fun noteDetail(noteId: String): String = "note/$noteId"
    fun addendum(noteId: String): String = "addendum/$noteId"

    fun summary(recipientId: String): String = "summary/$recipientId"
    fun summaryResult(recipientId: String, periodDays: Int): String =
        "summary-result/$recipientId/$periodDays"

    fun planProposal(planId: String): String = "plan-proposal/$planId"
    fun planEdit(planId: String): String = "plan-edit/$planId"
    fun planVersions(planId: String): String = "plan-versions/$planId"

    /** Reads a required String argument; crashes loudly on a missing arg. */
    fun arg(entry: androidx.navigation.NavBackStackEntry, name: String): String =
        checkNotNull(entry.arguments?.getString(name)) { "missing navigation argument: $name" }

    /** Reads a required Int argument; crashes loudly on a missing arg. */
    fun argInt(entry: androidx.navigation.NavBackStackEntry, name: String): Int =
        checkNotNull(entry.arguments?.getInt(name)) { "missing navigation argument: $name" }
}
