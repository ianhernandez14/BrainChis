package com.proyectofinal.brainchis

import kotlin.random.Random

class GestorJuego
{
    //--- PROPIEDADES DEL JUEGO ---
    private var jugadores: List<Jugador> = emptyList()
    private var indiceTurnoActual: Int = 0
    private var seisesConsecutivos: Int = 0
    var estadoJuego: EstadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
    val jugadorActual: Jugador
        get() = jugadores[indiceTurnoActual]

    //--- LÓGICA DEL TABLERO ---
    private val casillasTotalesTablero = 52

    //Estas son las casillas "seguras" (las estrellas)
    private val casillasSeguras: Set<Int> = setOf(
        1, 14, 27, 40, //Salidas de cada base (o sea, la primera casilla a la que sale cada jugador)
        9, 22, 35, 48  //Nuevas (5 antes de la las anteriores)
    )

    //Mapeo de la casilla de "salida" (la primera casilla de color)
    private val posicionSalida: Map<ColorJugador, Int> = mapOf(
        ColorJugador.ROJO to 1,
        ColorJugador.VERDE to 14,
        ColorJugador.AMARILLO to 27,
        ColorJugador.AZUL to 40
    )

    //Casilla JUSTO ANTES de entrar a la meta para cada color
    private val posicionEntradaMeta: Map<ColorJugador, Int> = mapOf(
        ColorJugador.ROJO to 52,
        ColorJugador.VERDE to 13,
        ColorJugador.AMARILLO to 26,
        ColorJugador.AZUL to 39
    )

    //Posición base para el inicio de la meta (usado para calcular 53-57, etc.)
    //Los caminos de meta tienen 5 casillas
    private val baseMeta: Map<ColorJugador, Int> = mapOf(
        ColorJugador.ROJO to 52, //52 + 1 = 53 (hasta 57)
        ColorJugador.VERDE to 57, //57 + 1 = 58 (hasta 62)
        ColorJugador.AMARILLO to 62, //62 + 1 = 63 (hasta 67)
        ColorJugador.AZUL to 67  //67 + 1 = 68 (hasta 72)
    )

    //--- FUNCIONES PÚBLICAS (ACCIONES DEL JUEGO) ---

    //Configura una nueva partida con los jugadores seleccionados
    fun iniciarJuego(coloresJugadores: List<ColorJugador>)
    {
        //Crea un objeto Jugador por cada color
        jugadores = coloresJugadores.map { color -> Jugador(color) }
        indiceTurnoActual = 0 //Empieza el primer jugador
        estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
    }

    //Simula el lanzamiento de un dado
    fun lanzarDado(): Int
    {
        if(estadoJuego != EstadoJuego.ESPERANDO_LANZAMIENTO)
            return -1

        val resultado = Random.nextInt(1, 7) //Números del 1 al 6

        //--- LÓGICA DE SEISES CONSECUTIVOS ---
        if(resultado == 6)
        {
            seisesConsecutivos++

            if(seisesConsecutivos == 3)
            {
                //PENALIZACIÓN: Se anula el turno con 3 seises
                seisesConsecutivos = 0

                //Se anula el turno y se pasa al siguiente jugador
                pasarTurno()
                return resultado //Devuelve el 6 para que la UI lo muestre
            }
        }
        else{
            //Si no es 6, se reinicia el contador
            seisesConsecutivos = 0
        }

        //Comprobar si hay movimientos
        val movimientosPosibles = obtenerMovimientosPosibles(resultado)

        if(movimientosPosibles.isEmpty())
        {
            //Si no hay movimientos Y NO sacó 6, pasa el turno
            if(resultado != 6)
                pasarTurno()

            //Si sacó 6 (y no fue el 3ro) y no hay movimientos, se queda en ESPERANDO_LANZAMIENTO
            //para que tire otra vez
        }
        else{
            //Si hay movimientos, se espera la selección de la ficha
            estadoJuego = EstadoJuego.ESPERANDO_MOVIMIENTO
        }

        return resultado
    }

