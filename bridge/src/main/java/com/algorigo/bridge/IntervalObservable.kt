package com.algorigo.bridge

import com.algorigo.rxipcbinder.ByteArrayObject
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import java.util.concurrent.TimeUnit

class IntervalObservable(values: ByteArray): Observable<ByteArrayObject>() {

    private val period: Long
    private val timeUnit: TimeUnit

    init {
        period = values.copyOfRange(0, 8).toLong()
        timeUnit = values[8].toTimeUnit()
    }

    override fun subscribeActual(observer: Observer<in ByteArrayObject>) {
        interval(period, timeUnit)
            .map { it.toString() }
            .map { StringObject(it) }
            .subscribe(observer)
    }

    companion object {
        fun getObservableTypeAndParam(period: Long, timeUnit: TimeUnit): Pair<Int, ByteArray> {
            val periodBytes = period.toByteArray()
            val intervalTimeUnit = timeUnit.toByteArray()
            val params = periodBytes + intervalTimeUnit
            return Pair(BridgeObservableType.Interval.value, params)
        }
    }
}