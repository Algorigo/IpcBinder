package com.algorigo.rxipcbinder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class RxIpcBinder private constructor() {

    inner class IpcObservable(private val observableId: Int) : Observable<ByteArrayObject>() {

        protected fun finalize() {
            iRxService?.releaseRxObject(this@RxIpcBinder.hashCode(), observableId)
        }

        override fun subscribeActual(observer: Observer<in ByteArrayObject>?) {
            val subscribeId = iRxService?.subscribe(this@RxIpcBinder.hashCode(), observableId)
            if (subscribeId != null) {
                val subject = PublishSubject.create<Pair<String, ByteArray>>()
                subjectMap[subscribeId] = subject.apply {
                    doOnDispose {
                        Log.i(LOG_TAG,"subscribeActual::doOnDispose")
                        iRxService?.dispose(this@RxIpcBinder.hashCode(), subscribeId)
                    }
                        .doFinally {
                            Log.i(LOG_TAG,"subscribeActual::doFinally")
                            if (!subject.hasObservers()) {
                                subjectMap.remove(subscribeId)
                            }
                        }
                        .map {
                            ByteArrayObject.createFrom(it.first, it.second)
                        }
                        .subscribe(observer)
                }
            }
        }
    }

    class IpcBinderException(exceptionName: String, stackString: String) : Exception("$exceptionName : $stackString")
    class BindFailedException : Exception()
    class IpcDisconnectedException : Exception()
    class NeedToUpdateClient : Exception()
    class NeedToUpdateService : Exception()

    private var iRxService: IRxService? = null
    private var bindSubject = PublishSubject.create<RxIpcBinder>()

    private val subjectMap = ConcurrentHashMap<Int, PublishSubject<Pair<String, ByteArray>>>()

    private val callback = object : IRxServiceCallback.Stub() {
        override fun callbackMessage(subscribeId: Int, message: Int, classes: String?, data: ByteArray?) {
            val subject = subjectMap[subscribeId]
            if (subject != null) {
                when (message) {
                    RxMessageType.TYPE_ON_NEXT -> {
                        subject.onNext(Pair(classes!!, data!!))
                    }
                    RxMessageType.TYPE_ON_ERROR -> {
                        subject.onError(
                            IpcBinderException(classes!!, data!!.toString(Charsets.UTF_8))
                        )
                    }
                    RxMessageType.TYPE_ON_COMPLETE -> {
                        subject.onComplete()
                    }
                }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(LOG_TAG, "onServiceConnected : $name")
            iRxService = IRxService.Stub.asInterface(service)
            iRxService?.setCallback(this@RxIpcBinder.hashCode(), callback)
            bindSubject.onNext(this@RxIpcBinder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(LOG_TAG, "onServiceDisconnected : $name")
            iRxService = null
            if (bindSubject.hasObservers()) {
                bindSubject.onError(IpcDisconnectedException())
            }
        }
    }

    private fun bind(context: Context, packageName: String, className: String): Observable<RxIpcBinder> {
        return bindSubject
            .doOnSubscribe {
                val intent = Intent(className)
                intent.setPackage(packageName)
                intent.putExtra(RxService.OBJECT_ID, hashCode())
                val result = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (!result && bindSubject.hasObservers()) {
                    bindSubject.onError(BindFailedException())
                }
            }
            .doFinally {
                Log.i(LOG_TAG, "bind doFinally")
                val iterator = subjectMap.iterator()
                while (iterator.hasNext()) {
                    iterator.next().let {
                        iRxService?.dispose(hashCode(), it.key)
                        if (it.value.hasObservers()) {
                            it.value.onError(IpcDisconnectedException())
                        }
                    }
                    iterator.remove()
                }
                Completable.timer(1, TimeUnit.SECONDS)
                    .subscribe {
                        context.unbindService(connection)
                        iRxService = null
                    }
            }
    }

    fun getObservable(type: Int, params: ByteArray): Observable<ByteArrayObject> {
        val observableId = iRxService?.createRxObject(hashCode(), type, params)
        return if (observableId != null) {
            IpcObservable(observableId)
        } else {
            Observable.error(IpcDisconnectedException())
        }
    }

    companion object {
        private val LOG_TAG = RxIpcBinder::class.java.simpleName

        fun bind(context: Context, packageName: String, className: String, binderVersion: Int): Observable<RxIpcBinder> {
            val rxIpcBinder = RxIpcBinder()
            return rxIpcBinder.bind(context, packageName, className)
                .doOnNext {
                    it.iRxService?.checkVersion(binderVersion)?.let { check ->
                        if (check > 0) {
                            throw NeedToUpdateClient()
                        } else if (check < 0) {
                            throw NeedToUpdateService()
                        }
                    }
                }
        }
    }
}