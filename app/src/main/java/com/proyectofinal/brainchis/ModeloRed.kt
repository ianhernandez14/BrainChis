package com.proyectofinal.brainchis

//Estructura simple para la lista del lobby
data class InfoJugador(
    val nombre: String,
    val color: ColorJugador,
    val esHost: Boolean = false
)

enum class AccionRed {
    CONECTAR,
    ACTUALIZAR_LOBBY,
    CAMBIAR_NOMBRE,
    INICIAR_PARTIDA,
    LANZAR_DADO,
    MOVER_FICHA,
    USAR_POWERUP,
    RESULTADO_BONIFICACION,
    SYNC_PUNTAJE,
    JUGADOR_DESCONECTADO,
    SOLICITUD_RECONEXION,
    PARTIDA_TERMINADA_POR_HOST,
    INICIO_TRIVIA,
    SINCRONIZAR_TIMER,
    PING,
    SOLICITAR_ESTADO,
    ENVIAR_ESTADO,
    DESCONEXION
}

data class MensajeRed(
    val accion: AccionRed,
    val colorJugador: ColorJugador? = null,
    val nombreJugador: String? = null,

    //Datos del Lobby
    val listaJugadores: List<InfoJugador>? = null,
    val cantidadCPUs: Int? = null,

    //Datos del Juego
    val posicionFinal: Int? = null,
    val resultadoDado: Int? = null,
    val idFicha: Int? = null,
    val tipoPowerUp: TipoPowerUp? = null,
    val mensajeTexto: String? = null,
    val exitoTrivia: Boolean? = null,

    //Puntaje
    val puntosAcumulados: Int? = null,
    val aciertosAcumulados: Int? = null,

    //Enviar la lista completa de jugadores (con sus fichas y posiciones)
    val estadoJuegoCompleto: List<Jugador>? = null,
    val turnoActual: Int? = null, //Para saber a quién le toca al reconectar

    val tiempoTimer: Long? = null, //Cuánto tiempo poner
    val esParaLanzar: Boolean? = null //Qué tipo de timer es (lanzar o mover)
)