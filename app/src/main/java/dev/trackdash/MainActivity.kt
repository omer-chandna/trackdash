package dev.trackdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import android.graphics.Color
import dev.trackdash.navigation.TrackDashApp
import dev.trackdash.ui.TrackDashTheme
import dev.trackdash.simulation.SimulationViewModel

class MainActivity : ComponentActivity() {
    private val simulation by viewModels<SimulationViewModel> { SimulationViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        setContent { TrackDashTheme { TrackDashApp(simulation) } }
    }

    override fun onStop() {
        super.onStop()
        // This is a foreground demo, not a recording service. Rotation retains the stream.
        if (!isChangingConfigurations) simulation.stop()
    }
}
