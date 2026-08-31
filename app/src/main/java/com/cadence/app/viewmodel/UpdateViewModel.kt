package com.cadence.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cadence.core.domain.update.UpdateManager
import com.cadence.core.domain.update.UpdateState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Thin presenter over the app-update [UpdateManager]. The UI never queries
 * GitHub/Play Store directly — it observes [state] and triggers [check]/[dismiss]
 * through this deterministic state machine. `check()` runs once (event-driven),
 * never on a polling/watcher loop.
 */
class UpdateViewModel(
    private val manager: UpdateManager,
) : ViewModel() {

    val state: StateFlow<UpdateState> = manager.state

    fun check() {
        viewModelScope.launch { manager.check() }
    }

    fun dismiss() = manager.dismiss()

    companion object {
        fun factory(manager: UpdateManager): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdateViewModel(manager) as T
        }
    }
}
