package com.caregiver.mobile.presentation.recipients

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.caregiver.mobile.R
import com.caregiver.mobile.data.recipient.RelationshipOptions

/** String resource for a known relationship code, or null for anything else. */
fun relationshipLabelRes(code: String): Int? = when (code) {
    "MOTHER" -> R.string.relationship_mother
    "FATHER" -> R.string.relationship_father
    "SPOUSE" -> R.string.relationship_spouse
    "GRANDMOTHER" -> R.string.relationship_grandmother
    "GRANDFATHER" -> R.string.relationship_grandfather
    "CHILD" -> R.string.relationship_child
    "SIBLING" -> R.string.relationship_sibling
    "RELATIVE" -> R.string.relationship_relative
    "FRIEND" -> R.string.relationship_friend
    "CLIENT" -> R.string.relationship_client
    "OTHER" -> R.string.relationship_other
    else -> null
}

/**
 * Localized label for a stored relationship value: the label for a known
 * code, the trimmed raw value for free text, "" for null or blank.
 */
@Composable
fun relationshipLabel(value: String?): String {
    val normalized = value?.trim()
    if (normalized.isNullOrEmpty()) return ""
    val code = RelationshipOptions.codeOf(normalized)
    if (code != null) {
        relationshipLabelRes(code)?.let { return stringResource(it) }
    }
    return normalized
}
