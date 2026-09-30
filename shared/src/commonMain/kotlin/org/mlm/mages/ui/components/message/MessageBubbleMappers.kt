package org.mlm.mages.ui.components.message

import mages.shared.generated.resources.*
import org.mlm.mages.AttachmentKind
import org.mlm.mages.MessageEvent
import org.mlm.mages.captionOr
import org.mlm.mages.ui.components.timeline.TimelineContent
import org.mlm.mages.ui.util.formatBytes
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import mages.shared.generated.resources.Res

private fun MessageEvent.toMediaCaption(): String? = attachment?.captionOr(body)

private fun buildAttachmentSubtitle(mime: String?, sizeBytes: Long?): String? {
    val parts = buildList {
        formatBytes(sizeBytes)?.let { add(it) }
        mime?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

@Composable
private fun MessageEvent.toAttachmentUi(
    resolvedPreviewPath: String?,
    resolvedAudioPath: String?,
    resolvedAudioWaveform: List<Float>,
): MessageAttachmentUi? {
    val info = attachment ?: return null
    val caption = toMediaCaption()
    val captionFormattedBody = formattedBody?.takeIf { it.isNotBlank() && caption != null }

    return when (val kind = info.kind) {
        AttachmentKind.File -> MessageAttachmentUi.File(
            fileName = info.fileName,
            mime = info.mime,
            sizeBytes = info.sizeBytes,
            title = info.fileName?.takeIf { it.isNotBlank() }
                ?: body.trim().ifBlank { stringResource(Res.string.file) },
            subtitle = buildAttachmentSubtitle(info.mime, info.sizeBytes),
            caption = caption,
            captionFormattedBody = captionFormattedBody,
        )
        AttachmentKind.Image -> MessageAttachmentUi.Image(
            previewPath = resolvedPreviewPath ?: info.thumbnailMxcUri,
            width = info.width,
            height = info.height,
            caption = caption,
            captionFormattedBody = captionFormattedBody,
        )
        AttachmentKind.Video -> MessageAttachmentUi.Video(
            previewPath = resolvedPreviewPath ?: info.thumbnailMxcUri,
            width = info.width,
            height = info.height,
            durationMs = info.durationMs,
            caption = caption,
            captionFormattedBody = captionFormattedBody,
        )
        AttachmentKind.Audio -> MessageAttachmentUi.Audio(
            filePath = resolvedAudioPath,
            durationMs = info.durationMs,
            waveform = resolvedAudioWaveform.ifEmpty { info.waveform.orEmpty() },
            caption = caption,
            captionFormattedBody = captionFormattedBody,
            fileName = info.fileName,
            mime = info.mime,
            sizeBytes = info.sizeBytes,
            title = info.fileName?.takeIf { it.isNotBlank() }
                ?: body.trim().ifBlank { stringResource(Res.string.audio) },
            subtitle = buildAttachmentSubtitle(info.mime, info.sizeBytes),
            // MSC3245 voice marker
            isVoice = info.isVoice == true,
        )
    }
}

@Composable
internal fun TimelineContent.Bubble.toBubbleModel(
    ctx: MessageBubbleRenderContext
): MessageBubbleModel {
    val event = event
    val stickerData = event.sticker?.let {
        MessageStickerUi(
            // The spec calls the flag a hint, not a guarantee, so an animated
            // sticker that turns out to be static just renders as one. The
            // thumbnail is a still frame, so preferring it would never animate.
            thumbPath = ctx.resolvedPreviewPath
                ?: if (it.isAnimated == true) it.mxcUri else it.thumbnailMxcUri ?: it.mxcUri,
            width = it.width,
            height = it.height,
            mime = it.mime,
        )
    }
    return MessageBubbleModel(
        eventId = event.eventId,
        isMine = ctx.isMine,
        body = if (stickerData != null) "" else event.body,
        formattedBody = event.formattedBody,
        sender = if (ctx.senderVisible) MessageSenderUi(
            id = event.sender,
            displayName = event.senderDisplayName,
            avatarPath = ctx.avatarPath,
        ) else null,
        timestamp = event.timestampMs,
        isDm = ctx.isDm,
        showMessageAvatars = ctx.showMessageAvatars,
        showUsernameInDms = ctx.showUsernameInDms,
        grouping = MessageGroupingUi(
            groupedWithPrev = ctx.groupedWithPrev,
            groupedWithNext = ctx.groupedWithNext,
        ),
        reactions = ctx.reactions,
        reactionAvatarsByUserId = ctx.reactionAvatarsByUserId,
        showReactionAvatars = ctx.showReactionAvatars,
        reactionImagePaths = ctx.reactionImagePaths,
        reactionShortcodes = ctx.reactionShortcodes,
        reply = MessageReplyUi(
            sender = event.replyToSenderDisplayName,
            body = event.replyToBody,
            preview = event.replyPreview,
            previewPath = ctx.resolvedReplyPreviewPath,
        ),
        sendState = event.sendState,
        attachment = event.toAttachmentUi(
            resolvedPreviewPath = ctx.resolvedPreviewPath,
            resolvedAudioPath = ctx.resolvedAudioPath,
            resolvedAudioWaveform = ctx.resolvedAudioWaveform,
        ),
        sticker = stickerData,
        isSticker = stickerData != null,
        isEdited = event.isEdited,
        isPinned = ctx.isPinned,
        poll = event.pollData,
        thread = ctx.threadCount?.let { count -> MessageThreadUi(count) },
        variant = ctx.variant,
    )
}
