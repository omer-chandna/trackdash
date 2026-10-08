package dev.trackdash.data

import android.app.Application
import androidx.room.Room
import dev.trackdash.core.gps.TrackJson
import dev.trackdash.core.simulation.DemoCircuit
import dev.trackdash.recording.RecordingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class TrackDashApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, TrackDashDatabase::class.java, "trackdash.db").build() }
    val recording = MutableStateFlow(RecordingState())
    val ready by lazy { scope.async {
        database.dao().recoverInterrupted()
        if (database.dao().track(DemoCircuit.track.id) == null)
            database.dao().saveTrack(TrackRow(DemoCircuit.track.id, DemoCircuit.track.name, TrackJson.encode(DemoCircuit.track)))
        if (database.dao().vehicle("bmw-n52") == null)
            database.dao().saveVehicle(VehicleRow("bmw-n52", "BMW 128i · N52", "BMW", "128i E82", 2009, "N52"))
    } }
    override fun onCreate() { super.onCreate(); ready }
}
