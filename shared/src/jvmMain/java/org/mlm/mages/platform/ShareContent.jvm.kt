package org.mlm.mages.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

@Composable
actual fun rememberShareHandler(): suspend (ShareContent) -> ShareOutcome {
    return remember {
        { content ->
            try {
                val files = content.allFilePaths.map { File(it) }.filter { it.exists() && it.canRead() }

                if (files.isNotEmpty()) {
                    val parent = files.first().parentFile
                    if (parent != null && Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(parent)
                    }

                    if (files.size > 1) {
                        copyToClipboard(files.joinToString("\n") { it.absolutePath })
                        return@remember ShareOutcome.Copied
                    }

                    return@remember if (parent != null && Desktop.isDesktopSupported()) {
                        ShareOutcome.Shared
                    } else {
                        ShareOutcome.Failed
                    }
                }

                val text = content.text
                if (text == null) {
                    ShareOutcome.Failed
                } else {
                    copyToClipboard(text)
                    ShareOutcome.Copied
                }
            } catch (e: Throwable) {
                Logger.w { "ShareContent.jvm: share failed: ${e.message}" }
                ShareOutcome.Failed
            }
        }
    }
}

private fun copyToClipboard(text: String) {
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
}