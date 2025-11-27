package com.proyectofinal.brainchis

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object GestorServidores {
    private const val PREFS_NAME = "BrainchisServidores"
    private const val KEY_SERVERS = "HistorialIPs"
    private val gson = Gson()

    //Guardar una nueva IP en el historial
    fun guardarServidor(context: Context, ip: String) {
        if (ip.isBlank()) return

        //1. Cargar lista actual
        val lista = obtenerServidores(context).toMutableList()

        //2. Si ya existe, la quitamos para ponerla al principio (más reciente)
        if (lista.contains(ip)) {
            lista.remove(ip)
        }

        //3. Agregar al inicio
        lista.add(0, ip)

        //4. Limitar a los últimos 5 servidores
        if (lista.size > 5) {
            lista.removeAt(lista.size - 1)
        }

        //5. Guardar
        val json = gson.toJson(lista)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVERS, json).apply()
    }

    //Obtener la lista de IPs
    fun obtenerServidores(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SERVERS, null) ?: return emptyList()

        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}