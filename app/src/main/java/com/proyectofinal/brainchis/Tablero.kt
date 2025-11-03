package com.proyectofinal.brainchis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class Tablero: View
{
    private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    //Variables para almacenar los colores cargados
    private var colorBaseRojo: Int = 0
    private var colorBaseVerde: Int = 0
    private var colorBaseAzul: Int = 0
    private var colorBaseAmarillo: Int = 0
    private var colorBaseGris: Int = 0

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
    {
        val ancho = MeasureSpec.getSize(widthMeasureSpec)
        val alto = MeasureSpec.getSize(heightMeasureSpec)
        val lado = minOf(ancho, alto)
        setMeasuredDimension(ancho, lado) //tablero cuadrado
    }

    override fun onDraw(canvas: Canvas)
    {
        //Obtener el ancho de la pantalla en pixeles
        val ancho = measuredWidth.toFloat()
        //Cada casilla debe medir ciertos pixeles, por lo que se divide el ancho de la pantalla entre 15
        //y así obtener cuantos pixeles es para cada casilla
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

        //Dibujar borde del tablero
        canvas.drawRect(0f, 0f, ancho, ancho, pBorde)
    }

    private fun dibujarBase(canvas: Canvas, x: Float, y: Float, color: Int)
    {
        val casilla = measuredWidth / 15f
        pRelleno.color = color
        pRelleno.style = Paint.Style.FILL

        canvas.drawRect(x, y, x + casilla*6, y + casilla*6, pRelleno)

        //Bordes (ya se incluye en la función dibujarBase)
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

        //Determinar la imagen según el color de base
        val nombreRecurso = when (color)
        {
            colorBaseRojo -> "ficha_roja"
            colorBaseVerde -> "ficha_verde"
            colorBaseAzul -> "ficha_azul"
            colorBaseAmarillo -> "ficha_amarilla"
            else -> "ficha_roja"
        }

        val resId = resources.getIdentifier(nombreRecurso, "drawable", context.packageName)
        val imagen = ContextCompat.getDrawable(context, resId)

        //Dibujar las 4 fichas (2x2)
        if(imagen != null)
        {
            val anchoFichaEspacio = cuadroTam / 2
            val altoFichaEspacio = cuadroTam / 2

            //proporción original de la imagen (ancho/alto)
            val proporcionOriginal = 225f / 375f //ancho / alto
            val factorEscala = 1.3f //puedes ajustar este valor a gusto (1.0 = tamaño normal, 1.5 = 50% más grande)

            val anchoFichaBase = if(anchoFichaEspacio / altoFichaEspacio < proporcionOriginal)
                anchoFichaEspacio
            else
                altoFichaEspacio * proporcionOriginal

            val altoFichaBase = anchoFichaBase / proporcionOriginal

            val anchoFichaReal = anchoFichaBase * factorEscala
            val altoFichaReal = altoFichaBase * factorEscala

            for (fila in 0..1)
            {
                for (col in 0..1)
                {
                    val cx = cuadroInteriorX + col * anchoFichaEspacio + anchoFichaEspacio / 2
                    val cy = cuadroInteriorY + fila * altoFichaEspacio + altoFichaEspacio / 2

                    val left = cx - anchoFichaReal / 2
                    val top = cy - altoFichaReal / 2
                    val right = cx + anchoFichaReal / 2
                    val bottom = cy + altoFichaReal / 2

                    imagen.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                    imagen.draw(canvas)
                }
            }
        }
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
        //El tablero es 15x15. Las coordenadas van de 0 a 14.
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
        run {
            val left = 8 * casilla
            val top = 1 * casilla
            val right = 9 * casilla
            val bottom = 2 * casilla
            pRelleno.color = colorBaseVerde
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla Roja (1, 6)
        run {
            val left = 1 * casilla
            val top = 6 * casilla
            val right = 2 * casilla
            val bottom = 7 * casilla
            pRelleno.color = colorBaseRojo
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla Amarilla (13, 8)
        run {
            val left = 13 * casilla
            val top = 8 * casilla
            val right = 14 * casilla
            val bottom = 9 * casilla
            pRelleno.color = colorBaseAmarillo
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla Azul (6, 13)
        run {
            val left = 6 * casilla
            val top = 13 * casilla
            val right = 7 * casilla
            val bottom = 14 * casilla
            pRelleno.color = colorBaseAzul
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla (6, 2)
        run {
            val left = 6 * casilla
            val top = 2 * casilla
            val right = 7 * casilla
            val bottom = 3 * casilla
            pRelleno.color = colorBaseGris
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla (2, 8)
        run {
            val left = 2 * casilla
            val top = 8 * casilla
            val right = 3 * casilla
            val bottom = 9 * casilla
            pRelleno.color = colorBaseGris
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla (12, 6)
        run {
            val left = 12 * casilla
            val top = 6 * casilla
            val right = 13 * casilla
            val bottom = 7 * casilla
            pRelleno.color = colorBaseGris
            canvas.drawRect(left, top, right, bottom, pRelleno) //Relleno
            canvas.drawRect(left, top, right, bottom, pBorde)   //Borde
        }

        //Casilla (8, 12)
        run {
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
}
