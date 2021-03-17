package com.algorigo.bridge

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class BindActivity : AppCompatActivity() {

    private var disposable: Disposable? = null
    private var bridgeBinder: BridgeBinder? = null
    private var observable: Observable<StringObject>? = null
    private var testDisposable: Disposable? = null

    private lateinit var bindBtn: Button
    private lateinit var textView: TextView
    private lateinit var testBtn: Button
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bind)

        bindBtn = findViewById(R.id.bindBtn)
        textView = findViewById(R.id.textView)
        testBtn = findViewById(R.id.button)
        logView = findViewById(R.id.logView)

        bindBtn.setOnClickListener {
            if (disposable != null) {
                disposable?.dispose()
            } else {
                disposable = BridgeBinder.bind(this)
                    .doFinally {
                        disposable = null
                        bridgeBinder = null
                        observable = null
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnDispose {
                        Log.e(LOG_TAG, "bind dispose")
                        textView.text = "Disconnected"
                    }
                    .subscribe({
                        Log.e(LOG_TAG, "onNext:$it")
                        bridgeBinder = it
                        textView.text = "Connected"

                        observable = getObservable()
                            .doOnSubscribe {
                                Log.e(LOG_TAG, "doOnSubscribe")
                            }
                            .doOnDispose {
                                Log.e(LOG_TAG, "doOnDispose")
                            }
                            .doOnTerminate {
                                Log.e(LOG_TAG, "doOnTerminate")
                            }
                            .doAfterTerminate {
                                Log.e(LOG_TAG, "doAfterTerminate")
                            }
                            .doFinally {
                                Log.e(LOG_TAG, "doFinally")
                            }
                    }, {
                        Log.e(LOG_TAG, "", it)
                        textView.text = "Error:$it"
                    })
            }
        }

        testBtn.setOnClickListener {
            startTest()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
    }

    private fun startTest() {
        if (testDisposable != null) {
            testDisposable?.dispose()
        } else {
            testDisposable = observable
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.doFinally {
                    testDisposable = null
                }
                ?.subscribe({
                    printLog("next:$it")
                }, {
                    Log.e(LOG_TAG, "", it)
                    printLog("error:$it")
                })
        }
    }

    private fun printLog(log: String) {
        logView.text = log+"\n"+logView.text
    }

    private fun getObservable(): Observable<StringObject> {
        return (bridgeBinder?.getIntervalObservable(100, TimeUnit.MILLISECONDS)
            ?.subscribeOn(Schedulers.computation())
            ?: Observable.error(IllegalArgumentException()))
    }

    companion object {
        private const val LOG_TAG = "IpcBinder:app:BindActiv"
    }
}