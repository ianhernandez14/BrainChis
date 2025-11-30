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

class MainActivity : AppCompatActivity()
{
    
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
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
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
        
        btnCrear = findViewById(R.id.btnCrear)
        btnUnirse = findViewById(R.id.btnUnirse)
        btnPuntajes = findViewById(R.id.btnPuntajes)
        btnAyuda = findViewById(R.id.btnAyudaMenu)
        
        //Inicializar sonido
        gestorSonido = GestorSonido(this)
        
        btnCrear.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            mostrarDialogoHost()
        }
        
        btnUnirse.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            mostrarDialogoUnirse()
        }
        
        btnPuntajes.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            
            //Le tuve que dar delay porque sino no se escuchaba el sonido del menú
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, PuntajeActivity::class.java)
                startActivity(intent)
            }, 200)
        }
        
        btnAyuda.setOnClickListener{
            gestorSonido.reproducir(TipoSonido.MENU)
            
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, AyudaActivity::class.java)
                startActivity(intent)
            }, 200)
        }
    }
    
    override fun onPause()
    {
        super.onPause()
        gestorSonido.pausarTodo()
    }
    
    override fun onResume()
    {
        super.onResume()
        gestorSonido.reanudarTodo()
    }
    
    override fun onDestroy()
    {
        super.onDestroy()
        ClienteRed.cerrar()
        ServidorBrainchis.detener()
        gestorSonido.liberar()
    }
    
    private fun mostrarDialogoHost()
    {
        //Iniciar el servidor en este celular
        ServidorBrainchis.iniciar()
        val miIpWifi = NetworkUtils.getLocalIpAddress()
        
        //Conectarme a mí mismo (localhost)
        ClienteRed.listener = { mensaje ->
            runOnUiThread { procesarMensajeLobby(mensaje) }
        }
        
        ClienteRed.conectar("127.0.0.1", 65432)
        
        //Crear Diálogo
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
        
        btnCopiar.setOnClickListener{
            val clipboard = getSystemService(CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("IP Brainchis", miIpWifi)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "IP copiada al portapapeles",
                Toast.LENGTH_SHORT).show()
        }
        
        txtIp.text = "Tu IP: $miIpWifi"
        btnIniciarLobby?.isEnabled = false //Se habilitará cuando ✨haiga✨ gente
        
        //Listener para iniciar la partida
        btnIniciarLobby?.setOnClickListener{
            val cpus = when(rgCPUs?.checkedRadioButtonId)
            {
                R.id.rb1CPU -> 1
                R.id.rb2CPU -> 2
                R.id.rb3CPU -> 3
                else -> 0
            }
            
            val msg = MensajeRed(AccionRed.INICIAR_PARTIDA, cantidadCPUs = cpus)
            ClienteRed.enviar(msg)
        }
        
        btnCancelar.setOnClickListener{
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
    
    //--- LÓGICA DEL CLIENTE ---
    private fun mostrarDialogoUnirse()
    {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_unirse,
            null)
        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val etIp = view.findViewById<EditText>(R.id.etIpServidor)
        val spinner = view.findViewById<Spinner>(R.id.spinnerHistorial)
        val btnConectar = view.findViewById<Button>(R.id.btnConectarServer)
        
        //Cargar historial
        val historial = GestorServidores.obtenerServidores(this).toMutableList()
        if(historial.isEmpty())
            historial.add("Seleccionar reciente...")
        else
            etIp.setText(historial[0])
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            historial)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            )
            {
                val seleccion = historial[position]
                if(seleccion != "Seleccionar reciente...")
                    etIp.setText(seleccion)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?){}
        }
        
        btnConectar.setOnClickListener{
            val ipIngresada = etIp.text.toString().trim()
            if(ipIngresada.isEmpty() || ipIngresada.count { it == '.' } != 3)
            {
                Toast.makeText(this, "IP Inválida",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            GestorServidores.guardarServidor(this, ipIngresada)
            dialog.dismiss()
            iniciarConexionConIP(ipIngresada)
        }
        dialog.show()
    }
    
    private fun iniciarConexionConIP(ip: String)
    {
        val builder = AlertDialog.Builder(this)
        
        //Inflar el diseño personalizado
        val view = LayoutInflater.from(this).inflate(
            R.layout.dialog_conectando, null)
        builder.setView(view)
        builder.setCancelable(false) //Bloquear toque externo
        
        dialogoEspera = builder.create()
        dialogoEspera?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        //Referencias a los elementos
        val txtMensaje = view.findViewById<TextView>(R.id.txtMensajeConectando)
        val btnCancelar = view.findViewById<Button>(R.id.btnCancelarConexion)
        
        txtMensaje.text = "Buscando servidor en:\n$ip"
        
        //Listener del botón Cancelar
        btnCancelar.setOnClickListener{
            ClienteRed.cerrar()
            dialogoEspera?.dismiss()
        }
        
        dialogoEspera?.show()
        
        //Configurar Listener de Red
        ClienteRed.listener = { mensaje ->
            runOnUiThread { procesarMensajeLobby(mensaje) }
        }
        
        //Conectar
        val puerto = 65432
        ClienteRed.conectar(ip, puerto)
    }
    
    //--- LÓGICA LOBBY ---
    private fun procesarMensajeLobby(mensaje: MensajeRed)
    {
        when(mensaje.accion)
        {
            AccionRed.CONECTAR ->
            {
                //Conexión Exitosa
                miColorOnline = mensaje.colorJugador
                dialogoEspera?.dismiss()
                
                //ABRIR EL LOBBY VISUAL (Si no soy host, lo abro ahora)
                //Si soy host, el diálogo ya está abierto desde antes
                if(dialogoLobby == null || !dialogoLobby!!.isShowing)
                    mostrarVistaLobbyCliente()
            }
            
            AccionRed.ACTUALIZAR_LOBBY ->{
                mensaje.listaJugadores?.let{ actualizarUILobby(it) }
            }
            
            AccionRed.INICIAR_PARTIDA ->
            {
                dialogoLobby?.dismiss()
                dialogoEspera?.dismiss()
                
                val intent = Intent(this, JuegoActivity::class.java)
                intent.putExtra("MODO_ONLINE", true)
                intent.putExtra("MI_COLOR", miColorOnline?.name)
                intent.putExtra("TURNO_INICIAL", mensaje.resultadoDado ?: 0)
                
                //Calcular total de jugadores humanos
                val numHumanos = listaJugadoresActual.size
                val numCPUs = mensaje.cantidadCPUs ?: 0
                intent.putExtra("CANTIDAD_JUGADORES", numHumanos + numCPUs)
                intent.putExtra("CANTIDAD_HUMANOS_REALES", numHumanos)
                
                //Crear arrays para pasarle nombres y colores de los jugadores a JuegoActivity
                val nombresArray = ArrayList<String>()
                val coloresArray = ArrayList<String>()
                
                for(info in listaJugadoresActual)
                {
                    nombresArray.add(info.nombre)
                    coloresArray.add(info.color.name)
                }
                
                intent.putStringArrayListExtra("LISTA_NOMBRES", nombresArray)
                intent.putStringArrayListExtra("LISTA_COLORES", coloresArray)
                
                startActivity(intent)
            }
            
            AccionRed.ENVIAR_ESTADO ->
            {
                //ESTO OCURRE CUANDO ME RECONECTO A UNA PARTIDA EN CURSO
                dialogoLobby?.dismiss()
                dialogoEspera?.dismiss()
                
                val intent = Intent(this, JuegoActivity::class.java)
                intent.putExtra("MODO_ONLINE", true)
                intent.putExtra("MI_COLOR", miColorOnline?.name)
                
                //Bandera clave para que JuegoActivity sepa que debe pedir/cargar el estado
                //del juego
                intent.putExtra("ES_RECONEXION", true)
                
                //Aunque el estado real viene en el mensaje, JuegoActivity lo pedirá de nuevo
                intent.putExtra("CANTIDAD_JUGADORES", 4)
                intent.putExtra("NOMBRE_JUGADOR", "Jugador Reconectado")
                
                startActivity(intent)
            }
            
            AccionRed.DESCONEXION ->
            {
                dialogoEspera?.dismiss()
                dialogoLobby?.dismiss()
                Toast.makeText(this, "Error: ${mensaje.mensajeTexto}",
                    Toast.LENGTH_LONG).show()
            }
            
            else -> {}
        }
    }
    
    private fun mostrarVistaLobbyCliente()
    {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(
            R.layout.dialog_lobby_host, null)
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
        val btnCopiar = view.findViewById<android.widget.ImageButton>(R.id.btnCopiarIp)
        
        //Obtener la IP a la que se conectó el cliente desde el objeto ClienteRed
        val ipServidor = ClienteRed.obtenerIpServidor() ?: "Desconocida"
        txtIp.text = "IP del Servidor: $ipServidor"
        
        btnCopiar.setOnClickListener{
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as
                    android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("IP Brainchis",
                ipServidor)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "IP copiada", Toast.LENGTH_SHORT).show()
        }
        
        //Ocultar controles del host a los clientes
        btnIniciarLobby?.visibility = View.GONE
        for(i in 0 until (rgCPUs?.childCount ?: 0))
            rgCPUs?.getChildAt(i)?.isEnabled = false
        
        btnCancelar.text = "Salir de la sala"
        btnCancelar.setOnClickListener{
            ClienteRed.cerrar()
            dialogoLobby?.dismiss()
        }
        
        //Configuración del teclado. Por si acaso sigue sin querer funcionar
        dialogoLobby?.window?.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        dialogoLobby?.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        
        dialogoLobby?.show()
    }
    
    private fun actualizarUILobby(jugadores: List<InfoJugador>)
    {
        listaJugadoresActual = jugadores
        containerJugadores?.removeAllViews()
        
        //Llenar el lobby con los jugadores que se conecten
        for(jugador in jugadores)
        {
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_lobby_jugador, containerJugadores,
                    false)
            val imgFicha = itemView.findViewById<ImageView>(R.id.imgFichaLobby)
            val etNombre = itemView.findViewById<EditText>(R.id.etNombreLobby)
            val imgEdit = itemView.findViewById<ImageView>(R.id.imgEditarNombre)
            
            val resId = when(jugador.color)
            {
                ColorJugador.ROJO -> R.drawable.ficha_roja
                ColorJugador.VERDE -> R.drawable.ficha_verde
                ColorJugador.AZUL -> R.drawable.ficha_azul
                ColorJugador.AMARILLO -> R.drawable.ficha_amarilla
            }
            imgFicha.setImageResource(resId)
            etNombre.setText(jugador.nombre)
            
            //--- LÓGICA DE EDICIÓN DEL NOMBRE ---
            if(jugador.color == miColorOnline)
            {
                //SI SOY YO:
                //Mostrar el lápiz
                imgEdit.visibility = View.VISIBLE
                
                //Configurar click en el lápiz para abrir el diálogo
                imgEdit.setOnClickListener{
                    mostrarDialogoCambiarNombre(jugador.nombre)
                }
                
                //Texto habilitado visualmente pero no editable directamente
                etNombre.isEnabled = true
                etNombre.setTextColor(android.graphics.Color.parseColor("#2D3748"))
                etNombre.isClickable = false
                etNombre.isFocusable = false
            }
            else
            {
                //SI ES OTRO JUGADOR:
                //Ocultar el lápiz
                imgEdit.visibility = View.GONE
                
                //Texto deshabilitado visualmente
                etNombre.isEnabled = false
                etNombre.setTextColor(android.graphics.Color.GRAY)
            }
            
            containerJugadores?.addView(itemView)
        }
        
        //Lógica de RadioButtons (solo visual para el host). Solo se pueden seleccionar los
        //RadioButtons que sean válidos y también se ajustan cada que algún jugador se une.
        //En sí lo que hace es deshabilitar ciertos RadioButtons que permiten seleccionar cuantos
        //CPUs se quieren en la partida. Si hay 3 jugadores en la partida, esta parte evita que
        //se pueda seleccionar más de 1 CPU porque El máximo de jugadores es 4
        if(btnIniciarLobby?.visibility == View.VISIBLE)
        {
            val numHumanos = jugadores.size
            val espaciosLibres = 4 - numHumanos
            
            val rb0 = dialogoLobby?.findViewById<RadioButton>(R.id.rb0CPU)
            val rb1 = dialogoLobby?.findViewById<RadioButton>(R.id.rb1CPU)
            val rb2 = dialogoLobby?.findViewById<RadioButton>(R.id.rb2CPU)
            val rb3 = dialogoLobby?.findViewById<RadioButton>(R.id.rb3CPU)
            
            //Habilitar según espacios disponibles
            rb1?.isEnabled = espaciosLibres >= 1
            rb2?.isEnabled = espaciosLibres >= 2
            rb3?.isEnabled = espaciosLibres >= 3
            
            //Corrección automática de selección si excede el límite
            val idSel = rgCPUs?.checkedRadioButtonId
            var cpus = 0
            
            if(idSel == R.id.rb1CPU)
                cpus = 1
            if(idSel == R.id.rb2CPU)
                cpus = 2
            if(idSel == R.id.rb3CPU)
                cpus = 3
            
            if(cpus > espaciosLibres)
            {
                //Forzar selección válida
                when(espaciosLibres)
                {
                    0 -> rb0?.isChecked = true
                    1 -> rb1?.isChecked = true
                    2 -> rb2?.isChecked = true
                }
                
                //Recalcular cpus tras el cambio
                cpus = if(cpus > espaciosLibres) espaciosLibres else cpus
            }
            
            //Activar botón "INICIAR" solo si hay al menos 2 jugadores
            val total = numHumanos + cpus
            btnIniciarLobby?.isEnabled = (total >= 2)
            
            //Listener para actualizar el botón cuando cambie la selección de CPU
            rgCPUs?.setOnCheckedChangeListener{ _, checkedId ->
                val cpusNew = when(checkedId)
                {
                    R.id.rb1CPU -> 1
                    R.id.rb2CPU -> 2
                    R.id.rb3CPU -> 3
                    else -> 0
                }
                val totalNew = listaJugadoresActual.size + cpusNew
                btnIniciarLobby?.isEnabled = (totalNew >= 2)
            }
        }
    }
    
    private fun mostrarDialogoCambiarNombre(nombreActual: String)
    {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(
            R.layout.dialog_cambiar_nombre, null)
        builder.setView(view)
        
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val etNombre = view.findViewById<EditText>(R.id.etNuevoNombre)
        val btnGuardar = view.findViewById<Button>(R.id.btnGuardarNombre)
        val btnCancelar = view.findViewById<Button>(R.id.btnCancelarNombre)
        
        etNombre.setText(nombreActual)
        etNombre.selectAll()
        
        btnGuardar.setOnClickListener{
            val nuevoNombre = etNombre.text.toString().trim()
            
            if(nuevoNombre.isNotEmpty() && nuevoNombre != nombreActual)
                ClienteRed.enviar(MensajeRed(AccionRed.CAMBIAR_NOMBRE, nombreJugador = nuevoNombre))
            
            dialog.dismiss()
        }
        
        btnCancelar.setOnClickListener{
            dialog.cancel()
        }
        
        //Configuración para asegurar que el teclado aparezca
        dialog.window?.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        
        dialog.show()
        etNombre.requestFocus()
    }
}