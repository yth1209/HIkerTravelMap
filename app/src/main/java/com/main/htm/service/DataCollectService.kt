package com.main.htm.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.hardware.Sensor
import android.hardware.Sensor.*
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import com.main.htm.R
import com.main.htm.common.SAMPLING_RATE
import com.main.htm.common.W
import com.main.htm.common.dto.Response
import com.main.htm.common.sec2micro
import com.main.htm.dto.CollectedDataDto
import com.main.htm.dto.WindowDto
import com.main.htm.enums.ActivityType
import com.main.htm.enums.DataCollectStatus
import com.main.htm.enums.DataFileType
import com.main.htm.event.RecordEventBus
import com.main.htm.rest.record.dto.PostDataReq
import com.main.htm.rest.record.dto.StartRecordReq
import com.main.htm.rest.record.dto.recordApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import libsvm.svm.svm_load_model
import libsvm.svm.svm_predict
import libsvm.svm_model
import retrofit2.Call
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.util.Collections
import java.util.UUID

class DataCollectService : LifecycleService(), SensorEventListener {
    private val SENSOR_TYPES = listOf(TYPE_LINEAR_ACCELERATION, TYPE_GRAVITY, TYPE_GYROSCOPE)

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient


    private lateinit var svmModel: svm_model
    private var normParams = Pair(0.0, 0.0)

    private var collectedDataMap = mutableMapOf<DataFileType, MutableList<CollectedDataDto>>()

    private var travelId: String? = null

    val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object{
        var isServiceRunning = false
        var currentDataCollectStatus = MutableStateFlow(DataCollectStatus.STOP)
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        Log.d("DataCollectService", "DataCollectService Create")
        startForegroundServiceWithNotification()
        initSVMModel()
        initNormParamsAndModel()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        isServiceRunning = true

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.registerSensors(this)


        // 코루틴 스코프에서 이벤트 감지
        lifecycleScope.launch {
            RecordEventBus.eventFlow.collect { activityType ->
                when(currentDataCollectStatus.value){
                    DataCollectStatus.STOP -> {
                        val tmp_travelId = UUID.randomUUID().toString().replace("-", "")
                        serviceScope.launch {
                            try {
                                recordApi.startRecord(StartRecordReq(tmp_travelId))
                                currentDataCollectStatus.value = DataCollectStatus.COLLECTING
                                travelId = tmp_travelId
                                sensorManager.registerSensors(this@DataCollectService)
                            } catch (e: Exception) {
                                Log.d("DataCollectService", "startRecord onFailure")
                            }
                        }
                    }
                    DataCollectStatus.COLLECTING -> {
                        serviceScope.launch {
                            try {
                                recordApi.endRecord(travelId!!)
                                currentDataCollectStatus.value = DataCollectStatus.STOP
                                sensorManager.unregisterListener(this@DataCollectService)
                                travelId = null
                            } catch (e: Exception) {
                                Log.d("DataCollectService", "endRecord onFailure")
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            while(true){
                delay(1000)

                CoroutineScope(Dispatchers.Default).launch {
                    if(currentDataCollectStatus.value == DataCollectStatus.COLLECTING) {
                        val current = SystemClock.elapsedRealtimeNanos() / 1000
                        fusedLocationProviderClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener {
                                val location = it
                                val activity = predict(current)
                                serviceScope.launch {
                                    try {
                                        recordApi.postData(
                                            PostDataReq(
                                                travelId!!,
                                                location.latitude,
                                                location.longitude,
                                                location.accuracy.toDouble(),
                                                activity.name
                                            )
                                        )
                                    } catch (e: Exception) {
                                        Log.d("DataCollectService", "postData onFailure")
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "data_collect_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Data Collection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Collecting Data")
            .setContentText("Collecting SensorData")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false

        // 센서 리스너 해제
        sensorManager.unregisterListener(this)

        Log.d("DataCollectService", "BroadcastReceiver unregistered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event == null) return
        if(event.sensor.type !in SENSOR_TYPES) return

        DataFileType.of(event.sensor.type)?.apply {
            collectedDataMap.getOrPut(this) { Collections.synchronizedList(mutableListOf()) }
                .add(
                    CollectedDataDto(
                        event.timestamp / 1000,
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    )
                )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 정확도 변경 이벤트는 처리하지 않음
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    fun predict(
        current: Long
    ): ActivityType{
        val toDelete = current - 2 * W.sec2micro()
        collectedDataMap.values.forEach {
            synchronized(it){
                it.removeIf { it.timestamp < toDelete}
            }
        }

        val beginWindow = current - W.sec2micro()
        val window = WindowDto(ActivityType.NONE, beginWindow, current)

        collectedDataMap.forEach { dataFileType, dataList ->
            synchronized(dataList){
                window.dataMap[dataFileType] =
                    dataList.filter { window.isValidTimestamp(it.timestamp) }
                        .toMutableList()
            }
        }

        window.genFeatureVector()
        window.normFeatureVector(normParams.first, normParams.second)
        val svmNodes = window.getSVMNodes()

        if(svmNodes.isEmpty()) return ActivityType.NONE

        return ActivityType.of(svm_predict(svmModel, svmNodes))
    }

    fun SensorManager.registerSensors(
        listener: SensorEventListener
    ): Boolean {
        SENSOR_TYPES.forEach {
            if (!this.getAndRegisterDefaultSensor(listener, it)) return false
        }
        return true
    }

    fun SensorManager.getAndRegisterDefaultSensor(
        listener: SensorEventListener,
        type: Int
    ): Boolean {
        return getDefaultSensor(type)?.let {
            registerListener(listener, it, 1000000/SAMPLING_RATE)
        } ?: false
    }

    fun initSVMModel(){
        val inputStream = this.assets.open("svm_model.model")
        val reader = BufferedReader(InputStreamReader(inputStream))
        svmModel = svm_load_model(reader)
        inputStream.close()
    }

    fun initNormParamsAndModel(){
        val inputStream = this.assets.open("norm_param.txt")

        val params = inputStream.bufferedReader().readText().split(",")
        normParams = Pair(params[0].toDouble(),params[1].toDouble())
    }
}