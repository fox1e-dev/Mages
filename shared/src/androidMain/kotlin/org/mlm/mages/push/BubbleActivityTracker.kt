package org.mlm.mages.push

import java.util.concurrent.ConcurrentHashMap

object BubbleActivityTracker {
    private val openRooms: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun onBubbleOpened(roomId: String) {
        openRooms.add(roomId)
    }

    fun onBubbleClosed(roomId: String) {
        openRooms.remove(roomId)
    }

    fun isBubbleOpen(roomId: String): Boolean = roomId in openRooms
}
