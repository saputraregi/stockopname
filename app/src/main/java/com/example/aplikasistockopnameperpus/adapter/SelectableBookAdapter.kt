package com.example.aplikasistockopnameperpus.adapter // atau package yang sesuai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.text
import androidx.glance.visibility
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.databinding.ItemSelectableBookBinding // Buat layout item ini

class SelectableBookAdapter(
    private val onItemClick: (BookMaster) -> Unit
) : ListAdapter<BookMaster, SelectableBookAdapter.BookViewHolder>(BookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemSelectableBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = getItem(position)
        holder.bind(book)
    }

    inner class BookViewHolder(private val binding: ItemSelectableBookBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(book: BookMaster) {
            binding.textViewBookTitleSelectable.text = book.title
            binding.textViewItemCodeSelectable.text = "Kode: ${book.itemCode}"
            binding.textViewRfidTagSelectable.text = "EPC: ${book.rfidTagHex ?: "Belum Ditag"}"
            // Hanya aktifkan item jika rfidTagHex tidak null/blank
            binding.root.isEnabled = !book.rfidTagHex.isNullOrBlank()
            binding.root.isClickable = !book.rfidTagHex.isNullOrBlank()
            binding.textViewRfidTagSelectable.visibility = if (book.rfidTagHex.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    class BookDiffCallback : DiffUtil.ItemCallback<BookMaster>() {
        override fun areItemsTheSame(oldItem: BookMaster, newItem: BookMaster): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BookMaster, newItem: BookMaster): Boolean {
            return oldItem == newItem
        }
    }
}
