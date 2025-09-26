package com.example.aplikasistockopnameperpus

import android.app.Activity
import android.content.Intent
import android.net.Uri // Diperlukan untuk openExportedFile
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider // PENTING: Untuk membuka file
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope // Sudah ada
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.adapter.ReportItemAdapter // Pastikan ini sudah diubah untuk BookMasterDisplayWrapper
import com.example.aplikasistockopnameperpus.databinding.ActivityReportDetailBinding
import com.example.aplikasistockopnameperpus.model.FilterCriteria
import com.example.aplikasistockopnameperpus.viewmodel.BookMasterDisplayWrapper // Import jika onItemClick menggunakannya
import com.example.aplikasistockopnameperpus.viewmodel.ReportDetailGlobalState
import com.example.aplikasistockopnameperpus.viewmodel.ReportDetailViewModel
import com.example.aplikasistockopnameperpus.viewmodel.ReportDetailViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File // PENTING: Untuk FileProvider

class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding
    private var currentReportId: Long = -1L
    private var currentReportSessionName: String = "Laporan Detail" // Untuk judul Toolbar dan nama file ekspor

    private lateinit var reportItemAdapter: ReportItemAdapter // Pastikan ini menerima BookMasterDisplayWrapper

    // Launcher untuk FilterActivity
    private val filterActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val returnedFilterCriteria = result.data?.getParcelableExtra<FilterCriteria>(FilterActivity.EXTRA_RESULT_FILTER_CRITERIA)
            returnedFilterCriteria?.let {
                Log.d(TAG, "Filter criteria untuk report diterima: $it")
                reportDetailViewModel.applyFilterToReportItems(it)
            }
        } else {
            Log.d(TAG, "Filter page dibatalkan atau tidak mengembalikan data.")
        }
    }

    private val reportDetailViewModel: ReportDetailViewModel by lazy {
        ViewModelProvider(
            this,
            ReportDetailViewModelFactory(application, currentReportId)
        )[ReportDetailViewModel::class.java]
    }

    companion object {
        const val EXTRA_REPORT_ID = "extra_report_id"
        const val EXTRA_TITLE = "extra_title"
        private const val TAG = "ReportDetailActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentReportId = intent.getLongExtra(EXTRA_REPORT_ID, -1L)
        currentReportSessionName = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.title_activity_report_detail_default)

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
                val baseFileName = currentReportSessionName.take(50).replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
                reportDetailViewModel.exportFilteredItemCodesToTxt(baseFileName)
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarReportDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_report_detail_loading)
        binding.toolbarReportDetail.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        reportItemAdapter = ReportItemAdapter { wrapper ->
            Toast.makeText(this, "Item: ${wrapper.bookMaster.title ?: wrapper.bookMaster.itemCode}", Toast.LENGTH_SHORT).show()
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
            reportDetailViewModel.filteredReportDisplayItems.collectLatest { displayItems ->
                Log.d(TAG, "Updating adapter dengan ${displayItems.size} display items.")
                reportItemAdapter.submitList(displayItems)
                updateEmptyStateView(displayItems.isEmpty())
            }
        }

        lifecycleScope.launch {
            reportDetailViewModel.globalListState.collectLatest { state ->
                Log.d(TAG, "Global list state changed: $state")
                when (state) {
                    is ReportDetailGlobalState.Idle -> {
                        binding.progressBarReportDetail.visibility = View.GONE
                    }
                    is ReportDetailGlobalState.Loading -> {
                        binding.progressBarReportDetail.visibility = View.VISIBLE
                        binding.textViewEmptyReportItems.visibility = View.GONE
                    }
                    is ReportDetailGlobalState.Error -> {
                        binding.progressBarReportDetail.visibility = View.GONE
                        Toast.makeText(this@ReportDetailActivity, state.message, Toast.LENGTH_LONG).show()
                        updateEmptyStateView(reportItemAdapter.itemCount == 0, state.message)
                    }
                    is ReportDetailGlobalState.Success -> {
                        binding.progressBarReportDetail.visibility = View.GONE
                    }
                }
            }
        }

        lifecycleScope.launch {
            reportDetailViewModel.exportTxtState.collectLatest { state ->
                Log.d(TAG, "Export state changed: $state")
                val exportMenuItem = binding.toolbarReportDetail.menu.findItem(R.id.action_export_found_items_txt)
                when (state) {
                    is ReportDetailGlobalState.Idle -> {
                        exportMenuItem?.isEnabled = true
                    }
                    is ReportDetailGlobalState.Loading -> {
                        exportMenuItem?.isEnabled = false
                        Toast.makeText(this@ReportDetailActivity, getString(R.string.message_exporting_data), Toast.LENGTH_SHORT).show()
                    }
                    is ReportDetailGlobalState.Error -> {
                        exportMenuItem?.isEnabled = true
                        Toast.makeText(this@ReportDetailActivity, state.message, Toast.LENGTH_LONG).show()
                        reportDetailViewModel.clearExportState()
                    }
                    is ReportDetailGlobalState.Success -> {
                        exportMenuItem?.isEnabled = true
                        state.message?.let {
                            Toast.makeText(this@ReportDetailActivity, it, Toast.LENGTH_LONG).show()
                        }
                        // Jika ViewModel mengirimkan URI atau path file di state Success:
                        // (state as? ReportDetailGlobalState.FileExportSuccess)?.fileUri?.let { uri -> // Jika Anda membuat state khusus
                        //      openExportedFile(uri)
                        // }
                        reportDetailViewModel.clearExportState()
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
                // Menggunakan fungsi dari ViewModel
                val report = reportDetailViewModel.getReportHeaderDetails(currentReportId)
                if (report != null) {
                    currentReportSessionName = report.reportName
                    supportActionBar?.title = getString(R.string.title_report_detail_prefix, currentReportSessionName.take(30))
                    binding.textViewReportNameHeader.text = getString(R.string.report_session_name_prefix, currentReportSessionName)
                    binding.textViewReportStatsHeader.text = getString(
                        R.string.report_stats_format,
                        report.totalItemsExpected,
                        report.totalItemsFound,
                        report.totalItemsMissing,
                        report.totalItemsNewOrUnexpected
                    )
                } else {
                    supportActionBar?.title = getString(R.string.title_report_not_found)
                    binding.textViewReportNameHeader.text = getString(R.string.error_report_not_found_detail)
                    binding.textViewReportStatsHeader.visibility = View.GONE
                    binding.buttonShowFilterPageReportDetail.isEnabled = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading report header", e)
                supportActionBar?.title = getString(R.string.title_report_error)
                binding.textViewReportNameHeader.text = getString(R.string.error_loading_report_header)
                binding.textViewReportStatsHeader.visibility = View.GONE
            }
        }
    }

    private fun openFilterPageForReport() {
        val intent = Intent(this, FilterActivity::class.java)
        val currentCriteria = reportDetailViewModel.currentReportFilterCriteria.value
        Log.d(TAG, "Membuka filter page dengan kriteria saat ini: $currentCriteria")
        intent.putExtra(FilterActivity.EXTRA_CURRENT_FILTER_CRITERIA, currentCriteria)
        // Menggunakan konstanta yang sudah didefinisikan di FilterActivity
        intent.putExtra(FilterActivity.EXTRA_CONTEXT_USAGE, FilterActivity.CONTEXT_REPORT_DETAIL)
        filterActivityLauncher.launch(intent)
    }

    private fun updateActiveFilterInfo() {
        val filterDescription = reportDetailViewModel.buildActiveFilterDescription()
        if (filterDescription != null) {
            binding.textViewActiveFilterInfo.text = filterDescription
            binding.textViewActiveFilterInfo.visibility = View.VISIBLE
        } else {
            binding.textViewActiveFilterInfo.text = getString(R.string.filter_not_active_placeholder)
            binding.textViewActiveFilterInfo.visibility = View.GONE
        }
    }

    private fun updateEmptyStateView(isEmpty: Boolean, customMessage: String? = null) {
        if (isEmpty) {
            binding.recyclerViewReportItems.visibility = View.GONE
            binding.textViewEmptyReportItems.visibility = View.VISIBLE
            binding.textViewEmptyReportItems.text = customMessage ?:
                    if (reportDetailViewModel.currentReportFilterCriteria.value != FilterCriteria()) {
                        getString(R.string.message_no_items_match_filter)
                    } else {
                        getString(R.string.message_no_items_in_report_initial)
                    }
        } else {
            binding.recyclerViewReportItems.visibility = View.VISIBLE
            binding.textViewEmptyReportItems.visibility = View.GONE
        }
    }

    private fun openExportedFile(fileUri: Uri) {
        try {
            val authority = "${applicationContext.packageName}.provider"
            val fileToOpen = File(fileUri.path!!) // ViewModel mengembalikan Uri.fromFile()
            val contentUri = FileProvider.getUriForFile(this, authority, fileToOpen)

            Log.d(TAG, "Attempting to open file with content URI: $contentUri (original URI: $fileUri)")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, getString(R.string.error_no_app_to_open_file), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error membuka file yang diekspor: $fileUri", e)
            Toast.makeText(this, getString(R.string.error_failed_to_open_file, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}

