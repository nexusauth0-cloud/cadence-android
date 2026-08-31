package com.cadence.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.app.ui.UserError
import com.cadence.core.database.DownloadEntry
import com.cadence.core.database.DownloadState
import com.cadence.core.database.DownloadStorageReport
import com.cadence.core.database.OfflineAvailability
import com.cadence.core.domain.CancelDownloadUseCase
import com.cadence.core.domain.DownloadOperationsPort
import com.cadence.core.domain.DownloadStorageUseCase
import com.cadence.core.domain.ObserveDownloadsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Presenter for the Downloads region (§12): tracks pinned for offline use.
 * Backed by the real download store. Distinct states surfaced honestly —
 * "Downloaded" (complete/local file) vs "downloading/queued/failed".
 */
class DownloadsViewModel(
    observeDownloads: ObserveDownloadsUseCase,
    private val downloadStorage: DownloadStorageUseCase,
    private val cancelDownload: CancelDownloadUseCase,
    private val downloadOperations: DownloadOperationsPort?,
) : ViewModel() {

    private val _downloads = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val downloads: StateFlow<List<DownloadEntry>> = _downloads.asStateFlow()

    /**
     * Per-track offline availability, keyed by "$providerId:$nativeId".
     * Drives the per-row download toggle in Songs / Playlist detail.
     */
    val availability: StateFlow<Map<String, OfflineAvailability>> = _downloads
        .map { entries ->
            entries.associate { entry ->
                key(entry) to entry.toAvailability()
            }
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyMap())

    private val _storage = MutableStateFlow<DownloadStorageReport?>(null)
    val storage: StateFlow<DownloadStorageReport?> = _storage.asStateFlow()

    private val _error = MutableStateFlow<UserError?>(null)
    val error: StateFlow<UserError?> = _error.asStateFlow()

    init {
        observeDownloads().onEach { _downloads.value = it }.launchIn(viewModelScope)
        refreshStorage()
    }


    fun refreshStorage() {
        viewModelScope.launch {
            try {
                _storage.value = downloadStorage()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                // Non-blocking: the list still renders.
            }
        }
    }

    fun cancel(download: DownloadEntry) {
        viewModelScope.launch {
            try {
                cancelDownload(download.id)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't cancel that download.", retryable = true)
            }
        }
    }

    /** Per-track availability for a [Track]-like ref key. */
    fun availabilityForKey(ref: com.cadence.core.model.TrackRef): StateFlow<OfflineAvailability> =
        availability
            .map { map -> map[keyFor(ref)] ?: OfflineAvailability.NotDownloaded }
            .stateIn(viewModelScope, SharingStarted.Eagerly, OfflineAvailability.NotDownloaded)

    /** Start downloading a track. */
    fun download(track: com.cadence.core.model.Track) {
        viewModelScope.launch {
            val ops = downloadOperations ?: return@launch
            try {
                ops.download(track)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't start that download.", retryable = true)
            }
        }
    }

    fun remove(download: DownloadEntry) {
        viewModelScope.launch {
            val ops = downloadOperations ?: return@launch
            try {
                ops.remove(download.id.value)
                refreshStorage()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't remove that download.", retryable = true)
            }
        }
    }

    /** Remove a download by its track ref (from a row toggle). */
    fun removeByRef(ref: com.cadence.core.model.TrackRef) {
        viewModelScope.launch {
            val entry = _downloads.value.firstOrNull { it.ref == ref } ?: return@launch
            remove(entry)
        }
    }

    /** Cancel an in-progress download by its track ref. */
    fun cancelForRef(ref: com.cadence.core.model.TrackRef) {
        viewModelScope.launch {
            val entry = _downloads.value.firstOrNull { it.ref == ref } ?: return@launch
            cancel(entry)
        }
    }

    fun retry(download: DownloadEntry) {
        viewModelScope.launch {
            val ops = downloadOperations ?: return@launch
            try {
                ops.retry(download.id.value)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = UserError("Couldn't retry that download.", retryable = true)
            }
        }
    }

    fun errorShown() {
        _error.value = null
    }

    private companion object {
        fun key(entry: DownloadEntry): String =
            "${entry.ref.providerId.value}:${entry.ref.nativeId}"

        fun keyFor(ref: com.cadence.core.model.TrackRef): String =
            "${ref.providerId.value}:${ref.nativeId}"

        fun DownloadEntry.toAvailability(): OfflineAvailability = when (state) {
            DownloadState.COMPLETED -> {
                val uri = localUri
                if (isPlayableOffline && uri != null) {
                    OfflineAvailability.Available(
                        localUri = uri,
                        fileSizeBytes = fileSizeBytes ?: 0L,
                    )
                } else {
                    OfflineAvailability.Unavailable
                }
            }
            DownloadState.DOWNLOADING -> OfflineAvailability.Downloading(progressBytes, totalBytes)
            DownloadState.QUEUED -> OfflineAvailability.Downloading(progressBytes = 0, totalBytes = null)
            DownloadState.FAILED -> OfflineAvailability.Failed(reason = error ?: com.cadence.core.database.DownloadFailureReason.UNKNOWN)
            DownloadState.CANCELLED -> OfflineAvailability.Cancelled
        }
    }
}
