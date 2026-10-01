package org.mlm.mages.matrix

import org.mlm.mages.settings.LinkPreviewsMode

/** Preview image size in pixels; the homeserver may return something smaller. */
const val LINK_PREVIEW_IMAGE_PX = 640

/** Requesting one hands the homeserver the URL, which is what the encrypted-room mode is about. */
fun LinkPreviewsMode.allowsLinkPreviews(isEncrypted: Boolean): Boolean = when (this) {
    LinkPreviewsMode.On -> true
    LinkPreviewsMode.UnencryptedOnly -> !isEncrypted
    LinkPreviewsMode.Off -> false
}
