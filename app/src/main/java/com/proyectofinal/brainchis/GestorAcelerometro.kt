package com.proyectofinal.brainchis

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class GestorAcelerometro(context: Context, private val onShake: () -> Unit) : SensorEventListener
{
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE)
            as SensorManager
    private val acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val SHAKE_THRESHOLD_GRAVITY = 3f
    private val SHAKE_SLOP_TIME_MS = 500
    private var shakeTimestamp: Long = 0
    
    fun iniciar()
    {
        acelerometro?.let{
            sensorManager.registerListener(this, it,
                SensorManager.SENSOR_DELAY_UI)
        }
    }
    
    fun detener(){
        sensorManager.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent?)
    {
        if(event == null)
            return
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        
        //Calcular fuerza G total
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()
        
        if(gForce > SHAKE_THRESHOLD_GRAVITY)
        {
            val now = System.currentTimeMillis()
            
            //Evitar rebotes (que se detecten 2 shakes en medio segundo)
            if(shakeTimestamp + SHAKE_SLOP_TIME_MS > now)
            {
                return
            }
            shakeTimestamp = now
            
            onShake()
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int){
        //Ni Jesucristo utiliza esta función
    }
}