package com.proyectofinal.brainchis

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AyudaActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ayuda)

        val btnVolver = findViewById<Button>(R.id.btnVolverAyuda)
        btnVolver.setOnClickListener {
            finish()
        }
    }
}