package com.proyectofinal.brainchis

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

object ClienteRed
{

    // CAMBIO: El listener es una variable mutable que podemos cambiar desde cualquier Activity
    var listener: ((MensajeRed) -> Unit)? = null

    private var socket: Socket? = null
    private var out: PrintWriter? = null
    private var `in`: BufferedReader? = null
    private val gson = Gson()
    private var estaConectado = false
    private val TAG = "BrainchisRed"

    fun conectar(ip: String, puerto: Int) {
        if (estaConectado) return // Si ya está conectado, no hacemos nada

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Conectando a $ip:$puerto...")
                socket = Socket(ip, puerto)
                out = PrintWriter(socket!!.getOutputStream(), true)
                `in` = BufferedReader(InputStreamReader(socket!!.getInputStream()))

                estaConectado = true

                // Saludo inicial
                val saludo = MensajeRed(AccionRed.CONECTAR, nombreJugador = "AndroidPlayer")
                enviar(saludo)

                escuchar()

            } catch (e: Exception) {
                Log.e(TAG, "Error conexión: ${e.message}")
                e.printStackTrace()
                // Avisar error si alguien escucha (opcional: crear mensaje de error)
            }
        }
    }

    private fun escuchar() {
        try {
            while (estaConectado) {
                val jsonRecibido = `in`?.readLine()
                if (jsonRecibido != null) {
                    Log.d(TAG, "RX: $jsonRecibido")
                    try {
                        val msg = gson.fromJson(jsonRecibido, MensajeRed::class.java)
                        // CAMBIO: Invocar al listener actual (sea MainActivity o JuegoActivity)
                        listener?.invoke(msg)
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON Error: ${e.message}")
                    }
                } else {
                    estaConectado = false
                }
            }
        } catch (e: Exception) {
            estaConectado = false
        }
    }

    fun enviar(mensaje: MensajeRed) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (estaConectado && out != null) {
                    val json = gson.toJson(mensaje)
                    out?.println(json)
                    // Log.d(TAG, "Enviado: $json") // Descomenta esto si quieres ver todo
                } else {
                    Log.e(TAG, "Error enviar: No conectado o out es null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "TX Error: ${e.message}")
            }
        }
    }

    fun cerrar() {
        estaConectado = false
        try { socket?.close() } catch (e: Exception) {}
        socket = null
    }

    fun estaConectado(): Boolean = estaConectado
}