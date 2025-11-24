package com.proyectofinal.brainchis

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

enum class TipoSonido{
    MENU, DADO, ESPECIAL, OPCION, KILL, PASO, VICTORIA, META
}

class GestorSonido(private val context: Context)
{
    private val soundPool: SoundPool
    private val mapaSonidos = mutableMapOf<TipoSonido, Int>()

    // Variable para controlar si suena o no
    var sonidoHabilitado: Boolean = true
        private set

    private val PREFS_NAME = "BrainchisConfig"
    private val KEY_SONIDO = "SonidoHabilitado"

    init {
        // 1. Cargar configuración guardada
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sonidoHabilitado = prefs.getBoolean(KEY_SONIDO, true) // Por defecto true (suena)

        // 2. Configuración de audio
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // 3. Cargar los sonidos
        try {
            mapaSonidos[TipoSonido.MENU] = soundPool.load(context, R.raw.boton_menu, 1)
            mapaSonidos[TipoSonido.DADO] = soundPool.load(context, R.raw.dado, 1)
            mapaSonidos[TipoSonido.ESPECIAL] = soundPool.load(context, R.raw.casilla_especial, 1)
            mapaSonidos[TipoSonido.OPCION] = soundPool.load(context, R.raw.elegir_ficha, 1)
            mapaSonidos[TipoSonido.KILL] = soundPool.load(context, R.raw.kill, 1)
            mapaSonidos[TipoSonido.PASO] = soundPool.load(context, R.raw.paso, 1)
            mapaSonidos[TipoSonido.VICTORIA] = soundPool.load(context, R.raw.win, 1)
            mapaSonidos[TipoSonido.META] = soundPool.load(context, R.raw.meta, 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun reproducir(tipo: TipoSonido) {
        // Si está silenciado, no hacemos nada
        if (!sonidoHabilitado) return

        val soundId = mapaSonidos[tipo] ?: return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    // Función para cambiar el estado (On/Off) y guardar
    fun alternarSonido(): Boolean {
        sonidoHabilitado = !sonidoHabilitado

        // Guardar en memoria
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SONIDO, sonidoHabilitado).apply()

        return sonidoHabilitado
    }

    fun pausarTodo() {
        soundPool.autoPause()
    }

    fun reanudarTodo() {
        if (sonidoHabilitado) {
            soundPool.autoResume()
        }
    }

    fun liberar() {
        soundPool.release()
    }
}