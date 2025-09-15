package com.example.robotcontroller

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button

class InfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        val btnBack: Button = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish() // quay về MainActivity
        }
    }
}