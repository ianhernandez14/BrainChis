package com.proyectofinal.brainchis

data class Jugador(
    val color: ColorJugador,
    val fichas: List<Ficha> = listOf(
        Ficha(1, color),
        Ficha(2, color),
        Ficha(3, color),
        Ficha(4, color)
    ),
    var tirosSinSeis: Int = 0,
    var usosPowerUpRestantes: Int = 5,
    var powerUpActivo: TipoPowerUp = TipoPowerUp.NINGUNO,
    var esIA: Boolean = false,
    var nombre: String = if(esIA) "CPU" else "Jugador",
    var aciertosTrivia: Int = 0,
    var puntaje: Int = 0
)