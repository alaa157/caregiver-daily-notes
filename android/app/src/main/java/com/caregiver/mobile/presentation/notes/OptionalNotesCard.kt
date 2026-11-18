package com.caregiver.mobile.presentation.notes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.util.appLocale
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.SectionCard
import java.util.Locale

/**
 * #905: append the first recognised utterance to what is already typed.
 * Pure so the dictation result handling is unit-testable without a device;
 * blank results never erase or duplicate existing text.
 */
internal fun appendDictation(existing: String, spoken: String): String {
    val words = spoken.trim()
    if (words.isEmpty()) return existing
    return if (existing.isBlank()) words else "${existing.trimEnd()} $words"
}

/** System speech recognizer intent; no in-app speech model is added. */
internal fun dictationIntent(context: Context, locale: Locale): Intent = Intent(
    RecognizerIntent.ACTION_RECOGNIZE_SPEECH,
).apply {
    putExtra(
        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
    )
    // Follow the in-app language, not just the device locale, so Arabic UI
    // asks the recognizer for Arabic even on a device set to English.
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
    putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.editor_dictate_prompt))
}

private fun hasMicPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

/**
 * Always-visible optional notes card: a multi-line field and a 44dp round
 * dictate button. The microphone permission is requested only on tap; a
 * denial or a device with no recognizer disables the button and shows a
 * short explanation instead of crashing.
 */
@Composable
fun OptionalNotesCard(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = appLocale()
    var micBlocked by rememberSaveable { mutableStateOf(false) }

    val recognizeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onValueChange(appendDictation(value, spoken))
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val started = granted &&
            startRecognition(context, dictationIntent(context, locale), recognizeLauncher)
        // Denied, or no recognizer installed: disable the button and explain.
        if (!started) micBlocked = true
    }

    SectionCard(modifier = modifier.testTag("optional_notes_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.editor_anything_else),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CaregiverColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = {
                        Text(
                            stringResource(R.string.editor_hint_extra),
                            fontFamily = PlexArabic,
                        )
                    },
                    minLines = 5,
                    shape = RoundedCornerShape(AppDimens.ControlRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CaregiverColors.Primary,
                        unfocusedBorderColor = CaregiverColors.Border,
                        focusedContainerColor = CaregiverColors.Surface,
                        unfocusedContainerColor = CaregiverColors.Surface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 140.dp)
                        .testTag("editor_free_text"),
                )
                val dictateCd = stringResource(R.string.editor_dictate_cd)
                IconButton(
                    onClick = {
                        // Ask only on tap. A denial (or a device with no
                        // recognizer) never throws — it just disables this
                        // button and shows the explanation below.
                        if (!hasMicPermission(context)) {
                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else if (!startRecognition(
                                context,
                                dictationIntent(context, locale),
                                recognizeLauncher,
                            )
                        ) {
                            micBlocked = true
                        }
                    },
                    enabled = !micBlocked,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = dictateCd }
                        .testTag("editor_dictate_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (micBlocked) CaregiverColors.Muted else CaregiverColors.Primary,
                    )
                }
            }
            if (micBlocked) {
                Text(
                    text = stringResource(R.string.editor_dictation_unavailable),
                    fontFamily = PlexArabic,
                    fontSize = 12.sp,
                    color = CaregiverColors.Muted,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .testTag("editor_dictation_note"),
                )
            }
        }
    }
}

/** Returns false when no recognizer can handle the intent. */
private fun startRecognition(
    context: Context,
    intent: Intent,
    launcher: ActivityResultLauncher<Intent>,
): Boolean {
    if (intent.resolveActivity(context.packageManager) == null) return false
    launcher.launch(intent)
    return true
}
