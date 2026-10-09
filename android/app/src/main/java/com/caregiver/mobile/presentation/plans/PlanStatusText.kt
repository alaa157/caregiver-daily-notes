package com.caregiver.mobile.presentation.plans

import com.caregiver.mobile.R

/**
 * Localized status labels. Spec copy has no per-status strings (spec gap,
 * flagged in the report), so every known status shares the plan disclaimer
 * until Step 5 / owner input. Unknown future statuses fall back to the raw
 * server value so new workflow states never render blank.
 */
object PlanStatusText {
    fun res(status: String): Int? = when (status) {
        "Suggested" -> R.string.plan_disclaimer
        "Accepted" -> R.string.plan_accept
        "Dismissed" -> R.string.plan_disclaimer
        "Edited-and-Accepted" -> R.string.plan_disclaimer
        "Archived" -> R.string.plan_disclaimer
        else -> null
    }
}
