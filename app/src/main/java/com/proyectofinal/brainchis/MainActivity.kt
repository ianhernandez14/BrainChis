package com.proyectofinal.brainchis

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {

    //--- VARIABLES GLOBALES (Vistas) ---
    private lateinit var btnCrear: Button
    private lateinit var btnUnirse: Button
    private lateinit var btnPuntajes: Button
    private lateinit var btnAyuda: Button

    //--- VARIABLES DE LÓGICA ---
    private var dialogoEspera: AlertDialog? = null
    private var miColorOnline: ColorJugador? = null

    private var dialogoLobby: AlertDialog? = null
    private var listaJugadoresActual: List<InfoJugador> = emptyList()
    private var containerJugadores: LinearLayout? = null
    private var rgCPUs: RadioGroup? = null
    private var btnIniciarLobby: Button? = null

    private lateinit var gestorSonido: GestorSonido

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        //2. Ajustar márgenes para que no se esconda detrás de la barra de estado
        //Asegúrate que en activity_main.xml el ID del root sea "main" (o cámbialo aquí por el que tengas)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            //Obtener el padding para las barras del sistema (arriba y abajo)
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            //Definir el padding deseado
            val customPadding =
                (20 * resources.displayMetrics.density).toInt() //Convertir 20dp a píxeles

            //Combinar ambos paddings
            v.setPadding(
                systemBars.left + customPadding,    //Padding izquierdo
                systemBars.top,                     //Padding de la barra de estado
                systemBars.right + customPadding,   //Padding derecho
                systemBars.bottom + customPadding   //Padding de la barra de navegación
            )

            insets
        }

        //--- 1. INICIALIZACIÓN DE VISTAS ---
        btnCrear = findViewById(R.id.btnCrear)
        btnUnirse = findViewById(R.id.btnUnirse)
        btnPuntajes = findViewById(R.id.btnPuntajes)
        btnAyuda = findViewById(R.id.btnAyudaMenu)

        // Inicializar sonido
        gestorSonido = GestorSonido(this)

        //--- 2. CONFIGURACIÓN DE LISTENERS ---

        btnCrear.setOnClickListener {
            gestorSonido.reproducir(TipoSonido.MENU)
            mostrarDialogoHost()
        }

        btnUnirse.setOnClickListener {
            gestorSonido.reproducir(TipoSonido.MENU)
            mostrarDialogoUnirse()
        }

        btnPuntajes.setOnClickListener {
            gestorSonido.reproducir(TipoSonido.MENU)

            // Damos 200ms para que el sonido arranque antes de cambiar de pantalla
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, PuntajeActivity::class.java)
                startActivity(intent)
            }, 200)
        }

        btnAyuda.setOnClickListener {
            gestorSonido.reproducir(TipoSonido.MENU)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, AyudaActivity::class.java)
                startActivity(intent)
            }, 200)
        }
    }

    override fun onPause() {
        super.onPause()
        gestorSonido.pausarTodo()
    }

    override fun onResume() {
        super.onResume()
        gestorSonido.reanudarTodo()
    }

    override fun onDestroy() {
        super.onDestroy()
        ClienteRed.cerrar()
        ServidorBrainchis.detener()
        gestorSonido.liberar()
    }

    //--- LÓGICA DEL HOST ---

    private fun mostrarDialogoHost()
    {
        //1. Iniciar el Servidor en este celular
        ServidorBrainchis.iniciar()
        val miIpWifi = NetworkUtils.getLocalIpAddress()

        //2. Conectarme a mí mismo (Localhost)
        ClienteRed.listener = { mensaje ->
            runOnUiThread { procesarMensajeLobby(mensaje) }
        }
        ClienteRed.conectar("127.0.0.1", 65432)

        //3. Crear Diálogo
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_lobby_host, null)
        builder.setView(view)
        builder.setCancelable(false)
        dialogoLobby = builder.create()
        dialogoLobby?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        //Referencias UI
        val txtIp = view.findViewById<TextView>(R.id.txtIpHost)
        val btnCopiar = view.findViewById<android.widget.ImageButton>(R.id.btnCopiarIp)
        containerJugadores = view.findViewById(R.id.containerJugadores)
        rgCPUs = view.findViewById(R.id.rgCPUs)
        btnIniciarLobby = view.findViewById<Button>(R.id.btnIniciarJuegoHost)
        val btnCancelar = view.findViewById<Button>(R.id.btnCancelarLobby)

        txtIp.text = miIpWifi

        btnCopiar.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("IP Brainchis", miIpWifi)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "IP copiada al portapapeles", Toast.LENGTH_SHORT).show()
        }

        txtIp.text = "Tu IP: $miIpWifi"
        btnIniciarLobby?.isEnabled = false //Se habilitará cuando haya gente

        //Listener para iniciar partida
        btnIniciarLobby?.setOnClickListener {
            val cpus = when(rgCPUs?.checkedRadioButtonId) {
                R.id.rb1CPU -> 1
                R.id.rb2CPU -> 2
                R.id.rb3CPU -> 3
                else -> 0
            }
            val msg = MensajeRed(AccionRed.INICIAR_PARTIDA, cantidadCPUs = cpus)
            ClienteRed.enviar(msg)
        }

        btnCancelar.setOnClickListener {
            ServidorBrainchis.detener() //Matar servidor
            ClienteRed.cerrar()
            dialogoLobby?.dismiss()
        }

        //Configuración para que el teclado funcione en los diálogos hijos
        dialogoLobby?.window?.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        dialogoLobby?.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        dialogoLobby?.show()
    }

    //==========================================
    //             LÓGICA DEL CLIENTE
    //==========================================

    private fun mostrarDialogoUnirse() {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_unirse, null)
        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etIp = view.findViewById<EditText>(R.id.etIpServidor)
        val spinner = view.findViewById<Spinner>(R.id.spinnerHistorial)
        val btnConectar = view.findViewById<Button>(R.id.btnConectarServer)

        //Cargar historial
        val historial = GestorServidores.obtenerServidores(this).toMutableList()
        if (historial.isEmpty()) {
            historial.add("Seleccionar reciente...")
        } else {
            etIp.setText(historial[0])
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, historial)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val seleccion = historial[position]
                if (seleccion != "Seleccionar reciente...") {
                    etIp.setText(seleccion)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnConectar.setOnClickListener {
            val ipIngresada = etIp.text.toString().trim()
            if (ipIngresada.isEmpty() || ipIngresada.count { it == '.' } != 3) {
                Toast.makeText(this, "IP Inválida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            GestorServidores.guardarServidor(this, ipIngresada)
            dialog.dismiss()
            iniciarConexionConIP(ipIngresada)
        }
        dialog.show()
    }

    private fun iniciarConexionConIP(ip: String) {
        //Mostrar diálogo de "Cargando..." mientras conecta
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Conectando...")
        builder.setMessage("Buscando servidor en $ip...")
        builder.setCancelable(false)
        builder.setNegativeButton("Cancelar") { _, _ -> ClienteRed.cerrar() }
        dialogoEspera = builder.create()
        dialogoEspera?.show()

        //Configurar Listener
        ClienteRed.listener = { mensaje ->
            runOnUiThread { procesarMensajeLobby(mensaje) }
        }
        //Conectar
        ClienteRed.conectar(ip, 65432)
    }

    //==========================================
    //          LÓGICA COMÚN (LOBBY)
    //==========================================

    private fun procesarMensajeLobby(mensaje: MensajeRed) {
        when(mensaje.accion) {
            AccionRed.CONECTAR -> {
                //¡Conexión Exitosa!
                miColorOnline = mensaje.colorJugador
                dialogoEspera?.dismiss()

                //ABRIR EL LOBBY VISUAL (Si no soy host, lo abro ahora)
                //Si soy host, el diálogo ya está abierto en mostrarDialogoHost
                if (dialogoLobby == null || !dialogoLobby!!.isShowing) {
                    mostrarVistaLobbyCliente()
                }
            }

            AccionRed.ACTUALIZAR_LOBBY -> {
                mensaje.listaJugadores?.let { actualizarUILobby(it) }
            }

            AccionRed.INICIAR_PARTIDA -> {
                dialogoLobby?.dismiss()
                dialogoEspera?.dismiss()

                val intent = Intent(this, JuegoActivity::class.java)
                intent.putExtra("MODO_ONLINE", true)
                intent.putExtra("MI_COLOR", miColorOnline?.name)
                intent.putExtra("TURNO_INICIAL", mensaje.resultadoDado ?: 0)

                // Calcular total
                val numHumanos = listaJugadoresActual.size
                val numCPUs = mensaje.cantidadCPUs ?: 0
                intent.putExtra("CANTIDAD_JUGADORES", numHumanos + numCPUs)
                intent.putExtra("CANTIDAD_HUMANOS_REALES", numHumanos)

                // --- NUEVO: ENVIAR LISTA DE NOMBRES ---
                // Creamos arrays paralelos para pasar nombres y colores
                val nombresArray = ArrayList<String>()
                val coloresArray = ArrayList<String>()

                for(info in listaJugadoresActual) {
                    nombresArray.add(info.nombre)
                    coloresArray.add(info.color.name)
                }

                intent.putStringArrayListExtra("LISTA_NOMBRES", nombresArray)
                intent.putStringArrayListExtra("LISTA_COLORES", coloresArray)

                startActivity(intent)
            }

            AccionRed.ENVIAR_ESTADO -> {
                // ESTO OCURRE CUANDO ME RECONECTO A UNA PARTIDA EN CURSO
                dialogoLobby?.dismiss()
                dialogoEspera?.dismiss()

                val intent = Intent(this, JuegoActivity::class.java)
                intent.putExtra("MODO_ONLINE", true)
                intent.putExtra("MI_COLOR", miColorOnline?.name)

                // Bandera clave para que JuegoActivity sepa que debe pedir/cargar estado
                intent.putExtra("ES_RECONEXION", true)

                // Aunque el estado real viene en el mensaje, JuegoActivity lo pedirá de nuevo
                // o podemos intentar pasarlo (pero es complejo pasar objetos).
                // Para que onCreate no falle, pasamos un dummy de cantidad,
                // JuegoActivity lo sobrescribirá al recibir el estado real.
                intent.putExtra("CANTIDAD_JUGADORES", 4)
                intent.putExtra("NOMBRE_JUGADOR", "Jugador Reconectado")

                startActivity(intent)
            }

            AccionRed.DESCONEXION -> {
                dialogoEspera?.dismiss()
                dialogoLobby?.dismiss()
                Toast.makeText(this, "Error: ${mensaje.mensajeTexto}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    private fun mostrarVistaLobbyCliente() {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_lobby_host, null)
        builder.setView(view)
        builder.setCancelable(false)
        dialogoLobby = builder.create()
        dialogoLobby?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        //Referencias UI
        val txtIp = view.findViewById<TextView>(R.id.txtIpHost)
        containerJugadores = view.findViewById(R.id.containerJugadores)
        rgCPUs = view.findViewById(R.id.rgCPUs)
        btnIniciarLobby = view.findViewById<Button>(R.id.btnIniciarJuegoHost)
        val btnCancelar = view.findViewById<Button>(R.id.btnCancelarLobby)

        //-- MODO CLIENTE --
        txtIp.text = "Conectado al Host"

        //Ocultar controles de Host
        btnIniciarLobby?.visibility = View.GONE
        for (i in 0 until (rgCPUs?.childCount ?: 0)) {
            rgCPUs?.getChildAt(i)?.isEnabled = false
        }

        btnCancelar.text = "Salir del Lobby"
        btnCancelar.setOnClickListener {
            ClienteRed.cerrar()
            dialogoLobby?.dismiss()
        }

        //Configuración de Teclado (Importante para el cliente también)
        dialogoLobby?.window?.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        dialogoLobby?.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        dialogoLobby?.show()
    }

    private fun actualizarUILobby(jugadores: List<InfoJugador>) {
        listaJugadoresActual = jugadores
        containerJugadores?.removeAllViews()

        //A. Llenar lista visual
        for (jugador in jugadores) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_lobby_jugador, containerJugadores, false)
            val imgFicha = itemView.findViewById<ImageView>(R.id.imgFichaLobby)
            val etNombre = itemView.findViewById<EditText>(R.id.etNombreLobby)

            val resId = when(jugador.color) {
                ColorJugador.ROJO -> R.drawable.ficha_roja
                ColorJugador.VERDE -> R.drawable.ficha_verde
                ColorJugador.AZUL -> R.drawable.ficha_azul
                ColorJugador.AMARILLO -> R.drawable.ficha_amarilla
            }
            imgFicha.setImageResource(resId)
            etNombre.setText(jugador.nombre)

            //VALIDACIÓN DE EDICIÓN (Solución Diálogo Externo)
            if (jugador.color == miColorOnline) {
                etNombre.isEnabled = true
                etNombre.isFocusable = false //No foco directo
                etNombre.isClickable = true  //Detecta clics

                etNombre.setOnClickListener {
                    mostrarDialogoCambiarNombre(jugador.nombre)
                }
            } else {
                etNombre.isEnabled = false
                etNombre.setTextColor(android.graphics.Color.GRAY)
            }
            containerJugadores?.addView(itemView)
        }

        //B. Lógica de RadioButtons (Solo visual para el Host)
        if (btnIniciarLobby?.visibility == View.VISIBLE) {
            val numHumanos = jugadores.size
            val espaciosLibres = 4 - numHumanos

            val rb0 = dialogoLobby?.findViewById<RadioButton>(R.id.rb0CPU)
            val rb1 = dialogoLobby?.findViewById<RadioButton>(R.id.rb1CPU)
            val rb2 = dialogoLobby?.findViewById<RadioButton>(R.id.rb2CPU)
            val rb3 = dialogoLobby?.findViewById<RadioButton>(R.id.rb3CPU)

            rb1?.isEnabled = espaciosLibres >= 1
            rb2?.isEnabled = espaciosLibres >= 2
            rb3?.isEnabled = espaciosLibres >= 3

            //Corrección selección
            val idSel = rgCPUs?.checkedRadioButtonId
            var cpus = 0
            if (idSel == R.id.rb1CPU) cpus = 1
            if (idSel == R.id.rb2CPU) cpus = 2
            if (idSel == R.id.rb3CPU) cpus = 3

            if (cpus > espaciosLibres) {
                when(espaciosLibres) {
                    0 -> rb0?.isChecked = true
                    1 -> rb1?.isChecked = true
                    2 -> rb2?.isChecked = true
                }
            }

            //Activar botón si hay al menos 2
            val total = numHumanos + (if (cpus > espaciosLibres) espaciosLibres else cpus)
            btnIniciarLobby?.isEnabled = (total >= 2)

            rgCPUs?.setOnCheckedChangeListener { _, checkedId ->
                val cpusNew = when(checkedId) {
                    R.id.rb1CPU -> 1
                    R.id.rb2CPU -> 2
                    R.id.rb3CPU -> 3
                    else -> 0
                }
                //Usamos listaJugadoresActual.size que se actualiza cada vez que entra alguien
                val totalNew = listaJugadoresActual.size + cpusNew
                btnIniciarLobby?.isEnabled = (totalNew >= 2)
            }
        }
    }

    private fun mostrarDialogoCambiarNombre(nombreActual: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Cambiar Nombre")

        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.setText(nombreActual)
        input.selectAll()

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = dpToPx(24)
        params.rightMargin = dpToPx(24)
        input.layoutParams = params
        container.addView(input)

        builder.setView(container)

        builder.setPositiveButton("Guardar") { dialog, _ ->
            val nuevoNombre = input.text.toString().trim()

            // Validación básica
            if (nuevoNombre.isEmpty()) {
                Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            // --- VALIDACIÓN DE DUPLICADOS (Case Sensitive Exacto) ---
            // Verificamos si alguien mas YA tiene ese nombre
            val nombreExiste = listaJugadoresActual.any {
                it.nombre == nuevoNombre && it.color != miColorOnline // Ignorar mi propio nombre actual
            }

            if (nombreExiste) {
                Toast.makeText(this, "Ese nombre ya está en uso en este lobby", Toast.LENGTH_SHORT).show()
                // No cerramos el dialogo idealmente, pero AlertDialog cierra automático en PositiveButton.
                // Para evitar cerrar, habría que configurar el listener después de show(),
                // pero para simplificar, mostramos Toast y el usuario tendrá que reintentar.
                // O enviamos el cambio y dejamos que el servidor rechace (pero el servidor actual no valida eso).
            }
            else if (nuevoNombre != nombreActual) {
                ClienteRed.enviar(MensajeRed(AccionRed.CAMBIAR_NOMBRE, nombreJugador = nuevoNombre))
                dialog.dismiss()
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    //NOTA: Asegúrate de tener esta función para cuando el usuario presiona "Crear"
    //La función mostrarDialogoConfiguracion (Local) la puedes conservar o borrar si ya no la usas.
    private fun mostrarDialogoConfiguracion() {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_configuracion, null)
        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnIniciar = view.findViewById<Button>(R.id.btnIniciarPartida)
        val radio2 = view.findViewById<RadioButton>(R.id.radio2Jugadores)
        val radio3 = view.findViewById<RadioButton>(R.id.radio3Jugadores)
        val checkSoloIA = view.findViewById<CheckBox>(R.id.checkSoloIA)
        val etNombre = view.findViewById<android.widget.EditText>(R.id.etNombreJugador)

        btnIniciar.setOnClickListener {
            //1. Determinar cantidad
            val cantidad = when {
                radio2.isChecked -> 2
                radio3.isChecked -> 3
                else -> 4
            }

            //2. Determinar si el Humano juega
            val humanoJuega = !checkSoloIA.isChecked

            //3. Obtener el nombre
            var nombreUsuario = etNombre.text.toString().trim()
            if (nombreUsuario.isEmpty()) nombreUsuario = "Jugador 1"

            //4. Lanzar JuegoActivity con estos datos
            val intent = Intent(this, JuegoActivity::class.java)
            intent.putExtra("CANTIDAD_JUGADORES", cantidad)
            intent.putExtra("HUMANO_JUEGA", humanoJuega)
            intent.putExtra("NOMBRE_JUGADOR", nombreUsuario)

            startActivity(intent)

            dialog.dismiss()
        }

        dialog.show()
    }
}