package com.proyectofinal.brainchis

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

object ClienteRed {

    var listener: ((MensajeRed) -> Unit)? = null

    private var socket: Socket? = null
    private var dataOut: DataOutputStream? = null
    private var dataIn: DataInputStream? = null

    private val gson = Gson()
    @Volatile private var estaConectado = false //Volatile asegura lectura correcta entre hilos
    private val TAG = "BrainchisRed"

    fun conectar(ip: String, puerto: Int) {
        if (estaConectado) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Iniciando conexión segura a $ip:$puerto")
                socket = Socket(ip, puerto)

                //Usamos DataStreams para leer/escribir enteros y bytes crudos
                dataOut = DataOutputStream(socket!!.getOutputStream())
                dataIn = DataInputStream(socket!!.getInputStream())

                estaConectado = true

                //Saludo inicial
                val saludo = MensajeRed(AccionRed.CONECTAR, nombreJugador = null)
                enviar(saludo)

                escucharBucleRobusto()

            } catch (e: Exception) {
                Log.e(TAG, "Fallo crítico conexión: ${e.message}")
                e.printStackTrace()
                estaConectado = false
            }
        }
    }

    private fun escucharBucleRobusto() {
        try {
            while (estaConectado && dataIn != null) {
                //1. LEER CABECERA (Bloqueante): Un entero de 4 bytes
                //Esto se quedará esperando aquí hasta que llegue un mensaje completo.
                //Si el servidor cierra, readInt() lanza EOFException.
                val longitudMensaje = dataIn!!.readInt()

                if (longitudMensaje > 0) {
                    //2. LEER CUERPO: Exactamente 'longitudMensaje' bytes
                    val buffer = ByteArray(longitudMensaje)
                    dataIn!!.readFully(buffer) //readFully garantiza que leemos todo

                    //3. Decodificar
                    val jsonString = String(buffer, Charsets.UTF_8)
                    Log.d(TAG, "RX Seguro: $jsonString")

                    try {
                        val msg = gson.fromJson(jsonString, MensajeRed::class.java)
                        listener?.invoke(msg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando JSON: ${e.message}")
                    }
                }
            }
        } catch (e: java.io.EOFException) {
            Log.w(TAG, "El servidor cerró la conexión.")
        } catch (e: Exception) {
            Log.e(TAG, "Error en bucle de escucha: ${e.message}")
        } finally {
            cerrar()
        }
    }

    fun enviar(mensaje: MensajeRed) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (estaConectado && dataOut != null) {
                    val json = gson.toJson(mensaje)
                    val bytes = json.toByteArray(Charsets.UTF_8)

                    synchronized(this) { //Evitar que dos hilos escriban a la vez y mezclen paquetes
                        //1. Escribir longitud (Int - 4 bytes)
                        dataOut!!.writeInt(bytes.size)
                        //2. Escribir contenido
                        dataOut!!.write(bytes)
                        dataOut!!.flush() //Forzar envío inmediato
                    }
                    //Log.d(TAG, "TX Seguro: $json")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar: ${e.message}")
                cerrar() //Si falla el envío, asumimos desconexión
            }
        }
    }

    fun cerrar() {
        if (!estaConectado) return
        estaConectado = false
        try {
            socket?.close()
            dataOut?.close()
            dataIn?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        socket = null
        Log.i(TAG, "Conexión cerrada y recursos liberados.")
    }

    fun estaConectado(): Boolean = estaConectado
}