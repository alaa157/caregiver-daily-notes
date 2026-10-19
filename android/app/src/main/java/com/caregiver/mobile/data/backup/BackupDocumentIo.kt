package com.caregiver.mobile.data.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Raised when the selected file exceeds [AndroidBackupDocumentIo.MAX_IMPORT_BYTES]. */
class BackupImportTooLargeException : IOException("Backup import exceeds the size limit")

/**
 * Reads and writes the document the user picked in the system file picker.
 * Abstracted so the ViewModel is unit-testable without a ContentResolver.
 */
interface BackupDocumentIo {
    suspend fun read(uri: Uri): String

    suspend fun write(uri: Uri, content: String)
}

/**
 * SAF-backed implementation. The app never requests broad storage
 * permissions: the picker grants access to the one document the user chose,
 * and all bytes stay on the device.
 *
 * Reads are capped at [MAX_IMPORT_BYTES] so a wrong pick (a huge media file)
 * fails fast with a clear message instead of exhausting memory. Text is
 * decoded as UTF-8; a BOM, if present, is handled by [CareBackupCodec].
 */
class AndroidBackupDocumentIo(
    private val resolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupDocumentIo {
    override suspend fun read(uri: Uri): String = withContext(dispatcher) {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("No input stream for the selected document")
        stream.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) throw BackupImportTooLargeException()
                output.write(buffer, 0, read)
            }
            output.toByteArray().toString(StandardCharsets.UTF_8)
        }
    }

    override suspend fun write(uri: Uri, content: String): Unit = withContext(dispatcher) {
        val stream = resolver.openOutputStream(uri)
            ?: throw IOException("No output stream for the selected document")
        stream.use { output ->
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }
    }

    companion object {
        /** Import cap: far above any realistic note history, low enough to fail fast. */
        const val MAX_IMPORT_BYTES: Int = 10 * 1024 * 1024
        const val MAX_IMPORT_MEGABYTES: Int = 10
        private const val BUFFER_SIZE = 8 * 1024
    }
}
