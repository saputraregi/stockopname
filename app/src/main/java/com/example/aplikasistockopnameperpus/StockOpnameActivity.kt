package com.example.aplikasistockopnameperpus // Sesuaikan dengan package Anda

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import android.content.Intent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.adapter.BookListAdapter
import com.example.aplikasistockopnameperpus.databinding.ActivityStockOpnameBinding
import com.example.aplikasistockopnameperpus.viewmodel.StockOpnameViewModel
import com.example.aplikasistockopnameperpus.viewmodel.StockOpnameUiState // PENYESUAIAN: Nama UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockOpnameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockOpnameBinding
    private val stockViewModel: StockOpnameViewModel by viewModels()
    private lateinit var bookListAdapter: BookListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockOpnameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // setSupportActionBar(binding.toolbarStockOpname) // Jika Anda menggunakan toolbar kustom
        // supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // supportActionBar?.setDisplayShowHomeEnabled(true)

        Log.d("StockOpnameActivity", "onCreate: ViewBinding initialized.")

        setupRecyclerView()
        setupSpinner()
        setupButtonListeners()
        observeViewModelState() // PENYESUAIAN: Nama fungsi diubah

        // Anda bisa memanggil startNewOpnameSession di sini jika diperlukan saat Activity pertama kali dibuat
        // Misalnya, jika belum ada state yang tersimpan atau ini adalah peluncuran pertama.
        // if (savedInstanceState == null) {
        //     stockViewModel.startNewOpnameSession("Sesi Awal Otomatis")
        // }

        Log.d("StockOpnameActivity", "onCreate: Setup methods called.")
    }

    // override fun onSupportNavigateUp(): Boolean {
    //     onBackPressedDispatcher.onBackPressed()
    //     return true
    // }

    private fun setupRecyclerView() {
        bookListAdapter = BookListAdapter { bookMaster -> // Tipe item sekarang BookMaster
            showToast("Buku: ${bookMaster.title}")
        }
        binding.recyclerViewBooks.apply {
            adapter = bookListAdapter
            layoutManager = LinearLayoutManager(this@StockOpnameActivity)
        }
        Log.d("StockOpnameActivity", "setupRecyclerView: RecyclerView initialized.")
    }

    private fun setupSpinner() {
        val filterOptions = resources.getStringArray(R.array.filter_status_options)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerFilterStatus.setAdapter(spinnerAdapter)

        binding.spinnerFilterStatus.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedFilter = parent.getItemAtPosition(position).toString()
            Log.d("StockOpnameActivity", "Filter selected: $selectedFilter")
            stockViewModel.setFilter(selectedFilter)
        }

        binding.spinnerFilterStatus.isFocusableInTouchMode = false
        binding.spinnerFilterStatus.isClickable = true
        binding.spinnerFilterStatus.isFocusable = false

        Log.d("StockOpnameActivity", "setupSpinner: Spinner (AutoCompleteTextView) initialized.")
    }

    private fun setupButtonListeners() {
        binding.buttonImportData.setOnClickListener {
            Log.d("StockOpnameActivity", "Import Data button clicked.")
            val currentState = stockViewModel.uiState.value

            if (currentState is StockOpnameUiState.Loading) { // PENYESUAIAN: Nama UiState
                showToast(getString(R.string.wait_for_loading_to_finish))
                return@setOnClickListener
            }

            if (currentState is StockOpnameUiState.Success && (currentState.isUhfScanning || currentState.isBarcodeScanning)) { // PENYESUAIAN: Nama field dan UiState
                showToast(getString(R.string.stop_scan_before_action))
                return@setOnClickListener
            }

            try {
                // Pastikan ImportExportActivity ada dan dideklarasikan di Manifest
                val intent = Intent(this, ImportExportActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("StockOpnameActivity", "Error starting ImportExportActivity", e)
                showToast("Error: Tidak bisa membuka halaman Import/Export.")
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

        binding.buttonSaveAndExport.setOnClickListener { // Tombol ini sekarang hanya akan "Simpan"
            Log.d("StockOpnameActivity", "Save button clicked.")
            handleSaveOpname()
        }
        Log.d("StockOpnameActivity", "setupButtonListeners: Listeners set.")
    }

    private fun observeViewModelState() { // PENYESUAIAN: Nama fungsi diubah
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                stockViewModel.uiState.collect { state ->
                    Log.d("StockOpnameActivity", "New UI State: ${state::class.java.simpleName}")
                    binding.progressBarOpname.visibility = if (state is StockOpnameUiState.Loading) View.VISIBLE else View.GONE // PENYESUAIAN: Nama UiState

                    when (state) {
                        is StockOpnameUiState.Initial -> { // PENYESUAIAN: Nama UiState
                            setButtonStates(isLoading = false, isUhfScanning = false, isBarcodeScanning = false)
                            bookListAdapter.submitList(emptyList())
                            updateStatistics(0, 0, 0, 0, 0) // PENYESUAIAN: Tambah parameter
                            binding.textViewLastScannedTag.text = getString(R.string.start_opname_session)
                            // ViewModel akan menangani pemuatan data awal atau memulai sesi baru jika diperlukan
                            // stockViewModel.startNewOpnameSession() // Panggil jika state Initial berarti sesi baru harus dimulai
                        }
                        is StockOpnameUiState.Loading -> { // PENYESUAIAN: Nama UiState
                            setButtonStates(isLoading = true, isUhfScanning = false, isBarcodeScanning = false)
                            binding.textViewLastScannedTag.text = getString(R.string.wait_for_loading_to_finish)
                        }
                        is StockOpnameUiState.Success -> { // PENYESUAIAN: Nama UiState
                            setButtonStates(isLoading = false, isUhfScanning = state.isUhfScanning, isBarcodeScanning = state.isBarcodeScanning) // PENYESUAIAN: Nama field
                            bookListAdapter.submitList(state.displayedBooks)
                            updateStatistics( // PENYESUAIAN: Parameter dan nama field
                                state.totalBooksInMaster,
                                state.foundBooksInSession,
                                state.misplacedBooksInSession,
                                state.missingBooksInSession,
                                state.newOrUnexpectedBooksCount
                            )
                            binding.textViewLastScannedTag.text = state.lastScanMessage.ifEmpty { getString(R.string.scan_ready_prompt) }

                            state.toastMessage?.let { message ->
                                showToast(message)
                                stockViewModel.toastMessageShown()
                            }
                        }
                        is StockOpnameUiState.Error -> { // PENYESUAIAN: Nama UiState
                            setButtonStates(isLoading = false, isUhfScanning = false, isBarcodeScanning = false)
                            val errorMessage = getString(R.string.error_prefix, state.message) // Gunakan string resource yang lebih sesuai
                            showToast(errorMessage, Toast.LENGTH_LONG)
                            binding.textViewLastScannedTag.text = errorMessage
                        }
                    }
                }
            }
        }
        Log.d("StockOpnameActivity", "observeViewModelState: UI state observer setup.")
    }

    // PENYESUAIAN: Tambahkan parameter untuk misplaced dan new/unexpected
    private fun updateStatistics(total: Int, found: Int, misplaced: Int, missing: Int, newOrUnexpected: Int) {
        binding.textViewTotalBooksValue.text = total.toString()
        binding.textViewFoundBooksValue.text = found.toString()
        binding.textViewMissingBooksValue.text = missing.toString()
        // Pastikan Anda memiliki TextViews ini di layout XML Anda (activity_stock_opname.xml)
        //binding.textViewMisplacedBooksValue.text = misplaced.toString() // Contoh, sesuaikan ID jika berbeda
        //binding.textViewNewOrUnexpectedBooksValue.text = newOrUnexpected.toString() // Contoh, sesuaikan ID jika berbeda
    }

    private fun setButtonStates(isLoading: Boolean, isUhfScanning: Boolean, isBarcodeScanning: Boolean) {
        val enableGlobalActions = !isLoading && !isUhfScanning && !isBarcodeScanning

        binding.buttonStartUhfScan.isEnabled = !isLoading && !isBarcodeScanning
        binding.buttonStartUhfScan.text = if (isUhfScanning) getString(R.string.button_stop_uhf_scan) else getString(R.string.button_start_uhf_scan)

        binding.buttonStartBarcodeScan.isEnabled = !isLoading && !isUhfScanning
        binding.buttonStartBarcodeScan.text = if (isBarcodeScanning) getString(R.string.button_stop_barcode_scan) else getString(R.string.button_start_barcode_scan)

        binding.buttonImportData.isEnabled = enableGlobalActions
        // PENYESUAIAN: Logika canSaveOrExport harus menggunakan field yang benar dari ViewModel
        val canSaveOrExport = enableGlobalActions &&
                ((stockViewModel.uiState.value as? StockOpnameUiState.Success)?.let {
                    it.foundBooksInSession > 0 || it.newOrUnexpectedBooksCount > 0 || it.misplacedBooksInSession > 0
                } ?: false)
        binding.buttonSaveAndExport.isEnabled = canSaveOrExport // Tombol ini sekarang hanya untuk "Simpan"
        binding.spinnerFilterStatus.isEnabled = enableGlobalActions
        binding.textFieldFilterStatus.isEnabled = enableGlobalActions
    }

    private fun handleSaveOpname() {
        val currentState = stockViewModel.uiState.value
        if (currentState !is StockOpnameUiState.Success) { // PENYESUAIAN: Nama UiState
            showToast(getString(R.string.no_active_session_to_save)) // PENYESUAIAN: Pesan lebih sesuai
            return
        }

        // PENYESUAIAN: Periksa apakah ada data yang bisa disimpan
        if (currentState.foundBooksInSession == 0 && currentState.newOrUnexpectedBooksCount == 0 && currentState.misplacedBooksInSession == 0) {
            showToast(getString(R.string.no_scanned_data_to_save))
            return
        }

        if (currentState.isUhfScanning || currentState.isBarcodeScanning) { // PENYESUAIAN: Nama field
            showToast(getString(R.string.stop_scan_before_saving))
            return
        }

        // Gunakan nama sesi dari ViewModel atau default jika kosong
        val opnameSessionName = currentState.currentOpnameSessionName.ifBlank {
            "Opname_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
        }
        // Jika Anda ingin mengizinkan pengguna mengubah nama sesi sebelum menyimpan,
        // Anda bisa menampilkan dialog dengan EditText di sini dan kemudian memanggil:
        // stockViewModel.updateCurrentOpnameSessionName(namaDariEditText)
        // sebelum memanggil saveSessionAndHandleResult().

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_opname_results_title))
            .setMessage(getString(R.string.save_confirmation_message_prefix, opnameSessionName))
            // Jika Anda ingin input nama sesi di dialog:
            // .setView(editTextUntukNamaSesi)
            .setPositiveButton(getString(R.string.save_label)) { _, _ -> // PENYESUAIAN: Hanya tombol simpan
                // val customName = editTextOpnameName.text.toString().ifBlank { opnameSessionName } // Jika ada EditText
                // stockViewModel.updateCurrentOpnameSessionName(customName)
                saveSessionAndHandleResult()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // PENYESUAIAN: Fungsi untuk memanggil ViewModel dan menangani hasil
    private fun saveSessionAndHandleResult() {
        // ViewModel.saveCurrentOpnameSession tidak lagi mengambil nama sesi, ia menggunakan dari state.
        stockViewModel.saveCurrentOpnameSession { reportId, success, message ->
            if (success) {
                val displayMessage = if (reportId != null && reportId > 0) {
                    getString(R.string.opname_saved_report_id, reportId, message)
                } else {
                    message // Pesan sukses dari ViewModel
                }
                showToast(displayMessage, Toast.LENGTH_LONG)
                // ViewModel akan memanggil startNewOpnameSession() secara internal jika berhasil,
                // yang akan di-observe oleh observeViewModelState() dan mereset UI.
            } else {
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
        // Pembersihan SDK (sdkManager.releaseResources()) sekarang ditangani di onCleared() ViewModel.
    }
}
