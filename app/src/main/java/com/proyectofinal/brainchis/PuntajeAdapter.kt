package com.proyectofinal.brainchis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PuntajeAdapter(private val listaPuntajes: List<Puntaje>) :
    RecyclerView.Adapter<PuntajeAdapter.PuntajeViewHolder>()
{
    class PuntajeViewHolder(view: View) : RecyclerView.ViewHolder(view)
    {
        val txtPosicion: TextView = view.findViewById(R.id.txtPosicion)
        val txtNombre: TextView = view.findViewById(R.id.txtNombreJugador)
        val txtAciertos: TextView = view.findViewById(R.id.txtAciertos)
        val txtPuntaje: TextView = view.findViewById(R.id.txtPuntajeJugador)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PuntajeViewHolder
    {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_puntaje, parent, false)
        return PuntajeViewHolder(view)
    }

    override fun onBindViewHolder(holder: PuntajeViewHolder, position: Int)
    {
        val puntaje = listaPuntajes[position]

        holder.txtPosicion.text = (position + 1).toString()
        holder.txtNombre.text = puntaje.nombre
        holder.txtAciertos.text = puntaje.aciertos.toString()
        holder.txtPuntaje.text = puntaje.puntosTotales.toString()

        //Colores especiales para el podio
        if(position == 0) holder.txtPosicion.setTextColor(android.graphics.Color.parseColor("#FFD700")) //Oro
        else if(position == 1) holder.txtPosicion.setTextColor(android.graphics.Color.parseColor("#C0C0C0")) //Plata
        else if(position == 2) holder.txtPosicion.setTextColor(android.graphics.Color.parseColor("#CD7F32")) //Bronce
    }

    override fun getItemCount() = listaPuntajes.size
}