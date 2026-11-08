package com.holo.assistant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val holoView = HolographicFaceView(this)
        setContentView(holoView)
    }
}
