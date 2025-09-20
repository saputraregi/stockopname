package com.example.aplikasistockopnameperpus.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat // Meskipun warna dari wrapper, mungkin butuh untuk default
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R // Mungkin tidak dibutuhkan langsung di sini
import com.example.aplikasistockopnameperpus.databinding.ItemBookRowBinding
import com.example.aplikasistockopnameperpus.viewmodel.BookMasterDisplayWrapper // IMPORT WRAPPER ANDA
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus

class BookListAdapter(
    private val onItemClick: (BookMasterDisplayWrapper) -> Unit,
    private val onItemLongClick: (BookMasterDisplayWrapper) -> Boolean
) : ListAdapter<BookMasterDisplayWrapper, BookListAdapter.BookViewHolder>(BookMasterDisplayWrapperDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val wrapperItem = getItem(position)
        holder.bind(wrapperItem, onItemClick, onItemLongClick)
    }

    class BookViewHolder(private val binding: ItemBookRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            wrapper: BookMasterDisplayWrapper,
            onItemClick: (BookMasterDisplayWrapper) -> Unit,
            onItemLongClick: (BookMasterDisplayWrapper) -> Boolean
        ) {
            val bookMaster = wrapper.bookMaster // Akses BookMaster asli jika masih ada properti yg hanya di sana

            // ... di dalam bind() setelah val bookMaster = wrapper.bookMaster
            val statusIconResId = when (bookMaster.opnameStatus) {
                OpnameStatus.FOUND -> R.drawable.ic_check_circle_green // Buat drawable ini
                OpnameStatus.MISSING -> R.drawable.ic_error_outline_red // Buat drawable ini
                OpnameStatus.NEW_ITEM -> R.drawable.ic_add_circle_blue // Buat drawable ini
                OpnameStatus.NOT_SCANNED -> R.drawable.ic_hourglass_empty_gray // Buat drawable ini
                // else -> R.drawable.ic_help_outline_gray // Default jika ada status lain
            }
            binding.imageViewStatusIcon.setImageResource(statusIconResId)

            binding.textViewItemCode.text = bookMaster.itemCode
            binding.textViewBookTitle.text = bookMaster.title

            // Menampilkan RFID Tag Hex (dari BookMaster di dalam wrapper)
            if (bookMaster.rfidTagHex != null) {
                binding.textViewRfidTag.text = "EPC: ${bookMaster.rfidTagHex}"
                binding.textViewRfidTag.visibility = View.VISIBLE
            } else {
                binding.textViewRfidTag.text = "EPC: -"
                binding.textViewRfidTag.visibility = View.GONE
            }

            // Menampilkan Lokasi yang Diharapkan (dari wrapper)
            binding.textViewBookLocation.text = "Lokasi: ${wrapper.displayExpectedLocation}"

            // Menampilkan Status Pairing RFID (dari wrapper)
            // Pastikan ID 'textViewRfidPairingStatus' benar di layout item_book_row.xml
            binding.textViewRfidPairingStatus.text = wrapper.displayPairingStatusText
            binding.textViewRfidPairingStatus.setTextColor(wrapper.displayPairingStatusColor)
            binding.textViewRfidPairingStatus.visibility = View.VISIBLE


            // Menampilkan Status Opname/Scan (dari wrapper)
            // Pastikan ID 'textViewScanStatus' dan 'textViewScanTime' benar di layout
            binding.textViewScanStatus.text = wrapper.displayOpnameStatusText
            binding.textViewScanStatus.setTextColor(wrapper.displayOpnameStatusColor)

            if (wrapper.displayLastSeenInfo != null) {
                binding.textViewScanTime.text = wrapper.displayLastSeenInfo
                binding.textViewScanTime.visibility = View.VISIBLE
            } else {
                binding.textViewScanTime.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onItemClick(wrapper)
            }

            itemView.setOnLongClickListener {
                onItemLongClick(wrapper)
            }
        }
    }

    class BookMasterDisplayWrapperDiffCallback : DiffUtil.ItemCallback<BookMasterDisplayWrapper>() {
        override fun areItemsTheSame(oldItem: BookMasterDisplayWrapper, newItem: BookMasterDisplayWrapper): Boolean {
            // Identifier unik dari BookMaster di dalam wrapper
            return oldItem.bookMaster.id == newItem.bookMaster.id
        }

        override fun areContentsTheSame(oldItem: BookMasterDisplayWrapper, newItem: BookMasterDisplayWrapper): Boolean {
            // Bandingkan wrapper secara keseluruhan. Data class akan membandingkan semua propertinya.
            return oldItem == newItem
        }
    }
}

