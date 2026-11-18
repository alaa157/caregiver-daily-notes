package com.caregiver.mobile.presentation.plans

import com.caregiver.mobile.R

/**
 * Localized status labels. Unknown future statuses fall back to the raw
 * server value so new workflow states never render blank.
 */
object PlanStatusText {
    fun res(status: String): Int? = when (status) {
        "Suggested" -> R.string.plan_status_suggested
        "Accepted" -> R.string.plan_status_accepted
        "Dismissed" -> R.string.plan_status_dismissed
        "Edited-and-Accepted" -> R.string.plan_status_edited_accepted
        "Archived" -> R.string.plan_status_archived
        else -> null
    }
}
