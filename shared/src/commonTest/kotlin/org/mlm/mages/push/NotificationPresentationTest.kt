package org.mlm.mages.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.mlm.mages.AttachmentKind
import org.mlm.mages.matrix.NotificationContent
import org.mlm.mages.matrix.NotificationContentKind
import org.mlm.mages.matrix.NotificationKind
import org.mlm.mages.matrix.RenderedNotification

class NotificationPresentationTest {

    @Test
    fun dmNamesTheSenderInItsTitleOnly() = runTest {
        val dm = presentation(sender = "Alice", roomName = "Alice", isDm = true)

        assertEquals("Alice", dm.title)
        assertEquals("hi", dm.body)
    }

    @Test
    fun roomTitlesLeaveTheSenderToTheBody() = runTest {
        val room = presentation(sender = "Alice", roomName = "Book Club", isDm = false)

        assertEquals("Book Club", room.title)
        assertEquals("Alice: hi", room.body)
    }

    @Test
    fun platformLabelledLinesNeverRepeatTheSender() = runTest {
        val room = presentation(
            sender = "Alice",
            roomName = "Book Club",
            isDm = false,
            senderShownByPlatform = true,
        )

        assertEquals("hi", room.body)
    }

    @Test
    fun captionsFollowTheSameRuleAsBody() = runTest {
        val photo = NotificationContent(
            kind = NotificationContentKind.Media,
            attachmentKind = AttachmentKind.Image,
            fileName = "beach.jpg",
            mxcUri = "mxc://example.org/beach",
            body = "at the beach",
        )

        val dm = presentation(sender = "Alice", roomName = "Alice", isDm = true, content = photo)
        assertEquals("at the beach", dm.bodyWithMedia)

        val room = presentation(sender = "Alice", roomName = "Book Club", isDm = false, content = photo)
        assertEquals("Alice: at the beach", room.bodyWithMedia)

        val labelled = presentation(
            sender = "Alice",
            roomName = "Book Club",
            isDm = false,
            content = photo,
            senderShownByPlatform = true,
        )
        assertEquals("at the beach", labelled.bodyWithMedia)
    }

    private suspend fun presentation(
        sender: String,
        roomName: String,
        isDm: Boolean,
        content: NotificationContent = NotificationContent(
            kind = NotificationContentKind.Text,
            body = "hi",
        ),
        senderShownByPlatform: Boolean = false,
    ): NotificationPresentation = NotificationPresentation.of(
        notification = RenderedNotification(
            roomId = "!room:example.org",
            eventId = "\$event:example.org",
            roomName = roomName,
            sender = sender,
            content = content,
            isNoisy = true,
            hasMention = false,
            senderUserId = "@alice:example.org",
            tsMs = 0L,
            isDm = isDm,
            kind = NotificationKind.Message,
        ),
        showPreview = true,
        redactedBody = "New message",
        senderShownByPlatform = senderShownByPlatform,
    )
}
