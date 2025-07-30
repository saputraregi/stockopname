// Di file terpisah, misal MenuAdapter.kt
package com.example.aplikasistockopnameperpus.adapter// Atau com.example.aplikasistockopnameperpus.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.model.MenuItem
import com.example.aplikasistockopnameperpus.model.MenuAction

class MenuAdapter(
    private val menuItems: List<MenuItem>,
    private val onItemClick: (MenuAction) -> Unit
) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        // Pastikan Anda punya layout item_menu.xml
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_menu, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val menuItem = menuItems[position]
        holder.bind(menuItem, onItemClick)
    }

    override fun getItemCount(): Int = menuItems.size

    class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Pastikan ID ini ada di item_menu.xml
        private val imageViewIcon: ImageView = itemView.findViewById(R.id.imageViewMenuItemIcon)
        private val textViewTitle: TextView = itemView.findViewById(R.id.textViewMenuItemTitle)

        fun bind(menuItem: MenuItem, onItemClick: (MenuAction) -> Unit) {
            textViewTitle.text = menuItem.title
            imageViewIcon.setImageResource(menuItem.iconResId) // Pastikan iconResId valid
            itemView.setOnClickListener {
                onItemClick(menuItem.actionId)
            }
        }
    }
}
