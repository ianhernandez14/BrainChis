package com.proyectofinal.brainchis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class Tablero: View
{
    //Se llama cuando el usuario toca una celda de la cuadrícula
    interface OnCasillaTocadaListener{
        fun onCasillaTocada(col: Int, fila: Int)
    }

    lateinit var onCasillaTocadaListener: MainActivity
    private var listener: OnCasillaTocadaListener? = null

    private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pResaltado = Paint(Paint.ANTI_ALIAS_FLAG)

    //Variables para almacenar los colores cargados
    private var colorBaseRojo: Int = 0
    private var colorBaseVerde: Int = 0
    private var colorBaseAzul: Int = 0
    private var colorBaseAmarillo: Int = 0
    private var colorBaseGris: Int = 0
    private var listaJugadores: List<Jugador> = emptyList()
    private var fichasMovibles: List<Ficha> = emptyList()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init
    {
        pBorde.style = Paint.Style.STROKE
        pBorde.color = Color.BLACK
        pBorde.strokeWidth = 2f

        gridPaint.style = Paint.Style.STROKE
        gridPaint.color = Color.DKGRAY
        gridPaint.strokeWidth = 2f

        pResaltado.style = Paint.Style.STROKE
        pResaltado.color = Color.MAGENTA
        pResaltado.strokeWidth = 8f

        try{
            colorBaseRojo = ContextCompat.getColor(context, R.color.rojo)
            colorBaseVerde = ContextCompat.getColor(context, R.color.verde)
            colorBaseAzul = ContextCompat.getColor(context, R.color.azul)
            colorBaseAmarillo = ContextCompat.getColor(context, R.color.amarillo)
            colorBaseGris = ContextCompat.getColor(context, R.color.gris)
        }catch (e: Exception){
            e.printStackTrace()
        }
    }

    companion object
    {
        private val TAG = "ParchisDEBUG"

        //Mapa para el camino principal (Posición 1 a 52)
        //(índice 0 = pos 1, índice 51 = pos 52)
        private val CAMINO_PRINCIPAL = listOf(
            (1 to 6), (2 to 6), (3 to 6), (4 to 6), (5 to 6), //1-5
            (6 to 5), (6 to 4), (6 to 3), (6 to 2), (6 to 1), (6 to 0), //6-11
            (7 to 0), (8 to 0), //12-13
            (8 to 1), (8 to 2), (8 to 3), (8 to 4), (8 to 5), //14-18 (Salida Verde)
            (9 to 6), (10 to 6), (11 to 6), (12 to 6), (13 to 6), (14 to 6), //19-24
            (14 to 7), (14 to 8), //25-26
            (13 to 8), (12 to 8), (11 to 8), (10 to 8), (9 to 8), //27-31 (Salida Amarillo)
            (8 to 9), (8 to 10), (8 to 11), (8 to 12), (8 to 13), (8 to 14), //32-37
            (7 to 14), (6 to 14), //38-39
            (6 to 13), (6 to 12), (6 to 11), (6 to 10), (6 to 9), //40-44 (Salida Azul)
            (5 to 8), (4 to 8), (3 to 8), (2 to 8), (1 to 8), (0 to 8), //45-50
            (0 to 7), (0 to 6) //51-52
        )

        //Mapas para las metas (6 casillas cada una)

        //Meta Roja: Col 1-6, Fila 7
        private val META_ROJA = listOf(
            (1 to 7), (2 to 7), (3 to 7), (4 to 7), (5 to 7), (6 to 7)
        ) //Pos 53-58

        //Meta Verde: Col 7, Fila 1-6
        private val META_VERDE = listOf(
            (7 to 1), (7 to 2), (7 to 3), (7 to 4), (7 to 5), (7 to 6)
        ) //Pos 59-64

        //Meta Amarilla: Col 13-8, Fila 7
        private val META_AMARILLA = listOf(
            (13 to 7), (12 to 7), (11 to 7), (10 to 7), (9 to 7), (8 to 7)
        ) //Pos 65-70

        //Meta Azul: Col 7, Fila 13-8
        private val META_AZUL = listOf(
            (7 to 13), (7 to 12), (7 to 11), (7 to 10), (7 to 9), (7 to 8)
        ) //Pos 71-76

        //Coordenadas relativas (gridX, gridY) para las 4 fichas en la base
        private val POS_BASE = listOf(
            (2.5f to 2.5f), //Ficha 1
            (3.5f to 2.5f), //Ficha 2
            (2.5f to 3.5f), //Ficha 3
            (3.5f to 3.5f)  //Ficha 4
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
    {
        val ancho = MeasureSpec.getSize(widthMeasureSpec)
        val alto = MeasureSpec.getSize(heightMeasureSpec)
        val lado = minOf(ancho, alto)
        setMeasuredDimension(ancho, lado) //Tablero cuadrado
    }

    override fun onDraw(canvas: Canvas)
    {
        //Obtener el ancho de la pantalla en pixeles
        val ancho = measuredWidth.toFloat()
        //Cada casilla debe medir ciertos pixeles, por lo que se divide el ancho de la pantalla
        //entre 15 y así obtener cuantos pixeles es para cada casilla
        val casilla = ancho / 15f

        //Fondo blanco
        pRelleno.style = Paint.Style.FILL
        pRelleno.color = Color.WHITE
        canvas.drawRect(0f, 0f, ancho, ancho, pRelleno)

        //Dibujar cuadrícula. Primero se dibujan las líneas verticales y luego las horizontales
        for(i in 0..15)
        {
            canvas.drawLine(i * casilla, 0f, i * casilla, ancho, gridPaint)
            canvas.drawLine(0f, i * casilla, ancho, i * casilla, gridPaint)
        }

        //Dibujar las bases de los colores
        dibujarBase(canvas, 0f, 0f, colorBaseRojo) //superior izquierda
        dibujarBase(canvas, ancho - casilla*6, 0f, colorBaseVerde) //superior derecha
        dibujarBase(canvas, 0f, ancho - casilla*6, colorBaseAzul) //inferior izquierda
        dibujarBase(canvas, ancho - casilla*6, ancho - casilla*6, colorBaseAmarillo) //inferior derecha

        dibujarCentro(canvas, casilla)
        dibujarCaminos(canvas, casilla)

        dibujarFichas(canvas)

        //Dibujar borde del tablero
        canvas.drawRect(0f, 0f, ancho, ancho, pBorde)
    }

    private fun dibujarBase(canvas: Canvas, x: Float, y: Float, color: Int)
    {
        val casilla = measuredWidth / 15f
        pRelleno.color = color
        pRelleno.style = Paint.Style.FILL

        canvas.drawRect(x, y, x + casilla*6, y + casilla*6, pRelleno)

        //Bordes
        canvas.drawRect(x, y, x + casilla*6, y + casilla*6, pBorde)

        //--- Dibujar cuadro blanco con borde y fichas ---
        val bordeTam = casilla * 0.5f
        val cuadroTam = casilla * 2
        val inicioX = x + (casilla * 3) - cuadroTam / 2 - bordeTam
        val inicioY = y + (casilla * 3) - cuadroTam / 2 - bordeTam
        val finX = inicioX + cuadroTam + bordeTam * 2
        val finY = inicioY + cuadroTam + bordeTam * 2

        //Dibujar borde blanco exterior
        pRelleno.color = Color.WHITE
        canvas.drawRect(inicioX, inicioY, finX, finY, pRelleno)

        //Dibujar cuadro interno blanco (2x2)
        val cuadroInteriorX = inicioX + bordeTam
        val cuadroInteriorY = inicioY + bordeTam
        val cuadroInteriorFinX = cuadroInteriorX + cuadroTam
        val cuadroInteriorFinY = cuadroInteriorY + cuadroTam
        canvas.drawRect(cuadroInteriorX, cuadroInteriorY, cuadroInteriorFinX, cuadroInteriorFinY, pRelleno)
    }

    private fun dibujarEstrella(canvas: Canvas, x: Float, y: Float, casilla: Float, color: Int)
    {
        val estrella = Path()
        val cX = x + casilla / 2 //Centro X
        val cY = y + casilla / 2 //Centro Y
        val radioExterior = casilla * 0.4f
        val radioInterior = casilla * 0.15f
        val numPuntas = 5

        //El punto de partida es arriba (ángulo PI/2)
        val startAngle = PI / 2.0

        for(i in 0 until numPuntas)
        {
            //Ángulo de la punta exterior
            val anguloExterior = startAngle - i * 2 * PI / numPuntas
            val puntoX = cX + radioExterior * cos(anguloExterior).toFloat()
            val puntoY = cY - radioExterior * sin(anguloExterior).toFloat()

            if(i == 0)
                estrella.moveTo(puntoX, puntoY)
            else
                estrella.lineTo(puntoX, puntoY)

            //Ángulo del punto interior
            val anguloInterior = startAngle - (i * 2 * PI / numPuntas + PI / numPuntas)
            val puntoInteriorX = cX + radioInterior * cos(anguloInterior).toFloat()
            val puntoInteriorY = cY - radioInterior * sin(anguloInterior).toFloat()
            estrella.lineTo(puntoInteriorX, puntoInteriorY)
        }
        estrella.close()

        pRelleno.color = color
        canvas.drawPath(estrella, pRelleno)

        //Dibujar el borde de la estrella
        pBorde.strokeWidth = 1f
        canvas.drawPath(estrella, pBorde)
    }

    private fun dibujarCaminos(canvas: Canvas, casilla: Float)
    {
        //El tablero es 15x15. Las coordenadas van de 0 a 14
        val colorEstrella = Color.WHITE

        //Camino Verde (Filas 1-5, Columna 7)
        pRelleno.color = colorBaseVerde
        for (i in 1..5) {
            val left = 7 * casilla
            val top = i * casilla
            val right = 8 * casilla
            val bottom = (i + 1) * casilla
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Camino Rojo (Columnas 1-5, Fila 7)
        pRelleno.color = colorBaseRojo
        for (i in 1..5) {
            val left = i * casilla
            val top = 7 * casilla
            val right = (i + 1) * casilla
            val bottom = 8 * casilla
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Camino Amarillo (Columnas 9-13, Fila 7)
        pRelleno.color = colorBaseAmarillo
        for (i in 9..13) {
            val left = i * casilla
            val top = 7 * casilla
            val right = (i + 1) * casilla
            val bottom = 8 * casilla
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Camino Azul (Filas 9-13, Columna 7)
        pRelleno.color = colorBaseAzul
        for (i in 9..13) {
            val left = 7 * casilla
            val top = i * casilla
            val right = 8 * casilla
            val bottom = (i + 1) * casilla
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla Verde (8, 1) - Primera casilla después de la base
        run{
            val left = 8 * casilla
            val top = 1 * casilla
            val right = 9 * casilla
            val bottom = 2 * casilla
            pRelleno.color = colorBaseVerde
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla Roja (1, 6)
        run{
            val left = 1 * casilla
            val top = 6 * casilla
            val right = 2 * casilla
            val bottom = 7 * casilla
            pRelleno.color = colorBaseRojo
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla Amarilla (13, 8)
        run{
            val left = 13 * casilla
            val top = 8 * casilla
            val right = 14 * casilla
            val bottom = 9 * casilla
            pRelleno.color = colorBaseAmarillo
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla Azul (6, 13)
        run{
            val left = 6 * casilla
            val top = 13 * casilla
            val right = 7 * casilla
            val bottom = 14 * casilla
            pRelleno.color = colorBaseAzul
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla (6, 2)
        run{
            val left = 6 * casilla
            val top = 2 * casilla
            val right = 7 * casilla
            val bottom = 3 * casilla
            pRelleno.color = colorBaseGris
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla (2, 8)
        run{
            val left = 2 * casilla
            val top = 8 * casilla
            val right = 3 * casilla
            val bottom = 9 * casilla
            pRelleno.color = colorBaseGris
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla (12, 6)
        run{
            val left = 12 * casilla
            val top = 6 * casilla
            val right = 13 * casilla
            val bottom = 7 * casilla
            pRelleno.color = colorBaseGris
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla (8, 12)
        run{
            val left = 8 * casilla
            val top = 12 * casilla
            val right = 9 * casilla
            val bottom = 13 * casilla
            pRelleno.color = colorBaseGris
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Estrella Roja (8, 1) - Coordenadas de las estrellas
        dibujarEstrella(canvas, 8 * casilla, 1 * casilla, casilla, colorEstrella)
        //Estrella Amarilla (1, 6)
        dibujarEstrella(canvas, 1 * casilla, 6 * casilla, casilla, colorEstrella)
        //Estrella Verde (13, 8)
        dibujarEstrella(canvas, 13 * casilla, 8 * casilla, casilla, colorEstrella)
        //Estrella Azul (6, 13)
        dibujarEstrella(canvas, 6 * casilla, 13 * casilla, casilla, colorEstrella)

        //Resto de casillas especiales. Cada una va 5 posiciones antes de las casillas especiales
        //que pertenecen a la casilla inicial de cada jugador cada que saca su primer ficha
        dibujarEstrella(canvas, 6 * casilla, 2 * casilla, casilla, colorEstrella)
        dibujarEstrella(canvas, 2 * casilla, 8 * casilla, casilla, colorEstrella)
        dibujarEstrella(canvas, 12 * casilla, 6 * casilla, casilla, colorEstrella)
        dibujarEstrella(canvas, 8 * casilla, 12 * casilla, casilla, colorEstrella)
    }

    private fun dibujarCentro(canvas: Canvas, casilla: Float)
    {
        val x0 = 6 * casilla
        val y0 = 6 * casilla
        val lado = casilla * 3

        //Cuadrado central
        pRelleno.color = Color.WHITE
        canvas.drawRect(x0, y0, x0 + lado, y0 + lado, pRelleno)
        canvas.drawRect(x0, y0, x0 + lado, y0 + lado, pBorde)

        val centroX = x0 + lado / 2
        val centroY = y0 + lado / 2
        val path = Path()

        //Triángulo Rojo (Arriba)
        pRelleno.color = colorBaseVerde //Relleno
        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x0 + lado, y0)
        path.lineTo(centroX, centroY)
        path.close()
        canvas.drawPath(path, pRelleno)
        //Borde (para que tenga consistencia con el borde de las casillas)
        canvas.drawPath(path, pBorde)

        //Triángulo Verde (Derecha)
        pRelleno.color = colorBaseAmarillo //Relleno
        path.reset()
        path.moveTo(x0 + lado, y0)
        path.lineTo(x0 + lado, y0 + lado)
        path.lineTo(centroX, centroY)
        path.close()
        canvas.drawPath(path, pRelleno)
        //Borde
        canvas.drawPath(path, pBorde)

        //Triángulo Azul (Abajo)
        pRelleno.color = colorBaseAzul //Relleno
        path.reset()
        path.moveTo(x0, y0 + lado)
        path.lineTo(x0 + lado, y0 + lado)
        path.lineTo(centroX, centroY)
        path.close()
        canvas.drawPath(path, pRelleno)
        //Borde
        canvas.drawPath(path, pBorde)

        //Triángulo Amarillo (Izquierda)
        pRelleno.color = colorBaseRojo //Relleno
        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x0, y0 + lado)
        path.lineTo(centroX, centroY)
        path.close()
        canvas.drawPath(path, pRelleno)
        //Borde
        canvas.drawPath(path, pBorde)
    }

    fun actualizarEstadoJuego(jugadores: List<Jugador>)
    {
        this.listaJugadores = jugadores
        invalidate()
    }

    fun setOnCasillaTocadaListener(listener: OnCasillaTocadaListener){
        this.listener = listener
    }

    private fun dibujarFichaIndividual(canvas: Canvas, casilla: Float, gridX: Float, gridY: Float,
        color: ColorJugador, factorEscala: Float, esMovible: Boolean)
    {
        //Determinar la imagen según el color
        val nombreRecurso = when(color)
        {
            ColorJugador.ROJO -> "ficha_roja"
            ColorJugador.VERDE -> "ficha_verde"
            ColorJugador.AZUL -> "ficha_azul"
            ColorJugador.AMARILLO -> "ficha_amarilla"
        }

        val resId = resources.getIdentifier(nombreRecurso, "drawable",
            context.packageName)
        val imagen = ContextCompat.getDrawable(context, resId) ?: return

        //--- Lógica de escalado ---
        val proporcionOriginal = 225f / 375f //ancho / alto

        //Usamos el tamaño de la casilla como base
        val anchoFichaBase = if(casilla / casilla < proporcionOriginal)
            casilla
        else
            casilla * proporcionOriginal

        val altoFichaBase = anchoFichaBase / proporcionOriginal
        val anchoFichaReal = anchoFichaBase * factorEscala
        val altoFichaReal = altoFichaBase * factorEscala

        //Calcular centro
        val cX = gridX * casilla
        val cY = gridY * casilla

        val left = cX - anchoFichaReal / 2
        val top = cY - altoFichaReal / 2
        val right = cX + anchoFichaReal / 2
        val bottom = cY + altoFichaReal / 2

        imagen.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        imagen.draw(canvas)

        if(esMovible)
        {
            //Dibujar un círculo de resaltado alrededor de la ficha
            val radioResaltado = (altoFichaReal / 2f) * 1.1f
            canvas.drawCircle(cX, cY, radioResaltado, pResaltado)
        }
    }

    private fun dibujarFichas(canvas: Canvas)
    {
        val casilla = measuredWidth / 15f

        for(jugador in listaJugadores)
        {
            for (ficha in jugador.fichas)
            {
                var gridX = -1f
                var gridY = -1f
                var offsetX = 0f
                var offsetY = 0f

                when(ficha.estado)
                {
                    EstadoFicha.EN_JUEGO ->
                    {
                        val (x, y) = CAMINO_PRINCIPAL[ficha.posicionGlobal - 1]
                        gridX = x + 0.5f
                        gridY = y + 0.5f
                    }

                    EstadoFicha.EN_META ->
                    {
                        when(ficha.color)
                        {
                            ColorJugador.ROJO ->
                            { //Pos 53-58
                                val (x, y) = META_ROJA[ficha.posicionGlobal - 53]
                                gridX = x + 0.5f
                                gridY = y + 0.5f
                            }
                            ColorJugador.VERDE ->
                            {
                                val (x, y) = META_VERDE[ficha.posicionGlobal - 59]
                                gridX = x + 0.5f
                                gridY = y + 0.5f
                            }
                            ColorJugador.AMARILLO ->
                            {
                                //Pos 65-70
                                val (x, y) = META_AMARILLA[ficha.posicionGlobal - 65]
                                gridX = x + 0.5f
                                gridY = y + 0.5f
                            }
                            ColorJugador.AZUL ->
                            {   //Pos 71-76
                                val (x, y) = META_AZUL[ficha.posicionGlobal - 71]
                                gridX = x + 0.5f
                                gridY = y + 0.5f
                            }
                        }
                    }

                    EstadoFicha.EN_BASE ->
                    {
                        val (baseOffsetX, baseOffsetY) = when(ficha.color)
                        {
                            ColorJugador.ROJO -> (0f to 0f)
                            ColorJugador.VERDE -> (9f to 0f)
                            ColorJugador.AZUL -> (0f to 9f)
                            ColorJugador.AMARILLO -> (9f to 9f)
                        }

                        val (relX, relY) = POS_BASE[ficha.id - 1]
                        gridX = baseOffsetX + relX
                        gridY = baseOffsetY + relY
                    }
                }

                //--- LÓGICA DE AGRUPACIÓN ---
                /*Esto es para poder ajustar el tamaño y posición de las fichas cuando estén
                agrupadas en una misma casilla.
                Solo aplicamos desplazamiento si la ficha no está en la base*/

                var factorEscalaDinamico = 1.3f

                if(ficha.estado != EstadoFicha.EN_BASE)
                {
                    val pila = obtenerPilaEnCasilla(ficha.posicionGlobal)
                    val total = pila.size

                    //Si hay más de 1 ficha, calculamos su posición
                    if(total > 1)
                    {
                        factorEscalaDinamico = 0.85f
                        val indice = pila.indexOf(ficha)
                        //18% del ancho de la casilla
                        val factorDespl = 0.18f

                        when(indice)
                        {
                            0 ->
                            {
                                //Arriba-Izquierda
                                offsetX = -factorDespl
                                offsetY = -factorDespl
                            }
                            1 ->
                            {
                                //Arriba-Derecha
                                offsetX = factorDespl
                                offsetY = -factorDespl
                            }
                            2 ->
                            {
                                //Abajo-Izquierda
                                offsetX = -factorDespl
                                offsetY = factorDespl
                            }
                            3 ->
                            {
                                //Abajo-Derecha
                                offsetX = factorDespl
                                offsetY = factorDespl
                            }
                            //Si hay 5 o más fichas, las distribuimos en un círculo
                            else ->
                            {
                                //Reducimos la escala aún más para que quepan
                                factorEscalaDinamico = 0.7f

                                //El radio del círculo donde se colocarán las fichas
                                val radioCirculo = 0.2f

                                //Calculamos el ángulo para esta ficha
                                val angulo = 2 * PI * indice / total

                                //Calculamos el desplazamiento (offsetX, offsetY) usando trigonometría
                                //cos(angulo) para X, sin(angulo) para Y
                                offsetX = (radioCirculo * cos(angulo)).toFloat()
                                offsetY = (radioCirculo * sin(angulo)).toFloat()
                            }
                        }
                    }
                }

                val esMovible = fichasMovibles.contains(ficha)

                if(gridX != -1f)
                {
                    //Aplicamos el desplazamiento (offsetX/offsetY) al centro
                    dibujarFichaIndividual(canvas, casilla, gridX + offsetX, gridY + offsetY,
                        ficha.color, factorEscalaDinamico, esMovible)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean
    {
        //Solo nos interesa el evento de tocar, no el de arrastrar
        if(event.action != MotionEvent.ACTION_DOWN)
            return true

        //Obtener el tamaño de una casilla
        val casilla = measuredWidth / 15f

        //Calcular la columna y fila tocadas
        val colTocada = (event.x / casilla).toInt()
        val filaTocada = (event.y / casilla).toInt()

        Log.v(TAG, "--- Tablero.onTouchEvent ---")
        Log.v(TAG, "Toque en Píxeles (x:${event.x}, y:${event.y}) -> (Col: $colTocada, " +
                "Fila: $filaTocada)")

        //Asegurarse de que el toque fue dentro del tablero (0-14)
        if(colTocada in 0..14 && filaTocada in 0..14)
        {
            //Avisar al listener (a MainActivity)
            listener?.onCasillaTocada(colTocada, filaTocada)
        }

        return true //Devolver true para indicar que se manejó el toque
    }

    /*Devuelve la 'posicionGlobal' (1-76) que corresponde a una celda (col, fila) de la cuadrícula.
     Devuelve -1 si no es una casilla jugable*/
    fun obtenerPosicionGlobalDeCasilla(col: Int, fila: Int): Int
    {
        val parTocado = (col to fila)

        //Buscar en el camino principal (1-52)
        var indice = CAMINO_PRINCIPAL.indexOf(parTocado)

        if(indice != -1)
            return indice + 1 //+1 porque el índice 0 es la posición 1

        //Buscar en Meta Roja (Pos 53-58)
        indice = META_ROJA.indexOf(parTocado)
        if(indice != -1)
            return indice + 53 //Base 53 (53-53=0)

        //Buscar en Meta Verde (Pos 59-64)
        indice = META_VERDE.indexOf(parTocado)
        if(indice != -1)
            return indice + 59 //Base 59 (59-59=0)

        //Buscar en Meta Amarilla (Pos 65-70)
        indice = META_AMARILLA.indexOf(parTocado)
        if(indice != -1)
            return indice + 65 //Base 65 (65-65=0)

        //Buscar en Meta Azul (Pos 71-76)
        indice = META_AZUL.indexOf(parTocado)
        if(indice != -1)
            return indice + 71 //Base 71 (71-71=0)

        //Si no es una casilla jugable se retorna -1
        return -1
    }

    /*Devuelve el color de la base que corresponde a una celda (col, fila) de la cuadrícula.
     Devuelve null si no es una casilla de base*/
    fun obtenerColorBaseTocada(col: Int, fila: Int): ColorJugador?
    {
        //Base Roja (Superior Izquierda: 0-5, 0-5)
        if(col in 0..5 && fila in 0..5) {
            return ColorJugador.ROJO
        }
        //Base Verde (Superior Derecha: 9-14, 0-5)
        if(col in 9..14 && fila in 0..5) {
            return ColorJugador.VERDE
        }
        //Base Azul (Inferior Izquierda: 0-5, 9-14)
        if(col in 0..5 && fila in 9..14) {
            return ColorJugador.AZUL
        }
        //Base Amarilla (Inferior Derecha: 9-14, 9-14)
        if(col in 9..14 && fila in 9..14) {
            return ColorJugador.AMARILLO
        }

        //No se tocó ninguna base
        return null
    }

    /*Busca todas las fichas (de cualquier jugador) que están
     en una posición global específica (fuera de la base)*/
    private fun obtenerPilaEnCasilla(posicionGlobal: Int): List<Ficha>
    {
        val pila = mutableListOf<Ficha>()
        //El orden de listaJugadores (Rojo, Verde, etc.) es importante
        //para que el 'indexOf' sea consistente en cada redibujado
        for (jugador in listaJugadores)
        {
            for (ficha in jugador.fichas)
            {
                //Comprobamos solo fichas EN_JUEGO o EN_META
                if(ficha.posicionGlobal == posicionGlobal && (ficha.estado == EstadoFicha.EN_JUEGO
                        || ficha.estado == EstadoFicha.EN_META))
                    pila.add(ficha)
            }
        }
        return pila
    }

    fun actualizarFichasMovibles(fichas: List<Ficha>)
    {
        this.fichasMovibles = fichas
        invalidate()
    }
}