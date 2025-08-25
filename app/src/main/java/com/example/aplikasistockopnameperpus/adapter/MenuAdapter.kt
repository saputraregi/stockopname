package com.example.aplikasistockopnameperpus.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast // Tambahkan ini jika ingin memberi feedback saat item non-aktif diklik
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.model.MenuItem // Pastikan path model ini benar
import com.example.aplikasistockopnameperpus.model.MenuAction // Pastikan path model ini benar

class MenuAdapter(
    private var menuItems: List<MenuItem>, // Ubah ke var jika daftar item bisa berubah
    private var isReaderConnected: Boolean, // Tambahkan parameter ini
    private val onItemClick: (MenuAction) -> Unit
) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    // Daftar MenuAction yang memerlukan reader untuk aktif
    // Sesuaikan daftar ini dengan kebutuhan aplikasi Anda
    private val readerRequiredActions = setOf(
        MenuAction.STOCK_OPNAME,
        MenuAction.READ_WRITE_TAG, // Jika Anda punya menu ini
        MenuAction.RADAR,         // Jika Anda punya menu ini
        MenuAction.PAIRING_WRITE  // Tambahkan jika menu "Pair & Write Tag" memerlukan reader
        // Tambahkan MenuAction lain yang memerlukan reader
    )

    /**
     * Fungsi untuk mengupdate status koneksi reader dari luar adapter.
     * Ini akan memicu refresh tampilan item jika statusnya berubah.
     */
    fun setReaderConnected(isConnected: Boolean) {
        if (this.isReaderConnected != isConnected) {
            this.isReaderConnected = isConnected
            notifyDataSetChanged() // Cara sederhana untuk refresh.
            // Untuk performa lebih baik pada list besar, gunakan DiffUtil
            // atau notifyItemRangeChanged jika Anda tahu item mana saja yang terpengaruh.
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_menu, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val menuItem = menuItems[position]
        // Tidak perlu mengirim onItemClick ke bind jika sudah menjadi properti class
        holder.bind(menuItem)
    }

    override fun getItemCount(): Int = menuItems.size

    // Jadikan MenuViewHolder sebagai inner class agar bisa mengakses properti dari MenuAdapter (seperti isReaderConnected dan onItemClick)
    inner class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Pastikan ID ini ada di item_menu.xml Anda
        private val imageViewIcon: ImageView = itemView.findViewById(R.id.imageViewMenuItemIcon) // Ganti jika ID berbeda
        private val textViewTitle: TextView = itemView.findViewById(R.id.textViewMenuItemTitle) // Ganti jika ID berbeda

        fun bind(menuItem: MenuItem) {
            textViewTitle.text = menuItem.title
            imageViewIcon.setImageResource(menuItem.iconResId) // Pastikan iconResId valid

            // Tentukan apakah item menu ini memerlukan reader
            val requiresReader = readerRequiredActions.contains(menuItem.actionId)

            // Tentukan apakah item ini harus aktif
            // Item aktif jika:
            // 1. Tidak memerlukan reader, ATAU
            // 2. Memerlukan reader DAN reader terhubung
            val isEnabled = !requiresReader || (requiresReader && isReaderConnected)

            itemView.isEnabled = isEnabled
            itemView.isClickable = isEnabled // Pastikan juga isClickable diatur
            itemView.alpha = if (isEnabled) 1.0f else 0.5f // Beri efek visual untuk item non-aktif

            itemView.setOnClickListener {
                if (isEnabled) {
                    onItemClick(menuItem.actionId)
                } else {
                    // Opsional: Beri tahu pengguna bahwa reader dibutuhkan
                    Toast.makeText(itemView.context, "Harap hubungkan reader terlebih dahulu.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
