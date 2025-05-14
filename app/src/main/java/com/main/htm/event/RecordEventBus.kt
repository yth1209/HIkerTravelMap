package com.main.htm.event

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object RecordEventBus {
    private val _eventFlow = MutableSharedFlow<String>(
        replay = 1
    )  // 이벤트 스트림
    val eventFlow = _eventFlow.asSharedFlow()

    suspend fun sendEvent() {
        _eventFlow.emit("event")  // 이벤트 발행
        Log.d("MainActivity", "Custom event broadcast sent")
    }
}