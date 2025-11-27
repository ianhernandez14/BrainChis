package com.proyectofinal.brainchis

//Estructura simple para la lista del lobby
data class InfoJugador(
    val nombre: String,
    val color: ColorJugador,
    val esHost: Boolean = false
)

enum class AccionRed {
    CONECTAR,
    ACTUALIZAR_LOBBY, //Servidor -> Clientes (Lista de jugadores actualizada)
    CAMBIAR_NOMBRE,   //Cliente -> Servidor (Me cambié el nombre)
    INICIAR_PARTIDA,
    LANZAR_DADO,
    MOVER_FICHA,
    USAR_POWERUP,
    RESULTADO_BONIFICACION,
    SYNC_PUNTAJE,
    JUGADOR_DESCONECTADO, // Alguien se fue en medio del juego
    SOLICITUD_RECONEXION,
    PARTIDA_TERMINADA_POR_HOST,
    SOLICITAR_ESTADO,     // El nuevo pide: "¿Cómo va el juego?"
    ENVIAR_ESTADO,
    DESCONEXION
}

data class MensajeRed(
    val accion: AccionRed,
    val colorJugador: ColorJugador? = null,
    val nombreJugador: String? = null,

    //Datos del Lobby
    val listaJugadores: List<InfoJugador>? = null, //La lista completa actual
    val cantidadCPUs: Int? = null, //Para iniciar partida con bots

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

    // Enviamos la lista completa de jugadores (con sus fichas y posiciones)
    val estadoJuegoCompleto: List<Jugador>? = null,
    val turnoActual: Int? = null // Para saber a quién le toca al reconectar
)