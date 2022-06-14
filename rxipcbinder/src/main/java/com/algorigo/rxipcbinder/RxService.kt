package com.algorigo.rxipcbinder

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

abstract class RxService : Service() {

    private val listenerMap = mutableMapOf<Int, IRxServiceCallback>()
    private val observableMap = mutableMapOf<Int, MutableMap<Int, Observable<ByteArrayObject>>>()
    private val disposables = mutableMapOf<Int, MutableMap<Int, Disposable>>()

    private val binder = object : IRxService.Stub() {
        override fun setCallback(objectId: Int, callback: IRxServiceCallback) {
            Log.i(LOG_TAG, "serCallback:${objectId}")
            observableMap[objectId] = mutableMapOf()
            disposables[objectId] = mutableMapOf()
            listenerMap[objectId] = callback
        }

        override fun createRxObject(objectId: Int, type: Int, params: ByteArray): Int {
            val observableId = (Math.random() * Int.MAX_VALUE).toInt()
            val observable = getObservable(type, params)
            observableMap[objectId]?.set(observableId, observable)
            return observableId
        }

        override fun releaseRxObject(objectId: Int, observableId: Int) {
            observableMap.remove(observableId)
        }

        override fun subscribe(objectId: Int, observableId: Int): Int {
            val observable = observableMap[objectId]?.get(observableId)
            if (observable != null) {
                val subscribeId = (Math.random() * Int.MAX_VALUE).toInt()
                val disposable = observable
                    .doFinally {
                        disposables[objectId]?.remove(subscribeId)
                    }
                    .observeOn(Schedulers.io())
                    .subscribe({
                        it.toByteArray().also {
                            listenerMap[objectId]?.callbackMessage(subscribeId, RxMessageType.TYPE_ON_NEXT, it.first, it.second)
                        }
                    }, {
                        listenerMap[objectId]?.callbackMessage(subscribeId, RxMessageType.TYPE_ON_ERROR, it.javaClass.name, it.stackTraceToString().toByteArray(Charsets.UTF_8))
                    }, {
                        listenerMap[objectId]?.callbackMessage(subscribeId, RxMessageType.TYPE_ON_COMPLETE, null, null)
                    })
                disposables[objectId]?.set(subscribeId, disposable)
                return subscribeId
            }
            return -1
        }

        override fun dispose(objectId: Int, subscribeId: Int) {
            disposables[objectId]?.get(subscribeId)?.dispose()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(LOG_TAG, "onBind:${intent.action}:${intent.extras?.keySet()?.toTypedArray()?.contentToString()}")
        val objectId = intent.getIntExtra(OBJECT_ID, -1)
        return if (objectId != -1) {
            binder
        } else {
            object : Binder() {}
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(LOG_TAG, "onUnbind:${intent?.action}:${intent?.extras?.keySet()?.toTypedArray()?.contentToString()}")
        val objectId = intent?.getIntExtra(OBJECT_ID, -1)
        disposables.remove(objectId)?.values?.forEach { it.dispose() }
        observableMap.remove(objectId)
        listenerMap.remove(objectId)
        return true
    }

    protected abstract fun getObservable(type: Int, values: ByteArray): Observable<ByteArrayObject>

    override fun onDestroy() {
        super.onDestroy()
        Log.i(LOG_TAG, "BridgeService onDestroy:\n${observableMap.keys.toTypedArray().contentToString()}\n${disposables.keys.toTypedArray().contentToString()}")
        disposables.values.map { it.values }.flatten().forEach {
            it.dispose()
        }
        disposables.clear()
        observableMap.clear()
    }

    companion object {
        private const val LOG_TAG = "IpcBinder:lib:RxService"
        const val OBJECT_ID = "ObjectId"
    }
}