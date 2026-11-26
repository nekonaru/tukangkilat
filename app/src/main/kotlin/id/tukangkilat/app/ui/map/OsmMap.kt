package id.tukangkilat.app.ui.map

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import id.tukangkilat.app.BuildConfig
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class MapMarker(
    val lat: Double,
    val lng: Double,
    val title: String,
    val isHome: Boolean
)

/** Minimal OpenStreetMap view with markers (no API key required). */
@Composable
fun OsmMap(
    centerLat: Double,
    centerLng: Double,
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    zoom: Double = 16.0
) {
    var lastSignature by remember { mutableStateOf("") }
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID)
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { map ->
                val signature = "$centerLat,$centerLng,$zoom|" +
                    markers.joinToString { "${it.lat},${it.lng},${it.title},${it.isHome}" }
                if (signature == lastSignature) return@AndroidView
                lastSignature = signature
                map.controller.setZoom(zoom)
                map.controller.setCenter(GeoPoint(centerLat, centerLng))
                map.overlays.clear()
                markers.forEach { marker ->
                    val iconRes = if (marker.isHome) android.R.drawable.ic_menu_myplaces else android.R.drawable.ic_menu_mylocation
                    val m = Marker(map).apply {
                        position = GeoPoint(marker.lat, marker.lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = marker.title
                        setIcon(context_icon(map.context, iconRes))
                    }
                    map.overlays.add(m)
                }
                map.invalidate()
            }
        )
    }
}

private fun context_icon(context: android.content.Context, resId: Int): android.graphics.drawable.Drawable {
    return androidx.core.content.ContextCompat.getDrawable(context, resId)!!
}
