package com.proyectofinal.brainchis

import android.content.Context

object GestorPuntajes
{

    //Guardar un nuevo puntaje en SQLite
    fun guardarPuntaje(context: Context, nuevoPuntaje: Puntaje)
    {
        val dbHelper = BaseDeDatosHelper(context)
        dbHelper.insertarOActualizarPuntaje(nuevoPuntaje)
    }

    //Obtener lista desde SQLite
    fun obtenerPuntajes(context: Context): List<Puntaje>
    {
        val dbHelper = BaseDeDatosHelper(context)
        return dbHelper.obtenerTodosLosPuntajes()
    }
}