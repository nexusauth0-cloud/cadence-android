package com.cadence.android.worker

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive

/**
 * Owns durable file storage for completed downloads (ADR-0007 DOWNLOADS tier).
 *
 * Atomic writes: content is first written to a temp file, then renamed.
 * This prevents partial/corrupted files from being mistaken for completed
 * assets (section 16).
 *
 * File integrity: a file is considered VERIFIED only if it exists, is
 * non-empty, and its size matches the expected byte count (section 17).
 *
 * The UI never sees raw paths — this class is internal to the storage layer.
 */
class DownloadStorage(
    private val downloadsDir: File,
) {
    /** Directory for temporary files during atomic writes. */
    private val tmpDir: File get() = File(downloadsDir, ".tmp")

    init {
        downloadsDir.mkdirs()
        tmpDir.mkdirs()
    }

    /**
     * Generate a stable key for a download entry. Uses SHA-256 of
     * `"$providerId:$nativeId"` to produce a deterministic filename.
     */
    fun keyFor(providerId: String, nativeId: String): String {
        val input = "$providerId:$nativeId"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
    }

    /** The canonical file path for a given key. */
    fun fileFor(key: String): File = File(downloadsDir, key)

    /**
     * Atomically write [bytes] to the downloads directory.
     * Returns the local file URI string for playback.
     *
     * @throws IOException on write failure or insufficient space.
     */
    @Throws(IOException::class)
    suspend fun write(key: String, bytes: ByteArray): String = with(kotlinx.coroutines.Dispatchers.IO) {
        val target = fileFor(key)
        val tmp = File(tmpDir, "$key.tmp")

        try {
            tmp.writeBytes(bytes)

            // Atomic rename (same volume).
            if (!tmp.renameTo(target)) {
                // Fallback: copy + delete (cross-device or platform edge case).
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }

            if (!target.exists() || target.length() == 0L) {
                throw IOException("Download file write failed: file missing or empty after write")
            }

            target.absolutePath
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    /**
     * Streamingly write [input] to the downloads directory with an atomic
     * temp→rename. Reads in [bufferSize] chunks and reports progress through
     * [onChunk]. [isStopped] is checked per chunk to honour cancellation.
     *
     * @throws IOException on write failure or insufficient space.
     * @throws kotlinx.coroutines.CancellationException if [isStopped] returns true.
     */
    @Throws(IOException::class)
    suspend fun writeStream(
        key: String,
        input: java.io.InputStream,
        bufferSize: Int = 8192,
        onChunk: suspend (bytesWritten: Long) -> Unit = {},
        isStopped: suspend () -> Boolean = { false },
    ): Long = with(kotlinx.coroutines.Dispatchers.IO) {
        val target = fileFor(key)
        val tmp = File(tmpDir, "$key.tmp")
        var written = 0L

        try {
            val buffer = ByteArray(bufferSize)
            tmp.outputStream().use { out ->
                while (true) {
                    ensureActive()
                    if (isStopped()) throw kotlinx.coroutines.CancellationException("Download cancelled")
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    written += read
                    onChunk(written)
                }
                out.flush()
            }

            if (written == 0L) throw IOException("Download file write failed: zero bytes read")

            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            written
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    /**
     * Read bytes from a completed download file.
     * @return bytes, or null if file missing/empty.
     */
    suspend fun read(key: String): ByteArray? = with(kotlinx.coroutines.Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists() || file.length() == 0L) null
        else file.readBytes()
    }

    /**
     * Check file integrity: exists, non-empty, and optionally matches expected size.
     * Returns true only when the file is GENUINELY playable (section 17).
     */
    fun verify(key: String, expectedSizeBytes: Long? = null): Boolean {
        val file = fileFor(key)
        if (!file.exists() || file.length() == 0L) return false
        if (expectedSizeBytes != null && file.length() != expectedSizeBytes) return false
        return true
    }

    /**
     * Delete a download file.
     */
    fun delete(key: String): Boolean {
        val file = fileFor(key)
        val deleted = file.delete()
        // Also clean up any leftover tmp file.
        File(tmpDir, "$key.tmp").delete()
        return deleted
    }

    /** Total bytes used by completed downloads (files only, excludes dirs). */
    fun totalBytes(): Long =
        downloadsDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    /** Free space available in the downloads directory. */
    fun availableBytes(): Long? = downloadsDir.usableSpace.takeIf { it > 0 }

    /** Clean up the tmp directory (stale files from interrupted writes). */
    fun cleanTmp() {
        tmpDir.listFiles()?.forEach { it.delete() }
    }
}
