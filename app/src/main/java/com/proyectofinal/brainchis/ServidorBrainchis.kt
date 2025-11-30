package com.proyectofinal.brainchis

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.random.Random

object ServidorBrainchis
{
    private const val PORT = 65432
    private const val TAG = "BrainchisServer"
    private var serverSocket: ServerSocket? = null
    private var estaCorriendo = false
    private val gson = Gson()
    private var serverJob: Job? = null
    
    private val clientes = Collections.synchronizedList(mutableListOf<ClientHandler>())
    
    //Colores disponibles actualmente
    private val coloresDisponibles = Collections.synchronizedList(
        mutableListOf(
            ColorJugador.ROJO, ColorJugador.AMARILLO,
            ColorJugador.VERDE, ColorJugador.AZUL
        )
    )
    
    //--- MEMORIA DE SESIÓN ---
    //Mapa: Color -> IP (Ej: ROJO -> 192.168.1.50)
    private val historialIPs = Collections.synchronizedMap(mutableMapOf<ColorJugador,
            String>())
    
    //Mapa: Color -> Nombre (Ej: AMARILLO -> "Oscar")
    private val historialNombres = Collections.synchronizedMap(mutableMapOf<ColorJugador,
            String>())
    
    private var juegoIniciado = false
    
    fun iniciar()
    {
        if(estaCorriendo)
            detener()
        
        estaCorriendo = true
        reiniciarEstado()
        
        serverJob = CoroutineScope(Dispatchers.IO).launch{
            try
            {
                serverSocket = ServerSocket(PORT)
                serverSocket?.reuseAddress = true
                Log.i(TAG, "Servidor iniciado en puerto $PORT")
                
                while(estaCorriendo)
                {
                    val socket = serverSocket?.accept()
                    
                    if(socket != null)
                    {
                        val handler = ClientHandler(socket)
                        clientes.add(handler)
                        handler.start()
                    }
                }
            }
            catch(e: Exception){
                Log.e(TAG, "Error servidor: ${e.message}")
            }
            finally{
                detener()
            }
        }
    }
    
    fun detener()
    {
        estaCorriendo = false
        juegoIniciado = false
        try{
            serverSocket?.close()
        }
        catch(e: Exception){}
        
        synchronized(clientes)
        {
            clientes.forEach { it.cerrarSocket() }
            clientes.clear()
        }
        
        serverJob?.cancel()
    }
    
    private fun reiniciarEstado()
    {
        coloresDisponibles.clear()
        coloresDisponibles.addAll(
            listOf(
                ColorJugador.ROJO,
                ColorJugador.AMARILLO,
                ColorJugador.VERDE,
                ColorJugador.AZUL
            )
        )
        
        //Limpiar memoria al crear nueva sala
        historialIPs.clear()
        historialNombres.clear()
        
        juegoIniciado = false
        clientes.clear()
    }
    
    private fun reciclarColor(color: ColorJugador?)
    {
        if(color == null)
            return
        
        synchronized(coloresDisponibles)
        {
            if(!coloresDisponibles.contains(color))
            {
                coloresDisponibles.add(color)
                
                //Ordenar por prioridad (Rojo -> Amarillo -> Verde -> Azul)
                val ordenPrioridad = listOf(
                    ColorJugador.ROJO,
                    ColorJugador.AMARILLO,
                    ColorJugador.VERDE,
                    ColorJugador.AZUL
                )
                coloresDisponibles.sortBy{ ordenPrioridad.indexOf(it) }
            }
        }
    }
    
    //Función para actualizar nombre en el historial (llamada cuando alguien edita su nombre)
    fun actualizarNombreHistorico(color: ColorJugador, nombre: String){
        historialNombres[color] = nombre
    }
    
    fun broadcast(mensaje: MensajeRed, remitente: ClientHandler?)
    {
        synchronized(clientes)
        {
            for(cliente in clientes)
            {
                if(cliente != remitente)
                    cliente.enviar(mensaje)
            }
        }
    }
    
    private fun enviarListaLobbyATodos()
    {
        val listaInfo = synchronized(clientes){
            clientes.map { InfoJugador(it.nombreJugador, it.colorAsignado!!) }
        }
        
        val msgLobby = MensajeRed(AccionRed.ACTUALIZAR_LOBBY, listaJugadores = listaInfo)
        broadcast(msgLobby, null)
    }
    
    class ClientHandler(val socket: Socket) : Thread()
    {
        private val dataIn = DataInputStream(socket.getInputStream())
        private val dataOut = DataOutputStream(socket.getOutputStream())
        private var corriendo = true
        
        //IP del cliente
        val ipCliente: String = socket.inetAddress.hostAddress ?: ""
        
        var nombreJugador: String = ""
        var colorAsignado: ColorJugador? = null
        private var ultimoMensajeTime = System.currentTimeMillis()
        
        override fun run()
        {
            iniciarMonitorTimeout()
            
            try
            {
                while(corriendo)
                {
                    val length = dataIn.readInt()
                    if(length > 0)
                    {
                        ultimoMensajeTime = System.currentTimeMillis()
                        val buffer = ByteArray(length)
                        dataIn.readFully(buffer)
                        val json = String(buffer, Charsets.UTF_8)
                        val mensaje = gson.fromJson(json, MensajeRed::class.java)
                        procesarMensaje(mensaje)
                    }
                }
            }
            catch(e: Exception){}
            finally
            {
                cerrarSocket()
                clientes.remove(this)
                
                if(!juegoIniciado)
                {
                    //Si es lobby, reciclar el color
                    reciclarColor(colorAsignado)
                    if(clientes.isNotEmpty())
                        enviarListaLobbyATodos()
                }
                else
                {
                    //Si es juego, reciclar el color y avisar de la desconexión
                    reciclarColor(colorAsignado)
                    
                    val msgDesc = MensajeRed(
                        AccionRed.JUGADOR_DESCONECTADO,
                        colorJugador = colorAsignado,
                        nombreJugador = nombreJugador
                    )
                    
                    broadcast(msgDesc, null)
                }
            }
        }
        
