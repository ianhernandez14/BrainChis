package com.proyectofinal.brainchis

import android.util.Log
import kotlin.random.Random

class GestorJuego
{
    private val LONGITUD_META = 6
    private val TAG = "ParchisDEBUG"

    //--- PROPIEDADES DEL JUEGO ---
    var jugadores: List<Jugador> = emptyList()
        private set

    private var indiceTurnoActual: Int = 0
    private var seisesConsecutivos: Int = 0
    private var turnosExtraPorKill: Int = 0

    var estadoJuego: EstadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO

    val jugadorActual: Jugador
        get() = jugadores[indiceTurnoActual]

    //--- LÓGICA DEL TABLERO ---
    //Número de casillas del camino principal (no incluye metas)
    private val casillasTotalesTablero = 52

    //Casillas seguras (estrellas + salidas) en el camino principal
    private val casillasSeguras: Set<Int> = setOf(
        1, 14, 27, 40, //salidas de cada base
        9, 22, 35, 48  //estrellas adicionales
    )

    //Casilla de salida (primer casillero de color) de cada jugador en el camino principal
    private val posicionSalida: Map<ColorJugador, Int> = mapOf(
        ColorJugador.ROJO to 1,
        ColorJugador.VERDE to 14,
        ColorJugador.AMARILLO to 27,
        ColorJugador.AZUL to 40
    )

    /*Casilla de ENTRADA AL CAMINO DE META
     Referencia (CAMINO_PRINCIPAL en Tablero):
      - ROJO recorre la parte izquierda -> su entrada la dejamos en 52
      - VERDE recorre arriba -> entrada en 13
      - AMARILLO recorre derecha -> entrada en 26
      - AZUL recorre abajo -> la casilla justo debajo de la estrella azul
        está en (6,13), que en CAMINO_PRINCIPAL es la posición 40 */
    private val posicionEntradaMeta: Map<ColorJugador, Int> = mapOf(
        ColorJugador.ROJO to 51,
        ColorJugador.VERDE to 12,
        ColorJugador.AMARILLO to 25,
        ColorJugador.AZUL to 38
    )

    /*Base de las metas
     Cada color tiene 5 casillas de meta:
         ROJO:   53-57
         VERDE:  58-62
         AMARILLO:63-67
         AZUL:   68-72 */
    private val baseMeta: Map<ColorJugador, Int> = mapOf(
        ColorJugador.ROJO to 52, //52 + 6 = 58
        ColorJugador.VERDE to 58, //58 + 6 = 64
        ColorJugador.AMARILLO to 64, //64 + 6 = 70
        ColorJugador.AZUL to 70  //70 + 6 = 76
    )

    //Inicia una nueva partida con los colores seleccionados
    fun iniciarJuego(coloresJugadores: List<ColorJugador>)
    {
        jugadores = coloresJugadores.map{ color -> Jugador(color) }
        indiceTurnoActual = 0
        seisesConsecutivos = 0
        estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
    }

    //Lanza el dado y decide si se espera movimiento o se pasa turno
    fun lanzarDado(): Int
    {
        if(estadoJuego != EstadoJuego.ESPERANDO_LANZAMIENTO)
            return -1

        val jugador = jugadorActual
        var resultado: Int

        //--- LÓGICA DE SUERTE ---
        //Cuando un jugador tiene todas sus fichas en la base y lleva 3 o más turnos sin sacar un 6,
        //se le da un boost de suerte para poder sacar el 6
        if(jugador.tirosSinSeis >= 3 && jugador.fichas.all { it.estado == EstadoFicha.EN_BASE })
        {
            val tiroConSuerte = Random.nextInt(1, 7)
            resultado = if(tiroConSuerte <= 3) 6 else tiroConSuerte
        }
        else
            resultado = Random.nextInt(1, 7)

        //--- SEISES CONSECUTIVOS ---
        if(resultado == 6)
        {
            seisesConsecutivos++
            jugador.tirosSinSeis = 0

            if(seisesConsecutivos == 3)
            {
                //3er seis es turno perdido
                seisesConsecutivos = 0
                turnosExtraPorKill = 0
                pasarTurno()
                return resultado
            }
        }
        else{
            seisesConsecutivos = 0
            jugador.tirosSinSeis++
        }

        //Comprobamos si hay al menos un movimiento posible
        val movimientosPosibles = obtenerMovimientosPosibles(resultado)

        if(movimientosPosibles.isEmpty())
        {
            if(resultado != 6)
                //No puede mover y no sacó 6 entonces se pasa el turno
                pasarTurno()
        }
        else
            //Hay algo que mover
            estadoJuego = EstadoJuego.ESPERANDO_MOVIMIENTO

        return resultado
    }

