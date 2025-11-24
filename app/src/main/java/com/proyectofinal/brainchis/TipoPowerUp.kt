package com.proyectofinal.brainchis

enum class TipoPowerUp
{
    NINGUNO,

    //--- Modificadores de dado ---
    DADO_SOLO_PARES,      //2, 4, 6
    DADO_SOLO_IMPARES,    //1, 3, 5
    DADO_BAJOS,           //1, 2, 3
    DADO_ALTOS,           //4, 5, 6

    //--- Doble turno ---
    DOBLE_TURNO_ASEGURADO, //Permite tirar otra vez al terminar, salga lo que salga

    //--- Mis propuestas ---
    SALIDA_MAESTRA,        //Garantiza un 6
    ESCUDO_TEMPORAL        //Inmune hasta el proximo turno propio
}