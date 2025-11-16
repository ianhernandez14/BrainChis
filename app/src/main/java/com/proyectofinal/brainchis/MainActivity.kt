package com.proyectofinal.brainchis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity(), Tablero.OnCasillaTocadaListener
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

    //Esta variable es nomás para filtrar los logs
    private val TAG = "ParchisDEBUG"

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_juego)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //Inicializar los objetos
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

        //Configurar la partida con 4 jugadores
        val colores = listOf(ColorJugador.ROJO, ColorJugador.VERDE, ColorJugador.AMARILLO,
            ColorJugador.AZUL)
        gestor.iniciarJuego(colores)

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

        tableroView.actualizarEstadoJuego(gestor.jugadores)

        //Preparar el primer turno
        prepararSiguienteTurno()
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
        //Ocultar todos los dados
        dadoRojo.visibility = View.INVISIBLE
        dadoVerde.visibility = View.INVISIBLE
        dadoAzul.visibility = View.INVISIBLE
        dadoAmarillo.visibility = View.INVISIBLE

        //Quitarles el listener por si acaso
        dadoRojo.setOnClickListener(null)
        dadoVerde.setOnClickListener(null)
        dadoAzul.setOnClickListener(null)
        dadoAmarillo.setOnClickListener(null)

        //Ver a quién le toca
        val colorTurno = gestor.jugadorActual.color

        //Mostrar el dado correcto y asignarle el listener
        val dadoActivo = when(colorTurno)
        {
            ColorJugador.ROJO -> dadoRojo
            ColorJugador.VERDE -> dadoVerde
            ColorJugador.AZUL -> dadoAzul
            ColorJugador.AMARILLO -> dadoAmarillo
        }

        dadoActivo.visibility = View.VISIBLE
        dadoActivo.setImageResource(R.drawable.dado_signo)

        //Cuando hagan clic en el dado activo se llama a la función lanzarDado
        dadoActivo.setOnClickListener{
            lanzarDado()
        }
    }

    private fun lanzarDado()
    {
        //Solo lanzar si estamos esperando
        if(gestor.estadoJuego != EstadoJuego.ESPERANDO_LANZAMIENTO)
            return

        ultimoResultadoDado = gestor.lanzarDado()

        //Obtener el dado que está activo
        val dadoActivo = when(gestor.jugadorActual.color)
        {
            ColorJugador.ROJO -> dadoRojo
            ColorJugador.VERDE -> dadoVerde
            ColorJugador.AZUL -> dadoAzul
            ColorJugador.AMARILLO -> dadoAmarillo
        }

        //Obtener el recurso de imagen correcto
        val recursoDado = obtenerRecursoDado(ultimoResultadoDado)

        //Mostrar el número en el dado
        dadoActivo.setImageResource(recursoDado)

        //Desactivar el clic en el dado para que no pueda volver a lanzar hasta el siguiente turno
        dadoActivo.setOnClickListener(null)

        //Si el gestor no está esperando movimiento (porque no hay movimientos, o fue el 3er seis),
        //el turno ya pasó
        if(gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)
        {
            tableroView.actualizarEstadoJuego(gestor.jugadores)
            prepararSiguienteTurno()
        }
    }

    override fun onCasillaTocada(col: Int, fila: Int)
    {
        //Validar que estemos esperando un movimiento
        if(gestor.estadoJuego != EstadoJuego.ESPERANDO_MOVIMIENTO)
            return //No es momento de mover

        //Obtener la lista de fichas que SÍ podemos mover
        val movimientosPosibles = gestor.obtenerMovimientosPosibles(ultimoResultadoDado)
        if(movimientosPosibles.isEmpty())
            return //No hay nada que mover

        //Traducir el toque
        val posGlobalTocada = tableroView.obtenerPosicionGlobalDeCasilla(col, fila)
        var fichaParaMover: Ficha? = null

        if(posGlobalTocada != -1)
        {
            //--- CASO A: Se tocó una casilla JUGABLE (en el camino o meta) ---

            //Buscamos si una de nuestras fichas movibles está en esa casilla
            fichaParaMover = movimientosPosibles.find{
                it.posicionGlobal == posGlobalTocada
            }

            if(fichaParaMover != null)
                Log.i(TAG, "FICHA SELECCIONADA: (En Camino/Meta) ${fichaParaMover.color} ID ${fichaParaMover.id} (En Pos ${fichaParaMover.posicionGlobal})")

        }
        else{
            //--- CASO B: Se tocó una casilla NO JUGABLE (¿Base?) ---

            //Solo nos importa si el dado fue 6
            if(ultimoResultadoDado == 6)
            {
                //Comprobamos si el toque fue en la base del jugador actual
                val colorBaseTocada = tableroView.obtenerColorBaseTocada(col, fila)

                if(colorBaseTocada == gestor.jugadorActual.color)
                {
                    //Si tocó su propia base con un 6, buscamos si hay una ficha "movible" que esté
                    //EN_BASE
                    fichaParaMover = movimientosPosibles.find{ it.estado == EstadoFicha.EN_BASE }

                    if(fichaParaMover != null)
                        Log.i(TAG, "FICHA SELECCIONADA: (Desde Base) ${fichaParaMover.color} ID ${fichaParaMover.id}")
                }
            }
        }

        //Mover la ficha (si se encontró una válida)
        if(fichaParaMover != null)
        {
            gestor.moverFicha(fichaParaMover, ultimoResultadoDado)

            //Actualizar lo visual
            tableroView.actualizarEstadoJuego(gestor.jugadores)

            //Comprobar si el juego terminó
            if(gestor.estadoJuego == EstadoJuego.JUEGO_TERMINADO)
            {
                //TODO: Mostrar pantalla de victoria
                Log.d(TAG, "¡JUEGO TERMINADO! Ha ganado ${gestor.jugadorActual.color}")
            }
            else if(gestor.estadoJuego == EstadoJuego.ESPERANDO_LANZAMIENTO)
            {
                //Si el gestor está esperando un nuevo lanzamiento (ej. sacó 6 o hizo una kill)
                prepararSiguienteTurno()
            }
        }
    }

}