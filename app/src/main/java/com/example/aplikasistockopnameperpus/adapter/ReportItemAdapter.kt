package com.example.aplikasistockopnameperpus.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.text
import androidx.glance.visibility
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.ItemReportDetailRowBinding
import com.example.aplikasistockopnameperpus.viewmodel.BookMasterDisplayWrapper // Pastikan path ini benar

class ReportItemAdapter(
    private val onItemClick: (BookMasterDisplayWrapper) -> Unit
) : ListAdapter<BookMasterDisplayWrapper, ReportItemAdapter.ReportItemViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportItemViewHolder {
        val binding = ItemReportDetailRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReportItemViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ReportItemViewHolder, position: Int) {
        val currentWrapper = getItem(position)
        holder.bind(currentWrapper)
    }

    class ReportItemViewHolder(
        private val binding: ItemReportDetailRowBinding,
        private val onItemClickCallback: (BookMasterDisplayWrapper) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(wrapper: BookMasterDisplayWrapper) {
            val context = itemView.context
            val book = wrapper.bookMaster // Akses BookMaster dari wrapper

            binding.textViewItemTitle.text = book.title ?: context.getString(R.string.title_not_available)
            binding.textViewItemCode.text = context.getString(R.string.prefix_item_code, book.itemCode ?: "-")
            binding.textViewItemEpc.text = context.getString(R.string.prefix_epc, book.rfidTagHex ?: context.getString(R.string.epc_not_available_short))

            // Status Opname (dari DisplayWrapper yang sudah diproses)
            binding.textViewItemOpnameStatus.text = context.getString(R.string.prefix_status_opname, wrapper.displayOpnameStatusText)
            binding.textViewItemOpnameStatus.setTextColor(wrapper.displayOpnameStatusColor)

            // Status Pairing (dari DisplayWrapper yang sudah diproses)
            binding.textViewItemPairingStatus.text = context.getString(R.string.prefix_status_pairing, wrapper.displayPairingStatusText)
            binding.textViewItemPairingStatus.setTextColor(wrapper.displayPairingStatusColor)

            // Lokasi
            binding.textViewItemExpectedLocation.text = context.getString(R.string.prefix_expected_location, wrapper.displayExpectedLocation)

            // Lokasi aktual scan diambil dari BookMaster (yang mungkin diisi dari StockOpnameItem di ViewModel)
            val actualLocationText = book.actualScannedLocation?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.location_scan_not_recorded)
            binding.textViewItemActualLocation.text = context.getString(R.string.prefix_actual_location, actualLocationText)

            // Last Seen Info (dari DisplayWrapper yang sudah diproses)
            if (!wrapper.displayLastSeenInfo.isNullOrBlank()) {
                binding.textViewLastSeenInfo.text = context.getString(R.string.prefix_last_seen, wrapper.displayLastSeenInfo)
                binding.textViewLastSeenInfo.visibility = View.VISIBLE
            } else {
                binding.textViewLastSeenInfo.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onItemClickCallback(wrapper)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<BookMasterDisplayWrapper>() {
        override fun areItemsTheSame(oldWrapper: BookMasterDisplayWrapper, newWrapper: BookMasterDisplayWrapper): Boolean {
            // Identifier unik BookMaster. Jika bisa 0 untuk item baru, itemCode bisa jadi fallback.
            return oldWrapper.bookMaster.id == newWrapper.bookMaster.id &&
                    (oldWrapper.bookMaster.id != 0L || // Jika bukan ID default (0L)
                            oldWrapper.bookMaster.itemCode == newWrapper.bookMaster.itemCode) // Maka bandingkan itemCode
        }

        override fun areContentsTheSame(oldWrapper: BookMasterDisplayWrapper, newWrapper: BookMasterDisplayWrapper): Boolean {
            return oldWrapper == newWrapper // Data class comparison
        }
    }
}