    //Devuelve una lista de fichas que el jugador PUEDE mover según el resultado del dado
    fun obtenerMovimientosPosibles(resultadoDado: Int): List<Ficha>
    {
        val fichasMovibles = mutableListOf<Ficha>()

        for(ficha in jugadorActual.fichas)
        {
            when(ficha.estado)
            {
                EstadoFicha.EN_BASE ->
                {
                    //Si la ficha está EN_BASE:
                    //Solo se puede mover si el dado es 5
                    if(resultadoDado == 6)
                        fichasMovibles.add(ficha)
                }

                EstadoFicha.EN_JUEGO ->
                {
                    //Si la ficha está EN_JUEGO:
                    val entradaMeta = posicionEntradaMeta[ficha.color] ?: 0
                    val nuevaPosicionTemp = ficha.posicionGlobal + resultadoDado

                    //Comprobar si la ficha intenta entrar a la meta
                    if(ficha.posicionGlobal in 1..entradaMeta && nuevaPosicionTemp > entradaMeta)
                    {

                        //Calcula cuántos pasos da en la meta
                        val pasosEnMeta = nuevaPosicionTemp - entradaMeta

                        //Es un movimiento válido si entra exacto o con menos
                        if(pasosEnMeta <= 5) //5 es el maximo de la meta
                            fichasMovibles.add(ficha)
                    }
                    else{
                        //Sigue en el tablero normal.
                        fichasMovibles.add(ficha)
                    }
                }

                EstadoFicha.EN_META ->
                {
                    //Si la ficha está EN_META:
                    val baseMetaColor = baseMeta[ficha.color] ?: 0
                    val finMeta = baseMetaColor + 5 //5 casillas de meta

                    //Calculamos si el movimiento es válido ANTES de añadirlo
                    val nuevaPosicionTemp = ficha.posicionGlobal + resultadoDado

                    //Es un movimiento válido SOLO SI la nueva posición no se pasa del final
                    if(nuevaPosicionTemp <= finMeta)
                        fichasMovibles.add(ficha)

                    //Si nuevaPosicionTemp > finMeta, no es un movimiento válido,
                    //por lo que no se añade a la lista.
                }
            }
        }

        return fichasMovibles
    }

    /*La acción final de mover una ficha
     Actualiza la posición de la ficha y luego resuelve los kills*/
    fun moverFicha(ficha: Ficha, resultadoDado: Int)
    {
        if(estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO)
            return

        var seHizoKill = false

        //--- LÓGICA PRINCIPAL DE MOVIMIENTO ---

        //SACAR DE LA BASE
        if(ficha.estado == EstadoFicha.EN_BASE)
        {
            if(resultadoDado == 6)
            {
                val casillaSalida = posicionSalida[jugadorActual.color] ?: 0

                //Mover la ficha PRIMERO
                ficha.estado = EstadoFicha.EN_JUEGO
                ficha.posicionGlobal = casillaSalida

                //Resolver la casilla DESPUÉS
                seHizoKill = resolverCasilla(casillaSalida, jugadorActual.color)
            }
        }
        //MOVER EN EL TABLERO (si ficha está EN_JUEGO)
        else if(ficha.estado == EstadoFicha.EN_JUEGO)
        {
            val entradaMeta = posicionEntradaMeta[ficha.color] ?: 0
            val baseMetaColor = baseMeta[ficha.color] ?: 0
            var nuevaPosicion = ficha.posicionGlobal + resultadoDado

            if(ficha.posicionGlobal in 1..entradaMeta && nuevaPosicion > entradaMeta)
            {
                //CASO A: LA FICHA ENTRA A LA META
                val pasosEnMeta = nuevaPosicion - entradaMeta

                if(pasosEnMeta <= 5)
                {
                    nuevaPosicion = baseMetaColor + pasosEnMeta
                    ficha.estado = EstadoFicha.EN_META
                    ficha.posicionGlobal = nuevaPosicion
                    //No hay kills en la meta
                }

            }
            else{
                //CASO B: LA FICHA SIGUE EN EL TABLERO NORMAL
                if(nuevaPosicion > casillasTotalesTablero)
                {
                    nuevaPosicion = nuevaPosicion % casillasTotalesTablero

                    if(nuevaPosicion == 0)
                        nuevaPosicion = casillasTotalesTablero
                }

                //Mover la ficha primero
                ficha.posicionGlobal = nuevaPosicion

                //Resolver la casilla después
                seHizoKill = resolverCasilla(nuevaPosicion, jugadorActual.color)
            }
        }
        //MOVER A LA META
        else if(ficha.estado == EstadoFicha.EN_META)
        {
            val baseMetaColor = baseMeta[ficha.color] ?: 0
            val finMeta = baseMetaColor + 5 //Ej: 57

            //Solo calculamos si la ficha NO está ya en el final
            if(ficha.posicionGlobal < finMeta)
            {
                val nuevaPosicion = ficha.posicionGlobal + resultadoDado

                //Solo movemos si la nueva posición es VÁLIDA (cae en o antes del final)
                if(nuevaPosicion <= finMeta)
                    ficha.posicionGlobal = nuevaPosicion

                //Si nuevaPosicion > finMeta (el dado fue muy alto),
                //no hacemos nada, la ficha no se mueve.
            }
        }

        //--- FIN DEL TURNO  ---

        if(comprobarVictoria(jugadorActual))
        {
            estadoJuego = EstadoJuego.JUEGO_TERMINADO
            return
        }

        if(seHizoKill)
        {
            seisesConsecutivos = 0
            estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
        }
        else if(resultadoDado == 6)
            estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
        else
            pasarTurno()
    }

