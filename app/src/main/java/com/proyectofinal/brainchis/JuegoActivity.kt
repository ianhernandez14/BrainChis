package com.proyectofinal.brainchis

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
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
    
    //Timer para decidir si tomar la bonificación al caer en casilla segura
    private var temporizadorBonificacion: CountDownTimer? = null
    
    //Variable para guardar la decisión del rival si llega mientras la ficha hace su animación
    private var resultadoBonificacionPendiente: Boolean? = null
    
    //Timer de seguridad por si el rival se desconecta o el mensaje se pierde
    private var watchdogRival: CountDownTimer? = null
    
    //Trabajo de animación para las fichas
    private var animacionJob: Job? = null
    
    //Bandera para saber si se están moviendo fichas visualmente
    private var animandoMovimientoRemoto: Boolean = false
    
    //Bandera para evitar 2 movimientos en un solo turno
    private var procesandoMovimiento: Boolean = false
    
    //Bandera para evitar que el timer corte el flujo si ya se respondió la trivia
    private var respondiendoTrivia: Boolean = false
    
    //Cola de jugadores esperando reconectarse: Mapa [Color -> Nombre]
    private val colaReconexion = mutableMapOf<ColorJugador, String>()
    
    //Referencia al proceso de pensamiento de la IA para poder cancelarlo si el jugador vuelve
    //a conectarse
    private var turnoIAJob: Job? = null
    
    //UI Pausa
    private lateinit var btnPausa: android.widget.ImageButton
    private lateinit var layoutPausa: View
    private lateinit var btnReanudarPausa: Button
    private lateinit var btnMenuPausa: Button
    
    //Acelerómetro para poder girar el dado al agitar el celular
    private lateinit var gestorAcelerometro: GestorAcelerometro
    
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_juego)
        
        //Ajustar el padding para las barras del sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)){ v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        //--- INICIALIZAR VISTAS Y OBJETOS ---
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
        
        //Layout powerup
        layoutSeleccionPowerUp = findViewById(R.id.layoutSeleccionPowerUp)
        layoutSeleccionPowerUp.elevation = 500f
        txtUsosRestantes = findViewById(R.id.txtUsosRestantes)
        btnPowerPares = findViewById(R.id.btnPowerPares)
        btnPowerImpares = findViewById(R.id.btnPowerImpares)
        btnPowerAltos = findViewById(R.id.btnPowerAltos)
        btnPowerBajos = findViewById(R.id.btnPowerBajos)
        btnPowerSalida = findViewById(R.id.btnPowerSalida)
        btnPowerEscudo = findViewById(R.id.btnPowerEscudo)
        btnCancelarPower = findViewById(R.id.btnCancelarPower)
        txtPowerUpActivo = findViewById(R.id.txtPowerUpActivo)
        
        //Layout trivia
        overlayPreguntas = findViewById(R.id.overlayPreguntas)
        txtPregunta = findViewById(R.id.txtPregunta)
        btnOpcion1 = findViewById(R.id.btnOpcion1)
        btnOpcion2 = findViewById(R.id.btnOpcion2)
        btnOpcion3 = findViewById(R.id.btnOpcion3)
        btnOpcion4 = findViewById(R.id.btnOpcion4)
        btnCancelarTrivia = findViewById(R.id.btnCancelar)
        txtFeedback = findViewById(R.id.txtFeedback)
        txtCategoria = findViewById(R.id.txtCategoria)
        
        //Layout bonificación
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
            finish() //Cerrar la actividad y volver al menú principal
        }
        
        btnMenuVic.setOnClickListener{
            finish() //Cerrar la actividad y volver al menú anterior
        }
        
        //Inicializar Vistas Pausa
        btnPausa = findViewById(R.id.btnPausa)
        layoutPausa =
            findViewById<View>(R.id.panelPausa).parent as View //Obtenemr el RelativeLayout padre
        layoutPausa.visibility = View.GONE
        layoutPausa.elevation = 500f
        
        btnReanudarPausa = findViewById(R.id.btnReanudar)
        btnMenuPausa = findViewById(R.id.btnMenuPrincipal)
        
        //Configurar visibilidad del botón de pausa
        //Si es online y no soy rojo (el host), ocultar
        if(esModoOnline && miColor != ColorJugador.ROJO)
            btnPausa.visibility = View.GONE
        else
            btnPausa.visibility = View.VISIBLE
        
        //Listener del botón de pausa para abrir el menú (el overlay)
        btnPausa.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            layoutPausa.visibility = View.VISIBLE
            layoutPausa.bringToFront()
        }
        
        //Listener para la opción de reanudar
        btnReanudarPausa.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            layoutPausa.visibility = View.GONE
        }
        
        //Listener para la opción de menú principal
        btnMenuPausa.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            mostrarDialogoSalir()
        }
        
        //--- CONFIGURAR JUGADORES ---
        
        //Leer lo que envió MainActivity con el intent
        val cantidadJugadores = intent.getIntExtra("CANTIDAD_JUGADORES", 4)
        val humanoJuega = intent.getBooleanExtra("HUMANO_JUEGA", true)
        
        //DETECTAR ONLINE
        esModoOnline = intent.getBooleanExtra("MODO_ONLINE", false)
        val miColorStr = intent.getStringExtra("MI_COLOR")
        
        if(miColorStr != null)
            miColor = ColorJugador.valueOf(miColorStr)
        
        val listaJugadores = mutableListOf<Jugador>()
        
        if(esModoOnline)
        {
            //--- MODO ONLINE ---
            val coloresOrdenados = listOf(
                ColorJugador.ROJO,
                ColorJugador.AMARILLO,
                ColorJugador.VERDE,
                ColorJugador.AZUL
            )
            
            val numHumanos = intent.getIntExtra("CANTIDAD_HUMANOS_REALES", cantidadJugadores)
            
            //--- RECIBIR NOMBRES DEL LOBBY ---
            val nombresRecibidos = intent.getStringArrayListExtra("LISTA_NOMBRES")
            val coloresRecibidos = intent.getStringArrayListExtra("LISTA_COLORES")
            
            for(i in 0 until cantidadJugadores)
            {
                val color = coloresOrdenados[i]
                val esBot = (i >= numHumanos)
                
                var nombreJugador = if(esBot) "CPU ${color.name}" else "Jugador ${color.name}"
                
                //Si no es bot, buscar si se tiene su nombre real en la lista que mandó el Main
                if(!esBot && nombresRecibidos != null && coloresRecibidos != null)
                {
                    val indiceEnLista = coloresRecibidos.indexOf(color.name)
                    
                    if(indiceEnLista != -1)
                        nombreJugador = nombresRecibidos[indiceEnLista]
                }
                
                listaJugadores.add(Jugador(color, esIA = esBot, nombre = nombreJugador))
            }
            
            //Toast.makeText(this, "Partida Online: Eres $miColor", Toast.LENGTH_LONG).show()
            
        }
        else
        {
            //--- MODO LOCAL ---
            if(cantidadJugadores == 2)
            {
                val nombre1 = intent.getStringExtra("NOMBRE_JUGADOR") ?: "Jugador"
                listaJugadores.add(
                    Jugador(
                        ColorJugador.ROJO,
                        esIA = !humanoJuega,
                        nombre = nombre1
                    )
                )
                listaJugadores.add(
                    Jugador(
                        ColorJugador.AMARILLO,
                        esIA = true,
                        nombre = "CPU Amarillo"
                    )
                )
            }
            else
            {
                listaJugadores.add(Jugador(ColorJugador.ROJO, esIA = !humanoJuega))
                listaJugadores.add(Jugador(ColorJugador.VERDE, esIA = true))
                listaJugadores.add(Jugador(ColorJugador.AMARILLO, esIA = true))
                if(cantidadJugadores == 4) listaJugadores.add(
                    Jugador(
                        ColorJugador.AZUL,
                        esIA = true
                    )
                )
            }
        }
        
        //--- LEER TURNO INICIAL ---
        val turnoInicial = intent.getIntExtra("TURNO_INICIAL", 0)
        
        //Pasar el turno inicial al gestor
        gestor.iniciarJuegoConJugadores(listaJugadores, turnoInicial)
        
        //--- MODO ONLINE ---
        val esOnline = intent.getBooleanExtra("MODO_ONLINE", false)
        //Leer el flag que mandó MainActivity cuando se recibe ENVIAR_ESTADO
        val esReconexion = intent.getBooleanExtra("ES_RECONEXION", false)
        
        if(esOnline)
        {
            //CAMBIAR EL LISTENER
            ClienteRed.listener = { mensaje ->
                runOnUiThread {
                    procesarMensajeJuego(mensaje)
                }
            }
            
            //SI ES RECONEXIÓN, PEDIR EL ESTADO AL HOST
            if(esReconexion && miColor != ColorJugador.ROJO)
            {
                //El rojo es el Host, él ya tiene el estado. Los demás solo lo piden
                val msg = MensajeRed(accion = AccionRed.SOLICITAR_ESTADO, colorJugador = miColor)
                ClienteRed.enviar(msg)
            }
        }
        
        tableroView.actualizarEstadoJuego(gestor.jugadores)
        prepararSiguienteTurno()
        
        //Este bloque es solo para hacer pruebas. Tuve un bug que me tardó mucho en solucionar y
        //mejor dejé este bloque para hacerlo más rápido
