package org.mlm.mages.push

import org.mlm.mages.AttachmentInfo
import org.mlm.mages.AttachmentKind
import org.mlm.mages.StickerInfo
import org.mlm.mages.matrix.ClassifiedNotification
import org.mlm.mages.matrix.RenderedNotification
import org.mlm.mages.matrix.classify
import org.mlm.mages.matrix.notificationSummary

data class NotificationPresentation(
    val title: String,
    val body: String,
    /** Used instead of [body] when a preview image is actually attached. */
    val bodyWithMedia: String,
    val media: NotificationMedia?,
    val accent: Accent
) {
    enum class Accent { Neutral, Mention, Call, Invite }

    companion object {
        private const val BODY_MAX_CHARS = 240

        fun of(
            notification: RenderedNotification,
            showPreview: Boolean,
            redactedBody: String
        ): NotificationPresentation {
            val content = notification.content.classify()
            val sender = notification.sender
            val roomName = notification.roomName

            val title = when (content) {
                is ClassifiedNotification.Call, ClassifiedNotification.Invite -> sender
                else ->
                    if (notification.isDm || sender == roomName) sender else roomName
            }

            val accent = when {
                content is ClassifiedNotification.Call -> Accent.Call
                content is ClassifiedNotification.Invite -> Accent.Invite
                notification.hasMention -> Accent.Mention
                else -> Accent.Neutral
            }

            // A reaction and a call both already name their subject, so prefixing
            // the sender reads as duplication on platforms that concatenate.
            val prefixSender = when (content) {
                is ClassifiedNotification.Reaction,
                is ClassifiedNotification.Call,
                ClassifiedNotification.Invite -> false
                else -> true
            }

            val body = when {
                !showPreview -> redactedBody
                prefixSender -> "$sender: ${notificationSummary(content)}".take(BODY_MAX_CHARS)
                else -> notificationSummary(content).take(BODY_MAX_CHARS)
            }

            // A preview image stands in for the "Sent an image" label, so the
            // label is only worth showing when there is no image. Whatever text
            // the sender actually wrote is the caption and survives either way.
            val caption = (content as? ClassifiedNotification.Media)?.body?.trim().orEmpty()
            val bodyWithMedia = when {
                !showPreview -> redactedBody
                caption.isEmpty() -> ""
                prefixSender -> "$sender: $caption".take(BODY_MAX_CHARS)
                else -> caption.take(BODY_MAX_CHARS)
            }

            return NotificationPresentation(title, body, bodyWithMedia, mediaOf(content), accent)
        }

        private fun mediaOf(content: ClassifiedNotification): NotificationMedia? {
            val attachment = when (content) {
                is ClassifiedNotification.Media -> content.attachment
                is ClassifiedNotification.Sticker -> content.sticker.asAttachment()
                else -> return null
            }

            val previewable = when (attachment.kind) {
                AttachmentKind.Image, AttachmentKind.Video -> true
                AttachmentKind.Audio -> attachment.isVoice != true
                AttachmentKind.File -> false
            }
            if (!previewable) return null

            return NotificationMedia(attachment)
        }

        private fun StickerInfo.asAttachment() = AttachmentInfo(
            kind = AttachmentKind.Image,
            mxcUri = mxcUri,
            mime = mime,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            thumbnailMxcUri = thumbnailMxcUri,
            encrypted = encrypted,
            thumbnailEncrypted = thumbnailEncrypted
        )
    }
}

/**
 * The full attachment, not just an mxc URI: in an encrypted room the URI alone
 * is undecryptable and the fetch fails, so the encryption keys travel with it.
 */
data class NotificationMedia(val attachment: AttachmentInfo)
