package com.algorigo.bridge

import android.app.Activity
import android.os.Bundle

class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        finish()
    }
}