package com.example.aplikasistockopnameperpus.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R // Pastikan import R benar
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.databinding.ItemReportDetailRowBinding

class ReportItemAdapter(private val onItemClick: (StockOpnameItem) -> Unit) :
    ListAdapter<StockOpnameItem, ReportItemAdapter.ReportItemViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportItemViewHolder {
        val binding = ItemReportDetailRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReportItemViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ReportItemViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class ReportItemViewHolder(
        private val binding: ItemReportDetailRowBinding,
        private val onItemClick: (StockOpnameItem) -> Unit
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StockOpnameItem) {
            binding.textViewItemTitle.text = item.titleMaster ?: itemView.context.getString(R.string.title_not_available)
            binding.textViewItemCode.text = itemView.context.getString(R.string.prefix_item_code, item.itemCodeMaster ?: "-")
            binding.textViewItemEpc.text = itemView.context.getString(R.string.prefix_epc, item.rfidTagHexScanned ?: "-")
            binding.textViewItemStatus.text = itemView.context.getString(R.string.prefix_status, item.status)

            binding.textViewItemActualLocation.text = itemView.context.getString(R.string.prefix_actual_location, item.actualLocationIfDifferent ?: "-")

            // Contoh pewarnaan berdasarkan status (opsional)
            val statusColor = when (item.status?.uppercase()) {
                "FOUND" -> R.color.opname_status_found_correct_color // Ganti dengan warna Anda
                "MISSING" -> R.color.opname_status_missing_color
                "NEW_ITEM", "UNKNOWN_ITEM_IN_REPORT_STATUS" -> R.color.opname_status_new_item_color
                else -> android.R.color.darker_gray
            }
            binding.textViewItemStatus.setTextColor(ContextCompat.getColor(itemView.context, statusColor))

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<StockOpnameItem>() {
        override fun areItemsTheSame(oldItem: StockOpnameItem, newItem: StockOpnameItem): Boolean {
            // Bandingkan berdasarkan composite primary key Anda
            return oldItem.reportId == newItem.reportId &&
                    oldItem.rfidTagHexScanned == newItem.rfidTagHexScanned
        }

        override fun areContentsTheSame(oldItem: StockOpnameItem, newItem: StockOpnameItem): Boolean {
            // Karena StockOpnameItem adalah data class, perbandingan ini akan memeriksa semua field.
            return oldItem == newItem
        }
    }
}
