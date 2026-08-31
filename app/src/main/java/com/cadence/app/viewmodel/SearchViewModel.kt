package com.cadence.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.app.ui.UserError
import com.cadence.app.ui.toUserError
import com.cadence.core.domain.SearchUseCase
import com.cadence.core.model.Track
import com.cadence.source.api.SearchFilter
import com.cadence.source.api.SourceError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(
        val query: String,
        val tracks: List<Track>,
    ) : SearchUiState
    data class Error(
        val query: String,
        val error: UserError,
    ) : SearchUiState
}

/**
 * Debounced instant search (≈300 ms) over the catalog (§9). Playback is
 * delegated through [onPlayTrack] to the shared [PlaybackViewModel] so the UI
 * never touches resolution providers directly.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val searchUseCase: SearchUseCase,
    private val onPlayTrack: (Track) -> Unit,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _activeFilter = MutableStateFlow<SearchFilter?>(null)
    val activeFilter: StateFlow<SearchFilter?> = _activeFilter.asStateFlow()

    init {
        _query
            .debounce(300)
            .filter { it.isNotBlank() }
            .onEach { text -> runSearch(text) }
            .launchIn(viewModelScope)
    }

    private suspend fun runSearch(text: String) {
        _uiState.value = SearchUiState.Loading
        try {
            val result = searchUseCase.invoke(text, filter = _activeFilter.value)
            _uiState.value = SearchUiState.Results(text, result.items)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: SourceError) {
            _uiState.value = SearchUiState.Error(text, e.toUserError())
        }
    }

    fun onQueryChange(text: String) {
        _query.value = text
        if (text.isBlank()) _uiState.value = SearchUiState.Idle
    }

    fun onResultTapped(track: Track) = onPlayTrack(track)

    fun clearQuery() {
        _query.value = ""
    }

    fun retry() {
        val q = _query.value
        if (q.isNotBlank()) viewModelScope.launch { runSearch(q) }
    }
}
