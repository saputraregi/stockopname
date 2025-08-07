package com.example.aplikasistockopnameperpus // Sesuaikan dengan package Anda

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.adapter.BookListAdapter
import com.example.aplikasistockopnameperpus.databinding.ActivityStockOpnameBinding
import com.example.aplikasistockopnameperpus.viewmodel.StockOpnameUiState
import com.example.aplikasistockopnameperpus.viewmodel.StockOpnameViewModel
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockOpnameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockOpnameBinding
    private val stockViewModel: StockOpnameViewModel by viewModels()
    private lateinit var bookListAdapter: BookListAdapter

    // Objek untuk konstanta, bisa juga diletakkan di file Constants.kt terpisah
    object AppConstants {
        const val EXPORT_DATE_FORMAT = "yyyyMMdd_HHmmss" // Contoh format
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockOpnameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarStockOpname)
        // Jika Anda ingin tombol kembali standar di ActionBar:
        // supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // supportActionBar?.setDisplayShowHomeEnabled(true)

        Log.d("StockOpnameActivity", "onCreate: ViewBinding initialized.")

        setupRecyclerView()
        setupSpinner()
        setupButtonListeners()
        observeViewModelState()

        // Memulai sesi baru jika ViewModel masih dalam state Initial.
        // Ini memastikan sesi default dimulai saat Activity pertama kali dibuat atau setelah proses dimatikan.
        if (stockViewModel.uiState.value is StockOpnameUiState.Initial) {
            stockViewModel.startNewOpnameSession()
            Log.d("StockOpnameActivity", "onCreate: Initial opname session started from Activity.")
        }

        Log.d("StockOpnameActivity", "onCreate: Setup methods completed.")
    }

    // Jika menggunakan supportActionBar?.setDisplayHomeAsUpEnabled(true)
    // override fun onSupportNavigateUp(): Boolean {
    //     onBackPressedDispatcher.onBackPressed()
    //     return true
    // }

    private fun setupRecyclerView() {
        bookListAdapter = BookListAdapter(
            onItemClick = { bookWrapper ->
                // Pastikan displayScanStatus adalah string yang siap tampil dari BookMasterDisplayWrapper
                showToast("Buku: ${bookWrapper.bookMaster.title}, Status: ${bookWrapper.displayOpnameStatusText}")
            },
            onItemLongClick = { bookWrapper ->
                // Memungkinkan aksi menandai hilang hanya jika buku belum berstatus MISSING atau NEW_ITEM
                val canMarkAsMissing = when (bookWrapper.bookMaster.opnameStatus) {
                    OpnameStatus.NOT_SCANNED,
                    OpnameStatus.FOUND_CORRECT_LOCATION,
                    OpnameStatus.FOUND_WRONG_LOCATION -> true
                    else -> false
                }
                if (canMarkAsMissing) {
                    showMarkAsMissingConfirmationDialog(bookWrapper.bookMaster.id, bookWrapper.bookMaster.title)
                }
                true // Mengonsumsi event long click
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
            // Pastikan R.string.dialog_mark_missing_message memiliki placeholder %1$s untuk bookTitle
            .setMessage(getString(R.string.dialog_mark_missing_message, bookTitle))
            .setPositiveButton(getString(R.string.action_mark_missing)) { _, _ ->
                stockViewModel.markSelectedMissing(listOf(bookId))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupSpinner() {
        val filterOptions = resources.getStringArray(R.array.filter_status_options)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        // Menggunakan AutoCompleteTextView di dalam TextInputLayout untuk spinner Material Design
        binding.spinnerFilterStatus.setAdapter(spinnerAdapter)

        // Set nilai awal spinner dari ViewModel jika ada filter yang tersimpan
        (stockViewModel.uiState.value as? StockOpnameUiState.Success)?.let { successState ->
            val currentFilter = successState.currentFilter
            val position = filterOptions.indexOf(currentFilter)
            if (position >= 0) {
                // false untuk tidak memicu onItemClickListener saat setText secara programatik
                binding.spinnerFilterStatus.setText(filterOptions[position], false)
            }
        }

        binding.spinnerFilterStatus.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedFilter = parent.getItemAtPosition(position).toString()
            Log.d("StockOpnameActivity", "Filter selected: $selectedFilter")
            stockViewModel.setFilter(selectedFilter)
        }
        Log.d("StockOpnameActivity", "setupSpinner: Spinner (AutoCompleteTextView) initialized.")
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
                // Pastikan ImportExportActivity ada dan dideklarasikan di Manifest
                val intent = Intent(this, ImportExportActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("StockOpnameActivity", "Error starting ImportExportActivity", e)
                showToast(getString(R.string.error_opening_import_export_page))
            }
        }

        binding.buttonStartUhfScan.setOnClickListener {
            Log.d("StockOpnameActivity", "UHF Scan button clicked.")
            stockViewModel.toggleUhfScan()
        }

        binding.buttonStartBarcodeScan.setOnClickListener {
            Log.d("StockOpnameActivity", "Barcode Scan button clicked.")
            stockViewModel.toggleBarcodeScan()
        }

        binding.buttonSaveAndExport.setOnClickListener { // Tombol "Simpan"
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

    private fun showStartNewSessionDialog() {
        val currentState = stockViewModel.uiState.value
        if (currentState is StockOpnameUiState.Success && (currentState.isUhfScanning || currentState.isBarcodeScanning)) {
            showToast(getString(R.string.stop_scan_before_new_session))
            return
        }

        val hasUnsavedData = (currentState as? StockOpnameUiState.Success)?.let {
            it.foundBooksInSession > 0 ||
                    it.misplacedBooksInSession > 0 ||
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
                            updateStatistics(0, 0, 0, 0, 0)
                            binding.textViewLastScannedTag.text = getString(R.string.opname_session_not_started)
                            binding.textViewSessionNameValue.text = getString(R.string.default_session_name_prefix) // Atau nama sesi default dari ViewModel
                            binding.editTextSessionName.setText("")
                        }
                        is StockOpnameUiState.Loading -> {
                            setButtonStates(isLoading = true, isUhfScanning = false, isBarcodeScanning = false, canSave = false)
                            binding.textViewLastScannedTag.text = getString(R.string.status_loading_data) // Pastikan string ini ada
                        }
                        is StockOpnameUiState.Success -> {
                            val canSaveCurrentSession = state.foundBooksInSession > 0 ||
                                    state.misplacedBooksInSession > 0 ||
                                    state.newOrUnexpectedBooksCount > 0 ||
                                    state.missingBooksInSession > 0 || // Memastikan semua kondisi penyimpanan diperhitungkan
                                    state.allBooksInCurrentSessionForCalc.any { it.opnameStatus == OpnameStatus.MISSING } // Double check untuk item yang ditandai hilang


                            setButtonStates(
                                isLoading = false,
                                isUhfScanning = state.isUhfScanning,
                                isBarcodeScanning = state.isBarcodeScanning,
                                canSave = canSaveCurrentSession
                            )
                            bookListAdapter.submitList(state.displayedBooks)
                            updateStatistics(
                                state.totalBooksInMaster,
                                state.foundBooksInSession,
                                state.misplacedBooksInSession,
                                state.missingBooksInSession,
                                state.newOrUnexpectedBooksCount
                            )
                            binding.textViewLastScannedTag.text = state.lastScanMessage.ifEmpty { getString(R.string.scan_ready_prompt) }
                            binding.textViewSessionNameValue.text = state.currentOpnameSessionName
                            // Hanya update EditText jika berbeda untuk menghindari loop atau kehilangan fokus pengguna
                            if (binding.editTextSessionName.text.toString() != state.currentOpnameSessionName) {
                                binding.editTextSessionName.setText(state.currentOpnameSessionName)
                            }

                            state.toastMessage?.let { message ->
                                showToast(message)
                                stockViewModel.clearToastMessage() // Memberitahu ViewModel bahwa pesan telah ditampilkan
                            }
                        }
                        is StockOpnameUiState.Error -> {
                            setButtonStates(isLoading = false, isUhfScanning = false, isBarcodeScanning = false, canSave = false)
                            // Pastikan R.string.error_prefix memiliki placeholder %1$s untuk state.message
                            val errorMessage = getString(R.string.error_prefix, state.message)
                            showToast(errorMessage, Toast.LENGTH_LONG)
                            binding.textViewLastScannedTag.text = errorMessage
                        }
                    }
                }
            }
        }
        Log.d("StockOpnameActivity", "observeViewModelState: UI state observer setup.")
    }

    private fun updateStatistics(total: Int, found: Int, misplaced: Int, missing: Int, newOrUnexpected: Int) {
        binding.textViewTotalBooksValue.text = total.toString()
        binding.textViewFoundBooksValue.text = found.toString()
        binding.textViewMissingBooksValue.text = missing.toString()
        binding.textViewMisplacedBooksValue.text = misplaced.toString()
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

        binding.spinnerFilterStatus.isEnabled = enableGlobalActions
        binding.textFieldFilterStatus.isEnabled = enableGlobalActions // TextInputLayout yang membungkus spinner
        binding.editTextSessionName.isEnabled = enableGlobalActions
        binding.buttonSaveSessionName.isEnabled = enableGlobalActions
    }

    private fun handleSaveOpname() {
        val currentState = stockViewModel.uiState.value
        if (currentState !is StockOpnameUiState.Success) {
            showToast(getString(R.string.no_active_session_to_save))
            return
        }

        // Periksa apakah ada data yang signifikan untuk disimpan
        val hasDataToSave = currentState.foundBooksInSession > 0 ||
                currentState.newOrUnexpectedBooksCount > 0 ||
                currentState.misplacedBooksInSession > 0 ||
                currentState.allBooksInCurrentSessionForCalc.any { it.opnameStatus == OpnameStatus.MISSING }

        if (!hasDataToSave) {
            showToast(getString(R.string.no_scanned_or_marked_data_to_save))
            return
        }

        if (currentState.isUhfScanning || currentState.isBarcodeScanning) {
            showToast(getString(R.string.stop_scan_before_saving))
            return
        }

        // Menggunakan nama sesi dari state, atau generate nama default jika kosong
        val opnameSessionName = currentState.currentOpnameSessionName.ifBlank {
            val timestamp = SimpleDateFormat(AppConstants.EXPORT_DATE_FORMAT, Locale.getDefault()).format(Date())
            // Pastikan R.string.default_opname_session_name_format memiliki placeholder %1$s untuk timestamp
            getString(R.string.default_opname_session_name_format, timestamp)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_opname_results_title))
            // Pastikan R.string.save_confirmation_message_prefix memiliki placeholder %1$s untuk opnameSessionName
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
                    // Pastikan R.string.opname_saved_report_id memiliki placeholder %1$d untuk reportId dan %2$s untuk message
                    getString(R.string.opname_saved_report_id, reportId, message)
                } else {
                    message // Pesan sukses umum dari ViewModel (pastikan sudah dilokalisasi jika perlu)
                }
                showToast(displayMessage, Toast.LENGTH_LONG)
                // ViewModel akan menangani reset sesi atau memulai sesi baru jika berhasil.
            } else {
                // Pastikan R.string.failed_to_save_opname_session memiliki placeholder %1$s untuk message
                val failMessage = getString(R.string.failed_to_save_opname_session, message)
                showToast(failMessage, Toast.LENGTH_LONG)
            }
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("StockOpnameActivity", "onDestroy called.")
        // Tidak ada pembersihan spesifik SDK yang disebutkan,
        // jadi fokus pada lifecycle ViewModel untuk pembersihan data.
    }
}
