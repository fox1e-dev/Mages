package org.mlm.mages.ui.components.composer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64
import org.mlm.mages.matrix.MatrixPort

/**
 * The image a spoiler's plaintext `body` fallback points at.
 *
 * The spec asks for the redacted text to be absent from `body` and strongly
 * suggests uploading a placeholder for clients to link to, so that a
 * notification or a plaintext client shows a redaction block rather than the
 * hidden text. The bytes are a fixed 32x32 near-black PNG, so the URI is
 * uploaded at most once per session however many spoilers get sent.
 */
object SpoilerPlaceholder {
    private const val MIME = "image/png"

    private val bytes: ByteArray = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAJ0lEQVR42u3NMQ0AAAwDoOqof6FV" +
            "sWMJGCA9FoFAIBAIBAKBQPAlGPrtOBCTuSkMAAAAAElFTkSuQmCC"
    )

    private val lock = Mutex()
    private var cached: String? = null

    /**
     * The uploaded `mxc://` URI, or null when the upload failed.
     *
     * A failure is not fatal: the caller falls back to a bare `[Spoiler]`
     * label, which still keeps the hidden text out of `body`. The lock is held
     * across the upload so that two sends racing on a first spoiler cannot
     * each pay for one.
     */
    suspend fun upload(port: MatrixPort): String? = lock.withLock {
        cached ?: port.uploadBytes(bytes, MIME).getOrNull()?.also { cached = it }
    }
}
