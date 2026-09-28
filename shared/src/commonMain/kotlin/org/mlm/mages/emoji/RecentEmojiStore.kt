package org.mlm.mages.emoji

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mlm.mages.matrix.MatrixPort
import org.mlm.mages.matrix.RecentEmojiEntry

/**
 * Holds `m.recent_emoji`, which is account data rather than room data, so it
 * does not belong to any one room's view model.
 */
class RecentEmojiStore(private val port: () -> MatrixPort) {
    private val scope = CoroutineScope(SupervisorJob())

    private val _recent = MutableStateFlow<List<RecentEmojiEntry>>(emptyList())
    val recent: StateFlow<List<RecentEmojiEntry>> = _recent.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _recent.value = runCatching { port().recentEmoji() }.getOrDefault(emptyList())
        }
    }

    /**
     * Records a use and updates the local list immediately, so the picker does
     * not wait on the round trip. The server copy is authoritative and wins on
     * the next refresh.
     */
    fun record(emoji: String) {
        if (emoji.isEmpty()) return
        scope.launch {
            val updated = runCatching { port().recordEmojiUse(emoji) }
            if (updated.isFailure) return@launch
            val current = _recent.value
            _recent.value = buildList {
                val existing = current.firstOrNull { it.emoji == emoji }
                if (existing != null) {
                    add(existing.copy(total = existing.total + 1u))
                    addAll(current.filter { it.emoji != emoji })
                } else {
                    add(RecentEmojiEntry(emoji = emoji, total = 1u))
                    addAll(current)
                }
            }.take(MAX_LEN)
        }
    }

    private companion object {
        /** The spec's recommended cap, mirrored from ruma. */
        const val MAX_LEN = 100
    }
}
