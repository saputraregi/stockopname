package com.example.aplikasistockopnameperpus

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts // Ditambahkan
import androidx.appcompat.app.AppCompatActivity
// import androidx.compose.ui.text.intl.Locale // Tidak digunakan, bisa dihapus jika hanya ini
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.adapter.ReportItemAdapter
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus
import com.example.aplikasistockopnameperpus.databinding.ActivityReportDetailBinding
import com.example.aplikasistockopnameperpus.model.FilterCriteria
import com.example.aplikasistockopnameperpus.util.exporter.ExportResult
import com.example.aplikasistockopnameperpus.util.exporter.TxtFileExporter
import com.example.aplikasistockopnameperpus.viewmodel.ReportDetailGlobalState
import com.example.aplikasistockopnameperpus.viewmodel.ReportDetailViewModel
import com.example.aplikasistockopnameperpus.viewmodel.ReportDetailViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat // Ditambahkan
import java.util.Date // Ditambahkan
import java.util.Locale // Pastikan ini java.util.Locale

class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding
    private var currentReportId: Long = -1L
    private lateinit var reportItemAdapter: ReportItemAdapter
    private val txtExporter = TxtFileExporter() // Instansiasi di sini

    // --- Ditambahkan untuk Ekspor ---
    private var reportNameForExport: String = "report_data" // Default
    private val createTxtFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        Log.d(TAG, "CreateDocument callback. Received URI: $uri")
        uri?.let { validUri ->
            Log.d(TAG, "URI is valid. Calling writeItemCodesToUri.")
            writeItemCodesToUri(validUri)
        } ?: run {
            Log.w(TAG, "URI is null. File creation probably cancelled by user.")
            Toast.makeText(this, "Operasi pembuatan file dibatalkan.", Toast.LENGTH_SHORT).show()
        }
    }
    // --- Akhir Bagian Ditambahkan untuk Ekspor ---

    private val reportDetailViewModel: ReportDetailViewModel by lazy {
        if (currentReportId == -1L) {
            throw IllegalStateException("Report ID not initialized before ViewModel access.")
        }
        ViewModelProvider(
            this,
            ReportDetailViewModelFactory(application, currentReportId)
        )[ReportDetailViewModel::class.java]
    }

    companion object {
        const val EXTRA_REPORT_ID = "extra_report_id"
        private const val TAG = "ReportDetailActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentReportId = intent.getLongExtra(EXTRA_REPORT_ID, -1L)
        Log.d(TAG, "Received report ID: $currentReportId")

        if (currentReportId == -1L) {
            Toast.makeText(this, getString(R.string.error_invalid_report_id), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupButtonListeners()
        observeViewModel()
        loadReportHeaderDetails()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.report_detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_found_items_txt -> {
                exportFoundItemsToTxt() // --- Diubah: Memanggil fungsi yang benar ---
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- Fungsi Baru/Dimodifikasi untuk Ekspor ---
    private fun exportFoundItemsToTxt() {
        Log.d(TAG, "exportFoundItemsToTxt called.")
        if (currentReportId == -1L) {
            Toast.makeText(this, "Report ID tidak valid untuk ekspor.", Toast.LENGTH_SHORT).show()
            return
        }

        if (reportNameForExport.isBlank() || reportNameForExport == "report_data") {
            Log.w(TAG, "reportNameForExport belum diinisialisasi dengan benar dari header, menggunakan default atau menunggu loadReportHeaderDetails.")
            // Pertimbangkan untuk memanggil loadReportHeaderDetails lagi atau menunggu callback-nya jika ini sering terjadi
            // Untuk sekarang, kita lanjutkan dengan nama default jika belum terisi dari header
        }

        lifecycleScope.launch {
            val allReportItems = try {
                reportDetailViewModel.bookRepository.getItemsForReportList(currentReportId)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching items for export", e)
                Toast.makeText(this@ReportDetailActivity, "Gagal mengambil data untuk ekspor.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Log.d(TAG, "Fetched ${allReportItems.size} items for report ID $currentReportId for export.")

            val itemsToExport = allReportItems.filter { innerItem ->
                (innerItem.status.equals(OpnameStatus.FOUND.name, ignoreCase = true) ||
                        innerItem.status.equals("DITEMUKAN_MASTER", ignoreCase = true)) && // Sesuaikan dengan string status Anda
                        !innerItem.isNewOrUnexpectedItem && !innerItem.itemCodeMaster.isNullOrBlank()
            }
            Log.d(TAG, "${itemsToExport.size} items to actually export after filtering.")

            val itemCodesOnly = itemsToExport.mapNotNull { it.itemCodeMaster }

            if (itemCodesOnly.isEmpty()) {
                Log.w(TAG, "No item codes found to export.")
                Toast.makeText(this@ReportDetailActivity, getString(R.string.message_no_found_items_with_code_to_export), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${reportNameForExport}_found_items_$timeStamp.txt"
            Log.d(TAG, "Launching file picker with suggested name: $fileName")
            createTxtFileLauncher.launch(fileName)
        }
    }

    private fun writeItemCodesToUri(uri: Uri) {
        Log.d(TAG, "writeItemCodesToUri called with URI: $uri")

        lifecycleScope.launch {
            Log.d(TAG, "Coroutine for writing started.")
            // Ambil ulang data untuk memastikan konsistensi, atau teruskan dari exportFoundItemsToTxt jika alurnya memungkinkan
            val allReportItems = reportDetailViewModel.bookRepository.getItemsForReportList(currentReportId)
            val itemsToExport = allReportItems.filter { item ->
                (item.status.equals(OpnameStatus.FOUND.name, ignoreCase = true) ||
                        item.status.equals("DITEMUKAN_MASTER", ignoreCase = true)) &&
                        !item.isNewOrUnexpectedItem && !item.itemCodeMaster.isNullOrBlank()
            }
            val itemCodesOnly = itemsToExport.mapNotNull { it.itemCodeMaster }

            if (itemCodesOnly.isEmpty()) {
                Log.w(TAG, "No item codes to write (checked again in writeItemCodesToUri).")
                Toast.makeText(this@ReportDetailActivity, getString(R.string.message_no_found_items_with_code_to_export), Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                Log.d(TAG, "Attempting to open output stream for URI.")
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    Log.d(TAG, "Output stream opened. Calling TxtFileExporter.exportItemCodeList.")
                    when (val result = txtExporter.exportItemCodeList(itemCodesOnly, outputStream)) {
                        is ExportResult.Success -> {
                            Toast.makeText(this@ReportDetailActivity, getString(R.string.export_txt_success, uri.lastPathSegment ?: "file"), Toast.LENGTH_LONG).show()
                            Log.i(TAG, "Successfully exported ${result.itemsExported} item codes. Path: ${result.filePath}")
                        }
                        is ExportResult.Error -> {
                            Toast.makeText(this@ReportDetailActivity, result.errorMessage, Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Export failed: ${result.errorMessage}")
                        }
                        is ExportResult.NoDataToExport -> {
                            Log.w(TAG, "ExportResult.NoDataToExport received from exporter.")
                            Toast.makeText(this@ReportDetailActivity, "Tidak ada data kode item untuk diekspor (dari exporter).", Toast.LENGTH_SHORT).show()
                        }
                    }
                } ?: run {
                    Log.e(TAG, "Failed to open output stream for URI: $uri")
                    Toast.makeText(this@ReportDetailActivity, getString(R.string.export_txt_failed, "Gagal membuka output stream."), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during TXT export process", e)
                Toast.makeText(this@ReportDetailActivity, getString(R.string.export_txt_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    // --- Akhir Fungsi Baru/Dimodifikasi untuk Ekspor ---

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarReportDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_report_detail_loading)
        binding.toolbarReportDetail.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        reportItemAdapter = ReportItemAdapter { item ->
            Log.d(TAG, "Clicked item: ${item.titleMaster ?: item.itemCodeMaster}")
            Toast.makeText(this, "Item: ${item.titleMaster ?: item.itemCodeMaster}", Toast.LENGTH_SHORT).show()
        }
        binding.recyclerViewReportItems.apply {
            adapter = reportItemAdapter
            layoutManager = LinearLayoutManager(this@ReportDetailActivity)
            setHasFixedSize(true)
        }
    }

    private fun setupButtonListeners() {
        binding.buttonShowFilterPageReportDetail.setOnClickListener {
            openFilterPageForReport()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            reportDetailViewModel.filteredReportItems.collectLatest { items ->
                Log.d(TAG, "Updating adapter with ${items.size} items.")
                reportItemAdapter.submitList(items)
                if (items.isEmpty() && reportDetailViewModel.globalState.value !is ReportDetailGlobalState.Loading) {
                    binding.recyclerViewReportItems.visibility = View.GONE
                    binding.textViewEmptyReportItems.visibility = View.VISIBLE
                    if (reportDetailViewModel.currentReportFilterCriteria.value != FilterCriteria()) {
                        binding.textViewEmptyReportItems.text = getString(R.string.message_no_items_match_filter)
                    } else {
                        binding.textViewEmptyReportItems.text = getString(R.string.message_no_items_in_report)
                    }
                } else {
                    binding.recyclerViewReportItems.visibility = View.VISIBLE
                    binding.textViewEmptyReportItems.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            reportDetailViewModel.globalState.collectLatest { state ->
                Log.d(TAG, "Global state changed: $state")
                when (state) {
                    is ReportDetailGlobalState.Loading -> {
                        binding.progressBarReportDetail.visibility = View.VISIBLE
                        binding.textViewEmptyReportItems.visibility = View.GONE
                    }
                    is ReportDetailGlobalState.Error -> {
                        binding.progressBarReportDetail.visibility = View.GONE
                        Toast.makeText(this@ReportDetailActivity, state.message, Toast.LENGTH_LONG).show()
                        if (reportItemAdapter.itemCount == 0) {
                            binding.recyclerViewReportItems.visibility = View.GONE
                            binding.textViewEmptyReportItems.visibility = View.VISIBLE
                            binding.textViewEmptyReportItems.text = state.message
                        }
                    }
                    is ReportDetailGlobalState.Success -> {
                        binding.progressBarReportDetail.visibility = View.GONE
                        state.message?.let {
                            Toast.makeText(this@ReportDetailActivity, it, Toast.LENGTH_SHORT).show()
                        }
                    }
                    is ReportDetailGlobalState.Idle -> {
                        binding.progressBarReportDetail.visibility = View.GONE
                    }
                }
            }
        }

        lifecycleScope.launch {
            reportDetailViewModel.currentReportFilterCriteria.collectLatest {
                updateActiveFilterInfo()
            }
        }
    }

    private fun loadReportHeaderDetails() {
        lifecycleScope.launch {
            try {
                val report = reportDetailViewModel.bookRepository.getReportById(currentReportId)
                if (report != null) {
                    supportActionBar?.title = getString(R.string.title_report_detail_prefix, report.reportName.take(25))
                    binding.textViewReportNameHeader.text = getString(R.string.report_session_name_prefix, report.reportName)
                    binding.textViewReportStatsHeader.text = getString(
                        R.string.report_stats_format,
                        report.totalItemsExpected,
                        report.totalItemsFound,
                        report.totalItemsMissing,
                        report.totalItemsNewOrUnexpected
                    )
                    // --- Ditambahkan: Inisialisasi reportNameForExport ---
                    reportNameForExport = report.reportName
                        .replace("\\s+".toRegex(), "_")
                        .replace("[^a-zA-Z0-9_.-]".toRegex(), "")
                        .take(50)
                    if (reportNameForExport.isBlank()) reportNameForExport = "exported_report"
                    Log.d(TAG, "reportNameForExport initialized to: $reportNameForExport")
                    // --- Akhir Bagian Ditambahkan ---
                } else {
                    supportActionBar?.title = getString(R.string.title_report_not_found)
                    binding.textViewReportNameHeader.text = getString(R.string.error_report_not_found_detail)
                    binding.textViewReportStatsHeader.visibility = View.GONE
                    binding.buttonShowFilterPageReportDetail.isEnabled = false
                    reportNameForExport = "unknown_report_${currentReportId}" // Fallback
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading report header", e)
                supportActionBar?.title = getString(R.string.title_report_error)
                binding.textViewReportNameHeader.text = getString(R.string.error_loading_report_header)
                binding.textViewReportStatsHeader.visibility = View.GONE
                reportNameForExport = "error_report_${currentReportId}" // Fallback
            }
        }
    }

    private fun openFilterPageForReport() {
        val intent = Intent(this, FilterActivity::class.java)
        val currentCriteria = reportDetailViewModel.currentReportFilterCriteria.value
        Log.d(TAG, "Opening filter page with current criteria: $currentCriteria")
        intent.putExtra(FilterActivity.EXTRA_CURRENT_FILTER_CRITERIA, currentCriteria)
        startActivityForResult(intent, FilterActivity.REQUEST_CODE_FILTER)
    }

    private fun updateActiveFilterInfo() {
        val filterDescription = reportDetailViewModel.buildActiveFilterDescription()
        if (filterDescription != null) {
            binding.textViewActiveFilterInfo.text = filterDescription
            binding.textViewActiveFilterInfo.visibility = View.VISIBLE
        } else {
            binding.textViewActiveFilterInfo.visibility = View.GONE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FilterActivity.REQUEST_CODE_FILTER) {
            if (resultCode == Activity.RESULT_OK) {
                val returnedFilterCriteria = data?.getParcelableExtra<FilterCriteria>(FilterActivity.EXTRA_RESULT_FILTER_CRITERIA)
                if (returnedFilterCriteria != null) {
                    Log.d(TAG, "Filter criteria for report received: $returnedFilterCriteria")
                    reportDetailViewModel.applyFilterToReportItems(returnedFilterCriteria)
                } else {
                    Log.d(TAG, "No filter criteria returned, but result was OK.")
                }
            } else {
                Log.d(TAG, "Filter page was canceled or returned no data.")
            }
        }
    }
}
