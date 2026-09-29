package org.mlm.mages.push

import org.mlm.mages.AttachmentInfo
import org.mlm.mages.matrix.MatrixPort
import org.mlm.mages.matrix.allowsMediaPreviews
import org.mlm.mages.settings.AppSettings

object NotificationMediaPolicy {
    private const val PREVIEW_PX = 320
    private const val PREVIEW_MAX_BYTES = 5L * 1024 * 1024

    fun allowed(settings: AppSettings): Boolean =
        settings.notificationShowMedia &&
            settings.mediaPreviews.allowsMediaPreviews(isPrivateRoom = null)

    /** A push preview is a still shown by another app, so it never wants an animated
     *  thumbnail, and pulling a whole original for it is only worth it up to a point. */
    suspend fun preview(port: MatrixPort, info: AttachmentInfo): String? =
        port.thumbnailToCache(
            info = info,
            width = PREVIEW_PX,
            height = PREVIEW_PX,
            crop = false,
            animated = false,
            maxBytes = PREVIEW_MAX_BYTES,
        ).getOrNull()?.takeIf { it.isNotBlank() }
}
