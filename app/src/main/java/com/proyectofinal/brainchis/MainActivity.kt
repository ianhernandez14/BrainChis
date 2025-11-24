package com.proyectofinal.brainchis

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // --- VARIABLES GLOBALES (Vistas) ---
    private lateinit var btnCrear: Button
    private lateinit var btnUnirse: Button
    private lateinit var btnPuntajes: Button
    private lateinit var btnAyuda: Button

    // --- VARIABLES DE LÓGICA ---
    private var dialogoEspera: AlertDialog? = null
    private var miColorOnline: ColorJugador? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- 1. INICIALIZACIÓN DE VISTAS ---
        btnCrear = findViewById(R.id.btnCrear)
        btnUnirse = findViewById(R.id.btnUnirse)
        btnPuntajes = findViewById(R.id.btnPuntajes)
        btnAyuda = findViewById(R.id.btnAyudaMenu)

        // --- 2. CONFIGURACIÓN DE LISTENERS ---

        // Botón CREAR -> Abre configuración local
        btnCrear.setOnClickListener {
            mostrarDialogoConfiguracion()
        }

        // Botón UNIRSE -> Conexión al Servidor (Multijugador)
        btnUnirse.setOnClickListener {
            conectarServidorPrueba()
        }

        // Botón PUNTAJES -> Abre pantalla de puntajes
        btnPuntajes.setOnClickListener {
            val intent = Intent(this, PuntajeActivity::class.java)
            startActivity(intent)
        }

        // Botón AYUDA -> Abre pantalla de ayuda
        btnAyuda.setOnClickListener {
            val intent = Intent(this, AyudaActivity::class.java)
            startActivity(intent)
        }
    }

    // Limpieza al cerrar la app
    override fun onDestroy() {
        super.onDestroy()
        // Cerramos la conexión si el usuario se sale de la app desde el menú
        ClienteRed.cerrar()
    }

    // --- MÉTODOS PRIVADOS ---

    private fun conectarServidorPrueba() {
        // 1. Mostrar diálogo de espera (Bloqueante)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Conectando...")
        builder.setMessage("Buscando servidor...")
        builder.setCancelable(false) // No se puede cerrar tocando afuera
        builder.setNegativeButton("Cancelar") { _, _ ->
            ClienteRed.cerrar() // Cancelar conexión si el usuario se arrepiente
        }
        dialogoEspera = builder.create()
        dialogoEspera?.show()

        // 2. Asignar el listener al Singleton ClienteRed
        // Esto define qué hacemos cuando llega un mensaje en ESTA pantalla
        ClienteRed.listener = { mensaje ->
            runOnUiThread {
                procesarMensajeLobby(mensaje)
            }
        }

        // 3. Conectar
        val miIP = "192.168.1.190" // Tu IP local (Asegúrate que sea la correcta)
        val puerto = 65432

        ClienteRed.conectar(miIP, puerto)
    }

    private fun procesarMensajeLobby(mensaje: MensajeRed) {
        when(mensaje.accion) {
            AccionRed.CONECTAR -> {
                // El servidor nos aceptó y nos asignó un color
                miColorOnline = mensaje.colorJugador
                dialogoEspera?.setTitle("¡Conectado!")
                dialogoEspera?.setMessage("Eres el equipo $miColorOnline.\n\nEsperando a más jugadores para iniciar...")
            }

            AccionRed.INICIAR_PARTIDA -> {
                // ¡A JUGAR!
                dialogoEspera?.dismiss()

                val intent = Intent(this, JuegoActivity::class.java)

                // Pasamos flags para que el juego sepa que es ONLINE
                intent.putExtra("MODO_ONLINE", true)
                intent.putExtra("MI_COLOR", miColorOnline?.name)

                // En el futuro, el servidor podría mandar cuántos jugadores son.
                // Por ahora, el script de prueba inicia con 2.
                intent.putExtra("CANTIDAD_JUGADORES", 2)
                intent.putExtra("HUMANO_JUEGA", true)

                // Nombre del jugador (podrías pedirlo antes, aquí ponemos uno genérico)
                intent.putExtra("NOMBRE_JUGADOR", "Jugador Android")

                startActivity(intent)
            }

            AccionRed.DESCONEXION -> {
                dialogoEspera?.dismiss()
                Toast.makeText(this, "Error: ${mensaje.mensajeTexto}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

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
            // 1. Determinar cantidad
            val cantidad = when {
                radio2.isChecked -> 2
                radio3.isChecked -> 3
                else -> 4
            }

            // 2. Determinar si el Humano juega
            val humanoJuega = !checkSoloIA.isChecked

            // 3. Obtener el nombre
            var nombreUsuario = etNombre.text.toString().trim()
            if (nombreUsuario.isEmpty()) nombreUsuario = "Jugador 1"

            // 4. Lanzar JuegoActivity con estos datos
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