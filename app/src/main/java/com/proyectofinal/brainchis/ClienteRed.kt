package com.proyectofinal.brainchis

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

object ClienteRed
{
    
    var listener: ((MensajeRed) -> Unit)? = null
    
    private var socket: Socket? = null
    private var dataOut: DataOutputStream? = null
    private var dataIn: DataInputStream? = null
    
    private val gson = Gson()
    
    //Volatile asegura lectura correcta entre hilos (supuestamente)
    @Volatile
    private var estaConectado = false
    private val TAG = "BrainchisRed"
    
    fun conectar(ip: String, puerto: Int)
    {
        if(estaConectado)
            return
        
        CoroutineScope(Dispatchers.IO).launch {
            
            try
            {
                Log.i(TAG, "Iniciando conexión a $ip:$puerto")
                socket = Socket(ip, puerto)
                
                //Usar DataStreams para leer/escribir enteros y bytes
                dataOut = DataOutputStream(socket!!.getOutputStream())
                dataIn = DataInputStream(socket!!.getInputStream())
                
                estaConectado = true
                
                //Saludo inicial
                val saludo = MensajeRed(AccionRed.CONECTAR, nombreJugador = null)
                enviar(saludo)
                
                iniciarLatido()
                
                escucharBucle()
                
            }
            catch(e: Exception)
            {
                Log.e(TAG, "Fallo en la conexión: ${e.message}")
                e.printStackTrace()
                estaConectado = false
            }
        }
    }
    
    fun obtenerIpServidor(): String?
    {
        return socket?.inetAddress?.hostAddress
    }
    
    //Esta función es para detectar si a alguien se le va el internet. Es para evitar
    //congelamientos en el juego y hacelro maás fluido
    private fun iniciarLatido()
    {
        CoroutineScope(Dispatchers.IO).launch {
            
            while(estaConectado)
            {
                try
                {
                    delay(2000) //Enviar "estoy vivo" cada 2 segundos
                    
                    if(estaConectado)
                        enviar(MensajeRed(AccionRed.PING))
                }
                catch(e: Exception){
                    //Si falla el envío localmente, cerramos
                    cerrar()
                }
            }
        }
    }
    
    /*Inicia un bucle infinito en el hilo actual para leer constantemente los mensajes
    que llegan desde el servidor. Procesa cada mensaje y lo notifica al listener.
    Si la conexión se pierde o el servidor la cierra, finaliza el bucle y limpia los recursos */
    private fun escucharBucle()
    {
        try
        {
            while(estaConectado && dataIn != null)
            {
                val longitudMensaje = dataIn!!.readInt()
                
                if(longitudMensaje > 0)
                {
                    val buffer = ByteArray(longitudMensaje)
                    dataIn!!.readFully(buffer)
                    val jsonString = String(buffer, Charsets.UTF_8)
                    
                    try
                    {
                        val msg = gson.fromJson(jsonString, MensajeRed::class.java)
                        
                        if(msg.accion == AccionRed.PING)
                        {
                            //El simple hecho de haberlo leído confirma la conexión,
                            //con lo cual continuamos
                            continue
                        }
                        
                        listener?.invoke(msg)
                    }
                    catch(e: Exception){
                        Log.e(TAG, "Error parseando JSON: ${e.message}")
                    }
                }
            }
        }
        catch(e: java.io.EOFException){
            Log.w(TAG, "El servidor cerró la conexión")
        }
        catch(e: Exception){
            Log.e(TAG, "Error en bucle de escucha: ${e.message}")
        }
        finally{
            cerrar()
        }
    }
    
    //Serializa y envía un objeto [MensajeRed] al servidor de forma asíncrona
    fun enviar(mensaje: MensajeRed)
    {
        CoroutineScope(Dispatchers.IO).launch{
            try
            {
                if(estaConectado && dataOut != null)
                {
                    val json = gson.toJson(mensaje)
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    
                    //Evitar que dos hilos escriban a la vez y mezclen paquetes
                    synchronized(this)
                    {
                        //Escribir longitud (Int - 4 bytes)
                        dataOut!!.writeInt(bytes.size)
                        
                        //Escribir contenido
                        dataOut!!.write(bytes)
                        dataOut!!.flush() //Forzar envío inmediato
                    }
                }
            }
            catch(e: Exception)
            {
                Log.e(TAG, "Error al enviar: ${e.message}")
                cerrar() //Si falla el envío, asumir que hubo una desconexión
            }
        }
    }
    
    //Cierra la conexión, los streams y libera todos los recursos de red
    fun cerrar()
    {
        if(!estaConectado) return
        estaConectado = false
        try
        {
            socket?.close()
            dataOut?.close()
            dataIn?.close()
        }
        catch(e: Exception)
        {
            e.printStackTrace()
        }
        socket = null
        Log.i(TAG, "Conexión cerrada y recursos liberados.")
    }
    
    //Verifica si el socket está actualmente conectado
    fun estaConectado(): Boolean = estaConectado
}