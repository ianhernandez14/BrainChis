package com.proyectofinal.brainchis

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PuntajeActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puntaje)

        val btnVolver = findViewById<Button>(R.id.btnVolverMenu)
        btnVolver.setOnClickListener{
            finish()
        }

        //Configurar RecyclerView
        val recycler = findViewById<RecyclerView>(R.id.recyclerPuntajes)
        recycler.layoutManager = LinearLayoutManager(this)

        //Cargar datos
        val lista = GestorPuntajes.obtenerPuntajes(this)
        recycler.adapter = PuntajeAdapter(lista)
    }
}