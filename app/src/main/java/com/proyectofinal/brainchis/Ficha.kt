package com.proyectofinal.brainchis
data class Ficha(
    val id: Int, //Un número del 1 al 4
    val color: ColorJugador,
    var estado: EstadoFicha = EstadoFicha.EN_BASE,

    //0 = en base
    //1-52 = casillas normales del tablero
    //53-57 = meta ROJA
    //58-62 = meta VERDE
    //63-67 = meta AMARILLA
    //68-72 = meta AZUL
    var posicionGlobal: Int = 0
)