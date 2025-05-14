package com.main.htm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.main.htm.service.DataCollectService
import com.main.htm.ui.theme.HIkerTravelMapTheme
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.main.htm.enums.DataCollectStatus
import com.main.htm.event.RecordEventBus
import com.main.htm.rest.record.dto.recordApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay


class MainActivity : ComponentActivity() {
    private val REQUEST_CODE_FOREGROUND_LOCATION = 100
    private val REQUEST_CODE_BACKGROUND_LOCATION = 101

    private lateinit var map: MapView

    private val activityScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentPolylines: MutableList<Polyline> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initOpenStreetMap()
        requestForegroundLocationPermissions()

        updateMap(isFiltered = false)

        setContent {
            HIkerTravelMapTheme {
                AndroidView(factory = { map })
                Box(modifier = Modifier.fillMaxSize()) {

                }
                Box(modifier = Modifier.fillMaxSize()){
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ){
                        RecordButton(
                            {
                                if(checkAllLocationPermission()){
                                    when(DataCollectService.currentDataCollectStatus.value) {
                                        DataCollectStatus.STOP -> lifecycleScope.launch{
                                            RecordEventBus.sendEvent()
                                        }
                                        DataCollectStatus.COLLECTING -> lifecycleScope.launch{
                                            RecordEventBus.sendEvent()
                                        }
                                    }
                                } else {
                                    requestForegroundLocationPermissions()
                                }
                            }
                        )
                        UpdateRawMapButton {
                            updateMap(isFiltered = false)
                        }
                        UpdateFilteredMapButton {
                            updateMap(isFiltered = true)
                        }
                    }
                }
            }
        }
    }

    fun startServices(){
        val intent = Intent(this, DataCollectService::class.java)
        if(!DataCollectService.isServiceRunning) startService(intent)
    }

    fun initOpenStreetMap(){
        // osmdroid 환경 설정 초기화
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))

        // MapView 생성 및 레이아웃 설정
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

    }

    fun updateMap(
        isFiltered: Boolean
    ){
        activityScope.launch {
            currentPolylines.forEach { map.overlays.remove(it) }
            currentPolylines.clear()

            val res = if(isFiltered) recordApi.getFiltered() else recordApi.getRawPath()
            val paths = res.paths.map { path ->
                path.map {
                    GeoPoint(it[1], it[0])
                }
            }

            val colors = listOf(Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.BLACK, Color.CYAN, Color.DKGRAY, Color.GRAY, Color.LTGRAY, Color.YELLOW)

            // 각 경로마다 Polyline을 생성해서 다른 색상 적용
            paths.forEachIndexed { index, geoPoints ->
                val line = Polyline()
                line.setPoints(geoPoints)
                line.color = colors[index % colors.size] // 색상 순환
                line.width = 6f
                currentPolylines.add(line)
                map.overlays.add(line)
            }

            map.invalidate()
        }
    }

    private fun setupMyLocationOnMap() {
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)

        locationOverlay.runOnFirstFix {
            runOnUiThread {
                val myLocation: GeoPoint = locationOverlay.myLocation
                map.controller.setZoom(18.0)
                map.controller.setCenter(myLocation)
            }
        }
    }

    fun checkAllLocationPermission(): Boolean {
        return listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.isEmpty()
    }

    fun requestForegroundLocationPermissions() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissions.isNotEmpty())
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE_FOREGROUND_LOCATION)
        else {
            setupMyLocationOnMap()
            requestBackgroundLocationPermission()
        }
    }

    fun requestBackgroundLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQUEST_CODE_BACKGROUND_LOCATION
            )
        } else {
            startServices()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)

        when(requestCode) {
            REQUEST_CODE_FOREGROUND_LOCATION -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    requestBackgroundLocationPermission()
                }
            }
            REQUEST_CODE_BACKGROUND_LOCATION -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startServices()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause(){
        super.onPause()
        map.onPause()
    }
}


@Composable
fun RecordButton(f:() -> Unit){
    val collectStatus by DataCollectService.currentDataCollectStatus.collectAsState()
    Button(
        onClick = {
            f()
        }
    ) {
        Text(text =
            when(collectStatus){
                DataCollectStatus.STOP -> "Start Recording"
                DataCollectStatus.COLLECTING -> "Stop Recording"
            }
        )
    }
}

@Composable
fun UpdateRawMapButton(f:() -> Unit){
    Button(
        onClick = {
            f()
        },
    ) {
        Text(text = "Update Raw Map")
    }
}

@Composable
fun UpdateFilteredMapButton(f:() -> Unit){
    Button(
        onClick = {
            f()
        },
    ) {
        Text(text = "Update Filtered Map")
    }
}