package dev.trackdash.core.telemetry

import dev.trackdash.core.VehicleSample
import dev.trackdash.core.VehicleSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AcquisitionStatus { STOPPED, RUNNING, ERROR }

data class VehicleTelemetryState(
    val status: AcquisitionStatus = AcquisitionStatus.STOPPED,
    val sample: VehicleSample? = null,
    val receivedSamples: Long = 0,
    val error: String? = null,
)

/** Owns one collector and one latest sample, never an unbounded session history.
 * Start/stop and collection use the same owning dispatcher (Main in the Android UI).
 * Physical sources must perform their blocking transport work on an IO dispatcher.
 */
class VehicleTelemetryRepository(private val source: VehicleSource, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(VehicleTelemetryState())
    val state = mutableState.asStateFlow()
    private var collection: Job? = null

    fun start() {
        if (collection?.isActive == true) return
        mutableState.value = VehicleTelemetryState(status = AcquisitionStatus.RUNNING)
        collection = scope.launch {
            try {
                source.samples().collect { sample ->
                    mutableState.value = VehicleTelemetryState(
                        status = AcquisitionStatus.RUNNING,
                        sample = sample,
                        receivedSamples = mutableState.value.receivedSamples + 1,
                    )
                }
                mutableState.value = mutableState.value.copy(status = AcquisitionStatus.STOPPED, sample = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = mutableState.value.copy(
                    status = AcquisitionStatus.ERROR, sample = null,
                    error = failure.message ?: "Vehicle source failed",
                )
            }
        }
    }

    fun stop() {
        collection?.cancel()
        collection = null
        mutableState.value = mutableState.value.copy(status = AcquisitionStatus.STOPPED, sample = null, error = null)
    }
}
