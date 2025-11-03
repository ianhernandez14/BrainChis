package com.proyectofinal.brainchis
enum class EstadoJuego
{
    ESPERANDO_LANZAMIENTO, //El jugador debe lanzar el dado
    ESPERANDO_MOVIMIENTO,  //El jugador ya lanzó y debe elegir una ficha
    JUEGO_TERMINADO
}
