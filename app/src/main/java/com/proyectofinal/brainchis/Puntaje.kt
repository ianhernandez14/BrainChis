package com.proyectofinal.brainchis
data class Puntaje(
    val nombre: String,
    val aciertos: Int,
    val puntosTotales: Int,
    val fecha: Long = System.currentTimeMillis()
) : Comparable<Puntaje> {
    // Ordenar de mayor a menor puntaje
    override fun compareTo(other: Puntaje): Int {
        return other.puntosTotales - this.puntosTotales
    }
}