package dev.trackdash.tracks

import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.trackdash.core.gps.GeoPoint
import dev.trackdash.core.gps.TimingGate
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.*
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*

/** A local background style keeps gates and traces available without any tile provider. */
@Composable
fun CircuitMap(gates: List<TimingGate>, center: GeoPoint, modifier: Modifier = Modifier,
    styleUrl: String = "", trace: List<GeoPoint> = emptyList(), onTap: (GeoPoint) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tap by rememberUpdatedState(onTap)
    val features = remember(gates, trace) {
        FeatureCollection.fromFeatures(buildList {
            gates.forEach { gate -> add(Feature.fromGeometry(LineString.fromLngLats(listOf(gate.start, gate.end).map { Point.fromLngLat(it.longitude, it.latitude) }))) }
            if (trace.size >= 2) add(Feature.fromGeometry(LineString.fromLngLats(trace.map { Point.fromLngLat(it.longitude, it.latitude) })))
        })
    }
    val latest by rememberUpdatedState(features)
    val view = remember { MapLibre.getInstance(context); MapView(context).apply { onCreate(Bundle()) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    DisposableEffect(view, lifecycle) {
        view.onStart(); view.onResume()
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> view.onStart()
            Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            Lifecycle.Event.ON_STOP -> view.onStop()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        view.getMapAsync { ready ->
            map = ready
            ready.addOnMapClickListener { point -> if (point.latitude in -85.0..85.0) tap(GeoPoint(point.latitude, point.longitude)); true }
        }
        onDispose { lifecycle.removeObserver(observer); view.onPause(); view.onStop(); view.onDestroy() }
    }
    LaunchedEffect(map, styleUrl) {
        map?.setStyle(if (styleUrl.isBlank()) Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#17212b"}}]}""") else Style.Builder().fromUri(styleUrl)) { loaded ->
            loaded.addSource(GeoJsonSource("trackdash-lines", latest))
            loaded.addLayer(LineLayer("trackdash-gates", "trackdash-lines").withProperties(lineColor("#55e6b2"), lineWidth(4f)))
            style = loaded
        }
    }
    LaunchedEffect(map, center) { map?.cameraPosition = CameraPosition.Builder().target(LatLng(center.latitude, center.longitude)).zoom(16.0).build() }
    LaunchedEffect(style, features) { style?.getSourceAs<GeoJsonSource>("trackdash-lines")?.setGeoJson(features) }
    AndroidView(factory = { view }, modifier = modifier)
}
