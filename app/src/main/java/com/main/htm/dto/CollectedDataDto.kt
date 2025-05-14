package com.main.htm.dto

import com.main.htm.enums.ActivityType

class CollectedDataDto{

    private val activityType: ActivityType
    val timestamp: Long
    val x: Float
    val y: Float
    val z: Float

    constructor(
        dataStr: String
    ){
        val datas = dataStr.split(",")
        if(datas.size != 5)
            throw Exception("Invalid Data Format")
        this.activityType = ActivityType.of(datas[0].toInt()) ?: throw Exception("Invalid activityType")
        this.timestamp = datas[1].toLong()
        this.x = datas[2].toFloatOrNull() ?: throw Exception("Invalid data value")
        this.y = datas[3].toFloatOrNull() ?: throw Exception("Invalid data value")
        this.z = datas[4].toFloatOrNull() ?: throw Exception("Invalid data value")
    }

    constructor(
        timestamp: Long,
        x: Float,
        y: Float,
        z: Float,
        activityType: ActivityType = ActivityType.NONE
    ){
        this.activityType = activityType
        this.timestamp = timestamp
        this.x = x
        this.y = y
        this.z = z
    }
}
