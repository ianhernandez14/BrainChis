package com.proyectofinal.brainchis

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        //Referencias para animar
        val logo = findViewById<ImageView>(R.id.imgLogoSplash)
        val texto = findViewById<TextView>(R.id.txtCargando)

        logo.alpha = 0f
        logo.translationY = 50f

        logo.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(1000)
            .start()

        texto.alpha = 0f
        texto.animate()
            .alpha(1f)
            .setDuration(1500)
            .setStartDelay(600) //Aparece después del logo
            .start()

        //Esperar 2 segundos y pasar al Menú
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() //Matar la Splash para que no se pueda volver a ella
        }, 2000)
    }
}