package org.mlm.mages.matrix

import org.mlm.mages.settings.MediaPreviewsMode

/** MSC4278. A null [isPrivateRoom] means the room's privacy is unknown, so allow. */
fun MediaPreviewsMode.allowsMediaPreviews(isPrivateRoom: Boolean?): Boolean = when (this) {
    MediaPreviewsMode.On -> true
    MediaPreviewsMode.Private -> isPrivateRoom != false
    MediaPreviewsMode.Off -> false
}
