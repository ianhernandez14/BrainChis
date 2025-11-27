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
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
    private lateinit var txtCategoria: TextView

    private var inicioTriviaTime: Long = 0

    //Control de reinicio de timer por toque
    private var timerReiniciadoEnEsteTurno: Boolean = false

    //Texto para mostrar en pantalla qué powerup ha usado un jugador
    private lateinit var txtPowerUpActivo: TextView

    //Botón del sonido
    private lateinit var btnSonido: android.widget.ImageButton

    //Variables Multijugador
    private var esModoOnline: Boolean = false
    private var miColor: ColorJugador? = null

    //Timer para decidir si tomar la bonificación (4 segundos)
    private var temporizadorBonificacion: CountDownTimer? = null

    //Variable para guardar la decisión del rival si llega mientras la ficha hace su animación
    private var resultadoBonificacionPendiente: Boolean? = null

    //Timer de seguridad por si el rival se desconecta o el mensaje se pierde
    private var watchdogRival: CountDownTimer? = null

    private var animacionJob: Job? = null

    //Bandera para saber si estamos moviendo fichas visualmente
    private var animandoMovimientoRemoto: Boolean = false

    //Bandera para evitar 2 movimientos en un solo turno
    private var procesandoMovimiento: Boolean = false

    //Bandera para evitar que el timer corte el flujo si ya respondiste
    private var respondiendoTrivia: Boolean = false

    // Bandera para congelar el juego mientras alguien se reconecta
    private var bloqueoPorReconexion: Boolean = false

    // Cola de jugadores esperando reconectarse: Mapa [Color -> Nombre]
    private val colaReconexion = mutableMapOf<ColorJugador, String>()

    // Referencia al proceso de pensamiento de la IA para poder cancelarlo si el humano vuelve
    private var turnoIAJob: Job? = null

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
        txtCategoria = findViewById(R.id.txtCategoria)

        //Layout Bonificación
        layoutDecisionBonificacion = findViewById(R.id.layoutDecisionBonificacion)
        btnAceptarReto = findViewById(R.id.btnAceptarReto)
        btnRechazarReto = findViewById(R.id.btnRechazarReto)

        //Sonido
        btnSonido = findViewById(R.id.btnSonido)
        gestorSonido = GestorSonido(this)
        actualizarIconoSonido()

        //Listener del botón
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

        //DETECTAR ONLINE
        esModoOnline = intent.getBooleanExtra("MODO_ONLINE", false)
        val miColorStr = intent.getStringExtra("MI_COLOR")
        if (miColorStr != null) {
            miColor = ColorJugador.valueOf(miColorStr)
        }

        val listaJugadores = mutableListOf<Jugador>()

        if (esModoOnline) {
            // --- MODO ONLINE ---
            val coloresOrdenados = listOf(ColorJugador.ROJO, ColorJugador.AMARILLO, ColorJugador.VERDE, ColorJugador.AZUL)
            val numHumanos = intent.getIntExtra("CANTIDAD_HUMANOS_REALES", cantidadJugadores)

            // --- NUEVO: RECIBIR NOMBRES DEL LOBBY ---
            val nombresRecibidos = intent.getStringArrayListExtra("LISTA_NOMBRES")
            val coloresRecibidos = intent.getStringArrayListExtra("LISTA_COLORES")
            // ----------------------------------------

            for (i in 0 until cantidadJugadores) {
                val color = coloresOrdenados[i]
                val esBot = (i >= numHumanos)

                var nombreJugador = if(esBot) "CPU ${color.name}" else "Jugador ${color.name}"

                // Si no es bot, buscamos si tenemos su nombre real en la lista que mandó el Main
                if (!esBot && nombresRecibidos != null && coloresRecibidos != null) {
                    val indiceEnLista = coloresRecibidos.indexOf(color.name)
                    if (indiceEnLista != -1) {
                        nombreJugador = nombresRecibidos[indiceEnLista]
                    }
                }

                listaJugadores.add(Jugador(color, esIA = esBot, nombre = nombreJugador))
            }

            //Toast.makeText(this, "Partida Online: Eres $miColor", Toast.LENGTH_LONG).show()

        } else {
            //--- MODO LOCAL (Tu código original) ---
            //... (Pega aquí tu lógica original de if(cantidadJugadores == 2)... etc) ...
            //Ojo: Asegúrate de que tu código local original quede aquí dentro
            if (cantidadJugadores == 2) {
                val nombre1 = intent.getStringExtra("NOMBRE_JUGADOR") ?: "Jugador"
                listaJugadores.add(Jugador(ColorJugador.ROJO, esIA = !humanoJuega, nombre = nombre1))
                listaJugadores.add(Jugador(ColorJugador.AMARILLO, esIA = true, nombre = "CPU Amarillo"))
            } else {
                //... lógica para 3 y 4 ...
                listaJugadores.add(Jugador(ColorJugador.ROJO, esIA = !humanoJuega))
                listaJugadores.add(Jugador(ColorJugador.VERDE, esIA = true))
                listaJugadores.add(Jugador(ColorJugador.AMARILLO, esIA = true))
                if(cantidadJugadores == 4) listaJugadores.add(Jugador(ColorJugador.AZUL, esIA = true))
            }
        }

        //--- LEER TURNO INICIAL ---
        val turnoInicial = intent.getIntExtra("TURNO_INICIAL", 0)

        //Pasamos el turno inicial al gestor
        gestor.iniciarJuegoConJugadores(listaJugadores, turnoInicial)

        //--- MODO ONLINE ---
        val esOnline = intent.getBooleanExtra("MODO_ONLINE", false)
        // Leemos el flag que nos mandó MainActivity cuando recibimos ENVIAR_ESTADO
        val esReconexion = intent.getBooleanExtra("ES_RECONEXION", false)

        if (esOnline)
        {
            // CAMBIAMOS EL LISTENER
            ClienteRed.listener = { mensaje ->
                runOnUiThread {
                    procesarMensajeJuego(mensaje)
                }
            }

            // SI ES RECONEXIÓN, PEDIMOS EL ESTADO AL HOST INMEDIATAMENTE
            if (esReconexion && miColor != ColorJugador.ROJO) {
                // El Rojo es el Host, él ya tiene el estado. Los demás lo piden.
                val msg = MensajeRed(accion = AccionRed.SOLICITAR_ESTADO, colorJugador = miColor)
                ClienteRed.enviar(msg)
                Toast.makeText(this, "Sincronizando con el Host...", Toast.LENGTH_SHORT).show()
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                mostrarDialogoConfirmarSalida()
            }
        })
    }

    override fun onPause()
    {
        super.onPause()
        Log.d(TAG, "Aplicación en pausa - Deteniendo temporizadores y audio")

        cancelarTemporizador()
        gestorSonido.pausarTodo()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Aplicación reanudada - Restaurando estado")

        //1. Reactivar el audio
        gestorSonido.reanudarTodo()

        //2. Reactivar el temporizador
        val hayVentanaAbierta = overlayPreguntas.visibility == View.VISIBLE ||
                layoutDecisionBonificacion.visibility == View.VISIBLE ||
                layoutSeleccionPowerUp.visibility == View.VISIBLE

        if (!hayVentanaAbierta) {

            //--- CORRECCIÓN DE SEGURIDAD ONLINE ---
            //Si es online y NO es mi turno, NO debo iniciar ningún timer.
            if (esModoOnline && gestor.jugadorActual.color != miColor) {
                Log.d(TAG, "onResume: No es mi turno, no inicio timer.")
                textoTemporizador.visibility = View.INVISIBLE //Asegurar que esté oculto
                return
            }
            //--------------------------------------

            when (gestor.estadoJuego) {
                EstadoJuego.ESPERANDO_LANZAMIENTO -> {
                    iniciarTemporizador(esParaLanzar = true)
                }
                EstadoJuego.ESPERANDO_MOVIMIENTO -> {
                    iniciarTemporizador(esParaLanzar = false)
                }
                else -> { }
            }
        }
    }

    override fun onDestroy()
    {
        super.onDestroy()
        gestorSonido.liberar()

        // Si soy Host (Soy Rojo y estoy en Online), matar el servidor al salir de la partida
        if (esModoOnline && miColor == ColorJugador.ROJO) {
            ServidorBrainchis.detener()
        }

        // Cerrar mi conexión cliente
        ClienteRed.cerrar()
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
        //1. LIMPIEZA INICIAL DE ESTADO
        cancelarTemporizador() //Asegurar que no haya timers previos corriendo
        tableroView.actualizarFichasMovibles(emptyList())

        //LIMPIEZA DE BANDERAS
        procesandoMovimiento = false
        timerReiniciadoEnEsteTurno = false
        respondiendoTrivia = false

        //3. Ocultar todos los dados y powerups primero
        dadoRojo.visibility = View.INVISIBLE
        dadoVerde.visibility = View.INVISIBLE
        dadoAzul.visibility = View.INVISIBLE
        dadoAmarillo.visibility = View.INVISIBLE

        powerupRojo.visibility = View.INVISIBLE
        powerupVerde.visibility = View.INVISIBLE
        powerupAzul.visibility = View.INVISIBLE
        powerupAmarillo.visibility = View.INVISIBLE

        //4. Quitarles el listener por seguridad
        dadoRojo.setOnClickListener(null)
        dadoVerde.setOnClickListener(null)
        dadoAzul.setOnClickListener(null)
        dadoAmarillo.setOnClickListener(null)

        //5. Datos del turno
        val jugadorActual = gestor.jugadorActual
        val colorTurno = jugadorActual.color

        // --- LÓGICA DE RECONEXIÓN (HOST) ---
        if (esModoOnline && miColor == ColorJugador.ROJO) {
            // Revisamos si hay alguien esperando para tomar ESTE color
            val nombrePendiente = colaReconexion[jugadorActual.color]

            if (nombrePendiente != null) {
                Log.i(TAG, "¡Es turno de $nombrePendiente! Aceptando conexión.")

                // 1. Quitar de la cola
                colaReconexion.remove(jugadorActual.color)

                // 2. Convertir a Humano
                jugadorActual.esIA = false
                jugadorActual.nombre = nombrePendiente

                // 3. Enviar Estado al Cliente (Esto desbloquea al jugador en su casa)
                lifecycleScope.launch {
                    // Pequeña espera por seguridad
                    delay(200)

                    val msgEstado = MensajeRed(
                        accion = AccionRed.ENVIAR_ESTADO,
                        estadoJuegoCompleto = gestor.jugadores,
                        turnoActual = gestor.jugadores.indexOf(jugadorActual),
                        resultadoDado = ultimoResultadoDado // El dado será 0 o el previo, está bien
                    )
                    ClienteRed.enviar(msgEstado)
                }

                Toast.makeText(this, "$nombrePendiente se ha unido a la partida.", Toast.LENGTH_SHORT).show()
            }
        }

        //6. GESTIÓN DE BOTONES DE POWER-UP
        val mapaBotones = mapOf(
            ColorJugador.ROJO to powerupRojo,
            ColorJugador.VERDE to powerupVerde,
            ColorJugador.AZUL to powerupAzul,
            ColorJugador.AMARILLO to powerupAmarillo
        )

        for ((color, boton) in mapaBotones) {
            //--- CORRECCIÓN: OCULTAR SI NO SOY YO (EN ONLINE) ---
            if (esModoOnline && color != miColor) {
                boton.visibility = View.INVISIBLE
                continue
            }

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

        //7. ACTUALIZAR TEXTO CENTRAL DE POWER-UP
        //Usamos la función auxiliar para que se actualice correctamente si el rival activó algo
        actualizarTextoPowerUp()

        //8. Identificar dado activo visualmente
        val dadoActivo = when(colorTurno)
        {
            ColorJugador.ROJO -> dadoRojo
            ColorJugador.VERDE -> dadoVerde
            ColorJugador.AZUL -> dadoAzul
            ColorJugador.AMARILLO -> dadoAmarillo
        }

        //Variable auxiliar para saber si soy el Host (Rojo es el Host siempre)
        val soyHost = (miColor == ColorJugador.ROJO)

        //9. LÓGICA PRINCIPAL DE CONTROL (HUMANO vs IA vs ONLINE)

        if (esModoOnline)
        {
            //CASO 1: ES MI TURNO (Humano Local)
            if (jugadorActual.color == miColor) {
                //... (Habilitar mis controles igual que antes) ...
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                dadoActivo.setOnClickListener {
                    cancelarTemporizador()
                    realizarLanzamientoHumano()
                }
                iniciarTemporizador(esParaLanzar = true)
            }
            //CASO 2: ES TURNO DE UNA CPU Y YO SOY EL HOST (Yo la controlo)
            else if (jugadorActual.esIA && soyHost) {
                Log.i(TAG, "Turno de CPU (${jugadorActual.color}) gestionado por Host.")

                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)

                cancelarTemporizador()
                //Ejecutar la rutina automática
                gestionarTurnoIA()
            }
            //CASO 3: ES RIVAL HUMANO O CPU (Y yo soy Cliente) -> ESPERAR
            else {
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                dadoActivo.setOnClickListener(null)
                cancelarTemporizador()
                //No hacemos nada, esperamos mensaje de red
            }
        }
        else
        {
            //--- MODO LOCAL ---

            if (jugadorActual.esIA) {
                //== ES TURNO DE LA CPU ==
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)

                cancelarTemporizador()
                gestionarTurnoIA() //Rutina automática
            } else {
                //== ES TURNO DEL HUMANO (LOCAL) ==
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)

                dadoActivo.setOnClickListener {
                    cancelarTemporizador()
                    realizarLanzamientoHumano()
                }
                iniciarTemporizador(esParaLanzar = true)
            }
        }

        // Si soy Host, revisar si alguien quiere entrar en este nuevo turno
        if (esModoOnline && miColor == ColorJugador.ROJO) {
            verificarReconexionPendiente()
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

            //--- ENVIAR A LA RED ---
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
        //Validar que estemos esperando un movimiento
        if(gestor.estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO)
            return

        //--- NUEVO: CANDADO DE SEGURIDAD ---
        if (procesandoMovimiento)
        {
            Log.w(TAG, "Intento de movimiento bloqueado: Ya se está procesando uno.")
            return
        }

        //2. Validar turno en ONLINE:
        //Si estamos en línea y el color del jugador actual NO es mi color,
        //significa que es el turno del rival. NO DEBO poder tocar nada.
        if (esModoOnline && gestor.jugadorActual.color != miColor) {
            Log.w(TAG, "Toque ignorado: No es tu turno.")
            return
        }

        //Obtener movimientos posibles (Código original)
        val movimientosPosibles = gestor.obtenerMovimientosPosibles(ultimoResultadoDado)
        if(movimientosPosibles.isEmpty())
            return

        val posGlobalTocada = tableroView.obtenerPosicionGlobalDeCasilla(col, fila)
        var fichaParaMover: Ficha? = null

        //... (Tu lógica de detección de CASO A y CASO B sigue igual) ...
        if(posGlobalTocada != -1) {
            fichaParaMover = movimientosPosibles.find { it.posicionGlobal == posGlobalTocada }
            //... logs ...
        } else {
            if(ultimoResultadoDado == 6) {
                val colorBaseTocada = tableroView.obtenerColorBaseTocada(col, fila)
                if(colorBaseTocada == gestor.jugadorActual.color) {
                    fichaParaMover = movimientosPosibles.find{ it.estado == EstadoFicha.EN_BASE }
                }
            }
        }

        //--- LÓGICA DE MOVIMIENTO Y TIMER ---
        if(fichaParaMover != null)
        {
            procesandoMovimiento = true

            //A. MOVIMIENTO VÁLIDO
            //Aquí SÍ cancelamos el timer porque el jugador ya decidió
            cancelarTemporizador()

            //--- NUEVO: ENVIAR MOVIMIENTO A LA RED ---
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
                //... (Tu bloque de animación y lógica sigue igual) ...
                gestor.moverFicha(fichaParaMover, ultimoResultadoDado)
                tableroView.actualizarEstadoJuego(gestor.jugadores)
                tableroView.actualizarFichasMovibles(emptyList())

                when(gestor.estadoJuego)
                {
                    EstadoJuego.JUEGO_TERMINADO -> mostrarVictoria(gestor.jugadorActual.color)
                    EstadoJuego.ESPERANDO_DECISION_BONIFICACION ->
                    {
                        //CAMBIO: Usar la función con timer
                        iniciarTemporizadorBonificacion()
                    }
                    else -> prepararSiguienteTurno()
                }
            }
        }
        else
        {
            //B. TOQUE INVÁLIDO (O solo consultando opciones)
            Log.w(TAG, "Toque inválido o ficha no movible.")

            if (!timerReiniciadoEnEsteTurno) {
                //Si es la primera vez que toca mal, le damos chance y reiniciamos el reloj
                Log.d(TAG_TIMER, "Reiniciando tiempo por interacción (Solo 1 vez)")
                iniciarTemporizador(esParaLanzar = false)
                timerReiniciadoEnEsteTurno = true

                //Feedback visual opcional
                //Toast.makeText(this, "Tiempo reiniciado", Toast.LENGTH_SHORT).show()
            } else {
                //Si ya lo reinició una vez, NO hacemos nada.
                //El reloj original sigue corriendo hacia su fin.
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
    //Inicia un nuevo temporizador (para lanzar o para mover)
    private fun iniciarTemporizador(esParaLanzar: Boolean)
    {
        //--- GUARDIA DE SEGURIDAD ONLINE ---
        //Jamás iniciar timer si no es mi turno en online
        if (esModoOnline && gestor.jugadorActual.color != miColor) {
            cancelarTemporizador() //Asegurar que esté apagado
            return
        }
        //-----------------------------------

        cancelarTemporizador() //Cancela el anterior y oculta el reloj

        val duracion = if(esParaLanzar) TIEMPO_PARA_LANZAR_MS else TIEMPO_PARA_MOVER_MS

        //--- Mostrar el reloj ---
        textoTemporizador.text = (duracion / 1000).toString()
        textoTemporizador.visibility = View.VISIBLE

        temporizadorTurno = object : CountDownTimer(duracion, 1000)
        {
            override fun onTick(millisUntilFinished: Long)
            {
                val segundosRestantes = (millisUntilFinished + 999) / 1000
                textoTemporizador.text = segundosRestantes.toString()
            }

            override fun onFinish()
            {
                Log.i(TAG_TIMER, "Tiempo agotado. Acción automática")
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
            //--- CORRECCIÓN: SI YA ESTÁ RESPONDIENDO, ESPERAR ---
            if (respondiendoTrivia) {
                Log.d(TAG_TIMER, "Tiempo agotado pero usuario está respondiendo. Esperando...")
                return //NO HACEMOS NADA, dejamos que verificarRespuesta termine
            }
            //----------------------------------------------------

            //Si la trivia estaba visible (y no estaba respondiendo), la cerramos
            if(overlayPreguntas.visibility == View.VISIBLE)
            {
                overlayPreguntas.visibility = View.GONE
                Toast.makeText(this, "¡Tiempo agotado! Se cerró la trivia.", Toast.LENGTH_SHORT).show()
            }

            if(layoutSeleccionPowerUp.visibility == View.VISIBLE) {
                layoutSeleccionPowerUp.visibility = View.GONE
                textoTemporizador.elevation = 0f
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
            // ... (Candados y Logs iguales) ...
            if (procesandoMovimiento) return

            // ... (Selección de ficha igual) ...
            val fichaParaMover = gestor.seleccionarFichaIA(ultimoResultadoDado)

            if(fichaParaMover != null)
            {
                procesandoMovimiento = true

                // Enviar a la red (Igual que antes)
                val soyHost = (miColor == ColorJugador.ROJO)
                val esMiFicha = (gestor.jugadorActual.color == miColor)
                val esFichaIAControlada = (soyHost && gestor.jugadorActual.esIA)

                if (esModoOnline && (esMiFicha || esFichaIAControlada)) {
                    val msg = MensajeRed(
                        accion = AccionRed.MOVER_FICHA,
                        colorJugador = gestor.jugadorActual.color,
                        idFicha = fichaParaMover.id,
                        resultadoDado = ultimoResultadoDado
                    )
                    ClienteRed.enviar(msg)
                }

                // --- CAMBIO: USAR LA NUEVA FUNCIÓN DE IA ---
                // Esto actualiza la lógica antes de animar, arreglando la sincronización.
                animarMovimientoIA(fichaParaMover, ultimoResultadoDado)
                // ------------------------------------------
            }
        }
    }

    private fun animarMovimientoIA(ficha: Ficha, valorDado: Int)
    {
        animacionJob?.cancel()

        // 1. Calcular camino visual DESDE la posición actual
        val camino = gestor.calcularCamino(ficha, valorDado)

        // 2. ACTUALIZAR LÓGICA INMEDIATAMENTE
        // El gestor ya sabrá dónde termina la ficha.
        gestor.moverFicha(ficha, valorDado)

        // Guardamos el destino final real para el aterrizaje forzoso
        val destinoFinal = ficha.posicionGlobal
        val estadoFinal = ficha.estado

        // 3. Animar visualmente
        animacionJob = lifecycleScope.launch {
            try {
                for (pos in camino) {
                    ficha.posicionGlobal = pos
                    // Ajustes visuales
                    if (pos != 0 && pos <= 52) ficha.estado = EstadoFicha.EN_JUEGO
                    if (pos > 52) ficha.estado = EstadoFicha.EN_META

                    tableroView.actualizarEstadoJuego(gestor.jugadores)
                    gestorSonido.reproducir(TipoSonido.PASO)
                    delay(200) // Velocidad rápida para IA
                }
            } finally {
                // --- ATERRIZAJE FORZOSO (CORRECCIÓN) ---
                // Aseguramos que la ficha termine donde la lógica dice, pase lo que pase.
                ficha.posicionGlobal = destinoFinal
                ficha.estado = estadoFinal
                tableroView.actualizarEstadoJuego(gestor.jugadores)
                // ---------------------------------------

                // Solo continuamos si la corrutina no fue cancelada
                if (isActive) {

                    // --- SONIDOS DE EVENTOS (Agregados para consistencia) ---
                    if (gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO) {
                        gestorSonido.reproducir(TipoSonido.VICTORIA)
                    }
                    else if (gestor.esPosicionFinalDeMeta(ficha)) {
                        gestorSonido.reproducir(TipoSonido.META)
                    }
                    else if (gestor.huboKill) {
                        gestorSonido.reproducir(TipoSonido.KILL)
                    }
                    else if (ficha.estado == EstadoFicha.EN_JUEGO && gestor.esCasillaSegura(ficha.posicionGlobal)) {
                        gestorSonido.reproducir(TipoSonido.ESPECIAL)
                    }
                    // -------------------------------------------------------

                    when(gestor.estadoJuego) {
                        EstadoJuego.JUEGO_TERMINADO -> {
                            cancelarTemporizador()
                            mostrarVictoria(gestor.jugadorActual.color)
                        }
                        EstadoJuego.ESPERANDO_LANZAMIENTO -> {
                            prepararSiguienteTurno()
                        }
                        EstadoJuego.ESPERANDO_DECISION_BONIFICACION -> {
                            Log.i(TAG, "IA cayó en casilla segura. Rechazando...")

                            // Si es IA/Host, enviamos el rechazo a la red para sincronizar
                            if (esModoOnline && miColor == ColorJugador.ROJO) {
                                lifecycleScope.launch {
                                    delay(100)
                                    val msg = MensajeRed(
                                        accion = AccionRed.RESULTADO_BONIFICACION,
                                        colorJugador = gestor.jugadorActual.color,
                                        exitoTrivia = false
                                    )
                                    ClienteRed.enviar(msg)

                                    gestor.resolverBonificacionCasillaSegura(false)
                                    prepararSiguienteTurno()
                                }
                            } else {
                                gestor.resolverBonificacionCasillaSegura(false)
                                prepararSiguienteTurno()
                            }
                        }
                        else -> prepararSiguienteTurno()
                    }
                }
            }
        }
    }

    private fun mostrarVictoria(ganadorColor: ColorJugador)
    {
        cancelarTemporizador()

        val jugadorGanador = gestor.jugadores.find { it.color == ganadorColor }
        val nombreGanador = jugadorGanador?.nombre ?: ganadorColor.name

        // --- CORRECCIÓN: GUARDAR PUNTAJES DE TODOS ---
        for (jugador in gestor.jugadores) {
            // Solo guardamos si es humano (opcional: quitar !esIA si quieres guardar bots)
            // Y si tiene algún punto acumulado
            if (!jugador.esIA) {
                var puntajeFinal = jugador.puntaje

                // Si es el ganador, sumamos el bono de victoria
                if (jugador.color == ganadorColor) {
                    puntajeFinal += 2000
                }

                // Solo guardar si hizo algo (tiene puntos)
                if (puntajeFinal > 0) {
                    val registro = Puntaje(jugador.nombre, jugador.aciertosTrivia, puntajeFinal)
                    GestorPuntajes.guardarPuntaje(this, registro)
                }
            }
        }

        // Mostrar mensaje genérico de guardado
        Toast.makeText(this, "Puntajes guardados.", Toast.LENGTH_SHORT).show()
        // ---------------------------------------------

        val texto = "¡HA GANADO $nombreGanador!"
        txtGanador.text = texto.uppercase()

        // ... (resto de colores y visibilidad igual) ...
        val colorRes = when(ganadorColor) {
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
        //Asignar listener específico para cada color
        powerupRojo.setOnClickListener { abrirMenuParaJugador(ColorJugador.ROJO) }
        powerupVerde.setOnClickListener { abrirMenuParaJugador(ColorJugador.VERDE) }
        powerupAzul.setOnClickListener { abrirMenuParaJugador(ColorJugador.AZUL) }
        powerupAmarillo.setOnClickListener { abrirMenuParaJugador(ColorJugador.AMARILLO) }

        //Configurar botón cancelar
        btnCancelarPower.setOnClickListener {
            layoutSeleccionPowerUp.visibility = View.GONE
            //Al cerrar, si es mi turno, aseguro que el timer se siga viendo normal
            textoTemporizador.elevation = 0f //Restaurar elevación normal
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
        val esSuTurno = (gestor.jugadorActual.color == colorInteresado)

        // --- NUEVA VALIDACIÓN: ESTADO DEL JUEGO ---
        // Si es mi turno, PERO ya tiré el dado (estoy esperando mover), NO puedo abrir el menú.
        if (esSuTurno && gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO) {
            Toast.makeText(this, "Ya tiraste el dado. No puedes usar Power-Ups ahora.", Toast.LENGTH_SHORT).show()
            return
        }

        val jugadorInteresado = gestor.jugadores.find { it.color == colorInteresado } ?: return

        gestorSonido.reproducir(TipoSonido.MENU)

        if(jugadorInteresado.usosPowerUpRestantes <= 0) {
            Toast.makeText(this, "No te quedan usos", Toast.LENGTH_SHORT).show()
            return
        }

        txtUsosRestantes.text = "Usos restantes: ${jugadorInteresado.usosPowerUpRestantes}"

        //Lógica del timer visible (Igual que antes)
        val esFaseLanzamiento = (gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)

        if (esSuTurno && esFaseLanzamiento) {
            textoTemporizador.visibility = View.VISIBLE
            textoTemporizador.elevation = 100f
        } else {
            textoTemporizador.elevation = 0f
        }

        // --- LÓGICA DE RESTRICCIÓN ACTUALIZADA ---

        // 1. Contar fichas que están REALMENTE en la base (disponibles para salir)
        val fichasEnBase = jugadorInteresado.fichas.count { it.estado == EstadoFicha.EN_BASE }

        // 2. Contar fichas activas en el tablero (ni en base, ni terminadas en meta final)
        //    (Para mantener tu idea de "solo si tengo pocas fichas fuera")
        val fichasActivas = jugadorInteresado.fichas.count {
            it.estado == EstadoFicha.EN_JUEGO ||
                    (it.estado == EstadoFicha.EN_META && !gestor.esPosicionFinalDeMeta(it))
        }

        // REGLA SALIDA MAESTRA:
        // - Debe tener AL MENOS UNA ficha en la base (si no, ¿qué va a sacar?).
        // - Y (opcional según tu gusto) tener pocas fichas activas fuera.
        val permitirSalidaMaestra = (fichasEnBase > 0 && fichasActivas <= 1)

        // REGLA OTROS PODERES:
        // Si TODAS están en la base, no tiene sentido usar escudos o modificadores de movimiento.
        val todasEnBase = (fichasEnBase == 4)

        // Configurar botón Salida Maestra
        habilitarBoton(btnPowerSalida, permitirSalidaMaestra)

        // Configurar botón Altos (Siempre útil para intentar salir o avanzar)
        habilitarBoton(btnPowerAltos, true)

        // Configurar el resto (Solo si hay al menos una ficha fuera para mover)
        val permitirOtros = !todasEnBase
        habilitarBoton(btnPowerBajos, permitirOtros)
        habilitarBoton(btnPowerPares, permitirOtros)
        habilitarBoton(btnPowerImpares, permitirOtros)
        habilitarBoton(btnPowerEscudo, permitirOtros)

        //-----------------------------------

        layoutSeleccionPowerUp.visibility = View.VISIBLE
        layoutSeleccionPowerUp.bringToFront()
    }

    //Función auxiliar para activar/desactivar botones visualmente
    private fun habilitarBoton(boton: Button, habilitar: Boolean) {
        boton.isEnabled = habilitar
        if (habilitar) {
            boton.alpha = 1.0f //Totalmente visible
        } else {
            boton.alpha = 0.5f //Semitransparente (efecto deshabilitado)
        }
    }

    private fun procesarSeleccion(tipo: TipoPowerUp)
    {
        layoutSeleccionPowerUp.visibility = View.GONE
        textoTemporizador.elevation = 0f

        mostrarTrivia(onExito =
            {
                val miJugadorLocal = gestor.jugadores.find { it.color == miColor }

                if (miJugadorLocal != null) {
                    val exito = gestor.activarPowerUp(miColor!!, tipo)

                    if(exito)
                    {
                        val mensaje = when(tipo)
                        {
                            TipoPowerUp.SALIDA_MAESTRA -> "¡Correcto! Salida Maestra activada."
                            TipoPowerUp.ESCUDO_TEMPORAL -> "¡Correcto! Escudo activado."
                            else -> "¡Correcto! Power-Up activado."
                        }
                        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()

                        if (esModoOnline) {
                            val msg = MensajeRed(
                                accion = AccionRed.USAR_POWERUP,
                                colorJugador = miColor,
                                tipoPowerUp = tipo
                            )
                            ClienteRed.enviar(msg)
                        }

                        // --- CORRECCIÓN AQUÍ ---

                        // Solo terminamos el turno si el escudo se usó DURANTE mi propio turno.
                        val esMiTurnoActual = (gestor.jugadorActual.color == miColor)

                        if (tipo == TipoPowerUp.ESCUDO_TEMPORAL && esMiTurnoActual) {
                            cancelarTemporizador()
                            gestor.pasarTurno()
                            prepararSiguienteTurno() // Actualizar UI para el siguiente jugador

                            Toast.makeText(this, "Turno finalizado por uso de Escudo.", Toast.LENGTH_SHORT).show()
                        }
                        else {
                            // Si no es mi turno (uso defensivo) o es otro powerup:
                            // Solo actualizamos el texto si casualmente es mi turno
                            if (esMiTurnoActual) {
                                actualizarTextoPowerUp()
                                prepararSiguienteTurno() // Refrescar botones si es necesario
                            }
                            // Si NO es mi turno, la partida sigue su curso (el rival sigue jugando)
                        }
                    }
                }
            })
    }

    private fun actualizarTextoPowerUp() {
        val jugadorActual = gestor.jugadorActual

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
    }

    private fun mostrarTrivia(onExito: () -> Unit, onFallo: (() -> Unit)? = null)
    {
        //1. Obtener pregunta random
        val pregunta = BancoPreguntas.obtenerPreguntaAleatoria()

        // GUARDAR TIEMPO DE INICIO
        inicioTriviaTime = System.currentTimeMillis()

        //2. Llenar la UI
        txtCategoria.text = pregunta.categoria.uppercase()
        txtPregunta.text = pregunta.texto

        txtFeedback.visibility = View.GONE
        btnOpcion1.isEnabled = true
        btnOpcion2.isEnabled = true
        btnOpcion3.isEnabled = true
        btnOpcion4.isEnabled = true

        //Asignamos las opciones
        btnOpcion1.text = pregunta.opciones[0]
        btnOpcion2.text = pregunta.opciones[1]
        btnOpcion3.text = pregunta.opciones[2]
        btnOpcion4.text = pregunta.opciones[3]

        //3. Configurar listeners
        btnOpcion1.setOnClickListener{ verificarRespuesta(0, pregunta, onExito, onFallo) }
        btnOpcion2.setOnClickListener{ verificarRespuesta(1, pregunta, onExito, onFallo) }
        btnOpcion3.setOnClickListener{ verificarRespuesta(2, pregunta, onExito, onFallo) }
        btnOpcion4.setOnClickListener{ verificarRespuesta(3, pregunta, onExito, onFallo) }

        //Botón Cancelar
        btnCancelarTrivia.setOnClickListener{
            respondiendoTrivia = false
            overlayPreguntas.visibility = View.GONE
            Toast.makeText(this, "Trivia cancelada", Toast.LENGTH_SHORT).show()

            if (onFallo != null) {
                onFallo()
            } else {
                //Si cancelas voluntariamente, el reloj sigue corriendo, no hace falta reiniciarlo
                //A menos que lo hubieras pausado.
            }
        }

        //4. Mostrar layout
        overlayPreguntas.visibility = View.VISIBLE
        overlayPreguntas.bringToFront()

        overlayPreguntas.bringToFront()

        //--- LÓGICA DEL TIMER PRINCIPAL ---

        val esMiTurno = if (esModoOnline) {
            gestor.jugadorActual.color == miColor
        } else {
            !gestor.jugadorActual.esIA //En local, soy yo si no es IA
        }

        if (esMiTurno) {
            //SI ES MI TURNO:
            //1. NO cancelamos el temporizador principal. Dejamos que siga corriendo.
            //2. Aseguramos que el texto se vea encima de la trivia
            textoTemporizador.visibility = View.VISIBLE
            textoTemporizador.bringToFront()
            textoTemporizador.elevation = 100f
        } else {
            //SI NO ES MI TURNO (Estoy viendo el menú mientras juega otro):
            //El timer principal no debería estar corriendo de todos modos.
            textoTemporizador.visibility = View.INVISIBLE
        }
    }

    private fun verificarRespuesta(indiceSeleccionado: Int, pregunta: Pregunta, onExito: () -> Unit, onFallo: (() -> Unit)?)
    {
        // 1. PROTECCIÓN CONTRA TIMEOUT
        // Levantamos la bandera para que si el timer llega a 0 mientras estamos aquí, no nos interrumpa.
        respondiendoTrivia = true

        // 2. BLOQUEAR BOTONES
        btnOpcion1.isEnabled = false
        btnOpcion2.isEnabled = false
        btnOpcion3.isEnabled = false
        btnOpcion4.isEnabled = false

        val esCorrecto = (indiceSeleccionado == pregunta.indiceCorrecto)

        // 3. CONFIGURAR FEEDBACK VISUAL
        txtFeedback.visibility = View.VISIBLE

        if(esCorrecto)
        {
            // --- CÁLCULO DE PUNTAJE POR TIEMPO ---
            val tiempoTardado = System.currentTimeMillis() - inicioTriviaTime

            // Fórmula: Base 100 + Bono por rapidez (max 600)
            val bonoTiempo = ((6000 - tiempoTardado) / 10).toInt().coerceAtLeast(0)
            val puntosGanados = 100 + bonoTiempo

            // Actualizar datos locales
            // Usamos 'find' con miColor para asegurar que sumamos al jugador correcto (YO)
            val yo = gestor.jugadores.find { it.color == miColor } ?: gestor.jugadorActual

            yo.aciertosTrivia++
            yo.puntaje += puntosGanados

            Log.d(TAG, "Acierto! Tiempo: ${tiempoTardado}ms. Puntos: +$puntosGanados. Total: ${yo.puntaje}")

            // --- NUEVO: SINCRONIZAR CON LA RED ---
            // Avisamos al rival de nuestro nuevo puntaje
            if (esModoOnline) {
                val msg = MensajeRed(
                    accion = AccionRed.SYNC_PUNTAJE,
                    colorJugador = miColor, // Soy yo
                    puntosAcumulados = yo.puntaje,
                    aciertosAcumulados = yo.aciertosTrivia
                )
                ClienteRed.enviar(msg)
            }
            // -------------------------------------

            // Feedback visual
            txtFeedback.text = "¡CORRECTO! +$puntosGanados pts"
            txtFeedback.setTextColor(android.graphics.Color.parseColor("#48BB78")) // Verde
        }
        else
        {
            txtFeedback.text = "INCORRECTO"
            txtFeedback.setTextColor(android.graphics.Color.parseColor("#E53E3E")) // Rojo
        }

        // 4. DELAY PARA LEER EL RESULTADO (800ms)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({

            // Al terminar el delay, ocultamos la UI
            overlayPreguntas.visibility = View.GONE
            textoTemporizador.elevation = 0f

            if(esCorrecto)
            {
                onExito()
            }
            else
            {
                // Si falló, bajamos la bandera 'respondiendoTrivia' para que el juego siga normal
                respondiendoTrivia = false

                if(onFallo != null)
                {
                    // Si es bonificación de casilla segura, esto avisará a la red (Rechazo)
                    onFallo()
                }
                else
                {
                    // Lógica default (PowerUps locales)
                    if(gestor.jugadorActual.usosPowerUpRestantes > 0)
                        gestor.jugadorActual.usosPowerUpRestantes--

                    // Mostrar respuesta correcta
                    val respuestaTexto = pregunta.opciones[pregunta.indiceCorrecto]
                    Toast.makeText(this, "Era: $respuestaTexto. Pierdes 1 uso.", android.widget.Toast.LENGTH_SHORT).show()

                    // Reactivar el turno para tirar el dado
                    val yaLanzoDado = gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO
                    iniciarTemporizador(!yaLanzoDado)
                }
            }
        }, 800)
    }

    private fun configurarBotonesBonificacion()
    {
        btnAceptarReto.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            layoutDecisionBonificacion.visibility = View.GONE
            cancelarTemporizadorBonificacion()

            mostrarTrivia(
                onExito = {
                    //1. PRIMERO AVISAR A LA RED (Mientras todavía es mi turno)
                    enviarResultadoBonificacion(true)

                    //2. LUEGO CAMBIAR TURNO LOCAL
                    gestor.resolverBonificacionCasillaSegura(true)
                    prepararSiguienteTurno()
                },
                onFallo = {
                    //1. PRIMERO AVISAR A LA RED
                    enviarResultadoBonificacion(false)

                    //2. LUEGO CAMBIAR TURNO LOCAL
                    gestor.resolverBonificacionCasillaSegura(false)
                    prepararSiguienteTurno()
                }
            )
        }

        btnRechazarReto.setOnClickListener {
            gestorSonido.reproducir(TipoSonido.MENU)
            layoutDecisionBonificacion.visibility = View.GONE
            cancelarTemporizadorBonificacion()

            //1. PRIMERO AVISAR A LA RED
            enviarResultadoBonificacion(false)

            //2. LUEGO CAMBIAR TURNO LOCAL
            gestor.resolverBonificacionCasillaSegura(false)
            prepararSiguienteTurno()
        }
    }

    //Función para animar movimientos LOCALES (Tu turno)
    private fun animarFicha(ficha: Ficha, pasosTotales: Int, alTerminar: () -> Unit)
    {
        //1. Cancelar animación anterior
        animacionJob?.cancel()

        //2. Calcular camino visual
        val camino = gestor.calcularCamino(ficha, pasosTotales)

        //3. GUARDAR ESTADO ORIGINAL (CRÍTICO)
        //Guardamos dónde estaba la ficha antes de empezar a moverla visualmente
        val posicionOriginal = ficha.posicionGlobal
        val estadoOriginal = ficha.estado

        animacionJob = lifecycleScope.launch {
            for(nuevaPos in camino) {
                //Ajustes visuales de estado
                if(ficha.estado == EstadoFicha.EN_BASE && nuevaPos != 0) ficha.estado = EstadoFicha.EN_JUEGO
                if(ficha.estado == EstadoFicha.EN_JUEGO && nuevaPos > 52) ficha.estado = EstadoFicha.EN_META

                //Mover visualmente
                ficha.posicionGlobal = nuevaPos

                tableroView.actualizarEstadoJuego(gestor.jugadores)
                gestorSonido.reproducir(TipoSonido.PASO)
                delay(300)
            }

            //--- AQUÍ ESTÁ EL ARREGLO ---
            //Antes de llamar a la lógica oficial (alTerminar -> moverFicha),
            //debemos RESTAURAR la ficha a donde estaba al principio.
            //Si no hacemos esto, moverFicha sumará: (PosiciónYaAvanzada + Dado) = Doble Movimiento.

            ficha.posicionGlobal = posicionOriginal
            ficha.estado = estadoOriginal
            //-----------------------------

            //Ahora sí, ejecutamos la lógica matemática
            alTerminar()

            //Sonidos post-movimiento
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
    // Gestiona el turno si es una IA
    private fun gestionarTurnoIA()
    {
        // Bloqueamos interacción visual
        dadoRojo.setOnClickListener(null)
        dadoVerde.setOnClickListener(null)
        dadoAzul.setOnClickListener(null)
        dadoAmarillo.setOnClickListener(null)

        // 1. CANCELAR PREVIOS (Por seguridad)
        turnoIAJob?.cancel()

        // 2. ASIGNAR EL JOB GLOBAL
        turnoIAJob = lifecycleScope.launch{
            // Tiempo de espera "pensando"
            delay(1000)

            // --- PUNTO CRÍTICO: Si se cancela aquí, no tira el dado ---

            // Guardamos el color ANTES de lanzar
            val colorIA = gestor.jugadorActual.color

            gestorSonido.reproducir(TipoSonido.DADO)
            ultimoResultadoDado = gestor.lanzarDado()

            // Enviar dado a la red (Solo Host)
            if (esModoOnline && miColor == ColorJugador.ROJO) {
                val msg = MensajeRed(
                    accion = AccionRed.LANZAR_DADO,
                    colorJugador = colorIA,
                    resultadoDado = ultimoResultadoDado
                )
                ClienteRed.enviar(msg)
            }

            // Actualizar UI del dado
            val dadoActivo = when(colorIA) {
                ColorJugador.ROJO -> dadoRojo
                ColorJugador.VERDE -> dadoVerde
                ColorJugador.AZUL -> dadoAzul
                ColorJugador.AMARILLO -> dadoAmarillo
            }

            dadoActivo.setImageResource(obtenerRecursoDado(ultimoResultadoDado))
            dadoActivo.visibility = View.VISIBLE

            delay(1500) // Tiempo para ver el resultado

            // Ejecutar movimiento
            if (gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO) {
                accionAutomaticaMover()
            }
            else {
                Log.i(TAG, "IA no tiene movimientos válidos. Pasando turno...")
                prepararSiguienteTurno()
            }
        }
    }

    private fun actualizarIconoSonido()
    {
        if (gestorSonido.sonidoHabilitado)
        {
            //Sonido Activado (Bocina normal)
            btnSonido.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            btnSonido.alpha = 1.0f
        }
        else{
            //Sonido Desactivado (Mute)
            //Nota: Si no tienes un icono de "mute", puedes usar el mismo con alpha bajo
            //o buscar android.R.drawable.ic_lock_silent_mode
            btnSonido.setImageResource(android.R.drawable.ic_lock_silent_mode)
            btnSonido.alpha = 0.5f //Lo hacemos semitransparente para indicar desactivado
        }
    }

    private fun procesarMensajeJuego(mensaje: MensajeRed) {
        //Ignoramos nuestros propios mensajes (eco)
        if (mensaje.colorJugador == miColor) return

        when(mensaje.accion)
        {
            AccionRed.LANZAR_DADO ->
            {
                watchdogRival?.cancel()
                //--- PROTECCIÓN CONTRA DESINCRONIZACIÓN ---
                //Si recibimos un dado nuevo, asumimos que cualquier decisión pendiente ya terminó.
                if (layoutDecisionBonificacion.visibility == View.VISIBLE)
                    layoutDecisionBonificacion.visibility = View.GONE

                val valor = mensaje.resultadoDado ?: return
                val colorRival = mensaje.colorJugador ?: return

                ultimoResultadoDado = valor

                //1. Identificar dado visual del rival
                val dadoRival = when(colorRival) {
                    ColorJugador.ROJO -> dadoRojo
                    ColorJugador.VERDE -> dadoVerde
                    ColorJugador.AZUL -> dadoAzul
                    ColorJugador.AMARILLO -> dadoAmarillo
            }

                //2. Ejecutar lógica en el gestor (FORZANDO EL VALOR)
                gestorSonido.reproducir(TipoSonido.DADO)
                ultimoResultadoDado = gestor.lanzarDado(valorForzado = valor)

                //3. Actualizar visualmente
                dadoRival.setImageResource(obtenerRecursoDado(valor))
                dadoRival.visibility = View.VISIBLE

                //4. Verificar si pasó el turno automáticamente (ej. sacó tres 6 o no puede mover)
                if (gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO) {
                    //El gestor ya pasó el turno internamente
                    //Pequeño delay para que se vea el dado y luego cambio
                    lifecycleScope.launch {
                        delay(1500)
                        tableroView.actualizarEstadoJuego(gestor.jugadores)
                        prepararSiguienteTurno()
                    }
                } else {
                    //Ahora espera movimiento (MOVER_FICHA)
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
                    //Limpiamos pendientes
                    resultadoBonificacionPendiente = null

                    //--- CAMBIO: Usamos la nueva función exclusiva para REMOTOS ---
                    //Esto asegura que la lógica se actualice YA, y la animación sea solo visual.
                    animarMovimientoRemoto(ficha, valorDado, colorRival)
                }
            }

            AccionRed.RESULTADO_BONIFICACION ->
            {
                val tuvoExito = mensaje.exitoTrivia ?: false

                //Esto evita que esperemos los 15 segundos si la respuesta ya llegó.
                watchdogRival?.cancel()

                if (animandoMovimientoRemoto) {
                    //Estamos ocupados moviendo la ficha. Guardamos para después.
                    Log.d(TAG, "Animando... guardando respuesta para el final.")
                    resultadoBonificacionPendiente = tuvoExito
                } else {
                    //Estamos quietos esperando (el Watchdog estaba corriendo).
                    //Aplicamos YA.
                    Log.d(TAG, "Esperando... respuesta recibida. Aplicando inmediato.")
                    aplicarDecisionRemota(tuvoExito)
                }
            }

            AccionRed.USAR_POWERUP -> {
                val colorQuien = mensaje.colorJugador ?: return
                val tipoPoder = mensaje.tipoPowerUp ?: return

                gestor.activarPowerUp(colorQuien, tipoPoder)

                val nombrePoder = tipoPoder.name.replace("_", " ")
                Toast.makeText(this, "$colorQuien activó $nombrePoder", Toast.LENGTH_SHORT).show()

                // --- CORRECCIÓN AQUÍ ---

                // Verificamos si quien usó el poder ERA el dueño del turno
                val eraTurnoDelActivador = (gestor.jugadorActual.color == colorQuien)

                // Solo si el dueño del turno activó ESCUDO, se acaba su turno.
                if (tipoPoder == TipoPowerUp.ESCUDO_TEMPORAL && eraTurnoDelActivador) {
                    gestor.pasarTurno()
                    prepararSiguienteTurno()
                }
                else {
                    // Si lo activó fuera de turno (defensa), o es otro poder, el juego sigue.
                    if (eraTurnoDelActivador) {
                        actualizarTextoPowerUp()
                    }
                }
            }

            AccionRed.SYNC_PUNTAJE -> {
                val colorRival = mensaje.colorJugador ?: return
                val nuevosPuntos = mensaje.puntosAcumulados ?: 0
                val nuevosAciertos = mensaje.aciertosAcumulados ?: 0

                // Actualizar la información del rival en mi memoria local
                val jugadorRival = gestor.jugadores.find { it.color == colorRival }
                if (jugadorRival != null) {
                    jugadorRival.puntaje = nuevosPuntos
                    jugadorRival.aciertosTrivia = nuevosAciertos
                    Log.d(TAG, "Sincronizado ${colorRival}: $nuevosPuntos pts, $nuevosAciertos aciertos")
                }
            }

            AccionRed.SOLICITAR_ESTADO -> {
                if (miColor == ColorJugador.ROJO) {
                    val msgEstado = MensajeRed(
                        accion = AccionRed.ENVIAR_ESTADO,
                        estadoJuegoCompleto = gestor.jugadores,
                        turnoActual = gestor.jugadores.indexOf(gestor.jugadorActual),
                        // --- AGREGAR ESTO ---
                        resultadoDado = ultimoResultadoDado
                        // --------------------
                    )
                    ClienteRed.enviar(msgEstado)
                }
            }

            AccionRed.ENVIAR_ESTADO -> {
                // SOY EL CLIENTE QUE SE RECONECTÓ
                val listaRemota = mensaje.estadoJuegoCompleto
                val turnoRemoto = mensaje.turnoActual ?: 0

                if (mensaje.resultadoDado != null)
                    ultimoResultadoDado = mensaje.resultadoDado

                if (listaRemota != null) {
                    Log.i(TAG, "Recibiendo estado del juego...")

                    // Cargar datos en mi gestor local
                    gestor.iniciarJuegoConJugadores(listaRemota, turnoRemoto)

                    // Actualizar UI
                    tableroView.actualizarEstadoJuego(gestor.jugadores)
                    prepararSiguienteTurno()

                    Toast.makeText(this, "¡Juego Sincronizado!", Toast.LENGTH_SHORT).show()
                }
            }

            AccionRed.JUGADOR_DESCONECTADO -> {
                val colorSeFue = mensaje.colorJugador ?: return
                val nombreSeFue = mensaje.nombreJugador ?: "Jugador"

                Toast.makeText(this, "$nombreSeFue desconectó. CPU activada.", Toast.LENGTH_LONG).show()

                // 1. Convertir a IA
                val jugador = gestor.jugadores.find { it.color == colorSeFue }
                if (jugador != null) {
                    jugador.esIA = true
                    jugador.nombre = "$nombreSeFue (CPU)"
                }

                // 2. Limpieza
                if (gestor.jugadorActual.color == colorSeFue) watchdogRival?.cancel()
                procesandoMovimiento = false

                // 3. Lógica de Host (Rescate)
                if (miColor == ColorJugador.ROJO && gestor.jugadorActual.color == colorSeFue) {
                    lifecycleScope.launch {
                        delay(2000)

                        // Corrección Anti-Retroceso:
                        // Si se fue esperando mover pero perdimos el dado (<=0), reiniciamos a LANZAR.
                        if (gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO && ultimoResultadoDado <= 0) {
                            Log.w(TAG, "Dado perdido. Reiniciando turno IA.")
                            gestor.estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
                        }

                        if (gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO) {
                            gestionarTurnoIA()
                        } else if (gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO) {
                            accionAutomaticaMover()
                        }
                    }
                }

                // 4. ACTUALIZACIÓN VISUAL SELECTIVA (CORRECCIÓN FINAL)
                tableroView.actualizarEstadoJuego(gestor.jugadores)

                if (gestor.jugadorActual.color != miColor) {
                    // CORRECCIÓN CRÍTICA:
                    // Si el estado es MOVIMIENTO, NO llamamos a prepararSiguienteTurno.
                    // ¿Por qué? Porque prepararSiguienteTurno invoca a gestionarTurnoIA (que tira dados).
                    // Nosotros NO queremos tirar dados, queremos mover la ficha pendiente (Rescue Logic).
                    // La Rescue Logic llamará a prepararSiguienteTurno cuando termine de mover.

                    if (gestor.estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO) {
                        prepararSiguienteTurno()
                    }
                }
            }

            AccionRed.CONECTAR -> {
                val colorNuevo = mensaje.colorJugador ?: return
                val nombreNuevo = mensaje.nombreJugador ?: "Jugador"

                // SI SOY HOST (ROJO)
                if (miColor == ColorJugador.ROJO) {
                    // Verificamos si el jugador ya está activo y es Humano (Reconexión rara)
                    // o si es una IA que podemos reemplazar.

                    Log.i(TAG, "Solicitud de entrada de $colorNuevo ($nombreNuevo). Poniendo en cola.")

                    // 1. Agregamos a la cola de espera
                    colaReconexion[colorNuevo] = nombreNuevo

                    // 2. Avisamos visualmente (Solo al Host)
                    Toast.makeText(this, "$nombreNuevo está esperando su turno para unirse...", Toast.LENGTH_SHORT).show()

                    // 3. VERIFICACIÓN INMEDIATA (Solo si el juego está totalmente quieto en cambio de turno)
                    // Esto ayuda si el jugador se une justo cuando ya le tocaba pero la UI no había arrancado.
                    // Pero para mayor seguridad, dejaremos que prepararSiguienteTurno lo maneje.

                    // Si el juego está esperando lanzamiento y casualmente es el turno de este color,
                    // podríamos forzar el refresco, pero es más seguro esperar al ciclo natural
                    // o forzar un chequeo si no hay nada pasando.

                    if (gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO &&
                        gestor.jugadorActual.color == colorNuevo &&
                        turnoIAJob?.isActive != true) {
                        // Si está quieto esperando que la IA tire, refrescamos para que entre ya.
                        prepararSiguienteTurno()
                    }
                }
            }

            AccionRed.ENVIAR_ESTADO -> {
                val listaRemota = mensaje.estadoJuegoCompleto
                val turnoRemoto = mensaje.turnoActual ?: 0

                if (listaRemota != null) {
                    Log.i(TAG, "Recibiendo estado del juego...")

                    // 1. Detener cualquier cosa que estuviera haciendo la CPU localmente
                    animacionJob?.cancel()
                    cancelarTemporizador()
                    bloqueoPorReconexion = false // Asegurar que el cliente nazca desbloqueado

                    // 2. Cargar datos
                    gestor.iniciarJuegoConJugadores(listaRemota, turnoRemoto)

                    if (mensaje.resultadoDado != null) {
                        ultimoResultadoDado = mensaje.resultadoDado
                    }

                    // 3. Actualizar y arrancar
                    tableroView.actualizarEstadoJuego(gestor.jugadores)
                    prepararSiguienteTurno()

                    Toast.makeText(this, "¡Sincronizado!", Toast.LENGTH_SHORT).show()
                }
            }

            AccionRed.SOLICITUD_RECONEXION -> {
                if (miColor == ColorJugador.ROJO) {
                    val color = mensaje.colorJugador ?: return
                    val nombre = mensaje.nombreJugador ?: "Jugador"

                    Log.i(TAG, "Solicitud de reconexión recibida para $color.")
                    Toast.makeText(this, "$nombre recuperando conexión...", Toast.LENGTH_SHORT).show()

                    // 1. Poner en la cola
                    colaReconexion[color] = nombre

                    // 2. VERIFICACIÓN INMEDIATA (INTERRUPCIÓN DE IA)
                    // Si el que se quiere conectar es JUSTAMENTE el que tiene el turno ahora
                    // y actualmente es una IA (bot de reemplazo)...
                    if (gestor.jugadorActual.color == color && gestor.jugadorActual.esIA) {
                        Log.w(TAG, "¡Interrumpiendo a la IA para devolver el turno al Humano!")

                        // A. MATAR A LA IA
                        turnoIAJob?.cancel() // Detiene el delay, el tiro o el movimiento

                        // B. Procesar el ingreso inmediatamente
                        verificarReconexionPendiente()
                    }
                    // Si no es su turno, verificarReconexionPendiente() se llamará naturalmente
                    // cuando el turno actual termine (en prepararSiguienteTurno).
                }
            }

            AccionRed.PARTIDA_TERMINADA_POR_HOST -> {
                // SOLO LOS CLIENTES RECIBEN ESTO
                Toast.makeText(this, "El Host ha terminado la partida.", Toast.LENGTH_LONG).show()

                // Cerramos la actividad para volver al menú principal
                finish()
            }

            else -> {}
        }
    }

    private fun verificarReconexionPendiente() {
        if (miColor != ColorJugador.ROJO) return // Solo Host

        val turnoActualColor = gestor.jugadorActual.color
        val nombrePendiente = colaReconexion[turnoActualColor]

        if (nombrePendiente != null) {
            // ¡ES SU TURNO! Lo dejamos entrar.
            Log.i(TAG, "Aceptando reconexión de $turnoActualColor ($nombrePendiente) en su turno.")

            // 1. Quitar de la cola
            colaReconexion.remove(turnoActualColor)

            // 2. Actualizar localmente (Quitar IA)
            val jugador = gestor.jugadores.find { it.color == turnoActualColor }
            if (jugador != null) {
                jugador.esIA = false
                jugador.nombre = nombrePendiente
            }

            // 3. ENVIAR ESTADO AL JUGADOR (Esto es lo que lo desbloquea)
            // Usamos el canal global, el cliente filtrará si es para él
            val msgEstado = MensajeRed(
                accion = AccionRed.ENVIAR_ESTADO,
                estadoJuegoCompleto = gestor.jugadores,
                turnoActual = gestor.jugadores.indexOf(gestor.jugadorActual),
                resultadoDado = ultimoResultadoDado // El dado que la IA sacó (si ya tiró) o 0
            )
            ClienteRed.enviar(msgEstado)

            // 4. Refrescar UI
            // Al quitar el flag esIA, prepararSiguienteTurno detendrá la lógica automática del Host
            // y dejará al jugador real jugar.
            tableroView.actualizarEstadoJuego(gestor.jugadores)
            prepararSiguienteTurno()

            Toast.makeText(this, "$nombrePendiente se unió a la partida.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun animarMovimientoVisual(ficha: Ficha, camino: List<Int>, colorRival: ColorJugador) {
        animacionJob?.cancel()

        animacionJob = lifecycleScope.launch {
            //La ficha ya está lógicamente en su destino, pero visualmente la movemos
            //por el camino que calculamos antes.

            for (pos in camino) {
                //Forzamos la posición visual temporalmente
                //Nota: Esto es un "hack" visual. La ficha real ya tiene el valor final en el objeto Jugador.
                //Pero al modificar 'ficha.posicionGlobal' aquí, estamos alterando el objeto vivo.
                //Como ya actualizamos la lógica, esto solo "recorre" el camino hasta llegar al valor que ya tiene.

                ficha.posicionGlobal = pos
                //(Ajustes de estado visual si sale de base/entra meta)
                if(pos != 0 && pos <= 52) ficha.estado = EstadoFicha.EN_JUEGO
                if(pos > 52) ficha.estado = EstadoFicha.EN_META

                tableroView.actualizarEstadoJuego(gestor.jugadores)
                gestorSonido.reproducir(TipoSonido.PASO)
                delay(300)
            }

            //Al terminar, verificamos estado del juego (Victoria, etc)
            //Usamos la lógica post-movimiento
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

        // 1. Calcular camino
        val camino = gestor.calcularCamino(ficha, valorDado)

        // 2. Actualizar lógica inmediata
        gestor.moverFicha(ficha, valorDado)

        // Guardamos el destino final real
        val destinoFinal = ficha.posicionGlobal
        val estadoFinal = ficha.estado

        animacionJob = lifecycleScope.launch {
            try {
                for (pos in camino) {
                    ficha.posicionGlobal = pos
                    if (pos != 0 && pos <= 52) ficha.estado = EstadoFicha.EN_JUEGO
                    if (pos > 52) ficha.estado = EstadoFicha.EN_META

                    tableroView.actualizarEstadoJuego(gestor.jugadores)
                    gestorSonido.reproducir(TipoSonido.PASO)
                    delay(200)
                }
            } finally {
                // --- ATERRIZAJE FORZOSO ---
                ficha.posicionGlobal = destinoFinal
                ficha.estado = estadoFinal
                tableroView.actualizarEstadoJuego(gestor.jugadores)
                // --------------------------

                // --- CORRECCIÓN AQUÍ: Usar 'isActive' directamente ---
                // Verificamos si la corrutina sigue activa (no fue cancelada)
                if (isActive) {
                    if (gestor.estadoJuego == EstadoJuego.ESPERANDO_DECISION_BONIFICACION) {
                        if (resultadoBonificacionPendiente != null) {
                            aplicarDecisionRemota(resultadoBonificacionPendiente!!)
                            resultadoBonificacionPendiente = null
                        } else {
                            iniciarWatchdogRival()
                        }
                    } else if (gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO) {
                        mostrarVictoria(colorRival)
                    } else {
                        prepararSiguienteTurno()
                    }
                }
            }
        }
    }

    private fun iniciarTemporizadorBonificacion() {
        cancelarTemporizador() //Cancelar timer de turno principal

        //Mostrar el diálogo
        layoutDecisionBonificacion.visibility = View.VISIBLE
        layoutDecisionBonificacion.bringToFront()

        Log.d(TAG_TIMER, "Iniciando timer de decisión (4s)")

        //Arrancamos cuenta de 4 segundos
        temporizadorBonificacion = object : CountDownTimer(4000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                //Tic tac...
            }

            override fun onFinish()
            {
                Log.i(TAG_TIMER, "Tiempo de decisión agotado. Rechazando bonificación.")

                layoutDecisionBonificacion.visibility = View.GONE
                Toast.makeText(this@JuegoActivity, "Tiempo agotado.", Toast.LENGTH_SHORT).show()

                //1. Aplicar lógica local
                gestor.resolverBonificacionCasillaSegura(false)

                //2. ENVIAR A LA RED (CRÍTICO)
                //Si no enviamos esto, el rival se queda esperando "RESULTADO_BONIFICACION" para siempre
                enviarResultadoBonificacion(false)

                //3. Continuar juego
                prepararSiguienteTurno()
            }
        }.start()
    }

    private fun cancelarTemporizadorBonificacion() {
        temporizadorBonificacion?.cancel()
    }

    //Función auxiliar para enviar el resultado a la red
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
            //Si fue automático/rechazo, a veces es mejor no mostrar nada para fluidez,
            //o un mensaje discreto.
            //Toast.makeText(this, "Oponente continúa turno normal.", Toast.LENGTH_SHORT).show()
        }

        prepararSiguienteTurno()
    }

    private fun iniciarWatchdogRival() {
        watchdogRival?.cancel()

        //CAMBIO: Bajamos de 6000 a 3000 (3 segundos)
        //Esto es suficiente para esperar lag, pero si falla, desbloquea rápido.
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

    private fun mostrarDialogoConfirmarSalida() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("¿Salir de la partida?")

        // Mensaje diferente para Host y Cliente
        if (esModoOnline && miColor == ColorJugador.ROJO) {
            builder.setMessage("Eres el Host. Si sales, la partida terminará para todos los jugadores.")
        } else {
            builder.setMessage("Si sales, el CPU jugará por ti y podrás reconectarte hasta que sea tu turno.")
        }

        builder.setPositiveButton("Salir") { dialog, _ ->
            dialog.dismiss()
            ejecutarSalida()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    private fun ejecutarSalida() {
        if (esModoOnline) {
            if (miColor == ColorJugador.ROJO) {
                // --- SOY HOST: AVISAR Y CERRAR SERVIDOR ---
                lifecycleScope.launch(Dispatchers.IO) {
                    // 1. Avisar a los clientes
                    val msg = MensajeRed(accion = AccionRed.PARTIDA_TERMINADA_POR_HOST)
                    ServidorBrainchis.broadcast(msg, null)

                    // Pequeña pausa para asegurar que el mensaje salga
                    delay(100)

                    // 2. Apagar el servidor
                    ServidorBrainchis.detener()

                    // 3. Cerrar mi actividad
                    withContext(Dispatchers.Main) {
                        finish()
                    }
                }
            } else {
                // --- SOY CLIENTE: SOLO DESCONECTARME ---
                // El onDestroy se encargará de cerrar el socket del cliente
                finish()
            }
        } else {
            // Modo Offline
            finish()
        }
    }
}