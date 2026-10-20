package com.caregiver.mobile.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.backup.BackupCounts
import com.caregiver.mobile.data.backup.BackupDocumentIo
import com.caregiver.mobile.data.backup.BackupFailure
import com.caregiver.mobile.data.backup.BackupFailureReason
import com.caregiver.mobile.data.backup.BackupImportTooLargeException
import com.caregiver.mobile.data.backup.BackupRestoreService
import com.caregiver.mobile.data.backup.RestoreOutcome
import com.caregiver.mobile.data.backup.RestorePreparation
import com.caregiver.mobile.data.backup.RestorePreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which Data & Backup backup action is running; null means idle. */
enum class BackupOperation { CREATE, RESTORE }

/** One result line shown under the Data & Backup group until dismissed. */
sealed interface BackupNotice {
    data class Created(val counts: BackupCounts) : BackupNotice

    data class Restored(val counts: BackupCounts) : BackupNotice

    data class Failed(val operation: BackupOperation, val failure: BackupFailure) : BackupNotice
}

data class BackupRestoreState(
    /** Non-null while a file is being read/written or a commit runs; blocks re-entry. */
    val operation: BackupOperation? = null,
    val notice: BackupNotice? = null,
    /** The reviewed-but-unconfirmed restore; shown as a confirmation dialog. */
    val preview: RestorePreview? = null,
)

/**
 * Backs the Data & Backup backup actions.
 *
 * Create: snapshot the local store, serialize the versioned document, and
 * write it to the document the system create-document picker returned.
 * Restore: read the picked document, decode/verify/validate it, show the
 * confirmation preview, and only then replace the local data — a cancel
 * anywhere leaves the database untouched.
 *
 * Every step runs in [exec] (IO and database work happen in the layers
 * below), and [BackupRestoreState.operation] blocks duplicate taps until a
 * step finishes. No backup content is logged or sent anywhere; the file is
 * the one document the user picked.
 */
class BackupRestoreViewModel(
    private val service: BackupRestoreService,
    private val documents: BackupDocumentIo,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow(BackupRestoreState())
    val state: StateFlow<BackupRestoreState> = _state.asStateFlow()

    /** False when this build has no local store (demo/server graphs). */
    val available: Boolean get() = service.available

    /** Filename offered in the system save dialog. */
    fun fileName(): String = service.fileName()

    /** Serializes every local record and writes it to [uri]. */
    fun createTo(uri: Uri) {
        if (_state.value.operation != null) return
        if (!service.available) {
            fail(BackupOperation.CREATE, BackupFailure(BackupFailureReason.UNAVAILABLE))
            return
        }
        _state.value = _state.value.copy(operation = BackupOperation.CREATE, notice = null)
        exec.launch {
            val backup = try {
                service.create()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(BackupOperation.CREATE, BackupFailure(BackupFailureReason.SOURCE_FAILED))
                return@launch
            }
            try {
                documents.write(uri, backup.content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(BackupOperation.CREATE, BackupFailure(BackupFailureReason.WRITE_FAILED))
                return@launch
            }
            _state.value = _state.value.copy(
                operation = null,
                notice = BackupNotice.Created(backup.counts),
            )
        }
    }

    /** Reads [uri], validates it, and opens the restore preview on success. */
    fun restoreFrom(uri: Uri) {
        if (_state.value.operation != null) return
        if (!service.available) {
            fail(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.UNAVAILABLE))
            return
        }
        _state.value = _state.value.copy(operation = BackupOperation.RESTORE, notice = null)
        exec.launch {
            val text = try {
                documents.read(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupImportTooLargeException) {
                fail(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.TOO_LARGE))
                return@launch
            } catch (e: Exception) {
                fail(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.READ_FAILED))
                return@launch
            }
            val preparation = try {
                service.prepare(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.UNKNOWN))
                return@launch
            }
            when (preparation) {
                is RestorePreparation.Ready -> _state.value = _state.value.copy(
                    operation = null,
                    preview = preparation.preview,
                )
                is RestorePreparation.Failed -> fail(BackupOperation.RESTORE, preparation.failure)
            }
        }
    }

    /** Writes the reviewed preview; runs only when the user confirms. */
    fun commitRestore() {
        if (_state.value.operation != null) return
        val preview = _state.value.preview ?: return
        _state.value = _state.value.copy(operation = BackupOperation.RESTORE)
        exec.launch {
            val outcome = try {
                service.commit(preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(BackupOperation.RESTORE, BackupFailure(BackupFailureReason.UNKNOWN))
                return@launch
            }
            when (outcome) {
                is RestoreOutcome.Restored -> _state.value = _state.value.copy(
                    operation = null,
                    preview = null,
                    notice = BackupNotice.Restored(outcome.counts),
                )
                is RestoreOutcome.Failed -> fail(BackupOperation.RESTORE, outcome.failure)
            }
        }
    }

    /** Closes the preview without writing anything. */
    fun cancelRestorePreview() {
        if (_state.value.operation != null) return
        _state.value = _state.value.copy(preview = null)
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    private fun fail(operation: BackupOperation, failure: BackupFailure) {
        _state.value = _state.value.copy(
            operation = null,
            preview = null,
            notice = BackupNotice.Failed(operation, failure),
        )
    }
}
