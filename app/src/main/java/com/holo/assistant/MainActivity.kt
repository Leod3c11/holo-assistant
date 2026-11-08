package com.holo.assistant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.view.Gravity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            text = "HoloAssistant\nInicializando interface holográfica..."
            textSize = 22f
            gravity = Gravity.CENTER
        }
        setContentView(view)
    }
}
