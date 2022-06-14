package com.algorigo.binderapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.algorigo.binderapp.databinding.ActivityMainBinding
import com.algorigo.bridge.BridgeBinder
import com.algorigo.rxipcbinder.RxIpcBinder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bridgeBinder: BridgeBinder? = null
    private var bindDisposable: Disposable? = null
    private var observableDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button.setOnClickListener {
            if (bindDisposable != null) {
                bindDisposable?.dispose()
            } else {
                bindDisposable = BridgeBinder.bind(this)
                    .doFinally {
                        bindDisposable = null
                        bridgeBinder = null
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnDispose {
                        binding.textView.text = "Disonnected"
                    }
                    .subscribe({
                        bridgeBinder = it
                        binding.textView.text = "Connected"
                    }, {
                        Log.e(LOG_TAG, "", it)
                        binding.textView.text = "Error:${it}"
                    })
            }
        }

        binding.startBtn.setOnClickListener {
            if (observableDisposable != null) {
                observableDisposable?.dispose()
            } else {
                observableDisposable = bridgeBinder?.getIntervalObservable(500, TimeUnit.MILLISECONDS)
                    ?.subscribeOn(Schedulers.computation())
                    ?.doFinally {
                        observableDisposable = null
                    }
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.doOnDispose {
                        printResult("Disposed")
                    }
                    ?.subscribe({
                        printResult("$it")
                    }, {
                        printResult("$it")
                    }, {
                        printResult("Completed")
                    })
            }
        }
    }

    private fun printResult(result: String) {
        binding.resultView.text = result + "\n" + binding.resultView.text
    }

    companion object {
        private val LOG_TAG = MainActivity::class.java.simpleName
    }
}