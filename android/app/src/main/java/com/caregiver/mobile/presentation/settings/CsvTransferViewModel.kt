package com.caregiver.mobile.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.csv.CsvDocumentIo
import com.caregiver.mobile.data.csv.CsvFailure
import com.caregiver.mobile.data.csv.CsvFailureReason
import com.caregiver.mobile.data.csv.CsvImportTooLargeException
import com.caregiver.mobile.data.csv.CsvTransferService
import com.caregiver.mobile.data.csv.ImportPlan
import com.caregiver.mobile.data.csv.ImportPreparation
import com.caregiver.mobile.data.csv.ImportSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which Data & Backup action is running; null means idle. */
enum class CsvOperation { EXPORT, IMPORT }

/** One result line shown under the Data & Backup group until dismissed. */
sealed interface CsvNotice {
    data class ExportDone(val recordCount: Int) : CsvNotice

    data class ImportDone(val summary: ImportSummary) : CsvNotice

    data class Failed(val operation: CsvOperation, val failure: CsvFailure) : CsvNotice
}

data class CsvTransferState(
    /** Non-null while a file is being read/written or a commit runs; blocks re-entry. */
    val operation: CsvOperation? = null,
    val notice: CsvNotice? = null,
    /** The reviewed-but-uncommitted import plan; shown as a preview dialog. */
    val preview: ImportPlan? = null,
)

/**
 * Backs the Data & Backup settings actions.
 *
 * Export: snapshot the active source, serialize, and write to the document
 * the system create-document picker returned. Import: read the picked
 * document, parse/validate/plan, let the user review and resolve unmatched
 * recipients, then commit. Every step runs in [exec] (IO for file/DB work is
 * handled by the layers below) so the UI thread never blocks, and
 * [CsvTransferState.operation] blocks duplicate taps until a step finishes.
 *
 * No note text or recipient name ever leaves this device: the file is the one
 * the user picked, and nothing is logged here.
 */
class CsvTransferViewModel(
    private val service: CsvTransferService,
    private val documents: CsvDocumentIo,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow(CsvTransferState())
    val state: StateFlow<CsvTransferState> = _state.asStateFlow()

    /** Filename offered in the system save dialog. */
    fun exportFileName(): String = service.exportFileName()

    /** Serializes and writes every note in the active source to [uri]. */
    fun exportTo(uri: Uri) {
        if (_state.value.operation != null) return
        _state.value = _state.value.copy(operation = CsvOperation.EXPORT, notice = null)
        exec.launch {
            val export = try {
                service.export()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(CsvOperation.EXPORT, CsvFailure(CsvFailureReason.SOURCE_FAILED))
                return@launch
            }
            try {
                documents.write(uri, export.content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(CsvOperation.EXPORT, CsvFailure(CsvFailureReason.WRITE_FAILED))
                return@launch
            }
            _state.value = _state.value.copy(
                operation = null,
                notice = CsvNotice.ExportDone(export.recordCount),
            )
        }
    }

    /** Reads [uri], validates it, and opens the preview on success. */
    fun importFrom(uri: Uri) {
        if (_state.value.operation != null) return
        _state.value = _state.value.copy(operation = CsvOperation.IMPORT, notice = null)
        exec.launch {
            val text = try {
                documents.read(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CsvImportTooLargeException) {
                fail(CsvOperation.IMPORT, CsvFailure(CsvFailureReason.TOO_LARGE))
                return@launch
            } catch (e: Exception) {
                fail(CsvOperation.IMPORT, CsvFailure(CsvFailureReason.READ_FAILED))
                return@launch
            }
            val preparation = try {
                service.prepare(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(CsvOperation.IMPORT, CsvFailure(CsvFailureReason.SOURCE_FAILED))
                return@launch
            }
            when (preparation) {
                is ImportPreparation.Ready -> _state.value = _state.value.copy(
                    operation = null,
                    preview = preparation.plan,
                )
                is ImportPreparation.Failed -> fail(CsvOperation.IMPORT, preparation.failure)
            }
        }
    }

    /**
     * Re-plans the open preview with one unresolved recipient mapped to an
     * existing recipient ([recipientId]) or back to skipped (null).
     */
    fun resolveUnmatched(key: String, recipientId: String?) {
        val plan = _state.value.preview ?: return
        val resolutions = plan.overrides.toMutableMap()
        if (recipientId == null) {
            resolutions.remove(key)
        } else {
            resolutions[key] = recipientId
        }
        _state.value = _state.value.copy(preview = plan.withResolutions(resolutions))
    }

    /** Commits the reviewed plan; unmapped rows stay skipped and are never guessed. */
    fun commitImport() {
        if (_state.value.operation != null) return
        val plan = _state.value.preview ?: return
        _state.value = _state.value.copy(operation = CsvOperation.IMPORT)
        exec.launch {
            val summary = try {
                service.commit(plan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(CsvOperation.IMPORT, CsvFailure(CsvFailureReason.INSERT_FAILED))
                return@launch
            }
            _state.value = _state.value.copy(
                operation = null,
                preview = null,
                notice = CsvNotice.ImportDone(summary),
            )
        }
    }

    /** Closes the preview without writing anything. */
    fun cancelImportPreview() {
        if (_state.value.operation != null) return
        _state.value = _state.value.copy(preview = null)
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    private fun fail(operation: CsvOperation, failure: CsvFailure) {
        _state.value = _state.value.copy(
            operation = null,
            notice = CsvNotice.Failed(operation, failure),
        )
    }
}
