package com.main.htm.dto

import com.main.htm.common.W_CNT
import com.main.htm.enums.ActivityType
import com.main.htm.enums.DataFileType
import libsvm.svm_node
import java.lang.Math.log
import kotlin.math.round

class WindowDto(
    val activityType: ActivityType,
    val startTimeStamp: Long,
    val endTimeStamp: Long
){
    var dataMap =  mutableMapOf<DataFileType, MutableList<CollectedDataDto>>()
    var featureVector = mutableListOf<Double>()

    init {
        DataFileType.entries.forEach {
            dataMap[it] = mutableListOf()
        }
    }

    fun isValidTimestamp(timestamp: Long): Boolean = timestamp in startTimeStamp..endTimeStamp

    fun isInValidSize() = dataMap.values.any { it.isEmpty() || it.size < (W_CNT - 10) }

    fun genFeatureVector(){
        featureVector.addAll(getFeatureVectorPerFileType(DataFileType.LINEAR))
        featureVector.addAll(getFeatureVectorPerFileType(DataFileType.GYROSCOPE))
        featureVector.addAll(getFeatureVectorPerFileType(DataFileType.GRAVITY))
    }

    fun getFeatureVectorPerFileType(dataFileType: DataFileType): List<Double>{
        val datas = try {
            dataMap[dataFileType]!!
        } catch (e: Exception) {
            throw Exception("DataFileType: $dataFileType")
        }

        val featureVector = mutableListOf<Double>()

        featureVector.addAll(datas.meanFeatureVector())
        featureVector.addAll(datas.entropyFeaturVector())

        return featureVector
    }

    fun List<CollectedDataDto>.meanFeatureVector(): MutableList<Double>{
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0

        this.forEach {
            sumX += it.x.toDouble()
            sumY += it.y.toDouble()
            sumZ += it.z.toDouble()
        }

        return mutableListOf(
            sumX / this.size,
            sumY / this.size,
            sumZ / this.size
        )
    }

    fun List<CollectedDataDto>.entropyFeaturVector(): MutableList<Double>{
        fun List<Double>.entropy(): Double{
            var entropy = 0.0

            this.map { round(it / 0.1) }
                .groupBy { it }
                .mapValues { it.value.size }
                .forEach{ value, count ->
                    val p = count.toDouble() / this.size.toDouble()
                    entropy += count * (-p * log(p))
                }
            return entropy
        }

        return mutableListOf(
            this.map { it.x.toDouble() }.entropy(),
            this.map { it.y.toDouble() }.entropy(),
            this.map { it.z.toDouble() }.entropy()
        )
    }

    fun normFeatureVector(a: Double, b: Double){
        featureVector.forEachIndexed { index, d ->
            featureVector[index] = a * d + b
        }
    }

    fun getSVMStr(): String{
        var str = "${activityType.classNo} "
        featureVector.forEachIndexed { idx, f ->
            str += "$idx:${"%.10f".format(f)} "
        }
        return str
    }

    fun getSVMNodes(): Array<svm_node> = featureVector.mapIndexed {idx, feature ->
        svm_node().apply {
            this.index = idx
            this.value = feature
        }
    }.toTypedArray()


}
