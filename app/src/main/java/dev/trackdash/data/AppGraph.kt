package dev.trackdash.data

import android.app.Application
import androidx.room.Room
import dev.trackdash.core.gps.TrackJson
import dev.trackdash.core.simulation.DemoCircuit
import dev.trackdash.recording.RecordingState
import dev.trackdash.core.simulation.SimulationScenario
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class TrackDashApplication : Application() {
    internal var recordingSources: dev.trackdash.sources.RecordingSourceFactory = dev.trackdash.sources.AndroidRecordingSources
    // Injectable failure boundary for storage regression tests; production always uses Room.
    internal var recordingBatchWriter: (suspend (List<SampleRow>) -> Unit)? = null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, TrackDashDatabase::class.java, "trackdash.db").build() }
    val recording = MutableStateFlow(RecordingState())
    val simulationControls = MutableStateFlow(SimulationControls())
    val ready by lazy { scope.async {
        database.dao().recoverInterrupted()
        if (database.dao().track(DemoCircuit.track.id) == null)
            database.dao().saveTrack(TrackRow(DemoCircuit.track.id, DemoCircuit.track.name, TrackJson.encode(DemoCircuit.track)))
        removeLegacyVehiclePreset(database.dao())
    } }
    override fun onCreate() { super.onCreate(); ready }
}

data class SimulationControls(val lapSeconds: Double = 60.0, val gpsDropout: Boolean = false,
    val gpsJump: Long = 0, val vehicleScenario: SimulationScenario = SimulationScenario.NORMAL)

internal suspend fun removeLegacyVehiclePreset(dao: TrackDashDao) {
    // This exact id belonged to the old bundled manufacturer preset. User-created vehicles use UUIDs.
    dao.deleteVehicle(LEGACY_VEHICLE_PRESET_ID)
}

internal const val LEGACY_VEHICLE_PRESET_ID = "bmw-n52"