        private fun iniciarMonitorTimeout()
        {
            CoroutineScope(Dispatchers.IO).launch{
                while(corriendo)
                {
                    sleep(1000)
                    
                    if(System.currentTimeMillis() - ultimoMensajeTime > 5000)
                    {
                        cerrarSocket()
                        break
                    }
                }
            }
        }
        
        private fun procesarMensaje(mensaje: MensajeRed)
        {
            when(mensaje.accion)
            {
                //Ignorar
                AccionRed.PING -> {}
                
                AccionRed.CONECTAR ->
                {
                    synchronized(coloresDisponibles)
                    {
                        if(coloresDisponibles.isEmpty())
                        {
                            enviar(MensajeRed(AccionRed.DESCONEXION, mensajeTexto = "Sala llena"))
                            return
                        }
                        
                        //--- ALGORITMO DE ASIGNACIÓN POR IP ---
                        var colorElegido: ColorJugador? = null
                        
                        // Buscar si esta IP ya tenía un color asignado en esta sesión
                        for(color in coloresDisponibles)
                        {
                            if(historialIPs[color] == ipCliente)
                            {
                                colorElegido = color
                                break
                            }
                        }
                        
                        //Si no tiene historial, o su color histórico está ocupado,
                        //buscar el primer color que no tenga historial (nuevo jugador)
                        if(colorElegido == null)
                        {
                            for(color in coloresDisponibles)
                            {
                                if(!historialIPs.containsKey(color))
                                {
                                    colorElegido = color
                                    break
                                }
                            }
                        }
                        
                        //Si todos tienen historial pero el mío no coincide,
                        //tomar el primero disponible
                        if(colorElegido == null)
                            colorElegido = coloresDisponibles[0]
                        
                        //Asignar y quitar de la lista
                        coloresDisponibles.remove(colorElegido)
                        colorAsignado = colorElegido
                        
                        //Guardar IP en historial
                        historialIPs[colorAsignado!!] = ipCliente
                        
                        //--- RECUPERAR NOMBRE ---
                        val nombreEnviado = mensaje.nombreJugador
                        val nombreHistorico = historialNombres[colorAsignado]
                        
                        if(nombreHistorico != null)
                        {
                            //Si ya había un nombre guardado, usarlo
                            nombreJugador = nombreHistorico
                        }
                        else if(nombreEnviado != null && nombreEnviado.isNotBlank())
                        {
                            nombreJugador = nombreEnviado
                            historialNombres[colorAsignado!!] = nombreJugador
                        }
                        else
                        {
                            val nombreColor = colorAsignado?.name?.lowercase()
                                ?.replaceFirstChar { it.uppercase() } ?: "Desconocido"
                            nombreJugador = "Jugador $nombreColor"
                            historialNombres[colorAsignado!!] = nombreJugador
                        }
                        
                        //RESPONDER
                        enviar(
                            MensajeRed(
                                AccionRed.CONECTAR,
                                colorJugador = colorAsignado,
                                nombreJugador = nombreJugador
                            )
                        )
                        
                        if(!juegoIniciado)
                            enviarListaLobbyATodos()
                        else
                        {
                            //Reconexión en juego
                            broadcast(
                                MensajeRed(
                                    AccionRed.SOLICITUD_RECONEXION, //Avisar al host
                                    colorJugador = colorAsignado,
                                    nombreJugador = nombreJugador
                                ), null
                            )
                        }
                    }
                }
                
                AccionRed.CAMBIAR_NOMBRE ->
                {
                    val nuevoNombre = mensaje.nombreJugador ?: nombreJugador
                    this.nombreJugador = nuevoNombre
                    
                    //Actualizar historial
                    if(colorAsignado != null)
                        actualizarNombreHistorico(colorAsignado!!, nuevoNombre)
                    
                    enviarListaLobbyATodos()
                }
                
                AccionRed.INICIAR_PARTIDA ->
                {
                    val cpus = mensaje.cantidadCPUs ?: 0
                    val turnoRandom = Random.nextInt(clientes.size + cpus)
                    val msgInicio = MensajeRed(
                        AccionRed.INICIAR_PARTIDA,
                        resultadoDado = turnoRandom,
                        cantidadCPUs = cpus
                    )
                    
                    broadcast(msgInicio, null)
                    juegoIniciado = true
                }
                
                else -> broadcast(mensaje, this)
            }
        }
        
        fun enviar(mensaje: MensajeRed)
        {
            if(!corriendo)
                return
            
            try
            {
                val json = gson.toJson(mensaje)
                val bytes = json.toByteArray(Charsets.UTF_8)
                synchronized(dataOut) {
                    dataOut.writeInt(bytes.size)
                    dataOut.write(bytes)
                    dataOut.flush()
                }
            }
            catch(e: Exception){
                corriendo = false
            }
        }
        
        fun cerrarSocket()
        {
            corriendo = false
            try
            {
                socket.close()
            }
            catch(e: Exception){}
        }
    }
}