package com.algorigo.bridge

import android.content.Context
import com.algorigo.rxipcbinder.RxIpcBinder
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.TimeUnit

class BridgeBinder private constructor(private val rxIpcBinder: RxIpcBinder){

    fun getIntervalObservable(period: Long, timeUnit: TimeUnit): Observable<StringObject> {
        return IntervalObservable.getObservableTypeAndParam(period, timeUnit)
            .let {
                rxIpcBinder.getObservable(it.first, it.second)
            }
            .map {
                it as StringObject
            }
    }

    companion object {
        fun bind(context: Context): Observable<BridgeBinder> {
            return RxIpcBinder.bind(context, "com.algorigo.bridge", "com.algorigo.bridge.BridgeService")
                .map {
                    BridgeBinder(it)
                }
        }
    }
}