    //Devuelve las fichas del jugador actual que se pueden mover dado el resultado
    fun obtenerMovimientosPosibles(resultadoDado: Int): List<Ficha>
    {
        val fichasMovibles = mutableListOf<Ficha>()

        for(ficha in jugadorActual.fichas)
        {
            when(ficha.estado)
            {
                EstadoFicha.EN_BASE ->
                {
                    //Solo puede salir con 6
                    if(resultadoDado == 6)
                        fichasMovibles.add(ficha)
                }

                EstadoFicha.EN_JUEGO ->
                {
                    val entradaMeta = posicionEntradaMeta[ficha.color]!!
                    val nuevaPosicionTemp = ficha.posicionGlobal + resultadoDado

                    //¿La ficha cruza su entrada o parte desde ella hacia la meta?
                    val esSuPropiaMeta = esZonaDeMetaDeSuColor(ficha.color, ficha.posicionGlobal)

                    val cruzaEntrada = esSuPropiaMeta && ficha.posicionGlobal < entradaMeta &&
                            nuevaPosicionTemp > entradaMeta

                    val saleDesdeEntrada = esSuPropiaMeta && ficha.posicionGlobal == entradaMeta

                    if(cruzaEntrada || saleDesdeEntrada)
                    {
                        val pasosEnMeta = nuevaPosicionTemp - entradaMeta

                        if(pasosEnMeta in 1..LONGITUD_META)
                            fichasMovibles.add(ficha)
                        //Si se pasa (>5), no se puede mover
                    }
                    else
                        //No entra a meta
                        fichasMovibles.add(ficha)
                }

                EstadoFicha.EN_META ->
                {
                    val baseMetaColor = baseMeta[ficha.color]!!
                    val finMeta = baseMetaColor + LONGITUD_META
                    val nuevaPosicionTemp = ficha.posicionGlobal + resultadoDado

                    if(nuevaPosicionTemp <= finMeta)
                        fichasMovibles.add(ficha)
                }
            }
        }

        return fichasMovibles
    }

