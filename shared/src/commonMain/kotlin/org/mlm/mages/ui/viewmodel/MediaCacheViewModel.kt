package org.mlm.mages.ui.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mlm.mages.MatrixService
import org.mlm.mages.matrix.MediaCacheOverview

data class MediaCacheUiState(
    val isLoading: Boolean = true,
    val overview: MediaCacheOverview? = null,
    val isClearing: Boolean = false,
    val error: String? = null,
)

class MediaCacheViewModel(
    private val service: MatrixService,
) : BaseViewModel<MediaCacheUiState>(MediaCacheUiState()) {

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            runCatching { service.port.mediaCacheOverview() }
                .onSuccess { updateState { copy(isLoading = false, overview = it) } }
                .onFailure { updateState { copy(isLoading = false, error = it.message) } }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            updateState { copy(isClearing = true, error = null) }
            service.port.clearMediaCache()
                .onSuccess { load() }
                .onFailure { updateState { copy(isClearing = false, error = it.message) } }
        }
    }
}
