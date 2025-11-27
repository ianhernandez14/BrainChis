package com.proyectofinal.brainchis

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BaseDeDatosHelper(context: Context) : SQLiteOpenHelper(context, "BrainchisDB", null, 1) {

    companion object {
        const val TABLA_PUNTAJES = "puntajes"
        const val COL_ID = "id"
        const val COL_NOMBRE = "nombre"
        const val COL_ACIERTOS = "aciertos"
        const val COL_PUNTOS = "puntos"
        const val COL_FECHA = "fecha"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val crearTabla = "CREATE TABLE $TABLA_PUNTAJES (" +
                "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_NOMBRE TEXT, " +
                "$COL_ACIERTOS INTEGER, " +
                "$COL_PUNTOS INTEGER, " +
                "$COL_FECHA LONG)"
        db?.execSQL(crearTabla)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLA_PUNTAJES")
        onCreate(db)
    }

    // Función para insertar un puntaje
    // Modificada para sumar puntos si el usuario ya existe (Case Sensitive)
    fun insertarOActualizarPuntaje(puntaje: Puntaje)
    {
        val db = this.writableDatabase

        // 1. Buscar si ya existe el nombre exacto
        // "BINARY" fuerza la distinción de mayúsculas/minúsculas en SQLite
        val cursor = db.rawQuery(
            "SELECT $COL_ID, $COL_ACIERTOS, $COL_PUNTOS FROM $TABLA_PUNTAJES WHERE $COL_NOMBRE = ? COLLATE BINARY",
            arrayOf(puntaje.nombre)
        )

        if (cursor.moveToFirst())
        {
            // --- YA EXISTE: ACTUALIZAR (SUMAR) ---
            val id = cursor.getInt(0)
            val aciertosViejos = cursor.getInt(1)
            val puntosViejos = cursor.getInt(2)

            val nuevosAciertos = aciertosViejos + puntaje.aciertos
            val nuevosPuntos = puntosViejos + puntaje.puntosTotales

            val valores = ContentValues().apply {
                put(COL_ACIERTOS, nuevosAciertos)
                put(COL_PUNTOS, nuevosPuntos)
                put(COL_FECHA, System.currentTimeMillis()) //Actualizamos fecha a la última jugada
            }

            db.update(TABLA_PUNTAJES, valores, "$COL_ID = ?", arrayOf(id.toString()))
        }
        else
        {
            // --- NO EXISTE: INSERTAR NUEVO ---
            val valores = ContentValues().apply {
                put(COL_NOMBRE, puntaje.nombre)
                put(COL_ACIERTOS, puntaje.aciertos)
                put(COL_PUNTOS, puntaje.puntosTotales)
                put(COL_FECHA, puntaje.fecha)
            }
            db.insert(TABLA_PUNTAJES, null, valores)
        }

        cursor.close()
        db.close()
    }

    // Función para leer todos los puntajes
    fun obtenerTodosLosPuntajes(): List<Puntaje> {
        val lista = mutableListOf<Puntaje>()
        val db = this.readableDatabase
        // Ordenamos por puntos de mayor a menor (DESC)
        val cursor = db.rawQuery("SELECT * FROM $TABLA_PUNTAJES ORDER BY $COL_PUNTOS DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val nombre = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOMBRE))
                val aciertos = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ACIERTOS))
                val puntos = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PUNTOS))
                val fecha = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FECHA))

                lista.add(Puntaje(nombre, aciertos, puntos, fecha))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return lista
    }
}