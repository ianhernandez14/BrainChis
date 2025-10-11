package com.proyectofinal.brainchis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class Tablero: View
{
    private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var casillaSeleccionadaX = -1
    private var casillaSeleccionadaY = -1

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init
    {
        pBorde.style = Paint.Style.STROKE
        pBorde.color = Color.BLACK
        pBorde.strokeWidth = 4f

        gridPaint.style = Paint.Style.STROKE
        gridPaint.color = Color.DKGRAY
        gridPaint.strokeWidth = 2f
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean
    {
        if(event.action == android.view.MotionEvent.ACTION_DOWN)
        {
            val casilla = measuredWidth / 15f
            val columna = (event.x / casilla).toInt()
            val fila = (event.y / casilla).toInt()

            //Guardamos la casilla seleccionada
            casillaSeleccionadaX = columna
            casillaSeleccionadaY = fila

            invalidate() //Redibujar el tablero
        }
        return true
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
        dibujarBase(canvas, 0f, 0f, Color.RED) //superior izquierda
        dibujarBase(canvas, ancho - casilla*6, 0f, Color.GREEN) //superior derecha
        dibujarBase(canvas, 0f, ancho - casilla*6, Color.BLUE) //inferior izquierda
        dibujarBase(canvas, ancho - casilla*6, ancho - casilla*6, Color.YELLOW) //inferior derecha

        //Dibujar borde del tablero
        canvas.drawRect(0f, 0f, ancho, ancho, pBorde)

        //Dibujar la casilla con otro color si se seleccionó con un toque
        if (casillaSeleccionadaX >= 0 && casillaSeleccionadaY >= 0)
        {
            val casilla = measuredWidth / 15f
            pRelleno.color = Color.argb(100, 255, 0, 0) // rojo semitransparente
            canvas.drawRect(
                casillaSeleccionadaX * casilla,
                casillaSeleccionadaY * casilla,
                (casillaSeleccionadaX + 1) * casilla,
                (casillaSeleccionadaY + 1) * casilla,
                pRelleno
            )
        }
    }

    private fun dibujarBase(canvas: Canvas, x: Float, y: Float, color: Int)
    {
        val casilla = measuredWidth / 15f
        pRelleno.color = color
        pRelleno.style = Paint.Style.FILL

        canvas.drawRect(x, y, x + casilla*6, y + casilla*6, pRelleno)

        //Bordes
        canvas.drawRect(x, y, x + casilla*6, y + casilla*6, pBorde)
    }
}