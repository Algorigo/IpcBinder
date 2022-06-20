package com.algorigo.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.algorigo.rxipcbinder.ByteArrayObject
import com.algorigo.rxipcbinder.RxService
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers

class BridgeService : RxService() {

    override fun onCreate() {
        super.onCreate()
        Log.e(LOG_TAG, "BridgeService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(LOG_TAG, "onStartCommand")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Title")
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun getObservable(type: Int, values: ByteArray): Observable<ByteArrayObject> {
        return when (type) {
            BridgeObservableType.Interval.value -> getIntervalObservable(values)
            else -> Observable.error(IllegalArgumentException())
        }
            .doFinally {
                Log.e(LOG_TAG, "getIntervalObservable doFinally")
            }
            .doOnNext {
                Log.e(LOG_TAG, "getIntervalObservable onNext:$it")
            }
            .subscribeOn(Schedulers.io())
    }

    private fun getIntervalObservable(values: ByteArray): Observable<ByteArrayObject> {
        return IntervalObservable(values)
    }

    companion object {
        private const val LOG_TAG = "IpcBinder:bridge:Bridge"
        private const val CHANNEL_ID = "channe_one"
        private const val CHANNEL_NAME = "Channel Name"
        private const val NOTIFICATION_ID = 1
    }
}