//        try
//        {
//            //ROJO: justo antes de su meta (pos 51)
//            var fichaRojaPrueba =
//                gestor.jugadores.find { it.color == ColorJugador.ROJO }?.fichas?.get(0)
//            fichaRojaPrueba?.estado = EstadoFicha.EN_META
//            fichaRojaPrueba?.posicionGlobal = 58
//
//            fichaRojaPrueba =
//                gestor.jugadores.find { it.color == ColorJugador.ROJO }?.fichas?.get(1)
//            fichaRojaPrueba?.estado = EstadoFicha.EN_META
//            fichaRojaPrueba?.posicionGlobal = 58
//
//            fichaRojaPrueba =
//                gestor.jugadores.find { it.color == ColorJugador.ROJO }?.fichas?.get(2)
//            fichaRojaPrueba?.estado = EstadoFicha.EN_META
//            fichaRojaPrueba?.posicionGlobal = 58
//
//            fichaRojaPrueba =
//                gestor.jugadores.find { it.color == ColorJugador.ROJO }?.fichas?.get(3)
//            fichaRojaPrueba?.estado = EstadoFicha.EN_META
//            fichaRojaPrueba?.posicionGlobal = 57
//
//            //VERDE: justo antes de su meta (pos 12)
//            var fichaVerdePrueba =
//                gestor.jugadores.find { it.color == ColorJugador.AMARILLO }?.fichas?.get(0)
//            fichaVerdePrueba?.estado = EstadoFicha.EN_JUEGO
//            fichaVerdePrueba?.posicionGlobal = 5
//
//            fichaVerdePrueba =
//                gestor.jugadores.find { it.color == ColorJugador.AMARILLO }?.fichas?.get(1)
//            fichaVerdePrueba?.estado = EstadoFicha.EN_JUEGO
//            fichaVerdePrueba?.posicionGlobal = 6
//
//            fichaVerdePrueba =
//                gestor.jugadores.find { it.color == ColorJugador.AMARILLO }?.fichas?.get(2)
//            fichaVerdePrueba?.estado = EstadoFicha.EN_JUEGO
//            fichaVerdePrueba?.posicionGlobal = 7
//
//            fichaVerdePrueba =
//                gestor.jugadores.find { it.color == ColorJugador.AMARILLO }?.fichas?.get(3)
//            fichaVerdePrueba?.estado = EstadoFicha.EN_JUEGO
//            fichaVerdePrueba?.posicionGlobal = 8
//
//            //AZUL: justo antes de su meta (pos 38)
//            val fichaAzulPrueba =
//                gestor.jugadores.find { it.color == ColorJugador.AZUL }?.fichas?.get(0)
//            fichaAzulPrueba?.estado = EstadoFicha.EN_JUEGO
//            fichaAzulPrueba?.posicionGlobal = 38
//
//            //AMARILLO: justo antes de su meta (pos 25)
//            val fichaAmarillaPrueba =
//                gestor.jugadores.find { it.color == ColorJugador.AMARILLO }?.fichas?.get(0)
//            fichaAmarillaPrueba?.estado = EstadoFicha.EN_JUEGO
//            fichaAmarillaPrueba?.posicionGlobal = 25
//
//        }
//        catch(e: Exception)
//        {
//            e.printStackTrace()
//        }
        
        //Esto es para detectar si se presionó el botón hacia atrás del dispositivo.
        //Si es así, entonces mostrar el diálogo de confirmar salida
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true)
        {
            override fun handleOnBackPressed(){
                mostrarDialogoConfirmarSalida()
            }
        })
        
        //Inicializar Acelerómetro
        gestorAcelerometro = GestorAcelerometro(this)
        {
            //Obtener los valores actuales
            val estadoJuego = gestor.estadoJuego
            val jugadorActual = gestor.jugadorActual.color
            val soyYo = miColor
            
//            Log.d("ParchisSensor", "--- SHAKE DETECTADO ---")
//            Log.d("ParchisSensor", "Modo Online: $esModoOnline")
//            Log.d("ParchisSensor", "Mi Color: $soyYo")
//            Log.d("ParchisSensor", "Turno Actual: $jugadorActual")
//            Log.d("ParchisSensor", "Estado Juego: $estadoJuego")
            
            //Calcular condiciones para validar el shake
            val esMiTurno =
                if(esModoOnline) (jugadorActual == soyYo) else (!gestor.jugadorActual.esIA)
            val esMomentoLanzar = (estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)
            val ventanasCerradas = overlayPreguntas.visibility != View.VISIBLE &&
                    layoutSeleccionPowerUp.visibility != View.VISIBLE
            
            if(esMiTurno && esMomentoLanzar && ventanasCerradas)
            {
                cancelarTemporizador()
                realizarLanzamientoHumano()
            }
        }
    }
    
    override fun onPause()
    {
        super.onPause()
        Log.d(TAG, "Aplicación en pausa - Deteniendo temporizadores y audio")
        
        cancelarTemporizador()
        gestorSonido.pausarTodo()
        gestorAcelerometro.detener()
    }
    
    override fun onResume()
    {
        super.onResume()
        Log.d(TAG, "Aplicación reanudada - Restaurando estado")
        
        gestorSonido.reanudarTodo()
        gestorAcelerometro.iniciar()
        
        val hayVentanaAbierta = overlayPreguntas.visibility == View.VISIBLE ||
                layoutDecisionBonificacion.visibility == View.VISIBLE ||
                layoutSeleccionPowerUp.visibility == View.VISIBLE
        
        if(!hayVentanaAbierta)
        {
            when(gestor.estadoJuego)
            {
                EstadoJuego.ESPERANDO_LANZAMIENTO ->
                {
                    //Iniciar timer visual (6s)
                    iniciarTemporizador(esParaLanzar = true)
                    //Asegurarse que tenga el color del turno actual
                    actualizarColorTemporizador(gestor.jugadorActual.color)
                }
                
                EstadoJuego.ESPERANDO_MOVIMIENTO ->
                {
                    //Iniciar timer visual (8s)
                    iniciarTemporizador(esParaLanzar = false)
                    actualizarColorTemporizador(gestor.jugadorActual.color)
                }
                
                else ->
                {
                    //Si es juego terminado o decisión, ocultar el timer principal
                    textoTemporizador.visibility = View.INVISIBLE
                }
            }
        }
    }
    
    override fun onDestroy()
    {
        super.onDestroy()
        gestorSonido.liberar()
        
        //Si soy host, matar el servidor al salir de la partida
        if(esModoOnline && miColor == ColorJugador.ROJO)
            ServidorBrainchis.detener()
        
        //Cerrar conexión cliente
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
        cancelarTemporizador()
        tableroView.actualizarFichasMovibles(emptyList())
        
        //LIMPIEZA DE BANDERAS
        procesandoMovimiento = false
        timerReiniciadoEnEsteTurno = false
        respondiendoTrivia = false
        
        //Ocultar todos los dados y powerups primero
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
        
        val jugadorActual = gestor.jugadorActual
        val colorTurno = jugadorActual.color
        
        val mapaBotones = mapOf(
            ColorJugador.ROJO to powerupRojo,
            ColorJugador.VERDE to powerupVerde,
            ColorJugador.AZUL to powerupAzul,
            ColorJugador.AMARILLO to powerupAmarillo
        )
        
        for((color, boton) in mapaBotones)
        {
            if(esModoOnline && color != miColor)
            {
                boton.visibility = View.INVISIBLE
                continue
            }
            
            val jugador = gestor.jugadores.find { it.color == color }
            if(jugador != null)
            {
                if(!jugador.esIA && jugador.usosPowerUpRestantes > 0)
                {
                    boton.visibility = View.VISIBLE
                }
                else
                {
                    boton.visibility = View.INVISIBLE
                }
            }
            else
            {
                boton.visibility = View.INVISIBLE
            }
        }
        
        actualizarTextoPowerUp()
        
        actualizarColorTemporizador(gestor.jugadorActual.color)
        
        val dadoActivo = when(colorTurno)
        {
            ColorJugador.ROJO -> dadoRojo
            ColorJugador.VERDE -> dadoVerde
            ColorJugador.AZUL -> dadoAzul
            ColorJugador.AMARILLO -> dadoAmarillo
        }
        
        //---  REVISIÓN DE COLA DE ESPERA (HOST) ---
        if(esModoOnline && miColor == ColorJugador.ROJO)
        {
            val nombrePendiente = colaReconexion[jugadorActual.color]
            if(nombrePendiente != null)
            {
                Log.i(TAG, "¡Es turno de $nombrePendiente! Aceptando conexión.")
                
                colaReconexion.remove(jugadorActual.color)
                jugadorActual.esIA = false
                jugadorActual.nombre = nombrePendiente
                
                lifecycleScope.launch{
                    delay(200)
                    val msgEstado = MensajeRed(
                        accion = AccionRed.ENVIAR_ESTADO,
                        estadoJuegoCompleto = gestor.jugadores,
                        turnoActual = gestor.jugadores.indexOf(jugadorActual),
                        resultadoDado = ultimoResultadoDado
                    )
                    ClienteRed.enviar(msgEstado)
                }
                Toast.makeText(this, "$nombrePendiente se ha unido.",
                    Toast.LENGTH_SHORT).show()
            }
        }
        
        if(esModoOnline)
        {
            //MODO ONLINE
            
            //Iniciar timer visual para todos
            iniciarTemporizador(esParaLanzar = true)
            
            val soyHost = (miColor == ColorJugador.ROJO)
            
            if(jugadorActual.color == miColor)
            {
                //--- ES MI TURNO ---
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                dadoActivo.setOnClickListener{
                    cancelarTemporizador()
                    realizarLanzamientoHumano()
                }
            }
            else if(jugadorActual.esIA && soyHost)
            {
                //--- ES TURNO DE CPU Y YO SOY EL HOST --
                //Yo tomo el control para moverla rápido (1s)
                Log.i(TAG, "Turno de CPU (${jugadorActual.color}) gestionado por Host.")
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                
                //No cancelar el timer inmediatamente
                gestionarTurnoIA()
            }
            else
            {
                //--- ES TURNO DEL RIVAL (Humano o CPU) ---
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                dadoActivo.setOnClickListener(null)
            }
        }
        else
        {
            //MODO LOCAL
            if(jugadorActual.esIA)
            {
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                iniciarTemporizador(esParaLanzar = true)
                gestionarTurnoIA()
            }
            else
            {
                dadoActivo.visibility = View.VISIBLE
                dadoActivo.setImageResource(R.drawable.dado_signo)
                dadoActivo.setOnClickListener{
                    cancelarTemporizador()
                    realizarLanzamientoHumano()
                }
                iniciarTemporizador(esParaLanzar = true)
            }
        }
    }
    
    //Esta función maneja el tiro del jugador humano, ya sea por clic o por tiempo agotado
    private fun realizarLanzamientoHumano()
    {
        //Bloquear los dados visualmente para que no pueda dar clic otra vez
        dadoRojo.setOnClickListener(null)
        dadoVerde.setOnClickListener(null)
        dadoAzul.setOnClickListener(null)
        dadoAmarillo.setOnClickListener(null)
        
        lifecycleScope.launch{
            //Guardar quién tira ANTES de lanzar el dado
            val colorJugadorLanzador = gestor.jugadorActual.color
            
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
            
            //Mostrar el resultado visualmente en el dado correcto (usando la variable guardada)
            val dadoActivo = when(colorJugadorLanzador)
            {
                ColorJugador.ROJO -> dadoRojo
                ColorJugador.VERDE -> dadoVerde
                ColorJugador.AZUL -> dadoAzul
                ColorJugador.AMARILLO -> dadoAmarillo
            }
            
            dadoActivo.setImageResource(obtenerRecursoDado(ultimoResultadoDado))
            
            if(gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)
            {
                delay(800)
                
                tableroView.actualizarEstadoJuego(gestor.jugadores)
                prepararSiguienteTurno()
            }
            else
            {
                val movimientos = gestor.obtenerMovimientosPosibles(ultimoResultadoDado)
                tableroView.actualizarFichasMovibles(movimientos)
                
                if(movimientos.isNotEmpty())
                    gestorSonido.reproducir(TipoSonido.OPCION)
                
                //Iniciar el timer para mover ficha
                iniciarTemporizador(esParaLanzar = false)
            }
        }
    }
    
    override fun onCasillaTocada(col: Int, fila: Int)
    {
        //Validar que se esté esperando un movimiento
        if(gestor.estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO)
            return
        
        //--- CANDADO DE SEGURIDAD ---
        if(procesandoMovimiento)
        {
            Log.w(TAG, "Intento de movimiento bloqueado: Ya se está procesando uno.")
            return
        }
        
        //Validar turno en ONLINE:
        //Si estamos en línea y el color del jugador actual NO es mi color,
        //significa que es el turno del rival. No debo poder tocar nada del otro jugador
        if(esModoOnline && gestor.jugadorActual.color != miColor)
        {
            Log.w(TAG, "Toque ignorado: No es tu turno.")
            return
        }
        
        //Obtener movimientos posibles
        val movimientosPosibles = gestor.obtenerMovimientosPosibles(ultimoResultadoDado)
        if(movimientosPosibles.isEmpty())
            return
        
        val posGlobalTocada = tableroView.obtenerPosicionGlobalDeCasilla(col, fila)
        var fichaParaMover: Ficha? = null
        
        if(posGlobalTocada != -1)
            fichaParaMover = movimientosPosibles.find { it.posicionGlobal == posGlobalTocada }
        else
        {
            if(ultimoResultadoDado == 6)
            {
                val colorBaseTocada = tableroView.obtenerColorBaseTocada(col, fila)
             
                if(colorBaseTocada == gestor.jugadorActual.color)
                    fichaParaMover = movimientosPosibles.find { it.estado == EstadoFicha.EN_BASE }
            }
        }
        
        //--- LÓGICA DE MOVIMIENTO Y TIMER ---
        if(fichaParaMover != null)
        {
            procesandoMovimiento = true
            
            //A. MOVIMIENTO VÁLIDO
            //Cancelar el timer porque el jugador ya decidió
            cancelarTemporizador()
            
            //--- ENVIAR MOVIMIENTO A LA RED ---
            if(esModoOnline && gestor.jugadorActual.color == miColor)
            {
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
                    EstadoJuego.JUEGO_TERMINADO -> mostrarVictoria(
                        gestor.jugadorActual.color)
                    
                    EstadoJuego.ESPERANDO_DECISION_BONIFICACION ->{
                        iniciarTemporizadorBonificacion()
                    }
                    
                    else -> prepararSiguienteTurno()
                }
            }
        }
        else
        {
            //B. TOQUE INVÁLIDO
            Log.w(TAG, "Toque inválido o ficha no movible.")
            
            if(!timerReiniciadoEnEsteTurno)
            {
                //Si es la primera vez que toca mal, le damos chance y reiniciamos el reloj
                Log.d(TAG_TIMER, "Reiniciando tiempo por interacción (Solo 1 vez)")
                iniciarTemporizador(esParaLanzar = false)
                timerReiniciadoEnEsteTurno = true
                
                //--- AVISAR A LA RED ---
                if(esModoOnline && gestor.jugadorActual.color == miColor)
                {
                    val msg = MensajeRed(
                        accion = AccionRed.SINCRONIZAR_TIMER,
                        colorJugador = miColor,
                        tiempoTimer = 8000L, //Tiempo para mover
                        esParaLanzar = false
                    )
                    ClienteRed.enviar(msg)
                }
            }
            else
            {
                //Si ya lo reinició una vez, no hacer nada.
                //El reloj original sigue corriendo hacia su fin.
                Log.d(TAG_TIMER, "El tiempo ya fue reiniciado una vez.")
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
    private fun iniciarTemporizador(esParaLanzar: Boolean, tiempoPersonalizado: Long? = null)
    {
        cancelarTemporizador()
        
        val duracion =
            tiempoPersonalizado ?: if(esParaLanzar) TIEMPO_PARA_LANZAR_MS else TIEMPO_PARA_MOVER_MS
        
        textoTemporizador.text = (duracion / 1000).toString()
        textoTemporizador.visibility = View.VISIBLE
        
        actualizarColorTemporizador(gestor.jugadorActual.color)
        
        temporizadorTurno = object : CountDownTimer(duracion, 1000)
        {
            override fun onTick(millisUntilFinished: Long)
            {
                val segundosRestantes = (millisUntilFinished + 999) / 1000
                textoTemporizador.text = segundosRestantes.toString()
            }
            
            override fun onFinish()
            {
                Log.i(TAG_TIMER, "Tiempo agotado.")
                textoTemporizador.visibility = View.INVISIBLE
                
                if(esModoOnline)
                {
                    val soyHost = (miColor == ColorJugador.ROJO)
                    val esMiTurno = (gestor.jugadorActual.color == miColor)
                    val esTurnoIAControlada = (gestor.jugadorActual.esIA && soyHost)
                    
                    //Si NO es mi turno Y NO es una IA que yo controlo, salgo
                    if(!esMiTurno && !esTurnoIAControlada)
                        return
                }
                
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
        //Esta función se llama cuando el timer llega a 0.
        //Puede ser porque se tardó en tirar el dado o porque se tardó en la trivia
        
        //Si estaba respondiendo trivia...
        if(overlayPreguntas.visibility == View.VISIBLE)
        {
            Log.i(TAG_TIMER, "Tiempo de Trivia agotado (Auto-Lanzar).")
            overlayPreguntas.visibility = View.GONE
            
            //Si está en bonificación de casilla segura...
            if(gestor.estadoJuego == EstadoJuego.ESPERANDO_DECISION_BONIFICACION)
            {
                enviarResultadoBonificacion(false)
                gestor.resolverBonificacionCasillaSegura(false)
                prepararSiguienteTurno()
                return
            }
            
            //Si era powerup, simplemente se cierra y se deja que tire normal
            Toast.makeText(this, "¡Tiempo agotado!",
                Toast.LENGTH_SHORT).show()
        }
        
        //Flujo normal de lanzar dado
        if(gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)
        {
            if(respondiendoTrivia)
                return
            
            if(layoutSeleccionPowerUp.visibility == View.VISIBLE)
            {
                layoutSeleccionPowerUp.visibility = View.GONE
                textoTemporizador.elevation = 0f
            }
            
            Log.i(TAG_TIMER, "Lanzando dado automáticamente...")
            
            //--- REDIRECCIONAR SEGÚN QUIÉN JUEGA ---
            if(gestor.jugadorActual.esIA && esModoOnline && miColor == ColorJugador.ROJO)
            {
                //Si es la IA del host, usar su rutina
                gestionarTurnoIA()
            }
            else
            {
                //Si soy yo (Humano), uso la rutina humana
                realizarLanzamientoHumano()
            }
        }
    }
    
    //Acción automática si se acaba el tiempo de mover
    private fun accionAutomaticaMover()
    {
        if(gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO)
        {
            if(procesandoMovimiento)
                return
            
            val fichaParaMover = gestor.seleccionarFichaIA(ultimoResultadoDado)
            
            if(fichaParaMover != null)
            {
                procesandoMovimiento = true
                
                //Enviar a la red
                val soyHost = (miColor == ColorJugador.ROJO)
                val esMiFicha = (gestor.jugadorActual.color == miColor)
                val esFichaIAControlada = (soyHost && gestor.jugadorActual.esIA)
                
                if(esModoOnline && (esMiFicha || esFichaIAControlada))
                {
                    val msg = MensajeRed(
                        accion = AccionRed.MOVER_FICHA,
                        colorJugador = gestor.jugadorActual.color,
                        idFicha = fichaParaMover.id,
                        resultadoDado = ultimoResultadoDado
                    )
                    ClienteRed.enviar(msg)
                }
                
                //Esto actualiza la lógica antes de animar, arreglando la desincronización
                animarMovimientoIA(fichaParaMover, ultimoResultadoDado)
            }
        }
    }
    
    private fun animarMovimientoIA(ficha: Ficha, valorDado: Int)
    {
        animacionJob?.cancel()
        
        //Calcular camino
        val camino = gestor.calcularCamino(ficha, valorDado)
        val destinoFinalCalculado = camino.lastOrNull() ?: ficha.posicionGlobal
        
        //--- A. PREPARAR VISUALIZACIÓN DE KILL ---
        val candidatosAMorir = mutableListOf<Ficha>()
        gestor.jugadores.forEach { j ->
            if(j.color != ficha.color)
            {
                j.fichas.forEach { f ->
                    if(f.posicionGlobal == destinoFinalCalculado && f.estado == EstadoFicha.EN_JUEGO)
                    {
                        candidatosAMorir.add(f)
                    }
                }
            }
        }
        
        //ACTUALIZAR LÓGICA
        gestor.moverFicha(ficha, valorDado)
        
        val destinoFinal = ficha.posicionGlobal
        val estadoFinal = ficha.estado
        
        //--- RESTAURAR VÍCTIMAS ---
        val victimasConfirmadas = candidatosAMorir.filter{ it.estado == EstadoFicha.EN_BASE }
        victimasConfirmadas.forEach{
            it.estado = EstadoFicha.EN_JUEGO
            it.posicionGlobal = destinoFinalCalculado
        }
        
        //Animar
        animacionJob = lifecycleScope.launch{
            try
            {
                for(pos in camino)
                {
                    ficha.posicionGlobal = pos
                    if(pos != 0 && pos <= 52) ficha.estado = EstadoFicha.EN_JUEGO
                    if(pos > 52) ficha.estado = EstadoFicha.EN_META
                    
                    tableroView.actualizarEstadoJuego(gestor.jugadores)
                    gestorSonido.reproducir(TipoSonido.PASO)
                    delay(200)
                }
            }
            finally
            {
                //--- ATERRIZAJE Y KILL ---
                ficha.posicionGlobal = destinoFinal
                ficha.estado = estadoFinal
                
                //Mandar víctimas a base visualmente
                victimasConfirmadas.forEach {
                    it.estado = EstadoFicha.EN_BASE
                    it.posicionGlobal = 0
                }
                
                tableroView.actualizarEstadoJuego(gestor.jugadores)
                
                if(isActive)
                {
                    //Sonidos
                    if(gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO)
                        gestorSonido.reproducir(TipoSonido.VICTORIA)
                    else if(gestor.esPosicionFinalDeMeta(ficha))
                        gestorSonido.reproducir(TipoSonido.META)
                    else if(gestor.huboKill)
                        gestorSonido.reproducir(TipoSonido.KILL)
                    else if(ficha.estado == EstadoFicha.EN_JUEGO && gestor.esCasillaSegura(
                                ficha.posicionGlobal))
                        gestorSonido.reproducir(TipoSonido.ESPECIAL)
                    
                    when(gestor.estadoJuego)
                    {
                        EstadoJuego.JUEGO_TERMINADO ->
                        {
                            cancelarTemporizador()
                            mostrarVictoria(gestor.jugadorActual.color)
                        }
                        
                        EstadoJuego.ESPERANDO_LANZAMIENTO ->{
                            prepararSiguienteTurno()
                        }
                        
                        EstadoJuego.ESPERANDO_DECISION_BONIFICACION ->
                        {
                            Log.i(TAG, "IA cayó en casilla segura. Rechazando trivia...")
                            
                            if(esModoOnline && miColor == ColorJugador.ROJO)
                            {
                                lifecycleScope.launch{
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
                            }
                            else
                            {
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
        
        //--- GUARDAR PUNTAJES DE TODOS ---
        for(jugador in gestor.jugadores)
        {
            //Solo guardar si es un jugador humano y si tiene algún puntaje acumulado
            if(!jugador.esIA)
            {
                var puntajeFinal = jugador.puntaje
                
                //Si es el ganador, sumar el bono de victoria
                if(jugador.color == ganadorColor)
                    puntajeFinal += 2000
                
                //Solo guardar si tiene puntos
                if(puntajeFinal > 0)
                {
                    val registro = Puntaje(jugador.nombre, jugador.aciertosTrivia,
                        puntajeFinal)
                    GestorPuntajes.guardarPuntaje(this, registro)
                }
            }
        }
        
        val texto = "¡HA GANADO $nombreGanador!"
        txtGanador.text = texto.uppercase()
        
        val colorRes = when(ganadorColor)
        {
            ColorJugador.ROJO -> androidx.core.content.ContextCompat.getColor(this, R.color.rojo)
            ColorJugador.VERDE -> androidx.core.content.ContextCompat.getColor(this, R.color.verde)
            ColorJugador.AZUL -> androidx.core.content.ContextCompat.getColor(this, R.color.azul)
            ColorJugador.AMARILLO -> androidx.core.content.ContextCompat.getColor(
                this,
                R.color.amarillo
            )
        }
        txtGanador.setTextColor(colorRes)
        
        layoutVictoria.visibility = View.VISIBLE
        layoutVictoria.bringToFront()
    }
    
    private fun configurarBotonesPowerUp()
    {
        //Asignar listener específico para cada color
        powerupRojo.setOnClickListener{ abrirMenuParaJugador(ColorJugador.ROJO) }
        powerupVerde.setOnClickListener{ abrirMenuParaJugador(ColorJugador.VERDE) }
        powerupAzul.setOnClickListener{ abrirMenuParaJugador(ColorJugador.AZUL) }
        powerupAmarillo.setOnClickListener{ abrirMenuParaJugador(ColorJugador.AMARILLO) }
        
        //Configurar botón cancelar
        btnCancelarPower.setOnClickListener{
            layoutSeleccionPowerUp.visibility = View.GONE
            //Al cerrar, si es mi turno, aseguro que el timer se siga viendo normal
            textoTemporizador.elevation = 0f //Restaurar elevación normal
        }
        
        //Configurar botones de selección de powerups
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
        
        //Si es mi turno pero ya tiré el dado (estoy esperando a mover), no puedo abrir el menú
        if(esSuTurno && gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO)
        {
            Toast.makeText(
                this,
                "Ya tiraste el dado. No puedes usar PowerUps ahora.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        val jugadorInteresado = gestor.jugadores.find{ it.color == colorInteresado } ?: return
        
        gestorSonido.reproducir(TipoSonido.MENU)
        
        if(jugadorInteresado.usosPowerUpRestantes <= 0)
        {
            Toast.makeText(this, "No te quedan usos de PowerUps.",
                Toast.LENGTH_SHORT).show()
            return
        }
        
        txtUsosRestantes.text = "Usos restantes: ${jugadorInteresado.usosPowerUpRestantes}"
        
        val esFaseLanzamiento = (gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)
        
        if(esSuTurno && esFaseLanzamiento)
        {
            textoTemporizador.visibility = View.VISIBLE
            textoTemporizador.elevation = 100f
        }
        else
        {
            textoTemporizador.elevation = 0f
        }
        
        //--- LÓGICA DE RESTRICCIÓN  ---
        
        //Contar fichas que están realmente en la base (disponibles para salir)
        val fichasEnBase = jugadorInteresado.fichas.count { it.estado == EstadoFicha.EN_BASE }
        
        //Contar fichas activas en el tablero (ni en base ni en la meta final)
        val fichasActivas = jugadorInteresado.fichas.count {
            it.estado == EstadoFicha.EN_JUEGO ||
                    (it.estado == EstadoFicha.EN_META && !gestor.esPosicionFinalDeMeta(it))
        }
        
        //REGLA SALIDA MAESTRA:
        //- Debe tener al menos una ficha en la base (si no, entonces qué va a sacar? jajaj)
        //- Tener pocas fichas activas fuera.
        val permitirSalidaMaestra = (fichasEnBase > 0 && fichasActivas <= 1)
        
        //REGLA OTROS PODERES:
        //Si todas las fichas están en la base, no tiene sentido usar escudos u otros powerups
        val todasEnBase = (fichasEnBase == 4)
        
        //Configurar botón de salida maestra
        habilitarBoton(btnPowerSalida, permitirSalidaMaestra)
        
        //Configurar botón números altos
        habilitarBoton(btnPowerAltos, true)
        
        //Configurar el resto solo si hay al menos una ficha fuera para mover
        val permitirOtros = !todasEnBase
        habilitarBoton(btnPowerBajos, permitirOtros)
        habilitarBoton(btnPowerPares, permitirOtros)
        habilitarBoton(btnPowerImpares, permitirOtros)
        habilitarBoton(btnPowerEscudo, permitirOtros)
        
        layoutSeleccionPowerUp.visibility = View.VISIBLE
        layoutSeleccionPowerUp.bringToFront()
    }
    
    //Función auxiliar para activar/desactivar botones visualmente
    private fun habilitarBoton(boton: Button, habilitar: Boolean)
    {
        boton.isEnabled = habilitar
        if(habilitar)
        {
            boton.alpha = 1.0f //Visible
        }
        else
        {
            boton.alpha = 0.5f //Semitransparente (efecto deshabilitado)
        }
    }
    
    private fun procesarSeleccion(tipo: TipoPowerUp)
    {
        layoutSeleccionPowerUp.visibility = View.GONE
        textoTemporizador.elevation = 0f
        
        mostrarTrivia(
            onExito =
                {
                    val miJugadorLocal = gestor.jugadores.find{ it.color == miColor }
                    
                    if(miJugadorLocal != null)
                    {
                        val exito = gestor.activarPowerUp(miColor!!, tipo)
                        
                        if(exito)
                        {
                            val mensaje = when(tipo)
                            {
                                TipoPowerUp.SALIDA_MAESTRA -> "¡Correcto! Salida Maestra activada."
                                TipoPowerUp.ESCUDO_TEMPORAL -> "¡Correcto! Escudo activado."
                                else -> "¡Correcto! Power-Up activado."
                            }
                            Toast.makeText(this, mensaje,
                                Toast.LENGTH_SHORT).show()
                            
                            if(esModoOnline)
                            {
                                val msg = MensajeRed(
                                    accion = AccionRed.USAR_POWERUP,
                                    colorJugador = miColor,
                                    tipoPowerUp = tipo
                                )
                                ClienteRed.enviar(msg)
                            }
                            
                            //Solo terminar el turno si el escudo se usó durante el turno
                            //del jugador que lo activó
                            val esMiTurnoActual = (gestor.jugadorActual.color == miColor)
                            
                            if(tipo == TipoPowerUp.ESCUDO_TEMPORAL && esMiTurnoActual)
                            {
                                cancelarTemporizador()
                                gestor.pasarTurno()
                                prepararSiguienteTurno()
                            }
                            else
                            {
                                if(esMiTurnoActual)
                                {
                                    actualizarTextoPowerUp()
                                    prepararSiguienteTurno()
                                }
                            }
                        }
                    }
                })
    }
    
    private fun actualizarTextoPowerUp()
    {
        val jugadorActual = gestor.jugadorActual
        
        if(jugadorActual.powerUpActivo != TipoPowerUp.NINGUNO)
        {
            val nombrePoder = when(jugadorActual.powerUpActivo)
            {
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
        }
        else
        {
            txtPowerUpActivo.visibility = View.GONE
        }
    }
    
    private fun mostrarTrivia(onExito: () -> Unit, onFallo: (() -> Unit)? = null)
    {
        //Obtener pregunta random
        val pregunta = BancoPreguntas.obtenerPreguntaAleatoria()
        
        //GUARDAR TIEMPO DE INICIO
        inicioTriviaTime = System.currentTimeMillis()
        
        txtCategoria.text = pregunta.categoria.uppercase()
        txtPregunta.text = pregunta.texto
        
        txtFeedback.visibility = View.GONE
        btnOpcion1.isEnabled = true
        btnOpcion2.isEnabled = true
        btnOpcion3.isEnabled = true
        btnOpcion4.isEnabled = true
        
        //Asignar las opciones
        btnOpcion1.text = pregunta.opciones[0]
        btnOpcion2.text = pregunta.opciones[1]
        btnOpcion3.text = pregunta.opciones[2]
        btnOpcion4.text = pregunta.opciones[3]
        
        //Configurar listeners
        btnOpcion1.setOnClickListener{ verificarRespuesta(0, pregunta, onExito, onFallo) }
        btnOpcion2.setOnClickListener{ verificarRespuesta(1, pregunta, onExito, onFallo) }
        btnOpcion3.setOnClickListener{ verificarRespuesta(2, pregunta, onExito, onFallo) }
        btnOpcion4.setOnClickListener{ verificarRespuesta(3, pregunta, onExito, onFallo) }
        
        //Botón Cancelar
        btnCancelarTrivia.setOnClickListener{
            respondiendoTrivia = false
            overlayPreguntas.visibility = View.GONE
            Toast.makeText(this, "Trivia cancelada", Toast.LENGTH_SHORT).show()
            
            if(onFallo != null)
                onFallo()
        }
        
        //Mostrar layout
        overlayPreguntas.visibility = View.VISIBLE
        overlayPreguntas.bringToFront()
        
        overlayPreguntas.bringToFront()
        
        //--- LÓGICA DEL TIMER PRINCIPAL ---
        
        val esMiTurno = if(esModoOnline)
            gestor.jugadorActual.color == miColor
        else
            !gestor.jugadorActual.esIA //En local, soy yo si no es IA
        
        if(esMiTurno)
        {
            //SI ES MI TURNO:
            //No cancelar el temporizador principal. Dejar que siga corriendo
            //Asegurar que el texto se vea encima de la trivia
            textoTemporizador.visibility = View.VISIBLE
            textoTemporizador.bringToFront()
            textoTemporizador.elevation = 100f
            
            if(gestor.estadoJuego == EstadoJuego.ESPERANDO_DECISION_BONIFICACION)
                iniciarTemporizador(esParaLanzar = true)
        }
        else
            textoTemporizador.visibility = View.INVISIBLE
    }
    
    private fun verificarRespuesta(
        indiceSeleccionado: Int,
        pregunta: Pregunta,
        onExito: () -> Unit,
        onFallo: (() -> Unit)?
    )
    {
        //PROTECCIÓN CONTRA TIMEOUT
        //Levantar la bandera para que si el timer llega a 0 mientras estamos aquí,
        //no nos interrumpa
        respondiendoTrivia = true
        
        //2. BLOQUEAR BOTONES
        btnOpcion1.isEnabled = false
        btnOpcion2.isEnabled = false
        btnOpcion3.isEnabled = false
        btnOpcion4.isEnabled = false
        
        val esCorrecto = (indiceSeleccionado == pregunta.indiceCorrecto)
        
        //CONFIGURAR FEEDBACK VISUAL
        txtFeedback.visibility = View.VISIBLE
        
        if(esCorrecto)
        {
            //--- CÁLCULO DE PUNTAJE POR TIEMPO ---
            val tiempoTardado = System.currentTimeMillis() - inicioTriviaTime
            
            //Fórmula: Base 100 + bono por rapidez (max 600)
            val bonoTiempo = ((6000 - tiempoTardado) / 10).toInt().coerceAtLeast(0)
            val puntosGanados = 100 + bonoTiempo
            
            //Actualizar datos locales
            //Usar 'find' con miColor para asegurar que se suma al jugador correcto (yo)
            val yo = gestor.jugadores.find { it.color == miColor } ?: gestor.jugadorActual
            
            yo.aciertosTrivia++
            yo.puntaje += puntosGanados
            
            //--- SINCRONIZAR CON LA RED ---
            //Avisar al rival de nuestro nuevo puntaje
            if(esModoOnline)
            {
                val msg = MensajeRed(
                    accion = AccionRed.SYNC_PUNTAJE,
                    colorJugador = miColor, //Soy yo
                    puntosAcumulados = yo.puntaje,
                    aciertosAcumulados = yo.aciertosTrivia
                )
                
                ClienteRed.enviar(msg)
            }
            
            //Feedback visual
            txtFeedback.text = "¡CORRECTO! +$puntosGanados pts"
            txtFeedback.setTextColor(android.graphics.Color.parseColor("#48BB78")) //Verde
        }
        else
        {
            txtFeedback.text = "INCORRECTO"
            txtFeedback.setTextColor(android.graphics.Color.parseColor("#E53E3E")) //Rojo
        }
        
        //DELAY PARA LEER EL RESULTADO (800ms)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            
            //Al terminar el delay, ocultar la UI
            overlayPreguntas.visibility = View.GONE
            textoTemporizador.elevation = 0f
            
            if(esCorrecto)
            {
                onExito()
                
                //--- REINICIAR Y SINCRONIZAR TIMER (6s) ---
                //Como se ganó (o se usó powerup), le toca tirar. Se reinicia a 6s para darle tiempo
                
                val tiempoLanzar = 6000L
                iniciarTemporizador(esParaLanzar = true, tiempoPersonalizado = tiempoLanzar)
                
                if(esModoOnline && gestor.jugadorActual.color == miColor)
                {
                    val msg = MensajeRed(
                        accion = AccionRed.SINCRONIZAR_TIMER,
                        colorJugador = miColor,
                        tiempoTimer = tiempoLanzar,
                        esParaLanzar = true
                    )
                    
                    ClienteRed.enviar(msg)
                }
            }
            else
            {
                //Si falló, desactivamos la bandera 'respondiendoTrivia' para que el juego
                //siga normal
                respondiendoTrivia = false
                
                if(onFallo != null)
                {
                    //Si es bonificación de casilla segura, avisar a la red
                    onFallo()
                }
                else
                {
                    if(gestor.jugadorActual.usosPowerUpRestantes > 0)
                        gestor.jugadorActual.usosPowerUpRestantes--
                    
                    //Reactivar el turno para tirar el dado
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
            
            //--- AVISAR A LA RED QUE EMPIEZA LA TRIVIA ---
            if(esModoOnline && gestor.jugadorActual.color == miColor)
            {
                ClienteRed.enviar(
                    MensajeRed(
                        accion = AccionRed.INICIO_TRIVIA,
                        colorJugador = miColor
                    )
                )
            }
            
            mostrarTrivia(
                onExito = {
                    //PRIMERO AVISAR A LA RED (Mientras todavía es mi turno)
                    enviarResultadoBonificacion(true)
                    
                    //LUEGO CAMBIAR TURNO LOCAL
                    gestor.resolverBonificacionCasillaSegura(true)
                    prepararSiguienteTurno()
                },
                onFallo = {
                    //PRIMERO AVISAR A LA RED
                    enviarResultadoBonificacion(false)
                    
                    //LUEGO CAMBIAR TURNO LOCAL
                    gestor.resolverBonificacionCasillaSegura(false)
                    prepararSiguienteTurno()
                }
            )
        }
        
        btnRechazarReto.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            layoutDecisionBonificacion.visibility = View.GONE
            cancelarTemporizadorBonificacion()
            
            //PRIMERO AVISAR A LA RED
            enviarResultadoBonificacion(false)
            
            //LUEGO CAMBIAR TURNO LOCAL
            gestor.resolverBonificacionCasillaSegura(false)
            prepararSiguienteTurno()
        }
    }
    
    //Función para animar movimientos locales (los míos)
    private fun animarFicha(ficha: Ficha, pasosTotales: Int, alTerminar: () -> Unit)
    {
        //Cancelar animación anterior
        animacionJob?.cancel()
        
        //Calcular camino visual
        val camino = gestor.calcularCamino(ficha, pasosTotales)
        
        //GUARDAR ESTADO ORIGINAL
        //Guardar dónde estaba la ficha antes de empezar a moverla visualmente
        val posicionOriginal = ficha.posicionGlobal
        val estadoOriginal = ficha.estado
        
        animacionJob = lifecycleScope.launch{
            for(nuevaPos in camino)
            {
                //Ajustes visuales de estado
                if(ficha.estado == EstadoFicha.EN_BASE && nuevaPos != 0) ficha.estado =
                    EstadoFicha.EN_JUEGO
                if(ficha.estado == EstadoFicha.EN_JUEGO && nuevaPos > 52) ficha.estado =
                    EstadoFicha.EN_META
                
                //Mover visualmente
                ficha.posicionGlobal = nuevaPos
                
                tableroView.actualizarEstadoJuego(gestor.jugadores)
                gestorSonido.reproducir(TipoSonido.PASO)
                delay(300)
            }
            
            //Antes de llamar a la lógica oficial (alTerminar -> moverFicha), se debe restaurar
            //la ficha a donde estaba al principio. Si no se hace esto, moverFicha sumará
            //PosicionYaAvanzada + Dado y va a avanzar el doble de casillas
            
            ficha.posicionGlobal = posicionOriginal
            ficha.estado = estadoOriginal
            
            //Ahora sí, ejecutar la lógica matemática
            alTerminar()
            
            //Sonidos post-movimiento
            if(gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO)
                gestorSonido.reproducir(TipoSonido.VICTORIA)
            else if(gestor.esPosicionFinalDeMeta(ficha))
                gestorSonido.reproducir(TipoSonido.META)
            else if(gestor.huboKill)
                gestorSonido.reproducir(TipoSonido.KILL)
            else if(ficha.estado == EstadoFicha.EN_JUEGO && gestor.esCasillaSegura(ficha.posicionGlobal))
                gestorSonido.reproducir(TipoSonido.ESPECIAL)
        }
    }
    
    //Gestiona el turno si es una IA
    private fun gestionarTurnoIA()
    {
        //Bloquear interacción visual
        dadoRojo.setOnClickListener(null)
        dadoVerde.setOnClickListener(null)
        dadoAzul.setOnClickListener(null)
        dadoAmarillo.setOnClickListener(null)
        
        turnoIAJob?.cancel()
        
        turnoIAJob = lifecycleScope.launch{
            //Tiempo de espera como si estuviera pensando
            delay(1000)
            
            //--- APAGAR TIMER VISUAL ANTES DE TIRAR ---
            cancelarTemporizador()
            
            //Guardar el color antes de lanzar
            val colorIA = gestor.jugadorActual.color
            
            gestorSonido.reproducir(TipoSonido.DADO)
            ultimoResultadoDado = gestor.lanzarDado()
            
            //Enviar dado a la red
            if(esModoOnline && miColor == ColorJugador.ROJO)
            {
                val msg = MensajeRed(
                    accion = AccionRed.LANZAR_DADO,
                    colorJugador = colorIA,
                    resultadoDado = ultimoResultadoDado
                )
                
                ClienteRed.enviar(msg)
            }
            
            //Actualizar UI del dado
            val dadoActivo = when(colorIA)
            {
                ColorJugador.ROJO -> dadoRojo
                ColorJugador.VERDE -> dadoVerde
                ColorJugador.AZUL -> dadoAzul
                ColorJugador.AMARILLO -> dadoAmarillo
            }
            
            dadoActivo.setImageResource(obtenerRecursoDado(ultimoResultadoDado))
            dadoActivo.visibility = View.VISIBLE
            
            delay(1500) //Tiempo para ver el resultado del dado
            
            //Ejecutar movimiento
            if(gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO)
            {
                iniciarTemporizador(esParaLanzar = false)
                accionAutomaticaMover()
            }
            else
            {
                Log.i(TAG, "IA no tiene movimientos válidos. Pasando turno...")
                prepararSiguienteTurno()
            }
        }
    }
    
    private fun actualizarIconoSonido()
    {
        if(gestorSonido.sonidoHabilitado)
        {
            //Sonido activado
            btnSonido.setImageResource(R.drawable.ic_volumen)
            btnSonido.alpha = 1.0f
        }
        else
        {
            //Sonido desactivado
            btnSonido.setImageResource(R.drawable.ic_mute)
            btnSonido.alpha = 0.5f //Hacerlo semitransparente para indicar desactivado
        }
    }
    
    private fun procesarMensajeJuego(mensaje: MensajeRed)
    {
        //Ignorar nuestros propios mensajes (eco)
        if(mensaje.colorJugador == miColor)
            return
        
        when(mensaje.accion)
        {
            AccionRed.LANZAR_DADO ->
            {
                watchdogRival?.cancel()
                cancelarTemporizador()
                
                //--- PROTECCIÓN CONTRA DESINCRONIZACIÓN ---
                //Si se recibe un dado nuevo, asumimos que cualquier decisión pendiente ya terminó
                if(layoutDecisionBonificacion.visibility == View.VISIBLE)
                    layoutDecisionBonificacion.visibility = View.GONE
                
                val valor = mensaje.resultadoDado ?: return
                val colorRival = mensaje.colorJugador ?: return
                
                ultimoResultadoDado = valor
                
                //Identificar dado visual del rival
                val dadoRival = when(colorRival)
                {
                    ColorJugador.ROJO -> dadoRojo
                    ColorJugador.VERDE -> dadoVerde
                    ColorJugador.AZUL -> dadoAzul
                    ColorJugador.AMARILLO -> dadoAmarillo
                }
                
                //Ejecutar lógica en el gestor
                gestorSonido.reproducir(TipoSonido.DADO)
                ultimoResultadoDado = gestor.lanzarDado(valorForzado = valor)
                
                //Actualizar visualmente
                dadoRival.setImageResource(obtenerRecursoDado(valor))
                dadoRival.visibility = View.VISIBLE
                
                //Verificar si pasó el turno automáticamente (ej. sacó tres 6 o no puede mover)
                if(gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)
                {
                    //El gestor ya pasó el turno internamente
                    //Pequeño delay para que se vea el dado y luego cambio
                    lifecycleScope.launch{
                        delay(1500)
                        tableroView.actualizarEstadoJuego(gestor.jugadores)
                        prepararSiguienteTurno()
                    }
                }
                else
                {
                    //Ahora espera movimiento (MOVER_FICHA)
                    val movimientos = gestor.obtenerMovimientosPosibles(valor)
                    tableroView.actualizarFichasMovibles(movimientos)
                    iniciarTemporizador(esParaLanzar = false)
                }
            }
            
            
            AccionRed.MOVER_FICHA ->
            {
                val idFicha = mensaje.idFicha ?: return
                val colorRival = mensaje.colorJugador ?: return
                val valorDado = mensaje.resultadoDado ?: ultimoResultadoDado
                
                val jugadorRival = gestor.jugadores.find { it.color == colorRival }
                val ficha = jugadorRival?.fichas?.find { it.id == idFicha }
                
                if(ficha != null)
                {
                    resultadoBonificacionPendiente = null
                    cancelarTemporizador()
                    animarMovimientoRemoto(ficha, valorDado, colorRival)
                }
            }
            
            AccionRed.RESULTADO_BONIFICACION ->
            {
                val tuvoExito = mensaje.exitoTrivia ?: false
                
                //Esto evita que se esperen los 15 segundos si la respuesta ya llegó
                watchdogRival?.cancel()
                
                if(animandoMovimientoRemoto)
                {
                    //Estamos ocupados moviendo la ficha. Guardamos para después.
                    Log.d(TAG, "Animando... guardando respuesta para el final.")
                    resultadoBonificacionPendiente = tuvoExito
                }
                else
                {
                    //Estamos quietos esperando (el Watchdog estaba corriendo).
                    //Aplicamos ya
                    Log.d(TAG, "Esperando... respuesta recibida. Aplicando inmediato.")
                    aplicarDecisionRemota(tuvoExito)
                }
            }
            
            AccionRed.SINCRONIZAR_TIMER ->
            {
                val tiempo = mensaje.tiempoTimer ?: return
                val esParaLanzar = mensaje.esParaLanzar ?: true
                val colorQuien = mensaje.colorJugador
                
                iniciarTemporizador(esParaLanzar, tiempo)
                
                //-- MOSTRAR TOAST INFORMATIVO ---
                //Buscar el nombre del jugador que reinició su tiempo
                val nombreJugador =
                    gestor.jugadores.find { it.color == colorQuien }?.nombre ?: "Oponente"
                
                //Solo mostrar el mensaje si se reinició a 8 segundos
                //Si es 6 segundos, suele ser porque ganó trivia y ahí ya sale otro Toast
                if(tiempo == 8000L && !esParaLanzar)
                {
                    Toast.makeText(this, "$nombreJugador reinició su tiempo.",
                        Toast.LENGTH_SHORT).show()
                }
            }
            
            AccionRed.USAR_POWERUP ->
            {
                val colorQuien = mensaje.colorJugador ?: return
                val tipoPoder = mensaje.tipoPowerUp ?: return
                
                gestor.activarPowerUp(colorQuien, tipoPoder)
                
                //Verificar si quien usó el poder era el jugador en turno
                val eraTurnoDelActivador = (gestor.jugadorActual.color == colorQuien)
                
                //Solo si el jugador en turno activó el escudo, se pasa su turno al sig. jugador
                if(tipoPoder == TipoPowerUp.ESCUDO_TEMPORAL && eraTurnoDelActivador)
                {
                    gestor.pasarTurno()
                    prepararSiguienteTurno()
                }
                else
                {
                    //Si lo activó fuera de turno, o es otro poder, el juego sigue
                    if(eraTurnoDelActivador)
                        actualizarTextoPowerUp()
                }
            }
            
            AccionRed.SYNC_PUNTAJE ->
            {
                val colorRival = mensaje.colorJugador ?: return
                val nuevosPuntos = mensaje.puntosAcumulados ?: 0
                val nuevosAciertos = mensaje.aciertosAcumulados ?: 0
                
                //Actualizar la información del rival en mi memoria local
                val jugadorRival = gestor.jugadores.find { it.color == colorRival }
                if(jugadorRival != null)
                {
                    jugadorRival.puntaje = nuevosPuntos
                    jugadorRival.aciertosTrivia = nuevosAciertos
                }
            }
            
            AccionRed.SOLICITAR_ESTADO ->
            {
                if(miColor == ColorJugador.ROJO)
                {
                    val msgEstado = MensajeRed(
                        accion = AccionRed.ENVIAR_ESTADO,
                        estadoJuegoCompleto = gestor.jugadores,
                        turnoActual = gestor.jugadores.indexOf(gestor.jugadorActual),
                        resultadoDado = ultimoResultadoDado
                    )
                    ClienteRed.enviar(msgEstado)
                }
            }
            
            AccionRed.JUGADOR_DESCONECTADO ->
            {
                val colorSeFue = mensaje.colorJugador ?: return
                val nombreSeFue = mensaje.nombreJugador ?: "Jugador"
                
                Toast.makeText(this, "$nombreSeFue se desconectó. CPU activada.",
                    Toast.LENGTH_LONG)
                    .show()
                
                //Convertir a IA
                val jugador = gestor.jugadores.find { it.color == colorSeFue }
                if(jugador != null)
                {
                    jugador.esIA = true
                    jugador.nombre = "$nombreSeFue (CPU)"
                }
                
                if(gestor.jugadorActual.color == colorSeFue) watchdogRival?.cancel()
                procesandoMovimiento = false
                
                if(miColor == ColorJugador.ROJO)
                {
                    //Solo prograe el rescate si al momento de llegar el mensaje era su turno
                    if(gestor.jugadorActual.color == colorSeFue)
                    {
                        lifecycleScope.launch{
                            delay(2000)
                            
                            //Si después de la espera el turno ya cambió (ej. llegó un movimiento
                            //tardío), pausar el turno para no jugar por el jugador que ya acaba
                            //de entrar
                            if(gestor.jugadorActual.color != colorSeFue)
                            {
                                Log.i(
                                    TAG,"El turno cambió durante la espera de desconexión."
                                )
                                return@launch
                            }
                            
                            //Corrección anti-retroceso
                            if(gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO
                                    && ultimoResultadoDado <= 0)
                            {
                                Log.w(TAG, "Dado perdido. Reiniciando turno IA.")
                                gestor.estadoJuego = EstadoJuego.ESPERANDO_LANZAMIENTO
                            }
                            
                            if(gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)
                                gestionarTurnoIA()
                            else if(gestor.estadoJuego == EstadoJuego.ESPERANDO_MOVIMIENTO)
                                accionAutomaticaMover()
                        }
                    }
                }

                tableroView.actualizarEstadoJuego(gestor.jugadores)
                
                if(gestor.jugadorActual.color != miColor)
                {
                    if(gestor.estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO)
                        prepararSiguienteTurno()
                }
            }
            
            AccionRed.INICIO_TRIVIA ->
            {
                //El rival aceptó el reto.
                //Reiniciar el Watchdog para darle más tiempo (los 6s de la trivia + margen)
                iniciarWatchdogRival()
                
                //Iniciar el temporizador visual para ver cuánto le queda
                iniciarTemporizador(esParaLanzar = true)
            }
            
            AccionRed.CONECTAR ->
            {
                val colorNuevo = mensaje.colorJugador ?: return
                val nombreNuevo = mensaje.nombreJugador ?: "Jugador"
                
                //SI SOY HOST (ROJO)
                if(miColor == ColorJugador.ROJO)
                {
                    //Agregar a la cola de espera
                    colaReconexion[colorNuevo] = nombreNuevo
                    
                    //Avisar visualmente al host
                    Toast.makeText(
                        this,
                        "$nombreNuevo está esperando su turno para unirse...",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    if(gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO &&
                            gestor.jugadorActual.color == colorNuevo &&
                            turnoIAJob?.isActive != true)
                    {
                        //Si está quieto esperando que la IA tire, refrescar para que entre ya
                        prepararSiguienteTurno()
                    }
                }
            }
            
            AccionRed.ENVIAR_ESTADO ->
            {
                val listaRemota = mensaje.estadoJuegoCompleto
                val turnoRemoto = mensaje.turnoActual ?: 0
                
                if(listaRemota != null)
                {
                    //Detener cualquier cosa que estuviera haciendo la CPU localmente
                    animacionJob?.cancel()
                    cancelarTemporizador()
                    
                    //Cargar datos
                    gestor.iniciarJuegoConJugadores(
                        listaRemota, turnoRemoto)
                    
                    if(mensaje.resultadoDado != null)
                        ultimoResultadoDado = mensaje.resultadoDado
                    
                    //Actualizar y arrancar
                    tableroView.actualizarEstadoJuego(gestor.jugadores)
                    prepararSiguienteTurno()
                }
            }
            
            AccionRed.SOLICITUD_RECONEXION ->
            {
                if(miColor == ColorJugador.ROJO)
                {
                    val color = mensaje.colorJugador ?: return
                    val nombre = mensaje.nombreJugador ?: "Jugador"
                    
                    Log.i(TAG, "Solicitud de reconexión recibida para $color.")
                    Toast.makeText(this, "$nombre recuperando conexión...", Toast.LENGTH_SHORT)
                        .show()
                    
                    //Poner en la cola
                    colaReconexion[color] = nombre
                    
                    //Si el que se quiere conectar es el que tiene el turno ahora
                    //y actualmente es una IA...
                    if(gestor.jugadorActual.color == color && gestor.jugadorActual.esIA)
                    {
                        turnoIAJob?.cancel() //Detiene el delay, el tiro o el movimiento
                        verificarReconexionPendiente()
                    }
                }
            }
            
            AccionRed.PARTIDA_TERMINADA_POR_HOST ->
            {
                //SOLO LOS CLIENTES RECIBEN ESTO
                Toast.makeText(this, "El Host ha terminado la partida.",
                    Toast.LENGTH_LONG).show()
                
                //Cerrar la actividad para volver al menú principal
                finish()
            }
            
            else -> {}
        }
    }
    
    private fun verificarReconexionPendiente()
    {
        if(miColor != ColorJugador.ROJO)
            return //Solo host
        
        val turnoActualColor = gestor.jugadorActual.color
        val nombrePendiente = colaReconexion[turnoActualColor]
        
        if(nombrePendiente != null)
        {
            //Si es su turno Lo dejamos entrar
            
            //Quitar de la cola
            colaReconexion.remove(turnoActualColor)
            
            //Actualizar localmente
            val jugador = gestor.jugadores.find { it.color == turnoActualColor }
            
            if(jugador != null)
            {
                jugador.esIA = false
                jugador.nombre = nombrePendiente
            }
            
            //ENVIAR ESTADO AL JUGADOR
            val msgEstado = MensajeRed(
                accion = AccionRed.ENVIAR_ESTADO,
                estadoJuegoCompleto = gestor.jugadores,
                turnoActual = gestor.jugadores.indexOf(gestor.jugadorActual),
                resultadoDado = ultimoResultadoDado
            )
            
            ClienteRed.enviar(msgEstado)
            
            tableroView.actualizarEstadoJuego(gestor.jugadores)
            prepararSiguienteTurno()
            
            Toast.makeText(this, "$nombrePendiente se unió a la partida.",
                Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun animarMovimientoRemoto(ficha: Ficha, valorDado: Int, colorRival: ColorJugador)
    {
        animacionJob?.cancel()
        
        //Calcular camino
        val camino = gestor.calcularCamino(ficha, valorDado)
        
        //Guardar dónde va a caer para buscar víctimas potenciales
        val destinoFinalCalculado = camino.lastOrNull() ?: ficha.posicionGlobal
        
        //Buscar quiénes están en la casilla de destino antes de que la lógica los borre
        val candidatosAMorir = mutableListOf<Ficha>()
        gestor.jugadores.forEach { j ->
            if(j.color != ficha.color)
            {
                j.fichas.forEach { f ->
                    if(f.posicionGlobal == destinoFinalCalculado && f.estado == EstadoFicha.EN_JUEGO)
                        candidatosAMorir.add(f)
                }
            }
        }
        
        gestor.moverFicha(ficha, valorDado)
        
        //Guardar el destino final real
        val destinoFinal = ficha.posicionGlobal
        val estadoFinal = ficha.estado
        
        //Las revivimos visualmente en el tablero para que esperen el golpe
        val victimasConfirmadas = candidatosAMorir.filter{ it.estado == EstadoFicha.EN_BASE }
        victimasConfirmadas.forEach{
            it.estado = EstadoFicha.EN_JUEGO
            it.posicionGlobal = destinoFinalCalculado
        }
        
        animacionJob = lifecycleScope.launch{
            try
            {
                for(pos in camino)
                {
                    ficha.posicionGlobal = pos
                    if(pos != 0 && pos <= 52) ficha.estado = EstadoFicha.EN_JUEGO
                    if(pos > 52) ficha.estado = EstadoFicha.EN_META
                    
                    tableroView.actualizarEstadoJuego(gestor.jugadores)
                    gestorSonido.reproducir(TipoSonido.PASO)
                    delay(200)
                }
            }
            finally
            {
                ficha.posicionGlobal = destinoFinal
                ficha.estado = estadoFinal
                
                //La animación terminó (o se canceló), mandar las víctimas a la base visualmente
                victimasConfirmadas.forEach {
                    it.estado = EstadoFicha.EN_BASE
                    it.posicionGlobal = 0
                }
                
                tableroView.actualizarEstadoJuego(gestor.jugadores)
                
                //Verificamos si la corrutina sigue activa
                if(isActive)
                {
                    //--- SONIDOS  ---
                    if(gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO)
                        gestorSonido.reproducir(TipoSonido.VICTORIA)
                    else if(gestor.esPosicionFinalDeMeta(ficha))
                        gestorSonido.reproducir(TipoSonido.META)
                    else if(gestor.huboKill)
                        gestorSonido.reproducir(TipoSonido.KILL)
                    else if(ficha.estado == EstadoFicha.EN_JUEGO && gestor.esCasillaSegura(
                                ficha.posicionGlobal))
                        gestorSonido.reproducir(TipoSonido.ESPECIAL)
                    
                    if(gestor.estadoJuego == EstadoJuego.ESPERANDO_DECISION_BONIFICACION)
                    {
                        if(resultadoBonificacionPendiente != null)
                        {
                            aplicarDecisionRemota(resultadoBonificacionPendiente!!)
                            resultadoBonificacionPendiente = null
                        }
                        else
                        {
                            //Poner el color del rival para que sepa quién está pensando
                            actualizarColorTemporizador(colorRival)
                            iniciarTemporizador(esParaLanzar = true)
                            iniciarWatchdogRival()
                        }
                    }
                    else if(gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO)
                        mostrarVictoria(colorRival)
                    else
                        prepararSiguienteTurno()
                }
            }
        }
    }
    
    private fun iniciarTemporizadorBonificacion()
    {
        cancelarTemporizador()
        
        layoutDecisionBonificacion.visibility = View.VISIBLE
        layoutDecisionBonificacion.bringToFront()
        
        Log.d(TAG_TIMER, "Iniciando timer de decisión (4s)")
        
        temporizadorBonificacion = object : CountDownTimer(6000, 1000)
        {
            override fun onTick(millisUntilFinished: Long){}
            
            override fun onFinish()
            {
                Log.i(TAG_TIMER, "Tiempo de decisión agotado. Rechazando.")
                
                layoutDecisionBonificacion.visibility = View.GONE
                Toast.makeText(this@JuegoActivity, "Tiempo agotado.", Toast.LENGTH_SHORT).show()
                
                //PRIMERO AVISAR A LA RED (Mientras todavía es mi turno)
                enviarResultadoBonificacion(false)
                
                //LUEGO APLICAR LÓGICA LOCAL (Esto cambia el turno al rival)
                gestor.resolverBonificacionCasillaSegura(false)
                
                //ACTUALIZAR UI
                prepararSiguienteTurno()
            }
        }.start()
    }
    
    private fun cancelarTemporizadorBonificacion(){
        temporizadorBonificacion?.cancel()
    }
    
    //Función auxiliar para enviar el resultado a la red
    private fun enviarResultadoBonificacion(exito: Boolean)
    {
        if(esModoOnline && gestor.jugadorActual.color == miColor)
        {
            val msg = MensajeRed(
                accion = AccionRed.RESULTADO_BONIFICACION,
                colorJugador = miColor,
                exitoTrivia = exito
            )
            
            ClienteRed.enviar(msg)
        }
    }
    
    private fun aplicarDecisionRemota(tuvoExito: Boolean)
    {
        val nombreJugador = gestor.jugadorActual.nombre
        
        gestor.resolverBonificacionCasillaSegura(tuvoExito)
        
        if(tuvoExito)
            Toast.makeText(this, "¡$nombreJugador ganó un turno extra!",
                Toast.LENGTH_SHORT).show()
        
        prepararSiguienteTurno()
    }
    
    private fun iniciarWatchdogRival()
    {
        watchdogRival?.cancel()
        
        watchdogRival = object : CountDownTimer(15000, 1000)
        {
            override fun onTick(millisUntilFinished: Long){}
            
            override fun onFinish()
            {
                Log.w(TAG, "WATCHDOG: El rival tardó. Forzando continuación.")
                
                if(gestor.estadoJuego == EstadoJuego.ESPERANDO_DECISION_BONIFICACION)
                {
                    gestor.resolverBonificacionCasillaSegura(false)
                    prepararSiguienteTurno()
                }
            }
        }.start()
    }
    
    private fun mostrarDialogoConfirmarSalida()
    {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        
        //Inflar el layout personalizado
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirmar_salir, null)
        builder.setView(view)
        
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val txtMensaje = view.findViewById<TextView>(R.id.txtMensajeSalir)
        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarSalir)
        val btnCancelar = view.findViewById<Button>(R.id.btnCancelarSalir)
        
        if(esModoOnline && miColor == ColorJugador.ROJO)
            txtMensaje.text = "Eres el HOST. La partida terminará para todos."
        else
        {
            txtMensaje.text =
                "Si sales, el CPU jugará por ti y podrás reconectarte hasta que sea tu turno."
        }
        
        //Listeners de los botones
        btnConfirmar.setOnClickListener{
            dialog.dismiss()
            ejecutarSalida()
        }
        
        btnCancelar.setOnClickListener{
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun ejecutarSalida()
    {
        if(esModoOnline)
        {
            if(miColor == ColorJugador.ROJO)
            {
                //--- SI SOY EL HOST, AVISAR A LOS CLIENTES Y CERRAR SERVIDOR ---
                lifecycleScope.launch(Dispatchers.IO) {
                    //Avisar a los clientes
                    val msg = MensajeRed(accion = AccionRed.PARTIDA_TERMINADA_POR_HOST)
                    ServidorBrainchis.broadcast(msg, null)
                    
                    //Pequeña pausa para asegurar que el mensaje salga
                    delay(100)
                    
                    //Apagar el servidor
                    ServidorBrainchis.detener()
                    
                    //Cerrar mi actividad
                    withContext(Dispatchers.Main) {
                        finish()
                    }
                }
            }
            else
            {
                //--- SI SOY CLIENTE, SOLO DESCONECTARME ---
                //El onDestroy se encargará de cerrar el socket del cliente
                finish()
            }
        }
        else
        {
            //Modo Offline
            finish()
        }
    }
    
    private fun mostrarDialogoSalir()
    {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(
            R.layout.dialog_confirmar_salir, null)
        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val txtMensaje = view.findViewById<TextView>(R.id.txtMensajeSalir)
        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarSalir)
        val btnCancelar = view.findViewById<Button>(R.id.btnCancelarSalir)
        
        if(esModoOnline && miColor == ColorJugador.ROJO)
        {
            txtMensaje.text = "Eres el Host. La partida terminará para todos."
        }
        
        btnConfirmar.setOnClickListener{
            dialog.dismiss()
            ejecutarSalida()
        }
        
        btnCancelar.setOnClickListener{
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun actualizarColorTemporizador(color: ColorJugador)
    {
        val colorRes = when(color)
        {
            ColorJugador.ROJO -> R.color.rojo
            ColorJugador.VERDE -> R.color.verde
            ColorJugador.AZUL -> R.color.azul
            ColorJugador.AMARILLO -> R.color.amarillo
        }
        textoTemporizador.backgroundTintList =
            androidx.core.content.ContextCompat.getColorStateList(this, colorRes)
    }
}