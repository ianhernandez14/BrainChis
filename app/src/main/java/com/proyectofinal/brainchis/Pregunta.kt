package com.proyectofinal.brainchis

data class Pregunta(
    val texto: String,
    val opciones: List<String>, //Siempre deben ser 4 opciones
    val indiceCorrecto: Int, //0, 1, 2 o 3
    val categoria: String //"Programación Básica", "Inglés", etc.
)