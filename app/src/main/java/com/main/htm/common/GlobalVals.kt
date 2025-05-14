package com.main.htm.common

import android.os.Environment
import java.io.File

//Hz
val SAMPLING_RATE = 100

fun Double.sec2micro() = (this * 1000000).toLong()

//sec
val W = 2.0
val W_CNT = (W * SAMPLING_RATE).toInt()