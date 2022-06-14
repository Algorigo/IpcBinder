package com.algorigo.bridge

import android.content.Context
import android.os.Build
import com.algorigo.rxipcbinder.RxIpcBinder
import io.reactivex.rxjava3.core.Observable
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class BridgeBinder private constructor(private val rxIpcBinder: RxIpcBinder){

    fun getIntervalObservable(period: Long, timeUnit: TimeUnit): Observable<StringObject> {
        val periodBytes = period.toByteArray()
        val intervalTimeUnit = timeUnit.toByteArray()
        val params = periodBytes + intervalTimeUnit
        return rxIpcBinder.getObservable(0, params)
            .map {
                it as StringObject
            }
    }

    companion object {
        fun bind(context: Context): Observable<BridgeBinder> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RxIpcBinder.bind(context, BridgeService::class.java.packageName, BridgeService::class.java.name)
            } else {
                BridgeService::class.java.`package`?.name?.let {
                    RxIpcBinder.bind(context, it, BridgeService::class.java.name)
                } ?: throw NullPointerException("BridgeService package name is null:${BridgeService::class.java.`package`}")
            }
                .map {
                    BridgeBinder(it)
                }
        }
    }
}

fun Long.toByteArray(): ByteArray {
    return ByteBuffer.allocate(8).putLong(this).array()
}

fun TimeUnit.toByteArray(): ByteArray {
    return byteArrayOf((0xff and this.ordinal).toByte())
}
