package dev.trackdash.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dev.trackdash.core.OperatingMode
import kotlinx.coroutines.flow.StateFlow

/** Selection survives activity recreation; this does not start acquisition. */
class HomeViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val mode: StateFlow<String> = savedState.getStateFlow(MODE_KEY, OperatingMode.GPS_LAP_TIMER.name)

    fun selectMode(mode: OperatingMode) {
        savedState[MODE_KEY] = mode.name
    }

    companion object { private const val MODE_KEY = "operating_mode" }
}
