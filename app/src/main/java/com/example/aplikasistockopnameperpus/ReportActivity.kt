package com.example.aplikasistockopnameperpus // Sesuaikan package jika perlu

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels // Untuk by viewModels()
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
// Import Adapter Anda
import com.example.aplikasistockopnameperpus.adapter.ReportAdapter
// Import ViewBinding untuk ActivityReportBinding
import com.example.aplikasistockopnameperpus.databinding.ActivityReportBinding
// Import ViewModel Anda
import com.example.aplikasistockopnameperpus.viewmodel.ReportViewModel
// Import Model jika diperlukan untuk onClick (meskipun sudah dihandle di Adapter)
// import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport

class ReportActivity : AppCompatActivity() {

    // Menggunakan ViewBinding untuk mengakses view dengan aman
    private lateinit var binding: ActivityReportBinding

    // Inisialisasi ViewModel menggunakan delegasi 'by viewModels()'
    private val reportViewModel: ReportViewModel by viewModels()

    // Deklarasi Adapter
    private lateinit var reportAdapter: ReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate layout menggunakan ViewBinding
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbarReport) // Asumsi ID toolbar adalah 'toolbarReport' di activity_report.xml
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Menampilkan tombol kembali
        supportActionBar?.title = "Laporan Stock Opname" // Set judul toolbar

        // Listener untuk tombol kembali di Toolbar
        binding.toolbarReport.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Aksi standar tombol kembali
        }

        setupRecyclerView()
        observeViewModel()

        // TODO: Implementasi logika untuk filter dan urut jika Anda menambahkannya di layout
        // Contoh:
        // binding.spinnerFilterDate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { ... }
    }

    private fun setupRecyclerView() {
        // Inisialisasi Adapter dengan lambda untuk menangani klik item
        reportAdapter = ReportAdapter { selectedReport ->
            // Aksi ketika item report di RecyclerView diklik
            Toast.makeText(this, "Report dipilih: ${selectedReport.reportName}", Toast.LENGTH_SHORT).show()

            // Anda bisa navigasi ke Activity detail report di sini jika ada.
            // Contoh:
            // val intent = Intent(this, DetailReportActivity::class.java).apply {
            //     putExtra(DetailReportActivity.EXTRA_REPORT_ID, selectedReport.reportId)
            // }
            // startActivity(intent)
        }

        // Setup RecyclerView
        binding.recyclerViewReports.apply { // Asumsi ID RecyclerView adalah 'recyclerViewReports'
            adapter = reportAdapter
            layoutManager = LinearLayoutManager(this@ReportActivity)
            // Anda bisa menambahkan ItemDecoration jika perlu, misalnya untuk spasi antar item
            // addItemDecoration(DividerItemDecoration(this@ReportActivity, LinearLayoutManager.VERTICAL))
        }
    }

    private fun observeViewModel() {
        // Observe LiveData 'allStockOpnameReports' dari ViewModel
        reportViewModel.allStockOpnameReports.observe(this) { reports ->
            // Ketika data report berubah, update RecyclerView
            if (reports.isNullOrEmpty()) {
                // Tampilkan pesan jika tidak ada report
                binding.recyclerViewReports.visibility = View.GONE
                binding.textViewNoReports.visibility = View.VISIBLE // Asumsi ID adalah 'textViewNoReports'
            } else {
                // Tampilkan RecyclerView dan sembunyikan pesan "tidak ada report"
                binding.recyclerViewReports.visibility = View.VISIBLE
                binding.textViewNoReports.visibility = View.GONE
                // Submit list baru ke Adapter. ListAdapter akan menangani pembaruan UI secara efisien.
                reportAdapter.submitList(reports)
            }
        }
    }

    // Jika Anda ingin menambahkan menu di Toolbar (misalnya untuk filter/sort)
    /*
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_report, menu) // Buat file menu_report.xml di res/menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { // Handle klik tombol kembali di toolbar jika tidak dihandle oleh setNavigationOnClickListener
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_filter_by_date -> {
                // TODO: Implementasi logika filter berdasarkan tanggal
                Toast.makeText(this, "Filter berdasarkan tanggal dipilih", Toast.LENGTH_SHORT).show()
                true
            }
            // Tambahkan case lain untuk item menu lainnya
            else -> super.onOptionsItemSelected(item)
        }
    }
    */
}
