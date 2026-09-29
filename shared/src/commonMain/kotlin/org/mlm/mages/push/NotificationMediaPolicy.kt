package org.mlm.mages.push

import org.mlm.mages.matrix.allowsMediaPreviews
import org.mlm.mages.settings.AppSettings

object NotificationMediaPolicy {
    fun allowed(settings: AppSettings): Boolean =
        settings.notificationShowMedia &&
            settings.mediaPreviews.allowsMediaPreviews(isPrivateRoom = null)
}
