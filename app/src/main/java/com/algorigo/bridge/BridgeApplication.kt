package com.algorigo.bridge

import android.app.Application
import android.content.Intent

class BridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startService(Intent(this, BridgeService::class.java))
    }
}
