package com.proyectofinal.brainchis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object GestorPuntajes
{
    private const val PREFS_NAME = "BrainchisPuntajes"
    private const val KEY_SCORES = "ListaPuntajes"

    // Guardar un nuevo puntaje
    fun guardarPuntaje(context: Context, nuevoPuntaje: Puntaje)
    {
        val listaActual = obtenerPuntajes(context).toMutableList()
        listaActual.add(nuevoPuntaje)

        // Ordenar y mantener solo los mejores 10
        listaActual.sort()
        if(listaActual.size > 10) {
            listaActual.removeAt(listaActual.size - 1)
        }

        // Convertir a JSON String manualmente
        val jsonArray = JSONArray()
        for(p in listaActual) {
            val obj = JSONObject()
            obj.put("nombre", p.nombre)
            obj.put("aciertos", p.aciertos)
            obj.put("puntos", p.puntosTotales)
            obj.put("fecha", p.fecha)
            jsonArray.put(obj)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SCORES, jsonArray.toString()).apply()
    }

    // Obtener lista
    fun obtenerPuntajes(context: Context): List<Puntaje>
    {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_SCORES, "[]")
        val lista = mutableListOf<Puntaje>()

        try {
            val jsonArray = JSONArray(jsonString)
            for(i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                lista.add(Puntaje(
                    obj.getString("nombre"),
                    obj.getInt("aciertos"),
                    obj.getInt("puntos"),
                    obj.optLong("fecha", 0)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        lista.sort()
        return lista
    }
}