    //Aplica el movimiento definitivo de una ficha (ya validado)
    //Actualiza estado/posición, resuelve kills y gestiona el turno
    fun moverFicha(ficha: Ficha, resultadoDado: Int)
    {
        if(estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO)
            return

        var seHizoKill = false

        Log.d(TAG, "--- GestorJuego.moverFicha ---")
        Log.i(TAG, "Moviendo Ficha: ${ficha.color} ID ${ficha.id}, Estado ${ficha.estado}, PosActual ${ficha.posicionGlobal}")
        Log.d(TAG, "Dado: $resultadoDado")

        when(ficha.estado)
        {
            EstadoFicha.EN_BASE ->
            {
                if(resultadoDado == 6)
                {
                    val casillaSalida = posicionSalida[jugadorActual.color]!!
                    ficha.estado = EstadoFicha.EN_JUEGO
                    ficha.posicionGlobal = casillaSalida
                    seHizoKill = resolverCasilla(casillaSalida, jugadorActual.color)
                }
            }

            EstadoFicha.EN_JUEGO ->
            {
                moverFichaEnJuego(ficha, resultadoDado).also{
                    seHizoKill = it
                }
            }

            EstadoFicha.EN_META ->{
                moverDentroDeMeta(ficha, resultadoDado)
            }
        }

        Log.i(TAG, "ESTADO FINAL FICHA: Estado ${ficha.estado}, PosGlobal ${ficha.posicionGlobal}")

        //--- Comprobación de victoria ---
        if(comprobarVictoria(jugadorActual))
        {
            estadoJuego = EstadoJuego.JUEGO_TERMINADO
            return
        }

        //--- Gestión del turno ---

        if(seHizoKill)
        {
            //Si hubo kill, se reinician los seises y se guarda 1 turno extra
            seisesConsecutivos = 0
            turnosExtraPorKill++
            Log.d(TAG, "¡Kill! Se añade 1 turno extra. Total: $turnosExtraPorKill")
        }

        if(resultadoDado == 6)
        {
            //Si sacó 6, siempre tiene otro turno (no se pasa el turno)
            estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
            Log.d(TAG, "¡Seis! Tiene otro turno.")

        }
        else if(turnosExtraPorKill > 0)
        {
            //Si no sacó 6, pero tiene un turno extra por un kill anterior,
            //se consume ese turno y se le da otra oportunidad.
            turnosExtraPorKill-- //Se consume el turno extra
            seisesConsecutivos = 0 //Un turno por kill "limpia" los seises
            estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
            //Log.d(TAG, "Turno extra por kill consumido. Quedan: $turnosExtraPorKill")

        }
        else
            //Si no sacó 6 y no hay turnos extra, se pasa el turno.
            pasarTurno()
    }

    //--- LÓGICA DE MOVIMIENTO ---
    //Devuelve true si la ficha terminó matando a alguien
    private fun moverFichaEnJuego(ficha: Ficha, resultadoDado: Int): Boolean
    {
        val entradaMeta = posicionEntradaMeta[ficha.color]!!
        val baseMetaColor = baseMeta[ficha.color]!!

        val nuevaPosicionTemp = ficha.posicionGlobal + resultadoDado
        val esSuPropiaMeta = esZonaDeMetaDeSuColor(ficha.color, ficha.posicionGlobal)

        Log.d(TAG, "Calculando... PosActual: ${ficha.posicionGlobal}, NuevaPos (calc): " +
                "$nuevaPosicionTemp, EntradaMeta: $entradaMeta")
        Log.d(TAG, "¿Es su propia zona de meta? -> $esSuPropiaMeta")

        val cruzaEntrada = esSuPropiaMeta && ficha.posicionGlobal < entradaMeta &&
                nuevaPosicionTemp > entradaMeta

        val saleDesdeEntrada = esSuPropiaMeta && ficha.posicionGlobal == entradaMeta

        Log.d(TAG, "Evaluando if(¿Entra a meta?): cruza=$cruzaEntrada," +
                "desdeEntrada=$saleDesdeEntrada")

        if(cruzaEntrada || saleDesdeEntrada)
        {
            val pasosEnMeta = nuevaPosicionTemp - entradaMeta

            Log.d(TAG, "Entrando a camino de META. PasosEnMeta=$pasosEnMeta," +
                    "baseMetaColor=$baseMetaColor")

            if(pasosEnMeta in 1..LONGITUD_META)
            {
                val nuevaPosicion = baseMetaColor + pasosEnMeta
                ficha.estado = EstadoFicha.EN_META
                ficha.posicionGlobal = nuevaPosicion
            }
            //Si pasosEnMeta > 5, se considera movimiento no válido
            //(ya estaba filtrado en obtenerMovimientosPosibles)
            return false
        }
        else
        {
            //Seguir en el camino principal
            var nuevaPosicion = ficha.posicionGlobal + resultadoDado

            if(nuevaPosicion > casillasTotalesTablero)
            {
                Log.d(TAG, "Wrap-around: $nuevaPosicion > $casillasTotalesTablero")
                nuevaPosicion %= casillasTotalesTablero
                if(nuevaPosicion == 0) nuevaPosicion = casillasTotalesTablero
            }

            ficha.posicionGlobal = nuevaPosicion
            return resolverCasilla(nuevaPosicion, jugadorActual.color)
        }
    }

