package com.example.aplikasistockopnameperpus.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.ItemBookRowBinding
// Impor dari lokasi definisi BookOpname dan ScanMethod Anda
import com.example.aplikasistockopnameperpus.model.BookOpname // Contoh jika di package model
import com.example.aplikasistockopnameperpus.model.ScanMethod   // Contoh jika di package model
// Atau jika tetap di viewmodel package:
// import com.example.aplikasistockopnameperpus.viewmodel.BookOpname
// import com.example.aplikasistockopnameperpus.viewmodel.ScanMethod
import com.example.aplikasistockopnameperpus.data.database.BookMaster // Untuk tipe bookMaster di dalam BookOpname
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookListAdapter(
    private val onItemClicked: (BookOpname) -> Unit // Menggunakan BookOpname
) : ListAdapter<BookOpname, BookListAdapter.BookViewHolder>(BookOpnameDiffCallback()) { // Menggunakan BookOpname

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val bookOpnameItem = getItem(position)
        holder.bind(bookOpnameItem, onItemClicked)
    }

    class BookViewHolder(private val binding: ItemBookRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bookOpname: BookOpname, onItemClicked: (BookOpname) -> Unit) {
            val bookMaster = bookOpname.bookMaster

            binding.textViewItemCode.text = bookMaster.itemCode
            binding.textViewBookTitle.text = bookMaster.title
            binding.textViewRfidTag.text = "EPC: ${bookMaster.rfidTagHex}"
            binding.textViewBookLocation.text = "Lokasi: ${bookMaster.expectedLocation ?: "N/A"}"

            // Menggunakan statusScanOpname dari BookMaster yang ada dalam BookOpname,
            // dan scanTime dari BookOpname
            when (bookMaster.scanStatus) {
                "DITEMUKAN" -> {
                    binding.textViewScanStatus.text = "DITEMUKAN"
                    binding.textViewScanStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.green_found))
                    val scanTimeText = if (bookOpname.scanTime != null && bookOpname.scanTime!! > 0)
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(bookOpname.scanTime!!))
                    else ""
                    binding.textViewScanTime.text = scanTimeText
                    binding.textViewScanTime.visibility = ViewGroup.VISIBLE
                }
                "BELUM_DITEMUKAN", null -> {
                    binding.textViewScanStatus.text = "BELUM DITEMUKAN"
                    binding.textViewScanStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.red_missing))
                    binding.textViewScanTime.visibility = ViewGroup.GONE
                }
                else -> { // Status lain jika ada
                    binding.textViewScanStatus.text = bookMaster.scanStatus ?: "N/A"
                    binding.textViewScanStatus.setTextColor(Color.DKGRAY)
                    binding.textViewScanTime.visibility = ViewGroup.GONE
                }
            }

            itemView.setOnClickListener {
                onItemClicked(bookOpname)
            }
        }
    }

    class BookOpnameDiffCallback : DiffUtil.ItemCallback<BookOpname>() {
        override fun areItemsTheSame(oldItem: BookOpname, newItem: BookOpname): Boolean {
            // Identifier unik dari BookMaster yang ada di dalam BookOpname
            // Jika rfidTagHex bisa null, Anda perlu menanganinya
            return oldItem.bookMaster.rfidTagHex == newItem.bookMaster.rfidTagHex &&
                    oldItem.bookMaster.itemCode == newItem.bookMaster.itemCode // Atau oldItem.bookMaster.id == newItem.bookMaster.id
        }

        override fun areContentsTheSame(oldItem: BookOpname, newItem: BookOpname): Boolean {
            return oldItem == newItem
        }
    }
}

