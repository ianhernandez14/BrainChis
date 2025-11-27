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

object ServidorBrainchis {
    private const val PORT = 65432
    private const val TAG = "BrainchisServer"
    private var serverSocket: ServerSocket? = null
    private var estaCorriendo = false
    private val gson = Gson()
    private var serverJob: Job? = null // Para poder cancelar la corrutina principal

    private val clientes = Collections.synchronizedList(mutableListOf<ClientHandler>())

    // Lista sincronizada para evitar conflictos de hilos
    private val coloresDisponibles = Collections.synchronizedList(mutableListOf(
        ColorJugador.ROJO, ColorJugador.AMARILLO,
        ColorJugador.VERDE, ColorJugador.AZUL
    ))

    private var juegoIniciado = false

    fun iniciar() {
        // 1. Si ya estaba corriendo, lo matamos primero para evitar "zombies"
        if (estaCorriendo) {
            Log.w(TAG, "Reiniciando servidor previo...")
            detener()
        }

        estaCorriendo = true
        reiniciarEstado()

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(PORT)
                serverSocket?.reuseAddress = true // Importante para reiniciar rápido
                Log.i(TAG, "Servidor iniciado en puerto $PORT")

                while (estaCorriendo) {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        val handler = ClientHandler(socket)
                        clientes.add(handler)
                        handler.start()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket servidor cerrado o error: ${e.message}")
            } finally {
                detener()
            }
        }
    }

    fun detener() {
        estaCorriendo = false
        juegoIniciado = false

        try {
            serverSocket?.close()
        } catch (e: Exception) { e.printStackTrace() }

        // Cerrar todos los clientes
        synchronized(clientes) {
            clientes.forEach { it.cerrarSocket() }
            clientes.clear()
        }

        serverJob?.cancel()
        Log.i(TAG, "Servidor detenido completamente.")
    }

    private fun reiniciarEstado() {
        coloresDisponibles.clear()
        coloresDisponibles.addAll(listOf(ColorJugador.ROJO, ColorJugador.AMARILLO, ColorJugador.VERDE, ColorJugador.AZUL))
        juegoIniciado = false
        clientes.clear()
    }

    // Función para reciclar un color cuando alguien se va del lobby
    private fun reciclarColor(color: ColorJugador?) {
        if (color == null) return

        synchronized(coloresDisponibles) {
            if (!coloresDisponibles.contains(color)) {
                coloresDisponibles.add(color)

                // --- CORRECCIÓN: ORDENAR POR PRIORIDAD DE JUEGO ---
                // Definimos el orden exacto en que queremos que se asignen
                val ordenPrioridad = listOf(
                    ColorJugador.ROJO,
                    ColorJugador.AMARILLO,
                    ColorJugador.VERDE,
                    ColorJugador.AZUL
                )

                // Ordenamos la lista disponible basándonos en ese orden maestro
                coloresDisponibles.sortBy { ordenPrioridad.indexOf(it) }

                Log.d(TAG, "Color reciclado y reordenado: $color. Lista actual: $coloresDisponibles")
            }
        }
    }

    fun broadcast(mensaje: MensajeRed, remitente: ClientHandler?) {
        synchronized(clientes) {
            for (cliente in clientes) {
                if (cliente != remitente) cliente.enviar(mensaje)
            }
        }
    }

    private fun enviarListaLobbyATodos() {
        val listaInfo = synchronized(clientes) {
            clientes.map { InfoJugador(it.nombreJugador, it.colorAsignado!!) }
        }
        val msgLobby = MensajeRed(AccionRed.ACTUALIZAR_LOBBY, listaJugadores = listaInfo)
        broadcast(msgLobby, null)
    }

    class ClientHandler(val socket: Socket) : Thread() {
        private val dataIn = DataInputStream(socket.getInputStream())
        private val dataOut = DataOutputStream(socket.getOutputStream())
        private var corriendo = true

        var nombreJugador: String = ""
        var colorAsignado: ColorJugador? = null

        override fun run() {
            try {
                while (corriendo) {
                    val length = dataIn.readInt()
                    if (length > 0) {
                        val buffer = ByteArray(length)
                        dataIn.readFully(buffer)
                        val json = String(buffer, Charsets.UTF_8)
                        val mensaje = gson.fromJson(json, MensajeRed::class.java)
                        procesarMensaje(mensaje)
                    }
                }
            } catch (e: Exception) {
                // Error de conexión (cliente se fue)
            }finally {
                cerrarSocket()
                clientes.remove(this)

                if (!juegoIniciado) {
                    // Si estamos en Lobby: Reciclar y actualizar lista
                    reciclarColor(colorAsignado)
                    if (clientes.isNotEmpty()) ServidorBrainchis.enviarListaLobbyATodos()
                } else {
                    // --- SI ESTAMOS EN JUEGO ---
                    // 1. Reciclamos color para que pueda volver a entrar
                    reciclarColor(colorAsignado)

                    // 2. Avisamos a los demás que se desconectó (para activar CPU)
                    val msgDesc = MensajeRed(
                        AccionRed.JUGADOR_DESCONECTADO,
                        colorJugador = colorAsignado,
                        nombreJugador = nombreJugador
                    )
                    broadcast(msgDesc, null)
                }
            }
        }

        private fun procesarMensaje(mensaje: MensajeRed) {
            when (mensaje.accion) {
                AccionRed.CONECTAR -> {
                    synchronized(coloresDisponibles) {
                        if (coloresDisponibles.isEmpty()) {
                            enviar(MensajeRed(AccionRed.DESCONEXION, mensajeTexto = "Sala llena"))
                            return
                        }

                        colorAsignado = coloresDisponibles.removeAt(0)

                        val nombreEnviado = mensaje.nombreJugador
                        if (nombreEnviado != null && nombreEnviado.isNotBlank()) {
                            nombreJugador = nombreEnviado
                        } else {
                            val nombreColor = colorAsignado?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Desconocido"
                            nombreJugador = "Jugador $nombreColor"
                        }

                        // RESPONDER AL CLIENTE (Para que sepa que conectó, pero se quedará esperando estado)
                        enviar(MensajeRed(AccionRed.CONECTAR, colorJugador = colorAsignado))

                        if (!juegoIniciado) {
                            enviarListaLobbyATodos()
                        } else {
                            // --- CAMBIO: NO HACEMOS BROADCAST DIRECTO ---
                            // En lugar de avisar a todos "Fulanito entró",
                            // le avisamos SOLO al HOST para que él decida cuándo meterlo.

                            val msgSolicitud = MensajeRed(
                                accion = AccionRed.SOLICITUD_RECONEXION,
                                colorJugador = colorAsignado,
                                nombreJugador = nombreJugador
                            )

                            // Buscamos al Host (asumimos que el Host siempre es el Rojo o el primero)
                            // Como ServidorBrainchis corre en el Host, podemos usar un truco:
                            // Enviar este mensaje a "mí mismo" (loopback) a través del socket del Host
                            // O mejor: Hacer broadcast y que solo el Rojo (Host) lo procese.
                            broadcast(msgSolicitud, null)
                        }
                    }
                }

                AccionRed.CAMBIAR_NOMBRE -> {
                    this.nombreJugador = mensaje.nombreJugador ?: this.nombreJugador
                    enviarListaLobbyATodos()
                }

                AccionRed.INICIAR_PARTIDA -> {
                    val cpus = mensaje.cantidadCPUs ?: 0
                    val turnoRandom = Random.nextInt(clientes.size + cpus)
                    val msgInicio = MensajeRed(AccionRed.INICIAR_PARTIDA, resultadoDado = turnoRandom, cantidadCPUs = cpus)
                    ServidorBrainchis.broadcast(msgInicio, null)
                    juegoIniciado = true
                }

                else -> ServidorBrainchis.broadcast(mensaje, this)
            }
        }

        fun enviar(mensaje: MensajeRed) {
            if (!corriendo) return
            try {
                val json = gson.toJson(mensaje)
                val bytes = json.toByteArray(Charsets.UTF_8)
                synchronized(dataOut) {
                    dataOut.writeInt(bytes.size)
                    dataOut.write(bytes)
                    dataOut.flush()
                }
            } catch (e: Exception) {
                corriendo = false // Marcar error
            }
        }

        fun cerrarSocket() {
            corriendo = false
            try { socket.close() } catch (e: Exception) {}
        }
    }
}