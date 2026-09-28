package org.mlm.mages.platform

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import kotlinx.browser.document
import kotlinx.coroutines.await
import org.w3c.dom.HTMLTextAreaElement
import kotlin.js.JsAny

@Composable
actual fun rememberShareHandler(): suspend (ShareContent) -> ShareOutcome {
    return { content -> shareContent(content) }
}

private suspend fun shareContent(content: ShareContent): ShareOutcome {
    val title = content.subject?.trim()?.takeIf { it.isNotEmpty() }
    val text = content.text?.trim()?.takeIf { it.isNotEmpty() }
    val url = content.url?.trim()?.takeIf { it.isNotEmpty() }

    val hasFilePayload = content.webObjectUrls.isNotEmpty() || content.allFilePaths.isNotEmpty()

    if (!hasFilePayload && windowIsSecureContext() && navigatorShareSupported()) {
        val shareData = shareDataCreate()

        if (!title.isNullOrBlank()) shareDataSetTitle(shareData, title)
        if (!text.isNullOrBlank()) shareDataSetText(shareData, text)
        if (!url.isNullOrBlank()) shareDataSetUrl(shareData, url)

        val canShare = !navigatorCanShareSupported() || navigatorCanShare(shareData)
        if (canShare) {
            try {
                navigatorShare(shareData).await<JsAny?>()
                return ShareOutcome.Shared
            } catch (_: Throwable) {
            }
        }
    }

    if (content.webObjectUrls.isNotEmpty()) {
        content.webObjectUrls.forEachIndexed { index, objectUrl ->
            downloadUrl(objectUrl, "download_${index + 1}")
        }
        return ShareOutcome.Shared
    }

    if (content.allFilePaths.isNotEmpty()) {
        var downloadedAny = false

        content.allFilePaths.forEachIndexed { index, path ->
            val fileName = suggestedDownloadName(path, index)
            val bytes = retrieveWebBlob(path)

            when {
                bytes != null -> {
                    FileKit.download(bytes, fileName)
                    downloadedAny = true
                }

                path.startsWith("blob:") ||
                    path.startsWith("data:") ||
                    path.startsWith("http://") ||
                    path.startsWith("https://") -> {
                    downloadedAny = downloadUrl(path, fileName) || downloadedAny
                }
            }
        }

        if (downloadedAny) return ShareOutcome.Shared
    }

    return when {
        !url.isNullOrBlank() -> {
            openUrl(url)
            ShareOutcome.Shared
        }

        !text.isNullOrBlank() -> {
            if (copyTextToClipboard(text)) ShareOutcome.Copied else ShareOutcome.Failed
        }

        else -> ShareOutcome.Failed
    }
}

private suspend fun copyTextToClipboard(text: String): Boolean {
    if (windowIsSecureContext() && navigatorClipboardWriteTextSupported()) {
        return try {
            navigatorClipboardWriteText(text).await<JsAny?>()
            true
        } catch (_: Throwable) {
            false
        }
    }

    val textarea = document.createElement("textarea") as HTMLTextAreaElement
    textarea.value = text
    textarea.setAttribute(
        "style",
        "position:fixed;left:-9999px;top:0;opacity:0;pointer-events:none;"
    )

    document.body?.appendChild(textarea)
    textareaSelect(textarea)
    val copied = documentExecCopy()
    document.body?.removeChild(textarea)
    return copied
}

private fun suggestedDownloadName(path: String, index: Int): String {
    val candidate = path
        .substringAfterLast('/')
        .substringAfterLast('\\')

    return when {
        candidate.isBlank() -> "download_${index + 1}"
        candidate.startsWith("web_blob_") -> "download_${index + 1}"
        else -> candidate
    }
}