    /*Devuelve una lista de todas las fichas (de cualquier jugador)
     que están actualmente en una posición específica*/
    private fun obtenerFichasEnCasilla(posicion: Int): List<Ficha>
    {
        val fichasEnPila = mutableListOf<Ficha>()

        for(jugador in jugadores)
        {
            fichasEnPila.addAll(jugador.fichas.filter{
                it.posicionGlobal == posicion && it.estado == EstadoFicha.EN_JUEGO
            })
        }

        return fichasEnPila
    }

    private fun pasarTurno()
    {
        seisesConsecutivos = 0
        indiceTurnoActual = (indiceTurnoActual + 1) % jugadores.size
        estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
    }

    /*Revisa el estado de una casilla DESPUÉS de que una ficha haya aterrizado en ella.
     Aplica la regla: si hay 2+ fichas propias y 1+ oponente,
     se come a todos los oponentes en esa pila.
     Retorna true si se realizó un kill, false si no */
    private fun resolverCasilla(posicion: Int, colorJugadorActual: ColorJugador): Boolean
    {
        //No se puede comer en casillas seguras
        if(casillasSeguras.contains(posicion))
            return false

        //Obtener la "pila" completa de fichas en esa casilla
        val fichasEnPila = obtenerFichasEnCasilla(posicion)

        //Separar las fichas por dueño
        val fichasPropias = fichasEnPila.filter { it.color == colorJugadorActual }
        val fichasOponentes = fichasEnPila.filter { it.color != colorJugadorActual }

        //¿Hay 2 o más mías y al menos 1 oponente?
        if(fichasPropias.size >= 2 && fichasOponentes.isNotEmpty())
        {
            //Enviar las fichas oponentes en la pila a su base
            for(oponente in fichasOponentes)
            {
                oponente.estado = EstadoFicha.EN_BASE
                oponente.posicionGlobal = 0
            }
            return true //Se hizo un kill
        }

        //Si no se cumple la condición, no hay kill y las fichas simplemente se apilan
        return false
    }

    /* Comprueba si el jugador actual ha ganado la partida
    Un jugador gana si sus 4 fichas han llegado al final de la meta.
    Retorna true si el jugador ha ganado y false si no */
    private fun comprobarVictoria(jugador: Jugador): Boolean
    {
        //Obtener la casilla final para el color de este jugador
        val baseMetaColor = baseMeta[jugador.color] ?: 0
        val finMeta = baseMetaColor + 5 //5 casillas de meta (ej: 52 + 5 = 57)

        //Contar cuántas fichas están en esa casilla final
        val fichasEnMetaFinal = jugador.fichas.count { ficha ->
            //Comprobamos que esté EN_META y en la posición final.
            //(Aunque si está en finMeta, ya debería estar EN_META)
            ficha.posicionGlobal == finMeta && ficha.estado == EstadoFicha.EN_META
        }

        //Si el conteo es 4, ha ganado
        return fichasEnMetaFinal == 4
    }
}