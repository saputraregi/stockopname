package com.example.aplikasistockopnameperpus.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.glance.visibility
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.data.database.BookMaster // Langsung menggunakan BookMaster
import com.example.aplikasistockopnameperpus.databinding.ItemBookRowBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Ganti BookOpname menjadi BookMaster
class BookListAdapter(
    private val onItemClicked: (BookMaster) -> Unit
) : ListAdapter<BookMaster, BookListAdapter.BookViewHolder>(BookMasterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val bookMasterItem = getItem(position)
        holder.bind(bookMasterItem, onItemClicked)
    }

    class BookViewHolder(private val binding: ItemBookRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bookMaster: BookMaster, onItemClicked: (BookMaster) -> Unit) {
            binding.textViewItemCode.text = bookMaster.itemCode
            binding.textViewBookTitle.text = bookMaster.title

            // Menampilkan RFID Tag Hex (jika ada)
            if (bookMaster.rfidTagHex != null) {
                binding.textViewRfidTag.text = "EPC: ${bookMaster.rfidTagHex}"
                binding.textViewRfidTag.visibility = View.VISIBLE
            } else {
                binding.textViewRfidTag.text = "EPC: -" // Atau sembunyikan jika lebih baik
                binding.textViewRfidTag.visibility = View.GONE // Atau View.INVISIBLE
            }

            binding.textViewBookLocation.text = "Lokasi: ${bookMaster.expectedLocation ?: "N/A"}"

            // Menampilkan Status Pairing RFID
            // Pastikan Anda memiliki textViewRfidPairingStatus di layout item_book_row.xml
            val rfidPairingStatusText = when (bookMaster.rfidPairingStatus) {
                "BELUM_DITAG" -> "Belum Ditag RFID"
                "SUDAH_DITAG" -> "Sudah Ditag RFID"
                "GAGAL_TAG" -> "Gagal Tag RFID"
                // Tambahkan kasus lain jika ada
                else -> bookMaster.rfidPairingStatus ?: "Status RFID N/A" // Fallback
            }
            binding.textViewRfidPairingStatus.text = rfidPairingStatusText // GANTI DENGAN ID YANG BENAR
            binding.textViewRfidPairingStatus.setTextColor(
                when (bookMaster.rfidPairingStatus) {
                    "BELUM_DITAG" -> ContextCompat.getColor(itemView.context, R.color.orange_needs_attention) // Buat warna ini
                    "SUDAH_DITAG" -> ContextCompat.getColor(itemView.context, R.color.green_found)
                    "GAGAL_TAG" -> ContextCompat.getColor(itemView.context, R.color.red_missing)
                    else -> Color.DKGRAY
                }
            )
            binding.textViewRfidPairingStatus.visibility = View.VISIBLE // Selalu tampilkan status pairing


            // Menampilkan Status Scan dari Sesi Opname (scanStatus & lastSeenTimestamp dari BookMaster)
            when (bookMaster.scanStatus) {
                "DITEMUKAN" -> {
                    binding.textViewScanStatus.text = "DITEMUKAN"
                    binding.textViewScanStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.green_found))
                    val scanTimeText = if (bookMaster.lastSeenTimestamp != null && bookMaster.lastSeenTimestamp!! > 0) {
                        SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(bookMaster.lastSeenTimestamp!!))
                    } else {
                        ""
                    }
                    binding.textViewScanTime.text = "Terakhir dilihat: $scanTimeText"
                    binding.textViewScanTime.visibility = View.VISIBLE
                }
                "BELUM_DITEMUKAN_SESI_INI" -> { // Sesuaikan dengan nilai yang Anda gunakan di DAO/Repository
                    binding.textViewScanStatus.text = "BELUM DITEMUKAN"
                    binding.textViewScanStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.red_missing))
                    binding.textViewScanTime.visibility = View.GONE
                }
                "LOKASI_SALAH" -> {
                    binding.textViewScanStatus.text = "LOKASI SALAH"
                    binding.textViewScanStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.yellow_warning)) // Buat warna ini
                    val scanTimeText = if (bookMaster.lastSeenTimestamp != null && bookMaster.lastSeenTimestamp!! > 0) {
                        SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(bookMaster.lastSeenTimestamp!!))
                    } else {
                        ""
                    }
                    binding.textViewScanTime.text = "Di ${bookMaster.actualScannedLocation ?: "-"} (${scanTimeText})"
                    binding.textViewScanTime.visibility = View.VISIBLE
                }
                null -> { // Jika status scan null (misalnya, sesi baru, belum diperiksa)
                    binding.textViewScanStatus.text = "Belum Diperiksa"
                    binding.textViewScanStatus.setTextColor(Color.GRAY)
                    binding.textViewScanTime.visibility = View.GONE
                }
                else -> { // Status lain jika ada
                    binding.textViewScanStatus.text = bookMaster.scanStatus // Tampilkan apa adanya
                    binding.textViewScanStatus.setTextColor(Color.DKGRAY)
                    binding.textViewScanTime.visibility = View.GONE
                }
            }

            itemView.setOnClickListener {
                onItemClicked(bookMaster)
            }
        }
    }

    // Ganti BookOpname menjadi BookMaster
    class BookMasterDiffCallback : DiffUtil.ItemCallback<BookMaster>() {
        override fun areItemsTheSame(oldItem: BookMaster, newItem: BookMaster): Boolean {
            // Identifier unik dari BookMaster
            // itemCode harusnya unik dan stabil setelah impor. ID juga bisa jika selalu ada.
            return oldItem.itemCode == newItem.itemCode // Atau oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BookMaster, newItem: BookMaster): Boolean {
            // Ini akan membandingkan semua field di data class BookMaster.
            // Jika ada perubahan di salah satu field, item akan dianggap berbeda kontennya.
            return oldItem == newItem
        }
    }
}

