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
    var turnosExtraPorKill: Int = 0
        private set
    
    //Para saber si el último movimiento causó una kill
    var huboKill: Boolean = false
        private set
    
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
    
    //Una casilla antes de la meta de cada jugador
    private val posicionEntradaMeta: Map<ColorJugador, Int> = mapOf(
        ColorJugador.ROJO to 51,
        ColorJugador.VERDE to 12,
        ColorJugador.AMARILLO to 25,
        ColorJugador.AZUL to 38
    )
    
    //Base de las metas. O sea, la primera casilla en donde el jugador ya entró a su camino
    //de su color a la meta
    private val baseMeta: Map<ColorJugador, Int> = mapOf(
        ColorJugador.ROJO to 52, //52 + 6 = 58
        ColorJugador.VERDE to 58, //58 + 6 = 64
        ColorJugador.AMARILLO to 64, //64 + 6 = 70
        ColorJugador.AZUL to 70  //70 + 6 = 76
    )
    
    private var ultimoValorDado: Int = 0
    
    //Función para activar el powerup (se llama desde la UI tras ganar la trivia)
    fun activarPowerUp(color: ColorJugador, tipo: TipoPowerUp): Boolean
    {
        val jugador = jugadores.find { it.color == color } ?: return false
        
        if(jugador.usosPowerUpRestantes > 0)
        {
            jugador.powerUpActivo = tipo
            jugador.usosPowerUpRestantes--
            Log.i(TAG, "PowerUp ${tipo.name} activado para ${jugador.color}")
            return true
        }
        return false
    }
    
    //Lanza el dado y decide si se espera movimiento o se pasa turno
    fun lanzarDado(valorForzado: Int? = null): Int
    {
        if(estadoJuego != EstadoJuego.ESPERANDO_LANZAMIENTO)
            return -1
        
        val jugador = jugadorActual
        var resultado: Int
        
        //--- LÓGICA DE VALOR FORZADO (ONLINE) ---
        if(valorForzado != null)
        {
            resultado = valorForzado
            Log.d(TAG, "Dado forzado por red: $resultado")
            
            //Aunque el valor venga de la red, se debe limpiar el estado local para que el letrero
            //desaparezca en la pantalla del rival
            if(jugador.powerUpActivo != TipoPowerUp.NINGUNO)
            {
                jugador.powerUpActivo = TipoPowerUp.NINGUNO
                Log.d(TAG, "PowerUp remoto consumido")
            }
        }
        else if(jugador.powerUpActivo != TipoPowerUp.NINGUNO)
        {
            //--- LÓGICA DE POWER-UPS (LOCAL) ---
            resultado = when(jugador.powerUpActivo)
            {
                TipoPowerUp.DADO_SOLO_PARES -> listOf(2, 4, 6).random()
                TipoPowerUp.DADO_SOLO_IMPARES -> listOf(1, 3, 5).random()
                TipoPowerUp.DADO_ALTOS -> Random.nextInt(4, 7)
                TipoPowerUp.DADO_BAJOS -> Random.nextInt(1, 4)
                TipoPowerUp.SALIDA_MAESTRA -> 6
                else -> Random.nextInt(1, 7)
            }
            
            Log.d(TAG, "PowerUp usado: ${jugador.powerUpActivo} -> Resultado: $resultado")
            jugador.powerUpActivo = TipoPowerUp.NINGUNO //Se consume aquí
        }
        else
        {
            //--- LÓGICA NORMAL/SUERTE ---
            val fichasActivas = jugador.fichas.filter { it.estado != EstadoFicha.EN_META }
            
            if(jugador.tirosSinSeis >= 3 && fichasActivas.isNotEmpty() &&
                    fichasActivas.all { it.estado == EstadoFicha.EN_BASE })
            {
                val tiroConSuerte = Random.nextInt(1, 7)
                resultado = if(tiroConSuerte <= 3) 6 else tiroConSuerte
                Log.d(TAG, "Boost de suerte aplicado.")
            }
            else
                resultado = Random.nextInt(1, 7)
        }
        
        ultimoValorDado = resultado
        
        //--- SEISES CONSECUTIVOS ---
        if(resultado == 6)
        {
            seisesConsecutivos++
            jugador.tirosSinSeis = 0
            
            if(seisesConsecutivos == 3)
            {
                seisesConsecutivos = 0
                turnosExtraPorKill = 0
                pasarTurno()
                
                return resultado
            }
        }
        else
        {
            seisesConsecutivos = 0
            jugador.tirosSinSeis++
        }
        
        val movimientosPosibles = obtenerMovimientosPosibles(resultado)
        
        if(movimientosPosibles.isEmpty())
        {
            if(resultado != 6)
                pasarTurno()
        }
        else
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
                    
                    //La ficha cruza su entrada o parte desde ella hacia la meta?
                    val esSuPropiaMeta = esZonaDeMetaDeSuColor(ficha.color,
                        ficha.posicionGlobal)
                    
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
    
    //Actualiza el estado/posición, resuelve kills y gestiona el turno
    fun moverFicha(ficha: Ficha, resultadoDado: Int)
    {
        if(estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO)
            return
        
        //Reiniciar la bandera al inicio del movimiento
        huboKill = false
        
        var seHizoKillLocal = false //Variable local temporal
        
        //Guardar el estado previo
        val estabaEnBase = (ficha.estado == EstadoFicha.EN_BASE)
        
        Log.d(TAG, "--- GestorJuego.moverFicha ---")
        Log.i(
            TAG, "Moviendo Ficha: ${ficha.color} ID ${ficha.id}, " +
                    "Estado ${ficha.estado}, PosActual ${ficha.posicionGlobal}"
        )
        Log.d(TAG, "Dado: $resultadoDado")
        
        //Ejecutar el movimiento según el estado
        when(ficha.estado)
        {
            EstadoFicha.EN_BASE ->
            {
                if(resultadoDado == 6)
                {
                    val casillaSalida = posicionSalida[jugadorActual.color]!!
                    ficha.estado = EstadoFicha.EN_JUEGO
                    ficha.posicionGlobal = casillaSalida
                    seHizoKillLocal = resolverCasilla(casillaSalida,
                        jugadorActual.color)
                }
            }
            
            EstadoFicha.EN_JUEGO ->
            {
                moverFichaEnJuego(ficha, resultadoDado).also{
                    seHizoKillLocal = it
                }
            }
            
            EstadoFicha.EN_META ->{
                moverDentroDeMeta(ficha, resultadoDado)
            }
        }
        
        Log.i(
            TAG, "ESTADO FINAL FICHA: Estado ${ficha.estado}, " +
                    "PosGlobal ${ficha.posicionGlobal}"
        )
        
        //Guardar el resultado en la variable pública para sonidos/animación
        huboKill = seHizoKillLocal
        
        //--- Comprobación de victoria ---
        if(comprobarVictoria(jugadorActual))
        {
            estadoJuego = EstadoJuego.JUEGO_TERMINADO
            return
        }
        
        //--- Cálculo de turnos extra por meta o kill ---
        val llegoAMetaFinal = ficha.estado == EstadoFicha.EN_META &&
                ficha.posicionGlobal == baseMeta[ficha.color]!! + LONGITUD_META
        
        if(huboKill)
        {
            turnosExtraPorKill++
            seisesConsecutivos = 0
            Log.d(TAG, "Kill realizado. +1 turno.")
        }
        else if(llegoAMetaFinal)
        {
            turnosExtraPorKill++
            seisesConsecutivos = 0
            Log.d(TAG, "Meta alcanzada. +1 turno.")
        }
        
        //--- GESTIÓN DEL SIGUIENTE TURNO ---
        
        //Prioridad 1: Casilla Segura
        if(casillasSeguras.contains(ficha.posicionGlobal) &&
                ficha.estado == EstadoFicha.EN_JUEGO && !estabaEnBase)
        {
            Log.i(TAG, "Ficha cayó en casilla segura. Ofreciendo bonificación...")
            
            //Si llega aquí con un 6, ese turno extra se debe guardar para después
            if(resultadoDado == 6)
            {
                turnosExtraPorKill++ //Sumarlo a la "cola" de turnos
                seisesConsecutivos = 0 //Reiniciar contador de castigo porque es zona segura
                Log.d(TAG, "Cayó en segura con 6. Se guarda el turno extra del 6 en" +
                        " la cola.")
            }
            
            estadoJuego = EstadoJuego.ESPERANDO_DECISION_BONIFICACION
        }
        //Prioridad 2: Si sacó 6 (y no cayó en casilla segura)
        else if(resultadoDado == 6)
        {
            estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
            Log.d(TAG, "¡Seis! Tiene otro turno por regla base.")
        }
        //Prioridad 3: Turnos extra acumulados (Cola)
        else if(turnosExtraPorKill > 0)
        {
            turnosExtraPorKill--
            seisesConsecutivos = 0
            estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
            Log.d(TAG, "Usando turno extra acumulado. Quedan: $turnosExtraPorKill")
        }
        //Prioridad 4: Power-Up de Doble Turno
        else if(jugadorActual.powerUpActivo == TipoPowerUp.DOBLE_TURNO_ASEGURADO)
        {
            Log.i(TAG, "PowerUp DOBLE_TURNO_ASEGURADO activado. El jugador tira de nuevo.")
            jugadorActual.powerUpActivo = TipoPowerUp.NINGUNO //Consumir el efecto
            seisesConsecutivos = 0
            estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO //Darle otro tiro
        }
        //Defecto: Se pasa el turno
        else
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
        
        Log.d(
            TAG, "Calculando... PosActual: ${ficha.posicionGlobal}, NuevaPos (calc): " +
                    "$nuevaPosicionTemp, EntradaMeta: $entradaMeta"
        )
        Log.d(TAG, "¿Es su propia zona de meta? -> $esSuPropiaMeta")
        
        val cruzaEntrada = esSuPropiaMeta && ficha.posicionGlobal < entradaMeta &&
                nuevaPosicionTemp > entradaMeta
        
        val saleDesdeEntrada = esSuPropiaMeta && ficha.posicionGlobal == entradaMeta
        
        Log.d(
            TAG, "Evaluando if(¿Entra a meta?): cruza=$cruzaEntrada," +
                    "desdeEntrada=$saleDesdeEntrada"
        )
        
        if(cruzaEntrada || saleDesdeEntrada)
        {
            val pasosEnMeta = nuevaPosicionTemp - entradaMeta
            
            Log.d(
                TAG, "Entrando a camino de META. PasosEnMeta=$pasosEnMeta," +
                        "baseMetaColor=$baseMetaColor"
            )
            
            if(pasosEnMeta in 1..LONGITUD_META)
            {
                val nuevaPosicion = baseMetaColor + pasosEnMeta
                ficha.estado = EstadoFicha.EN_META
                ficha.posicionGlobal = nuevaPosicion
            }
            //Si pasosEnMeta > 5, se considera movimiento no válido
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
    
    fun esCasillaSegura(posicion: Int): Boolean{
        return casillasSeguras.contains(posicion)
    }
    
    //Determina si una posición del camino principal está en el "sector" del color (para habilitar
    //entrada a meta). Sin esto la ficha recorrería infinitamente el tablero y no podría entrar
    //a su meta
    private fun esZonaDeMetaDeSuColor(color: ColorJugador, pos: Int): Boolean
    {
        //Asignar el pasillo correcto a cada color
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
            
            //Fallback por si acaso (no vaya a ser)
            else -> false
        }
    }
    
    //Solo elige la mejor ficha para mover, pero no realiza el movimiento
    fun seleccionarFichaIA(resultadoDado: Int): Ficha?
    {
        if(estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO)
            return null
        
        val movimientosPosibles = obtenerMovimientosPosibles(resultadoDado)
        
        if(movimientosPosibles.isEmpty())
            return null
        
        //--- PRIORIDAD 0: SACAR DE BASE ---
        //Si saca 6, tiene fichas en base, y tiene pocas fichas jugando (< 2),
        //es obligatorio sacar una ficha de la base para no quedarse sin opciones
        val fichasEnBase = movimientosPosibles.filter { it.estado == EstadoFicha.EN_BASE }
        
        //Contar cuántas fichas tiene este jugador activas en el tablero
        val fichasActivas = jugadorActual.fichas.count { it.estado == EstadoFicha.EN_JUEGO }
        
        if(fichasEnBase.isNotEmpty() && fichasActivas < 2)
        {
            val fichaElegida = fichasEnBase.first()
            Log.i(
                TAG,
                "IA Elige (P0 Salir de Base): ${fichaElegida.color} ID ${fichaElegida.id}"
            )
            return fichaElegida
        }
        
        //Prioridad 1: Fichas en juego que no están en casillas seguras (para poder huir)
        val fichasNoSeguras = movimientosPosibles.filter{
            it.estado == EstadoFicha.EN_JUEGO && it.posicionGlobal !in casillasSeguras
        }
        
        if(fichasNoSeguras.isNotEmpty())
        {
            val fichaElegida = fichasNoSeguras.random()
            Log.i(TAG, "IA Elige (P1 No Segura): ${fichaElegida.color} ID ${fichaElegida.id}")
            return fichaElegida
        }
        
        //Prioridad 2: Fichas en meta (para avanzar hacia la última casilla)
        val fichasMetaOBaseRestantes = movimientosPosibles.filter {
            it.estado == EstadoFicha.EN_META || it.estado == EstadoFicha.EN_BASE
        }
        
        if(fichasMetaOBaseRestantes.isNotEmpty())
        {
            val fichaElegida = fichasMetaOBaseRestantes.random()
            Log.i(TAG, "IA Elige (P2 Meta/Base Extra): ${fichaElegida.color} ID " +
                    "${fichaElegida.id}")
            return fichaElegida
        }
        
        //Prioridad 3: Fichas en juego pero ya seguras (última opción)
        if(movimientosPosibles.isNotEmpty())
        {
            val fichaElegida = movimientosPosibles.random()
            Log.i(TAG, "IA Elige (P3 Segura): ${fichaElegida.color} ID ${fichaElegida.id}")
            return fichaElegida
        }
        
        return null
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
    
    fun pasarTurno()
    {
        seisesConsecutivos = 0
        indiceTurnoActual = (indiceTurnoActual + 1) % jugadores.size
        estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
        
        //--- LÓGICA DE LIMPIEZA DE POWER-UPS ---
        //Al iniciar el turno, verificar si el jugador tenía un escudo activo
        //Si sí, quitarlo porque el efecto dura hasta su próximo turno
        val nuevoJugador = jugadores[indiceTurnoActual]
        
        if(nuevoJugador.powerUpActivo == TipoPowerUp.ESCUDO_TEMPORAL)
        {
            nuevoJugador.powerUpActivo = TipoPowerUp.NINGUNO
            Log.i(
                TAG, "El escudo temporal de ${nuevoJugador.color} se ha " +
                        "desactivado al iniciar su turno."
            )
        }
    }
    
    //Revisar la casilla después de que una ficha aterriza.
    //Devuelve true si hubo kill
    private fun resolverCasilla(posicion: Int, colorJugadorActual: ColorJugador): Boolean
    {
        //No hay kills en casillas seguras
        if(casillasSeguras.contains(posicion))
            return false
        
        val fichasEnPila = obtenerFichasEnCasilla(posicion)
        val fichasPropias = fichasEnPila.filter{ it.color == colorJugadorActual }
        val fichasOponentes = fichasEnPila.filter{
            if(it.color == colorJugadorActual)
                return@filter false
            
            //Buscar al dueño de esa ficha enemiga
            val duenoFicha = jugadores.find { j -> j.color == it.color }
            
            //Si tiene escudo activado, no incluirlo como "comible"
            val esInmune = duenoFicha?.powerUpActivo == TipoPowerUp.ESCUDO_TEMPORAL
            
            if(esInmune)
                Log.d(TAG, "Jugador ${it.color} salvado por ESCUDO_TEMPORAL")
            
            !esInmune //Solo devolver true si no es inmune
        }
        
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
    
    //Se llama cuando el usuario decide sobre la casilla segura
    //aceptaReto es true si aceptó y ganó la trivia. false si rechazó o perdió.
    fun resolverBonificacionCasillaSegura(aceptoYgano: Boolean)
    {
        if(estadoJuego != EstadoJuego.ESPERANDO_DECISION_BONIFICACION) return
        
        if(aceptoYgano)
        {
            Log.i(TAG, "Reto superado. Turno extra concedido.")
            //Si ganó trivia, juega este turno extra.
            //Si tenía un 6 guardado en turnosExtraPorKill, se usar después de este tiro
            seisesConsecutivos = 0
            estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
        }
        else
        {
            Log.i(TAG, "Reto rechazado o fallido.")
            
            //Si falló, verificar si tenía turno guardado (el del 6 o un kill previo)
            if(turnosExtraPorKill > 0)
            {
                Log.d(TAG, "Usando turno guardado (ej. por sacar 6).")
                turnosExtraPorKill--
                seisesConsecutivos = 0
                estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
            }
            else
                pasarTurno()
        }
    }
    
    //Devuelve una lista con los IDs de las casillas que pisará la ficha paso a paso
    fun calcularCamino(ficha: Ficha, pasos: Int): List<Int>
    {
        val camino = mutableListOf<Int>()
        var posActualSimulada = ficha.posicionGlobal
        var estadoSimulado = ficha.estado
        
        //Simular paso a paso
        for(i in 1..pasos)
        {
            if(estadoSimulado == EstadoFicha.EN_BASE)
            {
                //Si está en base y sale 6, salta directo a la salida
                val salida = posicionSalida[ficha.color]!!
                camino.add(salida)
                posActualSimulada = salida
                estadoSimulado = EstadoFicha.EN_JUEGO
                break
            }
            else if(estadoSimulado == EstadoFicha.EN_META)
            {
                //Dentro de meta
                val baseMetaColor = baseMeta[ficha.color]!!
                val finMeta = baseMetaColor + LONGITUD_META
                if(posActualSimulada < finMeta)
                {
                    posActualSimulada++
                    camino.add(posActualSimulada)
                }
            }
            else
            {
                //En Juego (camino principal)
                val entradaMeta = posicionEntradaMeta[ficha.color]!!
                
                //Está justo en la entrada de su meta?
                if(posActualSimulada == entradaMeta && esZonaDeMetaDeSuColor(
                            ficha.color,
                            posActualSimulada))
                {
                    //Entra a la meta
                    val baseMetaColor = baseMeta[ficha.color]!!
                    posActualSimulada = baseMetaColor + 1 //Primera casilla de meta
                    estadoSimulado = EstadoFicha.EN_META
                    camino.add(posActualSimulada)
                }
                else
                {
                    //Avanza normal
                    posActualSimulada++
                    if(posActualSimulada > casillasTotalesTablero)
                    {
                        posActualSimulada = 1 //Vuelta al tablero
                    }
                    camino.add(posActualSimulada)
                }
            }
        }
        return camino
    }
    
    fun iniciarJuegoConJugadores(listaJugadoresPreconfigurada: List<Jugador>, turnoInicial: Int = 0)
    {
        jugadores = listaJugadoresPreconfigurada
        
        //Validar que el índice exista (de 0 a size-1)
        if(turnoInicial in jugadores.indices)
            indiceTurnoActual = turnoInicial
        else
            indiceTurnoActual = 0
        
        seisesConsecutivos = 0
        estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
    }
    
    //Devuelve true solo si la ficha está en la última casilla de la meta
    fun esPosicionFinalDeMeta(ficha: Ficha): Boolean
    {
        if(ficha.estado != EstadoFicha.EN_META) return false
        
        val base = baseMeta[ficha.color]!!
        val finMeta = base + LONGITUD_META
        
        return ficha.posicionGlobal == finMeta
    }
}