    private fun moverDentroDeMeta(ficha: Ficha, resultadoDado: Int)
    {
        val baseMetaColor = baseMeta[ficha.color]!!
        val finMeta = baseMetaColor + LONGITUD_META

        if(ficha.posicionGlobal < finMeta)
        {
            val nuevaPosicion = ficha.posicionGlobal + resultadoDado

            if(nuevaPosicion <= finMeta)
                ficha.posicionGlobal = nuevaPosicion
        }
    }

    //Determina si una posición del camino principal está en el "sector" del color (para habilitar
    //entrada a meta)
    private fun esZonaDeMetaDeSuColor(color: ColorJugador, pos: Int): Boolean
    {
        //Asigna el pasillo correcto a cada color
        return when(color)
        {
            //Pasillo Rojo (1-12) lleva a Salida Verde (14)
            ColorJugador.VERDE -> pos in 1..12

            //Pasillo Verde (14-25) lleva a Salida Amarilla (27)
            ColorJugador.AMARILLO -> pos in 14..25

            //Pasillo Amarillo (27-38) lleva a Salida Azul (40)
            ColorJugador.AZUL -> pos in 27..38

            //Pasillo Azul (40-51) lleva a Salida Roja (1)
            ColorJugador.ROJO -> pos in 40..51

            //Fallback por si acaso
            else -> false
        }
    }

    //--- UTILIDADES DE TABLERO / TURNOS ---
    private fun obtenerFichasEnCasilla(posicion: Int): List<Ficha>
    {
        val fichasEnPila = mutableListOf<Ficha>()

        for(jugador in jugadores)
        {
            fichasEnPila.addAll(
                jugador.fichas.filter{
                    it.posicionGlobal == posicion && it.estado == EstadoFicha.EN_JUEGO
                }
            )
        }
        return fichasEnPila
    }

    private fun pasarTurno()
    {
        seisesConsecutivos = 0
        indiceTurnoActual = (indiceTurnoActual + 1) % jugadores.size
        estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
    }

    //Revisa la casilla después de que una ficha aterriza.
    //Devuelve true si hubo kill
    private fun resolverCasilla(posicion: Int, colorJugadorActual: ColorJugador): Boolean
    {
        //No hay kills en casillas seguras
        if(casillasSeguras.contains(posicion))
            return false

        val fichasEnPila = obtenerFichasEnCasilla(posicion)
        val fichasPropias = fichasEnPila.filter { it.color == colorJugadorActual }
        val fichasOponentes = fichasEnPila.filter { it.color != colorJugadorActual }

        val hayKill1v1 = (fichasPropias.size == 1 && fichasOponentes.size == 1)
        val hayKillSuperioridad = (fichasPropias.size >= 2 && fichasOponentes.isNotEmpty())

        if(hayKill1v1 || hayKillSuperioridad)
        {
            for(oponente in fichasOponentes)
            {
                oponente.estado = EstadoFicha.EN_BASE
                oponente.posicionGlobal = 0
            }
            return true
        }

        return false
    }

    //Comprueba si el jugador ha llevado sus 4 fichas al final de la meta
    private fun comprobarVictoria(jugador: Jugador): Boolean
    {
        val baseMetaColor = baseMeta[jugador.color]!!
        val finMeta = baseMetaColor + LONGITUD_META

        val fichasEnMetaFinal = jugador.fichas.count{ ficha ->
            ficha.posicionGlobal == finMeta && ficha.estado == EstadoFicha.EN_META
        }

        return fichasEnMetaFinal == 4
    }
}