package org.mlm.mages.ui.viewmodel

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.mlm.mages.MatrixService
import org.mlm.mages.matrix.SearchHit

private const val ROOM_BATCH = 10
private const val HITS_PER_ROOM = 20

class SearchPagingSource(
    private val service: MatrixService,
    private val roomId: String?,
    private val query: String
) : PagingSource<Int, SearchHit>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchHit> {
        return try {
            val offset = params.key

            if (roomId != null) {
                val page = service.port.searchRoom(
                    roomId = roomId,
                    query = query,
                    limit = params.loadSize,
                    offset = offset
                )
                LoadResult.Page(
                    data = page.hits,
                    prevKey = null,
                    nextKey = page.nextOffset?.toInt()
                )
            } else {
                val rooms = service.port.listRooms()
                val startIndex = offset ?: 0
                if (startIndex >= rooms.size) {
                    return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
                }

                val hits = mutableListOf<SearchHit>()
                var index = startIndex
                while (hits.size < params.loadSize && index < rooms.size) {
                    val batchEnd = minOf(index + ROOM_BATCH, rooms.size)
                    for (room in rooms.subList(index, batchEnd)) {
                        val page = runCatching {
                            service.port.searchRoom(
                                roomId = room.id,
                                query = query,
                                limit = HITS_PER_ROOM,
                                offset = null
                            )
                        }.getOrNull()
                        if (page != null) hits.addAll(page.hits)
                    }
                    index = batchEnd
                }

                LoadResult.Page(
                    data = hits.sortedByDescending { it.timestampMs.toLong() },
                    prevKey = (startIndex - ROOM_BATCH).takeIf { it >= 0 },
                    nextKey = if (index < rooms.size) index else null
                )
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SearchHit>): Int? = null
}
