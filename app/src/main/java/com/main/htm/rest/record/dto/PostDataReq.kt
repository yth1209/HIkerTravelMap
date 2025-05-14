package com.main.htm.rest.record.dto

class PostDataReq(
    val travelId: String,
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val activityType: String
) {
}