package com.example.aplikasistockopnameperpus.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R

class SimpleTextAdapter(private var items: List<String>) :
    RecyclerView.Adapter<SimpleTextAdapter.ViewHolder>() {

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_simple_list_text, parent, false) as TextView
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Tampilkan data terbaru di paling atas
        val item = items[items.size - 1 - position]
        holder.textView.text = item
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged() // Cara sederhana untuk update
    }
}
