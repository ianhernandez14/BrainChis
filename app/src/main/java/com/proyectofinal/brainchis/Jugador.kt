package com.proyectofinal.brainchis

data class Jugador(
    val color: ColorJugador,
    val fichas: List<Ficha> = listOf(
        Ficha(1, color),
        Ficha(2, color),
        Ficha(3, color),
        Ficha(4, color)
    )
)