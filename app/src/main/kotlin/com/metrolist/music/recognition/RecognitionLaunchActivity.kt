package com.metrolist.music.recognition

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

class RecognitionLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startRecognitionService()
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        startRecognitionService()
        finish()
    }

    private fun startRecognitionService() {
        val serviceIntent = Intent(this, RecognitionForegroundService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
