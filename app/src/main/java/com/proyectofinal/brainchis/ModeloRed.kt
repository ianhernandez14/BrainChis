package com.proyectofinal.brainchis

// Tipos de acciones posibles en la red
enum class AccionRed {
    CONECTAR,           // Cliente -> Servidor (Me uno)
    INICIAR_PARTIDA,    // Servidor -> Clientes (Ya estamos todos, a jugar)
    TURNO_CAMBIO,       // Servidor -> Clientes (Le toca al Azul)
    LANZAR_DADO,        // Cliente <-> Servidor (Saqué un 5)
    MOVER_FICHA,        // Cliente <-> Servidor (Movi la ficha 2)
    USAR_POWERUP,       // Cliente <-> Servidor (Activé escudo)
    RESULTADO_BONIFICACION, // Para avisar qué pasó en la casilla segura
    DESCONEXION
}

// La estructura del mensaje JSON
data class MensajeRed(
    val accion: AccionRed,
    val colorJugador: ColorJugador? = null, // Quién hace la acción
    val nombreJugador: String? = null,

    // Datos variables (payload)
    val posicionFinal: Int? = null, //Posición final de una ficha después de un movimiento
    val resultadoDado: Int? = null,
    val idFicha: Int? = null,
    val tipoPowerUp: TipoPowerUp? = null,
    val mensajeTexto: String? = null,
    val exitoTrivia: Boolean? = null // true = ganó turno extra, false = perdió/rechazó
)