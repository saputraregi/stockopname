package com.example.aplikasistockopnameperpus // Sesuaikan dengan package Anda

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
//import android.widget.AdapterView
//import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
// import androidx.compose.ui.geometry.isEmpty // Tidak digunakan, bisa dihapus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.adapter.BookListAdapter
import com.example.aplikasistockopnameperpus.databinding.ActivityStockOpnameBinding
import com.example.aplikasistockopnameperpus.viewmodel.StockOpnameUiState
import com.example.aplikasistockopnameperpus.viewmodel.StockOpnameViewModel
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus
import com.example.aplikasistockopnameperpus.FilterActivity // Pastikan import ini benar
import com.example.aplikasistockopnameperpus.model.FilterCriteria // Pastikan import ini benar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockOpnameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockOpnameBinding
    private val stockViewModel: StockOpnameViewModel by viewModels()
    private lateinit var bookListAdapter: BookListAdapter

    // --- AWAL PENAMBAHAN UNTUK TOMBOL FISIK & MODE SCAN ---
    private val TRIGGER_KEY_MAIN = 293
    private val TRIGGER_KEY_BACKUP = 139

    private enum class ScanMode { UHF, BARCODE }
    private var currentScanMode = ScanMode.UHF // Default mode, bisa Anda sesuaikan
    // --- AKHIR PENAMBAHAN UNTUK TOMBOL FISIK & MODE SCAN ---


    object AppConstants {
        const val EXPORT_DATE_FORMAT = "yyyyMMdd_HHmmss"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockOpnameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarStockOpname)

        Log.d("StockOpnameActivity", "onCreate: ViewBinding initialized.")

        // --- AWAL PENAMBAHAN: Inisialisasi Switch Mode Scan ---
        // Asumsikan Anda telah menambahkan <com.google.android.material.switchmaterial.SwitchMaterial
        // android:id="@+id/switchScanMode" ... /> di activity_stock_opname.xml
        // Jika belum, Anda perlu menambahkannya.
        // Pastikan binding.switchScanMode merujuk ke ID yang benar.

        // Defaultkan switch ke mode UHF (jika isChecked = true berarti BARCODE)
        binding.switchScanModeInternal.isChecked = (currentScanMode == ScanMode.BARCODE) // Sesuaikan jika perlu
        binding.switchScanModeInternal.setOnCheckedChangeListener { _, isChecked ->
             currentScanMode = if (isChecked) {
                 Log.d("StockOpnameActivity", "Mode Pindai diubah ke BARCODE melalui Switch")
                 ScanMode.BARCODE
             } else {
                 Log.d("StockOpnameActivity", "Mode Pindai diubah ke UHF melalui Switch")
                 ScanMode.UHF
             }
        //     // Anda mungkin ingin menonaktifkan tombol scan lain di UI jika ada
             updateScanButtonUIBasedOnMode()
         }
         updateScanButtonUIBasedOnMode() // Panggil untuk set tampilan awal tombol
        // --- AKHIR PENAMBAHAN: Inisialisasi Switch Mode Scan ---

        setupRecyclerView()
        setupButtonListeners()
        observeViewModelState()

        if (stockViewModel.uiState.value is StockOpnameUiState.Initial) {
            stockViewModel.startNewOpnameSession()
            Log.d("StockOpnameActivity", "onCreate: Initial opname session requested from Activity.")
        }

        Log.d("StockOpnameActivity", "onCreate: Setup methods completed.")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        if (keyCode == TRIGGER_KEY_MAIN || keyCode == TRIGGER_KEY_BACKUP) {
            if (event.repeatCount == 0) {
                Log.d("StockOpnameActivity", "Tombol fisik ditekan (keyCode: $keyCode), memanggil onPhysicalTriggerPressed.")
                onPhysicalTriggerPressed()
            }
            return true // Event sudah ditangani
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onPhysicalTriggerPressed() {
        // Pastikan Anda mendapatkan state terbaru dari switchScanMode jika listenernya
        // tidak langsung mengupdate currentScanMode atau jika ada race condition.
        // Untuk sekarang, kita asumsikan currentScanMode sudah benar dari listener switch.
        currentScanMode = if (binding.switchScanModeInternal.isChecked) ScanMode.BARCODE else ScanMode.UHF


        val currentState = stockViewModel.uiState.value
        if (currentState is StockOpnameUiState.Loading) {
            showToast(getString(R.string.wait_for_loading_to_finish))
            Log.d("StockOpnameActivity", "Tombol fisik: Aksi dibatalkan, sedang loading.")
            return
        }

        // Pengecekan silang scan (jika UHF mau scan tapi Barcode aktif, atau sebaliknya)
        if (currentState is StockOpnameUiState.Success) {
            if (currentScanMode == ScanMode.UHF && currentState.isBarcodeScanning) {
                showToast(getString(R.string.stop_barcode_scan_before_uhf)) // Tambahkan string ini
                Log.d("StockOpnameActivity", "Tombol fisik: Aksi UHF dibatalkan, scan Barcode sedang aktif.")
                return
            }
            if (currentScanMode == ScanMode.BARCODE && currentState.isUhfScanning) {
                showToast(getString(R.string.stop_uhf_scan_before_barcode)) // Tambahkan string ini
                Log.d("StockOpnameActivity", "Tombol fisik: Aksi Barcode dibatalkan, scan UHF sedang aktif.")
                return
            }
        }


        when (currentScanMode) {
            ScanMode.UHF -> {
                Log.i("StockOpnameActivity", "Tombol fisik: Mode UHF aktif, memanggil toggleUhfScan.")
                stockViewModel.toggleUhfScan()
            }
            ScanMode.BARCODE -> {
                Log.i("StockOpnameActivity", "Tombol fisik: Mode BARCODE aktif, memanggil toggleBarcodeScan.")
                stockViewModel.toggleBarcodeScan()
            }
        }
    }

    // --- AKHIR PENAMBAHAN: Metode untuk menangani onKeyDown dan aksi tombol fisik ---

    // Metode helper untuk mengupdate UI tombol scan di layout (jika Anda masih memilikinya)
    // berdasarkan mode switch. Ini opsional.
    private fun updateScanButtonUIBasedOnMode() {
         if (currentScanMode == ScanMode.UHF) {
             binding.buttonStartUhfScan.visibility = View.VISIBLE
             binding.buttonStartBarcodeScan.visibility = View.GONE // Atau set isEnabled = false
         } else {
             binding.buttonStartUhfScan.visibility = View.GONE // Atau set isEnabled = false
             binding.buttonStartBarcodeScan.visibility = View.VISIBLE
         }
     }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FilterActivity.REQUEST_CODE_FILTER) {
            if (resultCode == Activity.RESULT_OK) {
                val returnedFilterCriteria = data?.getParcelableExtra<FilterCriteria>(FilterActivity.EXTRA_RESULT_FILTER_CRITERIA)
                if (returnedFilterCriteria != null) {
                    Log.d("StockOpnameActivity", "Filter criteria received: $returnedFilterCriteria")
                    stockViewModel.applyFilterCriteria(returnedFilterCriteria) // Panggil fungsi di ViewModel
                    updateFilterButtonText(true) // Contoh fungsi
                } else {
                    Log.d("StockOpnameActivity", "No filter criteria returned, but result was OK.")
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.d("StockOpnameActivity", "Filter page was canceled.")
            }
        }
    }

    // Contoh fungsi untuk update teks tombol filter (opsional)
    private fun updateFilterButtonText(isFilterActive: Boolean) {
        if (isFilterActive) {
            // Cek apakah filter benar-benar memiliki kriteria selain default
            val currentCriteria = stockViewModel.currentFilterCriteria.value
            val isDefaultCriteria = currentCriteria.opnameStatus == null &&
                    currentCriteria.titleQuery.isNullOrBlank() &&
                    currentCriteria.itemCodeQuery.isNullOrBlank() &&
                    currentCriteria.locationQuery.isNullOrBlank() &&
                    currentCriteria.epcQuery.isNullOrBlank() &&
                    currentCriteria.isNewOrUnexpected == null

            if (!isDefaultCriteria) {
                binding.buttonShowFilterPage.text = getString(R.string.button_filter_label_active) // Tambahkan string "Filter (Aktif)"
                // Anda juga bisa mengubah ikon atau style tombol
            } else {
                binding.buttonShowFilterPage.text = getString(R.string.button_filter_label)
            }
        } else {
            binding.buttonShowFilterPage.text = getString(R.string.button_filter_label)
        }
    }

    private fun setupRecyclerView() {
        bookListAdapter = BookListAdapter(
            onItemClick = { bookWrapper ->
                showToast("Buku: ${bookWrapper.bookMaster.title}, Status: ${bookWrapper.displayOpnameStatusText}")
            },
            onItemLongClick = { bookWrapper ->
                // PENYESUAIAN: Logika canMarkAsMissing disesuaikan dengan status baru
                val canMarkAsMissing = when (bookWrapper.bookMaster.opnameStatus) {
                    OpnameStatus.NOT_SCANNED,
                    OpnameStatus.FOUND -> true // Buku yang sudah FOUND juga bisa ditandai hilang jika salah scan
                    else -> false
                }
                if (canMarkAsMissing) {
                    showMarkAsMissingConfirmationDialog(bookWrapper.bookMaster.id, bookWrapper.bookMaster.title ?: getString(R.string.unknown_title))
                }
                true
            }
        )
        binding.recyclerViewBooks.apply {
            adapter = bookListAdapter
            layoutManager = LinearLayoutManager(this@StockOpnameActivity)
        }
        Log.d("StockOpnameActivity", "setupRecyclerView: RecyclerView initialized.")
    }

    private fun showMarkAsMissingConfirmationDialog(bookId: Long, bookTitle: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_mark_missing_title))
            .setMessage(getString(R.string.dialog_mark_missing_message, bookTitle))
            .setPositiveButton(getString(R.string.action_mark_missing)) { _, _ ->
                stockViewModel.markSelectedMissing(listOf(bookId))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }



    private fun setupButtonListeners() {
        binding.buttonStartNewSession.setOnClickListener {
            Log.d("StockOpnameActivity", "Start New Session button clicked.")
            showStartNewSessionDialog()
        }

        binding.buttonImportData.setOnClickListener {
            Log.d("StockOpnameActivity", "Import Data button clicked.")
            val currentState = stockViewModel.uiState.value
            if (currentState is StockOpnameUiState.Loading) {
                showToast(getString(R.string.wait_for_loading_to_finish))
                return@setOnClickListener
            }
            if (currentState is StockOpnameUiState.Success && (currentState.isUhfScanning || currentState.isBarcodeScanning)) {
                showToast(getString(R.string.stop_scan_before_action))
                return@setOnClickListener
            }
            try {
                val intent = Intent(this, ImportExportActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("StockOpnameActivity", "Error starting ImportExportActivity", e)
                showToast(getString(R.string.error_opening_import_export_page))
            }
        }

        binding.buttonShowFilterPage.setOnClickListener { // Gunakan ID tombol filter baru Anda
            openFilterPage()
        }
        Log.d("StockOpnameActivity", "setupButtonListeners: Listeners set.")

        binding.gridLayoutStatistics.setOnClickListener {
            navigateToBookListDetail(getString(R.string.title_all_books), stockViewModel.currentFilterCriteria.value)
        }

        binding.textViewFoundBooksValue.setOnClickListener {
            val foundFilter = FilterCriteria(opnameStatus = OpnameStatus.FOUND)
            // Anda mungkin ingin menggabungkan dengan filter yang sudah aktif:
            val currentGlobalFilter = stockViewModel.currentFilterCriteria.value
            val combinedFilter = foundFilter.copy( // Salin filter global
                titleQuery = currentGlobalFilter.titleQuery,
                itemCodeQuery = currentGlobalFilter.itemCodeQuery,
                locationQuery = currentGlobalFilter.locationQuery,
                epcQuery = currentGlobalFilter.epcQuery
                // Jangan override isNewOrUnexpected kecuali memang diinginkan
            )
            navigateToBookListDetail(getString(R.string.stat_found), combinedFilter)
        }

        binding.textViewMissingBooksValue.setOnClickListener {
            val missingFilter = FilterCriteria(opnameStatus = OpnameStatus.MISSING)
            val currentGlobalFilter = stockViewModel.currentFilterCriteria.value
            val combinedFilter = missingFilter.copy(
                titleQuery = currentGlobalFilter.titleQuery,
                itemCodeQuery = currentGlobalFilter.itemCodeQuery,
                locationQuery = currentGlobalFilter.locationQuery,
                epcQuery = currentGlobalFilter.epcQuery
            )
            navigateToBookListDetail(getString(R.string.stat_missing), combinedFilter)
        }

        binding.textViewNewOrUnexpectedBooksValue.setOnClickListener {
            val newFilter = FilterCriteria(isNewOrUnexpected = true) // Ini mungkin perlu disesuaikan dengan bagaimana Anda menandai buku baru/tak terduga
            val currentGlobalFilter = stockViewModel.currentFilterCriteria.value
            val combinedFilter = newFilter.copy(
                titleQuery = currentGlobalFilter.titleQuery,
                itemCodeQuery = currentGlobalFilter.itemCodeQuery,
                locationQuery = currentGlobalFilter.locationQuery,
                epcQuery = currentGlobalFilter.epcQuery
            )
            // Jika ingin semua yang 'isNewOrUnexpected' tanpa peduli status opname lain:
            // val specificNewFilter = FilterCriteria(isNewOrUnexpected = true)
            navigateToBookListDetail(getString(R.string.stat_new_unexpected), combinedFilter)
        }

        binding.textViewTotalBooksValue.setOnClickListener {
            val totalMasterFilter = stockViewModel.currentFilterCriteria.value.copy(
                opnameStatus = null, // Tampilkan semua status opname
                isNewOrUnexpected = null // Tampilkan yang diharapkan dan tidak diharapkan
            )
            navigateToBookListDetail(getString(R.string.stat_total_master), totalMasterFilter)
        }


        binding.buttonStartUhfScan.setOnClickListener {
            Log.d("StockOpnameActivity", "UHF Scan button clicked.")
                if (::binding.isInitialized && binding.switchScanModeInternal.isChecked) { // Jika BARCODE aktif
                    binding.switchScanModeInternal.isChecked = false // Pindah ke UHF
                }
            currentScanMode = ScanMode.UHF // Pastikan mode sesuai
            stockViewModel.toggleUhfScan()
        }

        binding.buttonStartBarcodeScan.setOnClickListener {
            Log.d("StockOpnameActivity", "Barcode Scan button clicked.")
                if (::binding.isInitialized && !binding.switchScanModeInternal.isChecked) { // Jika UHF aktif
                    binding.switchScanModeInternal.isChecked = true // Pindah ke BARCODE
                }
             currentScanMode = ScanMode.BARCODE // Pastikan mode sesuai
            stockViewModel.toggleBarcodeScan()
        }

        binding.buttonSaveAndExport.setOnClickListener {
            Log.d("StockOpnameActivity", "Save button clicked.")
            handleSaveOpname()
        }

        binding.buttonSaveSessionName.setOnClickListener {
            val newName = binding.editTextSessionName.text.toString().trim()
            if (newName.isNotEmpty()) {
                stockViewModel.updateCurrentOpnameSessionName(newName)
                showToast(getString(R.string.toast_session_name_updated))
            } else {
                showToast(getString(R.string.toast_session_name_cannot_be_empty))
            }
        }
        Log.d("StockOpnameActivity", "setupButtonListeners: Listeners set.")
    }

    private fun navigateToBookListDetail(title: String, filterCriteria: FilterCriteria) {
        val intent = Intent(this, BookListDetailActivity::class.java).apply {
            putExtra(BookListDetailActivity.EXTRA_FILTER_CRITERIA, filterCriteria)
            putExtra(BookListDetailActivity.EXTRA_TITLE, title)
        }
        startActivity(intent)
    }

    private fun openFilterPage() {
        val intent = Intent(this, FilterActivity::class.java)
        // Ambil kriteria filter yang aktif saat ini dari ViewModel
        val currentCriteria = stockViewModel.currentFilterCriteria.value // Asumsi ViewModel sudah memiliki state ini
        intent.putExtra(FilterActivity.EXTRA_CURRENT_FILTER_CRITERIA, currentCriteria)
        startActivityForResult(intent, FilterActivity.REQUEST_CODE_FILTER)
    }

    private fun showStartNewSessionDialog() {
        val currentState = stockViewModel.uiState.value
        if (currentState is StockOpnameUiState.Success && (currentState.isUhfScanning || currentState.isBarcodeScanning)) {
            showToast(getString(R.string.stop_scan_before_new_session))
            return
        }

        // PENYESUAIAN: Hapus misplacedBooksInSession dari pengecekan hasUnsavedData
        val hasUnsavedData = (currentState as? StockOpnameUiState.Success)?.let {
            it.foundBooksInSession > 0 ||
                    // it.misplacedBooksInSession > 0 || // DIHAPUS
                    it.newOrUnexpectedBooksCount > 0 ||
                    it.allBooksInCurrentSessionForCalc.any { book -> book.opnameStatus == OpnameStatus.MISSING }
        } ?: false

        val dialogMessage = if (hasUnsavedData) {
            getString(R.string.dialog_new_session_warning_unsaved_data)
        } else {
            getString(R.string.dialog_new_session_confirmation)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_new_session_title))
            .setMessage(dialogMessage)
            .setPositiveButton(getString(R.string.action_start_new)) { _, _ ->
                stockViewModel.startNewOpnameSession()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                stockViewModel.uiState.collect { state ->
                    Log.d("StockOpnameActivity", "New UI State: ${state::class.java.simpleName}")
                    binding.progressBarOpname.visibility = if (state is StockOpnameUiState.Loading) View.VISIBLE else View.GONE

                    when (state) {
                        is StockOpnameUiState.Initial -> {
                            setButtonStates(isLoading = false, isUhfScanning = false, isBarcodeScanning = false, canSave = false)
                            bookListAdapter.submitList(emptyList())
                            updateStatistics(0, 0, 0, 0) // PENYESUAIAN: Hapus parameter misplaced
                            binding.textViewLastScannedTag.text = getString(R.string.opname_session_not_started)
                            binding.textViewSessionNameValue.text = getString(R.string.default_session_name_prefix)
                            binding.editTextSessionName.setText("")
                            Log.d("StockOpnameActivity", "UIState.Initial: UI Reset")
                        }
                        is StockOpnameUiState.Loading -> {
                            setButtonStates(isLoading = true, isUhfScanning = false, isBarcodeScanning = false, canSave = false)
                            binding.textViewLastScannedTag.text = getString(R.string.status_loading_data)
                            Log.d("StockOpnameActivity", "UIState.Loading: UI Updated")
                        }
                        is StockOpnameUiState.Success -> {
                            // PENYESUAIAN: Hapus misplacedBooksInSession dari kalkulasi canSaveCurrentSession
                            val canSaveCurrentSession = state.foundBooksInSession > 0 ||
                                    // state.misplacedBooksInSession > 0 || // DIHAPUS
                                    state.newOrUnexpectedBooksCount > 0 ||
                                    state.missingBooksInSession > 0 ||
                                    state.allBooksInCurrentSessionForCalc.any { it.opnameStatus == OpnameStatus.MISSING }

                            setButtonStates(
                                isLoading = false,
                                isUhfScanning = state.isUhfScanning,
                                isBarcodeScanning = state.isBarcodeScanning,
                                canSave = canSaveCurrentSession
                            )
                            bookListAdapter.submitList(state.displayedBooks)
                            // PENYESUAIAN: Hapus state.misplacedBooksInSession dari pemanggilan updateStatistics
                            updateStatistics(
                                state.totalBooksInMaster,
                                state.foundBooksInSession,
                                // state.misplacedBooksInSession, // DIHAPUS
                                state.missingBooksInSession,
                                state.newOrUnexpectedBooksCount
                            )
                            binding.textViewLastScannedTag.text = state.lastScanMessage.ifEmpty { getString(R.string.scan_ready_prompt) }
                            binding.textViewSessionNameValue.text = state.currentOpnameSessionName
                            if (binding.editTextSessionName.text.toString() != state.currentOpnameSessionName) {
                                binding.editTextSessionName.setText(state.currentOpnameSessionName)
                            }

                            state.toastMessage?.let { message ->
                                showToast(message)
                                stockViewModel.clearToastMessage()
                            }
                            Log.d("StockOpnameActivity", "UIState.Success: UHF=${state.isUhfScanning}, Barcode=${state.isBarcodeScanning}, Books=${state.displayedBooks.size}, Filter=${state.currentFilter}")
                        }
                        is StockOpnameUiState.Error -> {
                            setButtonStates(isLoading = false, isUhfScanning = false, isBarcodeScanning = false, canSave = false)
                            val errorMessage = getString(R.string.error_prefix_colon, state.message)
                            showToast(errorMessage, Toast.LENGTH_LONG)
                            binding.textViewLastScannedTag.text = errorMessage
                            Log.e("StockOpnameActivity", "UIState.Error: ${state.message}")
                        }
                    }
                }
            }
        }
        Log.d("StockOpnameActivity", "observeViewModelState: UI state observer setup.")
    }

    // PENYESUAIAN: Hapus parameter misplaced dan update UI yang sesuai
    private fun updateStatistics(total: Int, found: Int, /*misplaced: Int,*/ missing: Int, newOrUnexpected: Int) {
        binding.textViewTotalBooksValue.text = total.toString()
        binding.textViewFoundBooksValue.text = found.toString()
        binding.textViewMissingBooksValue.text = missing.toString()
        // Opsi: Sembunyikan atau set ke 0 untuk Misplaced Books
        //binding.textViewMisplacedBooksValue.text = "0" // Atau sembunyikan View: binding.textViewMisplacedBooksValue.visibility = View.GONE (dan labelnya)
        binding.textViewNewOrUnexpectedBooksValue.text = newOrUnexpected.toString()
    }

    private fun setButtonStates(isLoading: Boolean, isUhfScanning: Boolean, isBarcodeScanning: Boolean, canSave: Boolean) {
        val enableGlobalActions = !isLoading && !isUhfScanning && !isBarcodeScanning

        binding.buttonStartNewSession.isEnabled = enableGlobalActions
        binding.buttonImportData.isEnabled = enableGlobalActions

        binding.buttonStartUhfScan.isEnabled = !isLoading && !isBarcodeScanning
        binding.buttonStartUhfScan.text = if (isUhfScanning) getString(R.string.button_stop_uhf_scan) else getString(R.string.button_start_uhf_scan)

        binding.buttonStartBarcodeScan.isEnabled = !isLoading && !isUhfScanning
        binding.buttonStartBarcodeScan.text = if (isBarcodeScanning) getString(R.string.button_stop_barcode_scan) else getString(R.string.button_start_barcode_scan)

        binding.buttonSaveAndExport.isEnabled = enableGlobalActions && canSave

        binding.editTextSessionName.isEnabled = enableGlobalActions
        binding.buttonSaveSessionName.isEnabled = enableGlobalActions
    }

    private fun handleSaveOpname() {
        val currentState = stockViewModel.uiState.value
        if (currentState !is StockOpnameUiState.Success) {
            showToast(getString(R.string.no_active_session_to_save))
            return
        }

        // PENYESUAIAN: Hapus misplacedBooksInSession dari pengecekan hasDataToSave
        // val hasDataToSave = currentState.foundBooksInSession > 0 || // Ini sudah ada di canSaveCurrentSession
        //         currentState.newOrUnexpectedBooksCount > 0 ||
        //         // currentState.misplacedBooksInSession > 0 || // DIHAPUS
        //         currentState.allBooksInCurrentSessionForCalc.any { it.opnameStatus == OpnameStatus.MISSING }

        if (currentState.isUhfScanning || currentState.isBarcodeScanning) {
            showToast(getString(R.string.stop_scan_before_saving))
            return
        }

        // Kalkulasi canSaveCurrentSession dari observeViewModelState.Success sudah cukup.
        // Jika !canSave (dari setButtonStates), tombol save tidak akan aktif.
        // Jadi, jika fungsi ini dipanggil, kita asumsikan ada data yang bisa disimpan
        // atau pengguna tetap ingin menyimpan sesi kosong (jika diizinkan).
        // Untuk lebih aman, bisa cek ulang:
        val canSaveCurrentSession = currentState.foundBooksInSession > 0 ||
                currentState.newOrUnexpectedBooksCount > 0 ||
                currentState.missingBooksInSession > 0 || // missingBooksInSession dari state UI
                currentState.allBooksInCurrentSessionForCalc.any { it.opnameStatus == OpnameStatus.MISSING }
        if (!canSaveCurrentSession && currentState.allBooksInCurrentSessionForCalc.none { it.opnameStatus != OpnameStatus.NOT_SCANNED } ){
            showToast(getString(R.string.no_data_changes_to_save)) // Buat string resource ini
            return
        }


        val opnameSessionName = currentState.currentOpnameSessionName.ifBlank {
            val timestamp = SimpleDateFormat(AppConstants.EXPORT_DATE_FORMAT, Locale.getDefault()).format(Date())
            getString(R.string.default_opname_session_name_format, timestamp)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_opname_results_title))
            .setMessage(getString(R.string.save_confirmation_message_prefix, opnameSessionName))
            .setPositiveButton(getString(R.string.save_label)) { _, _ ->
                saveSessionAndHandleResult()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveSessionAndHandleResult() {
        stockViewModel.saveCurrentOpnameSession { reportId, success, message ->
            if (success) {
                val displayMessage = if (reportId != null && reportId > 0) {
                    getString(R.string.opname_saved_report_id, reportId, message)
                } else {
                    message
                }
                showToast(displayMessage, Toast.LENGTH_LONG)
            } else {
                val failMessage = getString(R.string.failed_to_save_opname_session, message)
                showToast(failMessage, Toast.LENGTH_LONG)
            }
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    override fun onPause() {
        super.onPause()
        Log.d("StockOpnameActivity", "onPause called.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("StockOpnameActivity", "onDestroy called.")
    }
}
