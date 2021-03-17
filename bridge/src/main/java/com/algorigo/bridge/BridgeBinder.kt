package com.algorigo.bridge

import android.content.Context
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
            return RxIpcBinder.bind(context, "com.algorigo.bridge", BridgeService::class.java.name)
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
