package com.example.aplikasistockopnameperpus.adapter // Sesuaikan package jika perlu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
// Import Entity StockOpnameReport Anda
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
// Import ViewBinding untuk item layout Anda (misalnya ItemReportBinding)
import com.example.aplikasistockopnameperpus.databinding.ItemReportBinding
import java.text.SimpleDateFormat
import java.util.Date // Pastikan import java.util.Date
import java.util.Locale

class ReportAdapter(
    private val onItemClicked: (StockOpnameReport) -> Unit
) : ListAdapter<StockOpnameReport, ReportAdapter.ReportViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        // Menggunakan ItemReportBinding yang dihasilkan dari item_report.xml
        val binding = ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReportViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val currentReport = getItem(position)
        holder.bind(currentReport)
    }

    class ReportViewHolder(
        private val binding: ItemReportBinding, // Menggunakan ViewBinding
        private val onItemClicked: (StockOpnameReport) -> Unit // Untuk callback klik
    ) : RecyclerView.ViewHolder(binding.root) {

        // Pindahkan SimpleDateFormat ke sini agar tidak dibuat ulang terus-menerus untuk setiap item
        private val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())

        fun bind(report: StockOpnameReport) {
            binding.textViewReportName.text = report.reportName

            // Format startTimeMillis (Long) ke String tanggal yang dapat dibaca
            binding.textViewReportDate.text = "Tanggal: ${dateFormat.format(Date(report.startTimeMillis))}"

            // Sesuaikan dengan nama field di StockOpnameReport.kt Anda
            binding.textViewReportTotalMaster.text = report.totalItemsExpected.toString()
            binding.textViewReportTotalFound.text = report.totalItemsFound.toString()
            binding.textViewReportTotalMissing.text = report.totalItemsMissing.toString()

            // Anda juga bisa menambahkan tampilan untuk totalItemsNewOrUnexpected jika ada TextView-nya di item_report.xml
            // binding.textViewReportTotalNew.text = report.totalItemsNewOrUnexpected.toString()

            // Setup listener klik untuk seluruh item view
            itemView.setOnClickListener {
                onItemClicked(report)
            }

            // Jika ada tombol spesifik di dalam item (misalnya "Lihat Detail") dan Anda ingin
            // listener terpisah untuk itu:
            // binding.buttonViewReportDetails.setOnClickListener {
            //     // Aksi spesifik untuk tombol detail, mungkin juga memanggil onItemClicked
            //     // atau callback lain yang berbeda.
            //     onItemClicked(report) // Atau callback lain untuk tombol spesifik
            // }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<StockOpnameReport>() {
            override fun areItemsTheSame(oldItem: StockOpnameReport, newItem: StockOpnameReport): Boolean {
                // reportId adalah PrimaryKey, jadi ini cara yang baik untuk mengecek kesamaan item
                return oldItem.reportId == newItem.reportId
            }

            override fun areContentsTheSame(oldItem: StockOpnameReport, newItem: StockOpnameReport): Boolean {
                // Memeriksa apakah semua konten objek sama.
                // Jika StockOpnameReport adalah data class, implementasi default equals() sudah cukup.
                return oldItem == newItem
            }
        }
    }
}
