package com.proyectofinal.brainchis

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class JuegoActivity : AppCompatActivity(), Tablero.OnCasillaTocadaListener
{
    private lateinit var gestor: GestorJuego
    private lateinit var tableroView: Tablero
    private var ultimoResultadoDado: Int = 0
    private lateinit var dadoRojo: ImageView
    private lateinit var dadoVerde: ImageView
    private lateinit var dadoAzul: ImageView
    private lateinit var dadoAmarillo: ImageView
    private lateinit var powerupRojo: ImageView
    private lateinit var powerupVerde: ImageView
    private lateinit var powerupAzul: ImageView
    private lateinit var powerupAmarillo: ImageView
    private lateinit var textoTemporizador: TextView

    //Layout Selección PowerUp
    private lateinit var layoutSeleccionPowerUp: View
    private lateinit var txtUsosRestantes: TextView
    private lateinit var btnPowerPares: Button
    private lateinit var btnPowerImpares: Button
    private lateinit var btnPowerAltos: Button
    private lateinit var btnPowerBajos: Button
    private lateinit var btnPowerSalida: Button
    private lateinit var btnPowerEscudo: Button
    private lateinit var btnCancelarPower: Button

    //Bonificación por casilla segura
    private lateinit var layoutDecisionBonificacion: View
    private lateinit var btnAceptarReto: Button
    private lateinit var btnRechazarReto: Button

    //Sonidos
    private lateinit var gestorSonido: GestorSonido

    //Esta variable es nomás para filtrar los logs
    private val TAG = "ParchisDEBUG"

    //Temporizador
    private var temporizadorTurno: CountDownTimer? = null
    private val TIEMPO_PARA_LANZAR_MS = 6000L //6 segundos
    private val TIEMPO_PARA_MOVER_MS = 8000L  //8 segundos
    private val TAG_TIMER = "ParchisTimer"

    //Layout victoria
    private lateinit var layoutVictoria: View
    private lateinit var txtGanador: TextView
    private lateinit var btnReiniciarVic: Button
    private lateinit var btnMenuVic: Button

    //Layout trivia
    private lateinit var overlayPreguntas: ViewGroup
    private lateinit var txtPregunta: TextView
    private lateinit var btnOpcion1: Button
    private lateinit var btnOpcion2: Button
    private lateinit var btnOpcion3: Button
    private lateinit var btnOpcion4: Button
    private lateinit var btnCancelarTrivia: Button
    private lateinit var txtFeedback: TextView

    // Control de reinicio de timer por toque
    private var timerReiniciadoEnEsteTurno: Boolean = false

    // Timer específico para la Trivia
    private var temporizadorTrivia: CountDownTimer? = null
    private val TIEMPO_TRIVIA_MS = 8000L // 8 segundos

    //Texto para mostrar en pantalla qué powerup ha usado un jugador
    private lateinit var txtPowerUpActivo: TextView

    //Botón del sonido
    private lateinit var btnSonido: android.widget.ImageButton

    // Variables Multijugador
    private var esModoOnline: Boolean = false
    private var miColor: ColorJugador? = null

    // Timer para decidir si tomar la bonificación (4 segundos)
    private var temporizadorBonificacion: CountDownTimer? = null

    // Variable para guardar la decisión del rival si llega mientras la ficha hace su animación
    private var resultadoBonificacionPendiente: Boolean? = null

    // Timer de seguridad por si el rival se desconecta o el mensaje se pierde
    private var watchdogRival: CountDownTimer? = null

    private var animacionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        //Asegúrate de que este layout coincida con el nombre de tu XML del juego
        setContentView(R.layout.activity_juego)

        //Ajuste de padding para barras del sistema (EdgeToEdge)
        //Nota: Asegúrate de que el ID 'main' o 'activity_juego' exista en tu XML raíz
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)){ v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //--- 1. INICIALIZAR VISTAS Y OBJETOS ---
        gestor = GestorJuego()
        tableroView = findViewById(R.id.tablero)
        tableroView.setOnCasillaTocadaListener(this)

        dadoRojo = findViewById(R.id.dado_rojo)
        dadoVerde = findViewById(R.id.dado_verde)
        dadoAzul = findViewById(R.id.dado_azul)
        dadoAmarillo = findViewById(R.id.dado_amarillo)

        powerupRojo = findViewById(R.id.dado_powerup_rojo)
        powerupVerde = findViewById(R.id.dado_powerup_verde)
        powerupAzul = findViewById(R.id.dado_powerup_azul)
        powerupAmarillo = findViewById(R.id.dado_powerup_amarillo)

        textoTemporizador = findViewById(R.id.texto_temporizador)

        //Layout Victoria
        layoutVictoria = findViewById(R.id.layoutVictoria)
        txtGanador = findViewById(R.id.txtGanador)
        btnReiniciarVic = findViewById(R.id.btnReiniciarVic)
        btnMenuVic = findViewById(R.id.btnMenuVic)

        //Layout PowerUp
        layoutSeleccionPowerUp = findViewById(R.id.layoutSeleccionPowerUp)
        txtUsosRestantes = findViewById(R.id.txtUsosRestantes)
        btnPowerPares = findViewById(R.id.btnPowerPares)
        btnPowerImpares = findViewById(R.id.btnPowerImpares)
        btnPowerAltos = findViewById(R.id.btnPowerAltos)
        btnPowerBajos = findViewById(R.id.btnPowerBajos)
        btnPowerSalida = findViewById(R.id.btnPowerSalida)
        btnPowerEscudo = findViewById(R.id.btnPowerEscudo)
        btnCancelarPower = findViewById(R.id.btnCancelarPower)
        txtPowerUpActivo = findViewById(R.id.txtPowerUpActivo)

        //Layout Trivia
        overlayPreguntas = findViewById(R.id.overlayPreguntas)
        txtPregunta = findViewById(R.id.txtPregunta)
        btnOpcion1 = findViewById(R.id.btnOpcion1)
        btnOpcion2 = findViewById(R.id.btnOpcion2)
        btnOpcion3 = findViewById(R.id.btnOpcion3)
        btnOpcion4 = findViewById(R.id.btnOpcion4)
        btnCancelarTrivia = findViewById(R.id.btnCancelar)
        txtFeedback = findViewById(R.id.txtFeedback)

        //Layout Bonificación
        layoutDecisionBonificacion = findViewById(R.id.layoutDecisionBonificacion)
        btnAceptarReto = findViewById(R.id.btnAceptarReto)
        btnRechazarReto = findViewById(R.id.btnRechazarReto)

        //Sonido
        btnSonido = findViewById(R.id.btnSonido)
        gestorSonido = GestorSonido(this)
        actualizarIconoSonido()

        // Listener del botón
        btnSonido.setOnClickListener{
            gestorSonido.alternarSonido()
            actualizarIconoSonido()
        }

        //Configurar Listeners de los menús
        configurarBotonesBonificacion()
        configurarBotonesPowerUp()


        btnReiniciarVic.setOnClickListener{
            recreate() //Reiniciar la actividad
        }

        btnMenuVic.setOnClickListener{
            finish() //Cerrar la actividad y volver al menú anterior
        }

        //--- 2. CONFIGURAR JUGADORES (NUEVO CÓDIGO) ---

        //Leemos lo que nos envió el Menú Principal (con el intent)
        val cantidadJugadores = intent.getIntExtra("CANTIDAD_JUGADORES", 4)
        val humanoJuega = intent.getBooleanExtra("HUMANO_JUEGA", true)

        // DETECTAR ONLINE
        esModoOnline = intent.getBooleanExtra("MODO_ONLINE", false)
        val miColorStr = intent.getStringExtra("MI_COLOR")
        if (miColorStr != null) {
            miColor = ColorJugador.valueOf(miColorStr)
        }

        val listaJugadores = mutableListOf<Jugador>()

        if (esModoOnline) {
            // --- MODO ONLINE ---
            // Usamos la cantidad que nos dijo el intent (por defecto 2)

            // 1. Crear los jugadores en el orden correcto del servidor:
            // (ROJO, AMARILLO, VERDE, AZUL) <- El mismo orden que pusiste en Python
            val coloresOrdenados = listOf(ColorJugador.ROJO, ColorJugador.AMARILLO, ColorJugador.VERDE, ColorJugador.AZUL)

            for (i in 0 until cantidadJugadores) {
                val color = coloresOrdenados[i]
                // Todos son "Humanos" (false), pero solo el mío es controlable
                listaJugadores.add(Jugador(color, esIA = false, nombre = "Jugador ${color.name}"))
            }

            // Configurar Listener de Red
            ClienteRed.listener = { mensaje ->
                runOnUiThread { procesarMensajeJuego(mensaje) }
            }

            Toast.makeText(this, "Partida Online: Eres $miColor", Toast.LENGTH_LONG).show()

        } else {
            // --- MODO LOCAL (Tu código original) ---
            // ... (Pega aquí tu lógica original de if(cantidadJugadores == 2)... etc) ...
            // Ojo: Asegúrate de que tu código local original quede aquí dentro
            if (cantidadJugadores == 2) {
                val nombre1 = intent.getStringExtra("NOMBRE_JUGADOR") ?: "Jugador"
                listaJugadores.add(Jugador(ColorJugador.ROJO, esIA = !humanoJuega, nombre = nombre1))
                listaJugadores.add(Jugador(ColorJugador.AMARILLO, esIA = true, nombre = "CPU Amarillo"))
            } else {
                // ... lógica para 3 y 4 ...
                listaJugadores.add(Jugador(ColorJugador.ROJO, esIA = !humanoJuega))
                listaJugadores.add(Jugador(ColorJugador.VERDE, esIA = true))
                listaJugadores.add(Jugador(ColorJugador.AMARILLO, esIA = true))
                if(cantidadJugadores == 4) listaJugadores.add(Jugador(ColorJugador.AZUL, esIA = true))
            }
        }

        gestor.iniciarJuegoConJugadores(listaJugadores)

        // --- MODO ONLINE ---
        val esOnline = intent.getBooleanExtra("MODO_ONLINE", false)

        if (esOnline) {
            Toast.makeText(this, "Modo Online Activado", Toast.LENGTH_SHORT).show()

            // CAMBIAMOS EL LISTENER: Ahora JuegoActivity escucha
            ClienteRed.listener = { mensaje ->
                runOnUiThread {
                    procesarMensajeJuego(mensaje)
                }
            }
        }

        tableroView.actualizarEstadoJuego(gestor.jugadores)
        prepararSiguienteTurno()

        //Este bloque es solo para hacer pruebas. Tuve un bug que me tardó mucho en solucionar y
        //mejor dejé este bloque para hacerlo más rápido
//        try{
//            //ROJO: justo antes de su meta (pos 51)
//            var fichaRojaPrueba = gestor.jugadores.find { it.color == ColorJugador.ROJO }?.fichas?.get(0)
//            fichaRojaPrueba?.estado = EstadoFicha.EN_META
//            fichaRojaPrueba?.posicionGlobal = 58
//
//            fichaRojaPrueba = gestor.jugadores.find { it.color == ColorJugador.ROJO }?.fichas?.get(1)
//            fichaRojaPrueba?.estado = EstadoFicha.EN_META
//            fichaRojaPrueba?.posicionGlobal = 58
//
//            fichaRojaPrueba = gestor.jugadores.find { it.color == ColorJugador.ROJO }?.fichas?.get(2)
//            fichaRojaPrueba?.estado = EstadoFicha.EN_META
//            fichaRojaPrueba?.posicionGlobal = 58
//
//            fichaRojaPrueba = gestor.jugadores.find { it.color == ColorJugador.ROJO }?.fichas?.get(3)
//            fichaRojaPrueba?.estado = EstadoFicha.EN_META
//            fichaRojaPrueba?.posicionGlobal = 57
//
//            //VERDE: justo antes de su meta (pos 12)
//            val fichaVerdePrueba = gestor.jugadores.find { it.color == ColorJugador.VERDE }?.fichas?.get(0)
//            fichaVerdePrueba?.estado = EstadoFicha.EN_JUEGO
//            fichaVerdePrueba?.posicionGlobal = 12
//
//            //AZUL: justo antes de su meta (pos 38)
//            val fichaAzulPrueba = gestor.jugadores.find { it.color == ColorJugador.AZUL }?.fichas?.get(0)
//            fichaAzulPrueba?.estado = EstadoFicha.EN_JUEGO
//            fichaAzulPrueba?.posicionGlobal = 38
//
//            //AMARILLO: justo antes de su meta (pos 25)
//            val fichaAmarillaPrueba = gestor.jugadores.find { it.color == ColorJugador.AMARILLO }?.fichas?.get(0)
//            fichaAmarillaPrueba?.estado = EstadoFicha.EN_JUEGO
//            fichaAmarillaPrueba?.posicionGlobal = 25
//
//        }
//        catch (e: Exception){
//            e.printStackTrace()
//        }
    }

    override fun onPause()
    {
        super.onPause()
        Log.d(TAG, "Aplicación en pausa - Deteniendo temporizadores y audio")

        cancelarTemporizador()
        cancelarTemporizadorTrivia()
        gestorSonido.pausarTodo()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Aplicación reanudada - Restaurando estado")

        //1. Reactivar el audio
        gestorSonido.reanudarTodo()

        //2. Reactivar el temporizador según el estado en que nos quedamos
        //Solo si no hay ventanas emergentes abiertas (Trivia o Bonificación)
        val hayVentanaAbierta = overlayPreguntas.visibility == View.VISIBLE ||
                layoutDecisionBonificacion.visibility == View.VISIBLE ||
                layoutSeleccionPowerUp.visibility == View.VISIBLE

        if (!hayVentanaAbierta) {
            when (gestor.estadoJuego) {
                EstadoJuego.ESPERANDO_LANZAMIENTO -> {
                    //Reiniciamos el timer de lanzar
                    iniciarTemporizador(esParaLanzar = true)
                }
                EstadoJuego.ESPERANDO_MOVIMIENTO -> {
                    //Reiniciamos el timer de mover
                    iniciarTemporizador(esParaLanzar = false)
                }
                else -> {
                    //En otros estados (JUEGO_TERMINADO o ESPERANDO_DECISION) no corre el reloj
                }
            }
        }
    }

    override fun onDestroy()
    {
        super.onDestroy()
        gestorSonido.liberar() //Liberar memoria al cerrar
    }

    private fun obtenerRecursoDado(numero: Int): Int
    {
        return when(numero)
        {
            1 -> R.drawable.dado_1
            2 -> R.drawable.dado_2
            3 -> R.drawable.dado_3
            4 -> R.drawable.dado_4
            5 -> R.drawable.dado_5
            6 -> R.drawable.dado_6
            else -> R.drawable.dado_signo //Por si acaso
        }
    }

    private fun prepararSiguienteTurno()
    {
        tableroView.actualizarFichasMovibles(emptyList())

        // Ocultar dados y powerups
        dadoRojo.visibility = View.INVISIBLE
        dadoVerde.visibility = View.INVISIBLE
        dadoAzul.visibility = View.INVISIBLE
        dadoAmarillo.visibility = View.INVISIBLE

        powerupRojo.visibility = View.INVISIBLE
        powerupVerde.visibility = View.INVISIBLE
        powerupAzul.visibility = View.INVISIBLE
        powerupAmarillo.visibility = View.INVISIBLE

        dadoRojo.setOnClickListener(null)
        dadoVerde.setOnClickListener(null)
        dadoAzul.setOnClickListener(null)
        dadoAmarillo.setOnClickListener(null)

        timerReiniciadoEnEsteTurno = false

        val jugadorActual = gestor.jugadorActual
        val colorTurno = jugadorActual.color

        // Gestión de Botones PowerUp (Tu código corregido)
        val mapaBotones = mapOf(
            ColorJugador.ROJO to powerupRojo,
            ColorJugador.VERDE to powerupVerde,
            ColorJugador.AZUL to powerupAzul,
            ColorJugador.AMARILLO to powerupAmarillo
        )
        for ((color, boton) in mapaBotones) {
            val jugador = gestor.jugadores.find { it.color == color }
            if (jugador != null) {
                if (!jugador.esIA && jugador.usosPowerUpRestantes > 0) {
                    boton.visibility = View.VISIBLE
                } else {
                    boton.visibility = View.INVISIBLE
                }
            } else {
                boton.visibility = View.INVISIBLE
            }
        }

        // Texto PowerUp (Tu código)
        if (jugadorActual.powerUpActivo != TipoPowerUp.NINGUNO) {
            val nombrePoder = when(jugadorActual.powerUpActivo) {
                TipoPowerUp.SALIDA_MAESTRA -> "SALIDA MAESTRA (6)"
                TipoPowerUp.DADO_ALTOS -> "DADOS ALTOS"
                TipoPowerUp.DADO_BAJOS -> "DADOS BAJOS"
                TipoPowerUp.DADO_SOLO_PARES -> "SOLO PARES"
                TipoPowerUp.DADO_SOLO_IMPARES -> "SOLO IMPARES"
                TipoPowerUp.ESCUDO_TEMPORAL -> "ESCUDO ACTIVO"
                else -> ""
            }
            txtPowerUpActivo.text = "POWER-UP: $nombrePoder"
            txtPowerUpActivo.visibility = View.VISIBLE
        } else {
            txtPowerUpActivo.visibility = View.GONE
        }

        // Identificar dado activo
        val dadoActivo = when(colorTurno) {
            ColorJugador.ROJO -> dadoRojo
            ColorJugador.VERDE -> dadoVerde
            ColorJugador.AZUL -> dadoAzul
            ColorJugador.AMARILLO -> dadoAmarillo
        }

        // --- NUEVA LÓGICA DE CONTROL DE TURNO ---

        if (esModoOnline) {
            // MODO ONLINE

            if (jugadorActual.color == miColor) {
                // == ES MI TURNO ==
                // Habilitar controles igual que un humano local
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)

                dadoActivo.setOnClickListener {
                    cancelarTemporizador()
                    realizarLanzamientoHumano()
                }
                iniciarTemporizador(esParaLanzar = true)

            } else {
                // == ES TURNO DEL RIVAL ==
                // Bloquear todo. Solo mostramos el dado del rival sin listener.
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                dadoActivo.setOnClickListener(null)

                cancelarTemporizador() // No corre tiempo local

                // NO llamamos a gestionarTurnoIA().
                // Simplemente no hacemos NADA. Esperamos a que llegue un mensaje de red.
            }

        } else {
            // MODO LOCAL (Tu código original)
            if (jugadorActual.esIA) {
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                cancelarTemporizador()
                gestionarTurnoIA()
            } else {
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                dadoActivo.setOnClickListener {
                    cancelarTemporizador()
                    realizarLanzamientoHumano()
                }
                iniciarTemporizador(esParaLanzar = true)
            }
        }
    }

    //Esta función maneja el tiro del humano, ya sea por clic o por tiempo agotado
    private fun realizarLanzamientoHumano()
    {
        //Bloqueamos los dados visualmente para que no pueda dar clic otra vez
        dadoRojo.setOnClickListener(null)
        dadoVerde.setOnClickListener(null)
        dadoAzul.setOnClickListener(null)
        dadoAmarillo.setOnClickListener(null)

        lifecycleScope.launch{
            //1. CORRECCIÓN: Guardamos quién tira ANTES de lanzar el dado.
            //Si no puedes mover, 'lanzarDado' cambiará el turno internamente,
            //así que necesitamos recordar quién eras tú para actualizar TU dado.
            val colorJugadorLanzador = gestor.jugadorActual.color

            //2. Sonido y Lógica
            gestorSonido.reproducir(TipoSonido.DADO)
            ultimoResultadoDado = gestor.lanzarDado()

            // --- ENVIAR A LA RED ---
            if(ClienteRed.estaConectado())
            {
                val msg = MensajeRed(
                    accion = AccionRed.LANZAR_DADO,
                    colorJugador = colorJugadorLanzador,
                    resultadoDado = ultimoResultadoDado
                )
                ClienteRed.enviar(msg)
            }

            //3. Mostrar el resultado visualmente en el dado CORRECTO (usando la variable guardada)
            val dadoActivo = when(colorJugadorLanzador) {
                ColorJugador.ROJO -> dadoRojo
                ColorJugador.VERDE -> dadoVerde
                ColorJugador.AZUL -> dadoAzul
                ColorJugador.AMARILLO -> dadoAmarillo
            }
            dadoActivo.setImageResource(obtenerRecursoDado(ultimoResultadoDado))

            //4. LÓGICA DE DELAY CONDICIONAL
            if (gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO) {
                //CASO A: NO PUEDES MOVER (O sacaste tres 6).
                //El turno ya pasó automáticamente.
                //Aquí SÍ hacemos delay para que veas el número feo que te salió antes de cambiar.
                delay(800)

                tableroView.actualizarEstadoJuego(gestor.jugadores)
                prepararSiguienteTurno()
            }
            else {
                //CASO B: SÍ PUEDES MOVER.
                //NO hay delay. Mostramos las opciones inmediatamente para agilidad.

                val movimientos = gestor.obtenerMovimientosPosibles(ultimoResultadoDado)
                tableroView.actualizarFichasMovibles(movimientos)

                if(movimientos.isNotEmpty())
                    gestorSonido.reproducir(TipoSonido.OPCION)

                //Iniciamos el timer para mover ficha
                iniciarTemporizador(esParaLanzar = false)
            }
        }
    }

    override fun onCasillaTocada(col: Int, fila: Int)
    {
        // Validar que estemos esperando un movimiento
        if(gestor.estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO)
            return

        // 2. Validar turno en ONLINE:
        // Si estamos en línea y el color del jugador actual NO es mi color,
        // significa que es el turno del rival. NO DEBO poder tocar nada.
        if (esModoOnline && gestor.jugadorActual.color != miColor) {
            Log.w(TAG, "Toque ignorado: No es tu turno.")
            return
        }

        // Obtener movimientos posibles (Código original)
        val movimientosPosibles = gestor.obtenerMovimientosPosibles(ultimoResultadoDado)
        if(movimientosPosibles.isEmpty()) return

        val posGlobalTocada = tableroView.obtenerPosicionGlobalDeCasilla(col, fila)
        var fichaParaMover: Ficha? = null

        // ... (Tu lógica de detección de CASO A y CASO B sigue igual) ...
        if(posGlobalTocada != -1) {
            fichaParaMover = movimientosPosibles.find { it.posicionGlobal == posGlobalTocada }
            // ... logs ...
        } else {
            if(ultimoResultadoDado == 6) {
                val colorBaseTocada = tableroView.obtenerColorBaseTocada(col, fila)
                if(colorBaseTocada == gestor.jugadorActual.color) {
                    fichaParaMover = movimientosPosibles.find{ it.estado == EstadoFicha.EN_BASE }
                }
            }
        }

        // --- LÓGICA DE MOVIMIENTO Y TIMER ---
        if(fichaParaMover != null)
        {
            // A. MOVIMIENTO VÁLIDO
            // Aquí SÍ cancelamos el timer porque el jugador ya decidió
            cancelarTemporizador()

            // --- NUEVO: ENVIAR MOVIMIENTO A LA RED ---
            if (esModoOnline && gestor.jugadorActual.color == miColor) {
                val msg = MensajeRed(
                    accion = AccionRed.MOVER_FICHA,
                    colorJugador = miColor,
                    idFicha = fichaParaMover.id,
                    resultadoDado = ultimoResultadoDado
                )
                ClienteRed.enviar(msg)
            }

            animarFicha(fichaParaMover, ultimoResultadoDado) {
                // ... (Tu bloque de animación y lógica sigue igual) ...
                gestor.moverFicha(fichaParaMover, ultimoResultadoDado)
                tableroView.actualizarEstadoJuego(gestor.jugadores)
                tableroView.actualizarFichasMovibles(emptyList())

                when(gestor.estadoJuego)
                {
                    EstadoJuego.JUEGO_TERMINADO -> mostrarVictoria(gestor.jugadorActual.color)
                    EstadoJuego.ESPERANDO_DECISION_BONIFICACION ->
                    {
                        // CAMBIO: Usar la función con timer
                        iniciarTemporizadorBonificacion()
                    }
                    else -> prepararSiguienteTurno()
                }
            }
        }
        else
        {
            // B. TOQUE INVÁLIDO (O solo consultando opciones)
            Log.w(TAG, "Toque inválido o ficha no movible.")

            if (!timerReiniciadoEnEsteTurno) {
                // Si es la primera vez que toca mal, le damos chance y reiniciamos el reloj
                Log.d(TAG_TIMER, "Reiniciando tiempo por interacción (Solo 1 vez)")
                iniciarTemporizador(esParaLanzar = false)
                timerReiniciadoEnEsteTurno = true

                // Feedback visual opcional
                // Toast.makeText(this, "Tiempo reiniciado", Toast.LENGTH_SHORT).show()
            } else {
                // Si ya lo reinició una vez, NO hacemos nada.
                // El reloj original sigue corriendo hacia su fin.
                Log.d(TAG_TIMER, "El tiempo ya fue reiniciado una vez. No se detiene.")
            }
        }
    }

    private fun cancelarTemporizador()
    {
        temporizadorTurno?.cancel()
        Log.d(TAG_TIMER, "Temporizador cancelado por accion del usuario")

        //Ocultar el reloj
        textoTemporizador.visibility = View.INVISIBLE
    }

    //Inicia un nuevo temporizador (para lanzar o para mover)
    private fun iniciarTemporizador(esParaLanzar: Boolean)
    {
        cancelarTemporizador() //Cancela el anterior y oculta el reloj

        val duracion = if(esParaLanzar) TIEMPO_PARA_LANZAR_MS else TIEMPO_PARA_MOVER_MS

        //--- Mostrar el reloj ---
        //Mostrar el tiempo inicial
        textoTemporizador.text = (duracion / 1000).toString()
        textoTemporizador.visibility = View.VISIBLE

        temporizadorTurno = object : CountDownTimer(duracion, 1000)
        {
            override fun onTick(millisUntilFinished: Long)
            {
                //--- Actualizar el número ---
                //Sumar 999ms para redondear hacia arriba
                val segundosRestantes = (millisUntilFinished + 999) / 1000
                textoTemporizador.text = segundosRestantes.toString()
            }

            override fun onFinish()
            {
                Log.i(TAG_TIMER, "Tiempo agotado. Acción automática")

                //Ocultar el reloj
                textoTemporizador.visibility = View.INVISIBLE

                if(esParaLanzar)
                    accionAutomaticaLanzar()
                else
                    accionAutomaticaMover()
            }
        }.start()
    }

    //Acción automática si se acaba el tiempo de lanzar
    private fun accionAutomaticaLanzar()
    {
        if(gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)
        {
            // Si el menú estaba abierto y se acabó el tiempo:
            if(layoutSeleccionPowerUp.visibility == View.VISIBLE)
            {
                layoutSeleccionPowerUp.visibility = View.GONE
                // Restaurar elevación del timer
                textoTemporizador.elevation = 0f
                Toast.makeText(this, "¡Tiempo agotado! No seleccionaste nada.", Toast.LENGTH_SHORT).show()
            }

            Log.i(TAG_TIMER, "Lanzando dado automáticamente...")
            realizarLanzamientoHumano()
        }
    }

    //Acción automática si se acaba el tiempo de mover
    private fun accionAutomaticaMover()
    {
        if(gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO)
        {
            Log.i(TAG_TIMER, "Moviendo ficha automáticamente (IA/AFK)...")

            val fichaParaMover = gestor.seleccionarFichaIA(ultimoResultadoDado)

            if(fichaParaMover != null)
            {
                // 1. ENVIAR MOVIMIENTO A LA RED (Antes de animar)
                if (esModoOnline && gestor.jugadorActual.color == miColor) {
                    val msg = MensajeRed(
                        accion = AccionRed.MOVER_FICHA,
                        colorJugador = miColor,
                        idFicha = fichaParaMover.id,
                        resultadoDado = ultimoResultadoDado
                    )
                    ClienteRed.enviar(msg)
                }

                animarFicha(fichaParaMover, ultimoResultadoDado)
                {
                    gestor.moverFicha(fichaParaMover, ultimoResultadoDado)

                    tableroView.actualizarEstadoJuego(gestor.jugadores)
                    tableroView.actualizarFichasMovibles(emptyList())

                    when(gestor.estadoJuego)
                    {
                        EstadoJuego.JUEGO_TERMINADO -> {
                            cancelarTemporizador()
                            mostrarVictoria(gestor.jugadorActual.color)
                        }
                        EstadoJuego.ESPERANDO_LANZAMIENTO -> {
                            prepararSiguienteTurno()
                        }
                        EstadoJuego.ESPERANDO_DECISION_BONIFICACION -> {
                            Log.i(TAG, "Automático cayó en casilla segura. Rechazando...")

                            // DEBUG LOG: Verificamos por qué camino se va
                            Log.d(TAG, "Debug AFK: Online=$esModoOnline, Turno=${gestor.jugadorActual.color}, MiColor=$miColor")

                            if (esModoOnline && gestor.jugadorActual.color == miColor) {
                                // MODO ONLINE: Usamos corrutina y delay
                                lifecycleScope.launch {
                                    delay(1000) // Aumentamos a 1 seg para estar seguros

                                    val msg = MensajeRed(
                                        accion = AccionRed.RESULTADO_BONIFICACION,
                                        colorJugador = miColor,
                                        exitoTrivia = false
                                    )
                                    ClienteRed.enviar(msg)
                                    Log.d(TAG, "Envio rechazo automático a la red (con delay).")

                                    // IMPORTANTE: Ejecutar lógica local AQUÍ dentro
                                    gestor.resolverBonificacionCasillaSegura(false)
                                    prepararSiguienteTurno()
                                }
                            } else {
                                // MODO LOCAL (O turno de IA en local)
                                Log.d(TAG, "Aplicando rechazo localmente.")
                                gestor.resolverBonificacionCasillaSegura(false)
                                prepararSiguienteTurno()
                            }
                        }
                        else -> {
                            prepararSiguienteTurno()
                        }
                    }
                }
            }
        }
    }

    private fun mostrarVictoria(ganadorColor: ColorJugador)
    {
        cancelarTemporizador()

        val jugadorGanador = gestor.jugadores.find { it.color == ganadorColor }
        val nombre = jugadorGanador?.nombre ?: ganadorColor.name

        // --- LÓGICA DE PUNTAJE ---
        // Solo guardamos si NO es IA (o si quieres guardar IAs también, quita el if)
        if(jugadorGanador != null && !jugadorGanador.esIA)
        {
            val puntosBase = 1000
            val puntosTrivia = jugadorGanador.aciertosTrivia * 100
            val total = puntosBase + puntosTrivia

            val nuevoRecord = Puntaje(nombre, jugadorGanador.aciertosTrivia, total)
            GestorPuntajes.guardarPuntaje(this, nuevoRecord)

            Toast.makeText(this, "¡Puntaje guardado! ($total pts)", Toast.LENGTH_LONG).show()
        }
        // -------------------------

        val texto = "¡HA GANADO $nombre!"
        txtGanador.text = texto.uppercase()

        val colorRes = when(ganadorColor)
        {
            ColorJugador.ROJO -> androidx.core.content.ContextCompat.getColor(this, R.color.rojo)
            ColorJugador.VERDE -> androidx.core.content.ContextCompat.getColor(this, R.color.verde)
            ColorJugador.AZUL -> androidx.core.content.ContextCompat.getColor(this, R.color.azul)
            ColorJugador.AMARILLO -> androidx.core.content.ContextCompat.getColor(this, R.color.amarillo)
        }
        txtGanador.setTextColor(colorRes)

        layoutVictoria.visibility = View.VISIBLE
        layoutVictoria.bringToFront()
    }

    private fun configurarBotonesPowerUp()
    {
        // Asignar listener específico para cada color
        powerupRojo.setOnClickListener { abrirMenuParaJugador(ColorJugador.ROJO) }
        powerupVerde.setOnClickListener { abrirMenuParaJugador(ColorJugador.VERDE) }
        powerupAzul.setOnClickListener { abrirMenuParaJugador(ColorJugador.AZUL) }
        powerupAmarillo.setOnClickListener { abrirMenuParaJugador(ColorJugador.AMARILLO) }

        // Configurar botón cancelar
        btnCancelarPower.setOnClickListener {
            layoutSeleccionPowerUp.visibility = View.GONE
            // Al cerrar, si es mi turno, aseguro que el timer se siga viendo normal
            textoTemporizador.elevation = 0f // Restaurar elevación normal
        }

        //Configurar botones de selección (Lo que pasa al elegir uno)
        btnPowerPares.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            procesarSeleccion(TipoPowerUp.DADO_SOLO_PARES)
        }
        btnPowerImpares.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            procesarSeleccion(TipoPowerUp.DADO_SOLO_IMPARES)
        }
        btnPowerAltos.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            procesarSeleccion(TipoPowerUp.DADO_ALTOS)
        }
        btnPowerBajos.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            procesarSeleccion(TipoPowerUp.DADO_BAJOS)
        }
        btnPowerSalida.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            procesarSeleccion(TipoPowerUp.SALIDA_MAESTRA)
        }
        btnPowerEscudo.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            procesarSeleccion(TipoPowerUp.ESCUDO_TEMPORAL)
        }
    }

    private fun abrirMenuParaJugador(colorInteresado: ColorJugador)
    {
        val jugadorInteresado = gestor.jugadores.find { it.color == colorInteresado } ?: return

        gestorSonido.reproducir(TipoSonido.MENU)

        if(jugadorInteresado.usosPowerUpRestantes <= 0) {
            Toast.makeText(this, "No te quedan usos",
                Toast.LENGTH_SHORT).show()
            return
        }

        txtUsosRestantes.text = "Usos restantes: ${jugadorInteresado.usosPowerUpRestantes}"

        // --- LÓGICA DEL TIMER VISIBLE ---
        // Solo mostramos el timer si el que abre el menú ES el jugador del turno actual
        val esSuTurno = (gestor.jugadorActual.color == colorInteresado)
        val esFaseLanzamiento = (gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)

        if (esSuTurno && esFaseLanzamiento) {
            // Es mi turno -> El timer debe verse ENCIMA del menú
            textoTemporizador.visibility = View.VISIBLE
            textoTemporizador.elevation = 100f // Valor alto para superar al menú
        } else {
            // No es mi turno -> El timer (si existe) se queda DETRÁS del menú para no confundir
            textoTemporizador.elevation = 0f
        }

        // --- LÓGICA DE RESTRICCIÓN (Tu código previo) ---
        val todasEnBase = jugadorInteresado.fichas.all { it.estado == EstadoFicha.EN_BASE }
        if (todasEnBase) {
            habilitarBoton(btnPowerSalida, true)
            habilitarBoton(btnPowerAltos, true)
            habilitarBoton(btnPowerBajos, false)
            habilitarBoton(btnPowerPares, false)
            habilitarBoton(btnPowerImpares, false)
            habilitarBoton(btnPowerEscudo, false)
        } else {
            habilitarBoton(btnPowerSalida, true)
            habilitarBoton(btnPowerAltos, true)
            habilitarBoton(btnPowerBajos, true)
            habilitarBoton(btnPowerPares, true)
            habilitarBoton(btnPowerImpares, true)
            habilitarBoton(btnPowerEscudo, true)
        }

        layoutSeleccionPowerUp.visibility = View.VISIBLE
        layoutSeleccionPowerUp.bringToFront()
    }

    // Función auxiliar para activar/desactivar botones visualmente
    private fun habilitarBoton(boton: Button, habilitar: Boolean) {
        boton.isEnabled = habilitar
        if (habilitar) {
            boton.alpha = 1.0f // Totalmente visible
        } else {
            boton.alpha = 0.5f // Semitransparente (efecto deshabilitado)
        }
    }

    private fun procesarSeleccion(tipo: TipoPowerUp)
    {
        //Ocultar menú de selección
        layoutSeleccionPowerUp.visibility = View.GONE

        //Mostrar la trivia
        mostrarTrivia(onExito =
        {
            //--- CÓDIGO DE ÉXITO (Se ejecuta al ganar) ---
            val exito = gestor.activarPowerUp(gestor.jugadorActual, tipo)

            if(exito)
            {
                val mensaje = when(tipo)
                {
                    TipoPowerUp.SALIDA_MAESTRA -> "¡Correcto! Salida Maestra activada (Siguiente tiro: 6)."
                    TipoPowerUp.ESCUDO_TEMPORAL -> "¡Correcto! Escudo activado hasta tu próximo turno."
                    else -> "¡Correcto! Power-Up activado: ${tipo.name}"
                }

                Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()

                //Actualizar UI para que desaparezca el rayito si se acabaron los usos
                prepararSiguienteTurno()
            }
        })
    }

    private fun mostrarTrivia(onExito: () -> Unit, onFallo: (() -> Unit)? = null)
    {
        //Obtener pregunta random
        val pregunta = BancoPreguntas.obtenerPreguntaAleatoria()

        //Llenar la UI
        txtPregunta.text = pregunta.texto

        txtFeedback.visibility = View.GONE
        btnOpcion1.isEnabled = true
        btnOpcion2.isEnabled = true
        btnOpcion3.isEnabled = true
        btnOpcion4.isEnabled = true

        //Asignamos las opciones (Lista índice 0 -> Botón 1, etc.)
        btnOpcion1.text = pregunta.opciones[0]
        btnOpcion2.text = pregunta.opciones[1]
        btnOpcion3.text = pregunta.opciones[2]
        btnOpcion4.text = pregunta.opciones[3]

        //3. Configurar listeners
        //Pasamos el índice lógico (0-3) a verificarRespuesta
        btnOpcion1.setOnClickListener{ verificarRespuesta(0, pregunta, onExito, onFallo) }
        btnOpcion2.setOnClickListener{ verificarRespuesta(1, pregunta, onExito, onFallo) }
        btnOpcion3.setOnClickListener{ verificarRespuesta(2, pregunta, onExito, onFallo) }
        btnOpcion4.setOnClickListener{ verificarRespuesta(3, pregunta, onExito, onFallo) }

        //Botón Cancelar: Simplemente cierra la trivia sin restar usos
        btnCancelarTrivia.setOnClickListener{
            cancelarTemporizadorTrivia()
            overlayPreguntas.visibility = View.GONE

            //Reactivar timer si estaba pausado
            val yaLanzoDado = gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO
            iniciarTemporizador(!yaLanzoDado)

            Toast.makeText(this, "Trivia cancelada", Toast.LENGTH_SHORT).show()
        }

        //4. Mostrar layout
        overlayPreguntas.visibility = View.VISIBLE
        overlayPreguntas.bringToFront()

        //Pausar timer de lanzamiento
        cancelarTemporizador()

        //--- INICIAR TIMER TRIVIA ---
        iniciarTemporizadorTrivia(onFallo)
    }

    private fun verificarRespuesta(indiceSeleccionado: Int, pregunta: Pregunta, onExito: () -> Unit, onFallo: (() -> Unit)?)
    {
        // --- DETENER TIMER ---
        cancelarTemporizadorTrivia()

        //Bloquear botones para que no sigan pulsando
        btnOpcion1.isEnabled = false
        btnOpcion2.isEnabled = false
        btnOpcion3.isEnabled = false
        btnOpcion4.isEnabled = false

        val esCorrecto = (indiceSeleccionado == pregunta.indiceCorrecto)

        //Configurar el Texto de Feedback
        txtFeedback.visibility = View.VISIBLE

        if(esCorrecto)
        {
            txtFeedback.text = "¡CORRECTO!"
            txtFeedback.setTextColor(android.graphics.Color.parseColor("#48BB78")) //Verde
            gestor.jugadorActual.aciertosTrivia++
        }
        else{
            txtFeedback.text = "INCORRECTO"
            txtFeedback.setTextColor(android.graphics.Color.parseColor("#E53E3E")) //Rojo
        }

        //Esperar 1.5 segundos leyendo el texto antes de continuar
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({

            overlayPreguntas.visibility = View.GONE //Ocultamos todo el layout

            if(esCorrecto)
                onExito()
            else{
                if(onFallo != null)
                    onFallo()
                else{
                    //Penalización estándar
                    if(gestor.jugadorActual.usosPowerUpRestantes > 0)
                        gestor.jugadorActual.usosPowerUpRestantes--

                    val respuestaTexto = pregunta.opciones[pregunta.indiceCorrecto]
                    Toast.makeText(this, "Era: $respuestaTexto. Pierdes 1 uso.", android.widget.Toast.LENGTH_SHORT).show()

                    val yaLanzoDado = gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO
                    iniciarTemporizador(!yaLanzoDado)
                }
            }
        }, 1500)
    }

    private fun configurarBotonesBonificacion()
    {
        btnAceptarReto.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            layoutDecisionBonificacion.visibility = View.GONE

            // IMPORTANTE: El usuario decidió, paramos el reloj de 4s
            cancelarTemporizadorBonificacion()

            mostrarTrivia(
                onExito = {
                    // GANÓ
                    gestor.resolverBonificacionCasillaSegura(true)
                    enviarResultadoBonificacion(true) // <--- AVISAR RED
                    prepararSiguienteTurno()
                },
                onFallo = {
                    // PERDIÓ
                    gestor.resolverBonificacionCasillaSegura(false)
                    enviarResultadoBonificacion(false) // <--- AVISAR RED
                    prepararSiguienteTurno()
                }
            )
        }

        btnRechazarReto.setOnClickListener {
            gestorSonido.reproducir(TipoSonido.MENU)
            layoutDecisionBonificacion.visibility = View.GONE

            // IMPORTANTE: Decidió, paramos reloj
            cancelarTemporizadorBonificacion()

            gestor.resolverBonificacionCasillaSegura(false)
            enviarResultadoBonificacion(false) // <--- AVISAR RED

            prepararSiguienteTurno()
        }
    }

    // Función para animar movimientos LOCALES (Tu turno)
    private fun animarFicha(ficha: Ficha, pasosTotales: Int, alTerminar: () -> Unit)
    {
        // 1. Cancelar animación anterior
        animacionJob?.cancel()

        // 2. Calcular camino visual
        val camino = gestor.calcularCamino(ficha, pasosTotales)

        // 3. GUARDAR ESTADO ORIGINAL (CRÍTICO)
        // Guardamos dónde estaba la ficha antes de empezar a moverla visualmente
        val posicionOriginal = ficha.posicionGlobal
        val estadoOriginal = ficha.estado

        animacionJob = lifecycleScope.launch {
            for(nuevaPos in camino) {
                // Ajustes visuales de estado
                if(ficha.estado == EstadoFicha.EN_BASE && nuevaPos != 0) ficha.estado = EstadoFicha.EN_JUEGO
                if(ficha.estado == EstadoFicha.EN_JUEGO && nuevaPos > 52) ficha.estado = EstadoFicha.EN_META

                // Mover visualmente
                ficha.posicionGlobal = nuevaPos

                tableroView.actualizarEstadoJuego(gestor.jugadores)
                gestorSonido.reproducir(TipoSonido.PASO)
                delay(300)
            }

            // --- AQUÍ ESTÁ EL ARREGLO ---
            // Antes de llamar a la lógica oficial (alTerminar -> moverFicha),
            // debemos RESTAURAR la ficha a donde estaba al principio.
            // Si no hacemos esto, moverFicha sumará: (PosiciónYaAvanzada + Dado) = Doble Movimiento.

            ficha.posicionGlobal = posicionOriginal
            ficha.estado = estadoOriginal
            // -----------------------------

            // Ahora sí, ejecutamos la lógica matemática
            alTerminar()

            // Sonidos post-movimiento
            if (gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO)
                gestorSonido.reproducir(TipoSonido.VICTORIA)
            else if (gestor.esPosicionFinalDeMeta(ficha))
                gestorSonido.reproducir(TipoSonido.META)
            else if (gestor.huboKill)
                gestorSonido.reproducir(TipoSonido.KILL)
            else if (ficha.estado == EstadoFicha.EN_JUEGO && gestor.esCasillaSegura(ficha.posicionGlobal))
                gestorSonido.reproducir(TipoSonido.ESPECIAL)
        }
    }

    //Gestiona el turno si es una IA
    private fun gestionarTurnoIA()
    {
        //Bloqueamos interacción
        dadoRojo.setOnClickListener(null)
        dadoVerde.setOnClickListener(null)
        dadoAzul.setOnClickListener(null)
        dadoAmarillo.setOnClickListener(null)

        lifecycleScope.launch{
            delay(800)

            //--- CORRECCIÓN AQUÍ ---
            //1. Guardamos el color de la IA ANTES de lanzar,
            //por si 'lanzarDado' cambia el turno inmediatamente.
            val colorIA = gestor.jugadorActual.color
            //-----------------------

            gestorSonido.reproducir(TipoSonido.DADO)
            ultimoResultadoDado = gestor.lanzarDado()

            //Usamos la variable guardada 'colorIA' en vez de 'gestor.jugadorActual'
            val dadoActivo = when(colorIA) {
                ColorJugador.ROJO -> dadoRojo
                ColorJugador.VERDE -> dadoVerde
                ColorJugador.AZUL -> dadoAzul
                ColorJugador.AMARILLO -> dadoAmarillo
            }

            dadoActivo.setImageResource(obtenerRecursoDado(ultimoResultadoDado))
            dadoActivo.visibility = View.VISIBLE

            delay(800)

            //VERIFICAR QUÉ PASÓ
            if (gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO) {
                accionAutomaticaMover()
            }
            else {
                Log.i(TAG, "IA no tiene movimientos válidos. Pasando turno...")
                prepararSiguienteTurno()
            }
        }
    }

    private fun iniciarTemporizadorTrivia(onFallo: (() -> Unit)?) {
        cancelarTemporizadorTrivia() // Limpiar previo

        Log.d(TAG_TIMER, "Iniciando timer de Trivia (8s)")

        // Opcional: Puedes reutilizar el TextView del reloj principal si quieres que se vea
        textoTemporizador.visibility = View.VISIBLE
        textoTemporizador.bringToFront() // Para que se vea sobre el overlay si es posible

        temporizadorTrivia = object : CountDownTimer(TIEMPO_TRIVIA_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seg = (millisUntilFinished + 999) / 1000
                textoTemporizador.text = seg.toString()
            }

            override fun onFinish() {
                Log.i(TAG_TIMER, "Tiempo de Trivia agotado.")
                textoTemporizador.visibility = View.INVISIBLE

                // Tratamos el tiempo agotado como una respuesta incorrecta (-1)
                // Pasamos una pregunta dummy o manejamos la lógica de fallo directo

                // Para no complicar pasando la pregunta original, ejecutamos la lógica de fallo directa:
                overlayPreguntas.visibility = View.GONE

                if (onFallo != null) {
                    onFallo()
                } else {
                    // Lógica de fallo por defecto (PowerUps)
                    if (gestor.jugadorActual.usosPowerUpRestantes > 0) {
                        gestor.jugadorActual.usosPowerUpRestantes--
                    }
                    Toast.makeText(this@JuegoActivity, "¡Tiempo agotado! Pierdes 1 uso.", Toast.LENGTH_SHORT).show()

                    val yaLanzoDado = gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO
                    iniciarTemporizador(!yaLanzoDado)
                }
            }
        }.start()
    }

    private fun cancelarTemporizadorTrivia()
    {
        temporizadorTrivia?.cancel()
        textoTemporizador.visibility = View.INVISIBLE
    }

    private fun actualizarIconoSonido()
    {
        if (gestorSonido.sonidoHabilitado)
        {
            // Sonido Activado (Bocina normal)
            btnSonido.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            btnSonido.alpha = 1.0f
        }
        else{
            // Sonido Desactivado (Mute)
            // Nota: Si no tienes un icono de "mute", puedes usar el mismo con alpha bajo
            // o buscar android.R.drawable.ic_lock_silent_mode
            btnSonido.setImageResource(android.R.drawable.ic_lock_silent_mode)
            btnSonido.alpha = 0.5f // Lo hacemos semitransparente para indicar desactivado
        }
    }

    private fun procesarMensajeJuego(mensaje: MensajeRed) {
        // Ignoramos nuestros propios mensajes (eco)
        if (mensaje.colorJugador == miColor) return

        when(mensaje.accion)
        {
            AccionRed.LANZAR_DADO ->
            {
                watchdogRival?.cancel()
                // --- PROTECCIÓN CONTRA DESINCRONIZACIÓN ---
                // Si recibimos un dado nuevo, asumimos que cualquier decisión pendiente ya terminó.
                if (layoutDecisionBonificacion.visibility == View.VISIBLE)
                    layoutDecisionBonificacion.visibility = View.GONE

                val valor = mensaje.resultadoDado ?: return
                val colorRival = mensaje.colorJugador ?: return

                ultimoResultadoDado = valor

                // 1. Identificar dado visual del rival
                val dadoRival = when(colorRival) {
                    ColorJugador.ROJO -> dadoRojo
                    ColorJugador.VERDE -> dadoVerde
                    ColorJugador.AZUL -> dadoAzul
                    ColorJugador.AMARILLO -> dadoAmarillo
            }

                // 2. Ejecutar lógica en el gestor (FORZANDO EL VALOR)
                gestorSonido.reproducir(TipoSonido.DADO)
                ultimoResultadoDado = gestor.lanzarDado(valorForzado = valor)

                // 3. Actualizar visualmente
                dadoRival.setImageResource(obtenerRecursoDado(valor))
                dadoRival.visibility = View.VISIBLE

                // 4. Verificar si pasó el turno automáticamente (ej. sacó tres 6 o no puede mover)
                if (gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO) {
                    // El gestor ya pasó el turno internamente
                    // Pequeño delay para que se vea el dado y luego cambio
                    lifecycleScope.launch {
                        delay(1500)
                        tableroView.actualizarEstadoJuego(gestor.jugadores)
                        prepararSiguienteTurno()
                    }
                } else {
                    // Ahora espera movimiento (MOVER_FICHA)
                    val movimientos = gestor.obtenerMovimientosPosibles(valor)
                    tableroView.actualizarFichasMovibles(movimientos)
                }
            }


            AccionRed.MOVER_FICHA -> {
                val idFicha = mensaje.idFicha ?: return
                val colorRival = mensaje.colorJugador ?: return
                val valorDado = mensaje.resultadoDado ?: ultimoResultadoDado

                val jugadorRival = gestor.jugadores.find { it.color == colorRival }
                val ficha = jugadorRival?.fichas?.find { it.id == idFicha }

                if (ficha != null) {
                    // Limpiamos pendientes
                    resultadoBonificacionPendiente = null

                    // --- CAMBIO: Usamos la nueva función exclusiva para REMOTOS ---
                    // Esto asegura que la lógica se actualice YA, y la animación sea solo visual.
                    animarMovimientoRemoto(ficha, valorDado, colorRival)
                }
            }

            AccionRed.RESULTADO_BONIFICACION -> {
                val tuvoExito = mensaje.exitoTrivia ?: false

                /// ¡Llegó el mensaje! Cancelamos la seguridad
                watchdogRival?.cancel()

                if (gestor.estadoJuego == EstadoJuego.ESPERANDO_DECISION_BONIFICACION) {
                    aplicarDecisionRemota(tuvoExito)
                } else {
                    Log.d(TAG, "Recibido resultado antes de tiempo. Guardando...")
                    resultadoBonificacionPendiente = tuvoExito
                }
            }
            else -> {}
        }
    }

    private fun animarMovimientoVisual(ficha: Ficha, camino: List<Int>, colorRival: ColorJugador) {
        animacionJob?.cancel()

        animacionJob = lifecycleScope.launch {
            // La ficha ya está lógicamente en su destino, pero visualmente la movemos
            // por el camino que calculamos antes.

            for (pos in camino) {
                // Forzamos la posición visual temporalmente
                // Nota: Esto es un "hack" visual. La ficha real ya tiene el valor final en el objeto Jugador.
                // Pero al modificar 'ficha.posicionGlobal' aquí, estamos alterando el objeto vivo.
                // Como ya actualizamos la lógica, esto solo "recorre" el camino hasta llegar al valor que ya tiene.

                ficha.posicionGlobal = pos
                // (Ajustes de estado visual si sale de base/entra meta)
                if(pos != 0 && pos <= 52) ficha.estado = EstadoFicha.EN_JUEGO
                if(pos > 52) ficha.estado = EstadoFicha.EN_META

                tableroView.actualizarEstadoJuego(gestor.jugadores)
                gestorSonido.reproducir(TipoSonido.PASO)
                delay(300)
            }

            // Al terminar, verificamos estado del juego (Victoria, etc)
            // Usamos la lógica post-movimiento
            if (gestor.estadoJuego == EstadoJuego.ESPERANDO_DECISION_BONIFICACION) {
                if (resultadoBonificacionPendiente != null) {
                    aplicarDecisionRemota(resultadoBonificacionPendiente!!)
                    resultadoBonificacionPendiente = null
                } else {
                    Toast.makeText(this@JuegoActivity, "El oponente está decidiendo...", Toast.LENGTH_SHORT).show()
                    iniciarWatchdogRival()
                }
            } else if (gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO) {
                mostrarVictoria(colorRival)
            } else {
                prepararSiguienteTurno()
            }
        }
    }

    private fun animarMovimientoRemoto(ficha: Ficha, valorDado: Int, colorRival: ColorJugador) {
        animacionJob?.cancel()

        // 1. Calcular el camino DESDE donde está ahora (antes de actualizar la lógica)
        val camino = gestor.calcularCamino(ficha, valorDado)

        // 2. ACTUALIZAR LÓGICA INMEDIATAMENTE (Para evitar race conditions)
        // Así el juego ya sabe dónde está la ficha realmente
        gestor.moverFicha(ficha, valorDado)

        // 3. Lanzar animación puramente visual
        animacionJob = lifecycleScope.launch {
            for (pos in camino) {
                // Forzamos la posición visual temporalmente
                // (Aunque el objeto lógico ya está en el destino, visualmente lo llevamos paso a paso)
                ficha.posicionGlobal = pos

                // Ajustes visuales
                if (pos != 0 && pos <= 52) ficha.estado = EstadoFicha.EN_JUEGO
                if (pos > 52) ficha.estado = EstadoFicha.EN_META

                tableroView.actualizarEstadoJuego(gestor.jugadores)
                gestorSonido.reproducir(TipoSonido.PASO)
                delay(300)
            }

            // AL TERMINAR:
            // No llamamos a 'moverFicha' porque YA lo hicimos al principio.
            // Solo verificamos qué sigue.

            if (gestor.estadoJuego == EstadoJuego.ESPERANDO_DECISION_BONIFICACION) {
                if (resultadoBonificacionPendiente != null) {
                    aplicarDecisionRemota(resultadoBonificacionPendiente!!)
                    resultadoBonificacionPendiente = null
                } else {
                    Toast.makeText(this@JuegoActivity, "El oponente está decidiendo...", Toast.LENGTH_SHORT).show()
                    // Iniciar watchdog por si acaso
                    iniciarWatchdogRival()
                }
            } else if (gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO) {
                mostrarVictoria(colorRival)
            } else {
                prepararSiguienteTurno()
            }
        }
    }

    private fun iniciarTemporizadorBonificacion() {
        cancelarTemporizador() // Cancelar timer de turno principal

        // Mostrar el diálogo
        layoutDecisionBonificacion.visibility = View.VISIBLE
        layoutDecisionBonificacion.bringToFront()

        Log.d(TAG_TIMER, "Iniciando timer de decisión (4s)")

        // Arrancamos cuenta de 4 segundos
        temporizadorBonificacion = object : CountDownTimer(4000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Tic tac...
            }

            override fun onFinish() {
                Log.i(TAG_TIMER, "Tiempo de decisión agotado. Rechazando bonificación.")

                layoutDecisionBonificacion.visibility = View.GONE
                Toast.makeText(this@JuegoActivity, "Tiempo agotado.", Toast.LENGTH_SHORT).show()

                // 1. Aplicar lógica local
                gestor.resolverBonificacionCasillaSegura(false)

                // 2. ENVIAR A LA RED (CRÍTICO)
                // Si no enviamos esto, el rival se queda esperando "RESULTADO_BONIFICACION" para siempre
                enviarResultadoBonificacion(false)

                // 3. Continuar juego
                prepararSiguienteTurno()
            }
        }.start()
    }

    private fun cancelarTemporizadorBonificacion() {
        temporizadorBonificacion?.cancel()
    }

    // Función auxiliar para enviar el resultado a la red
    private fun enviarResultadoBonificacion(exito: Boolean) {
        if (esModoOnline && gestor.jugadorActual.color == miColor) {
            val msg = MensajeRed(
                accion = AccionRed.RESULTADO_BONIFICACION,
                colorJugador = miColor,
                exitoTrivia = exito
            )
            ClienteRed.enviar(msg)
        }
    }

    private fun aplicarDecisionRemota(tuvoExito: Boolean) {
        gestor.resolverBonificacionCasillaSegura(tuvoExito)

        if (tuvoExito) {
            Toast.makeText(this, "¡Oponente ganó bonificación!", Toast.LENGTH_SHORT).show()
        } else {
            // Si fue automático/rechazo, a veces es mejor no mostrar nada para fluidez,
            // o un mensaje discreto.
            // Toast.makeText(this, "Oponente continúa turno normal.", Toast.LENGTH_SHORT).show()
        }

        prepararSiguienteTurno()
    }

    private fun iniciarWatchdogRival() {
        watchdogRival?.cancel()

        // CAMBIO: Bajamos de 6000 a 3000 (3 segundos)
        // Esto es suficiente para esperar lag, pero si falla, desbloquea rápido.
        watchdogRival = object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                Log.w(TAG, "WATCHDOG: El rival tardó. Forzando continuación.")

                if (gestor.estadoJuego == EstadoJuego.ESPERANDO_DECISION_BONIFICACION) {
                    gestor.resolverBonificacionCasillaSegura(false)
                    prepararSiguienteTurno()
                }
            }
        }.start()
    }
}