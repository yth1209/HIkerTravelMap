package com.main.htm.enums

import android.hardware.Sensor

enum class DataFileType(
    val sensorType: Int,
    val fileName: String
) {
    LINEAR(Sensor.TYPE_LINEAR_ACCELERATION, "linear.csv"),
    GRAVITY(Sensor.TYPE_GRAVITY, "gravity.csv"),
    GYROSCOPE(Sensor.TYPE_GYROSCOPE, "gyro.csv");

    companion object{
        fun of(sensorType: Int): DataFileType? {
            return DataFileType.entries.find { it.sensorType == sensorType }
        }
    }
}