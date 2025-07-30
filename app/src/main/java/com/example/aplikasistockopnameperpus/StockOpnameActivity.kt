package com.example.aplikasistockopnameperpus // Sesuaikan dengan package Anda

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import android.content.Intent
import androidx.activity.viewModels // Jika Anda menggunakan by viewModels()
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.adapter.BookListAdapter // Ganti dengan path adapter Anda
import com.example.aplikasistockopnameperpus.databinding.ActivityStockOpnameBinding
import com.example.aplikasistockopnameperpus.viewmodel.StockOpnameViewModel // Ganti dengan path ViewModel Anda
import com.example.aplikasistockopnameperpus.viewmodel.UiState // Ganti dengan path UiState Anda
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockOpnameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockOpnameBinding
    private val stockViewModel: StockOpnameViewModel by viewModels() // Pastikan ViewModel dan factory-nya benar
    private lateinit var bookListAdapter: BookListAdapter // Pastikan adapter Anda ada

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockOpnameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarStockOpname)
        // Jika Anda ingin tombol kembali di Toolbar:
        // supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // supportActionBar?.setDisplayShowHomeEnabled(true)

        Log.d("StockOpnameActivity", "onCreate: ViewBinding initialized.")

        setupRecyclerView()
        setupSpinner()
        setupButtonListeners()
        observeViewModel()

        // Meminta ViewModel untuk memuat data awal jika diperlukan (misalnya dari database/sharedpref)
        // Ini bisa juga dihandle di dalam init block di ViewModel atau saat state Initial pertama kali diobserve.
        // stockViewModel.loadInitialMasterData() // Uncomment jika Anda punya fungsi ini di ViewModel

        Log.d("StockOpnameActivity", "onCreate: Setup methods called.")
    }

    // Jika menggunakan setDisplayHomeAsUpEnabled:
    // override fun onSupportNavigateUp(): Boolean {
    //     onBackPressedDispatcher.onBackPressed()
    //     return true
    // }

    private fun setupRecyclerView() {
        bookListAdapter = BookListAdapter { bookOpname -> // Ganti BookOpname dengan tipe data item Anda
            // Aksi ketika item di RecyclerView diklik
            // Contoh: Tampilkan detail atau dialog
            showToast("Buku: ${bookOpname.bookMaster.title}") // Asumsi bookOpname punya bookMaster.title
        }
        binding.recyclerViewBooks.apply {
            adapter = bookListAdapter
            layoutManager = LinearLayoutManager(this@StockOpnameActivity)
            // Tambahkan ItemDecoration jika perlu (misalnya, untuk garis pemisah)
        }
        Log.d("StockOpnameActivity", "setupRecyclerView: RecyclerView initialized.")
    }

    private fun setupSpinner() {
        val filterOptions = resources.getStringArray(R.array.filter_status_options)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        // Untuk AutoCompleteTextView di dalam TextInputLayout, kita set adapter ke AutoCompleteTextView-nya
        binding.spinnerFilterStatus.setAdapter(spinnerAdapter)

        // Listener untuk AutoCompleteTextView
        binding.spinnerFilterStatus.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedFilter = parent.getItemAtPosition(position).toString()
            Log.d("StockOpnameActivity", "Filter selected: $selectedFilter")
            stockViewModel.setFilter(selectedFilter) // Panggil fungsi di ViewModel untuk memfilter data
        }

        // Opsional: Jika Anda ingin mencegah input manual dan hanya mengizinkan pemilihan dari dropdown
        binding.spinnerFilterStatus.isFocusableInTouchMode = false
        binding.spinnerFilterStatus.isClickable = true
        binding.spinnerFilterStatus.isFocusable = false


        Log.d("StockOpnameActivity", "setupSpinner: Spinner (AutoCompleteTextView) initialized.")
    }

    private fun setupButtonListeners() {
        binding.buttonImportData.setOnClickListener {
            Log.d("StockOpnameActivity", "Import Data button clicked.")
            val currentState = stockViewModel.uiState.value // Dapatkan state saat ini

            // Cek apakah sedang loading
            if (currentState is UiState.Loading) {
                showToast(getString(R.string.wait_for_loading_to_finish)) // Buat string resource jika belum ada
                return@setOnClickListener
            }

            // Cek apakah sedang scanning (jika Success state)
            if (currentState is UiState.Success && (currentState.isScanning || currentState.isBarcodeScanning)) {
                showToast(getString(R.string.stop_scan_before_action))
                return@setOnClickListener
            }

            // Jika tidak loading dan tidak scanning, lanjutkan ke ImportExportActivity
            try {
                val intent = Intent(this, ImportExportActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                // Tangani jika ImportExportActivity tidak ditemukan atau ada error lain saat memulai
                Log.e("StockOpnameActivity", "Error starting ImportExportActivity", e)
                showToast("Error: Tidak bisa membuka halaman Import/Export.")
            }
        }

        binding.buttonStartUhfScan.setOnClickListener {
            Log.d("StockOpnameActivity", "UHF Scan button clicked.")
            stockViewModel.toggleUhfScan() // ViewModel akan mengatur state isScanning
        }

        binding.buttonStartBarcodeScan.setOnClickListener {
            Log.d("StockOpnameActivity", "Barcode Scan button clicked.")
            stockViewModel.toggleBarcodeScan() // ViewModel akan mengatur state isBarcodeScanning
        }

        binding.buttonSaveAndExport.setOnClickListener {
            Log.d("StockOpnameActivity", "Save/Export button clicked.")
            handleSaveOpname()
        }
        Log.d("StockOpnameActivity", "setupButtonListeners: Listeners set.")
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                stockViewModel.uiState.collect { state ->
                    Log.d("StockOpnameActivity", "New UI State: ${state::class.java.simpleName}")
                    binding.progressBarOpname.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE

                    when (state) {
                        is UiState.Initial -> {
                            setButtonStates(isLoading = false, isUhfScanning = false, isBarcodeScanning = false)
                            bookListAdapter.submitList(emptyList()) // Atau data default
                            updateStatistics(0, 0, 0) // Reset statistik
                            binding.textViewLastScannedTag.text = getString(R.string.start_opname_session) // Buat string resource
                            // Mungkin panggil ViewModel untuk load master data jika state ini berarti awal sesi
                            stockViewModel.loadInitialMasterData()
                        }
                        is UiState.Loading -> {
                            setButtonStates(isLoading = true, isUhfScanning = false, isBarcodeScanning = false) // Sesuaikan jika scan bisa terjadi saat loading
                            binding.textViewLastScannedTag.text = getString(R.string.wait_for_loading_to_finish) // Buat string resource
                        }
                        is UiState.Success -> {
                            setButtonStates(isLoading = false, isUhfScanning = state.isScanning, isBarcodeScanning = state.isBarcodeScanning)
                            bookListAdapter.submitList(state.displayedBooks) // ViewModel menyediakan list yang sudah difilter
                            updateStatistics(state.totalBooks, state.foundBooks, state.missingBooks)
                            binding.textViewLastScannedTag.text = state.lastScanMessage.ifEmpty { getString(R.string.scan_ready_prompt) } // Buat string resource

                            state.toastMessage?.let { message ->
                                showToast(message)
                                stockViewModel.toastMessageShown() // Beritahu ViewModel bahwa pesan sudah ditampilkan
                            }
                        }
                        is UiState.Error -> {
                            setButtonStates(isLoading = false, isUhfScanning = false, isBarcodeScanning = false)
                            val errorMessage = getString(R.string.error_prefix, state.message)
                            showToast(errorMessage, Toast.LENGTH_LONG)
                            binding.textViewLastScannedTag.text = errorMessage
                        }
                    }
                }
            }
        }
        Log.d("StockOpnameActivity", "observeViewModel: UI state observer setup.")
    }

    private fun updateStatistics(total: Int, found: Int, missing: Int) {
        binding.textViewTotalBooksValue.text = total.toString()
        binding.textViewFoundBooksValue.text = found.toString()
        binding.textViewMissingBooksValue.text = missing.toString()
    }

    private fun setButtonStates(isLoading: Boolean, isUhfScanning: Boolean, isBarcodeScanning: Boolean) {
        val enableGlobalActions = !isLoading && !isUhfScanning && !isBarcodeScanning

        binding.buttonStartUhfScan.isEnabled = !isLoading && !isBarcodeScanning
        binding.buttonStartUhfScan.text = if (isUhfScanning) getString(R.string.button_stop_uhf_scan) else getString(R.string.button_start_uhf_scan)

        binding.buttonStartBarcodeScan.isEnabled = !isLoading && !isUhfScanning
        binding.buttonStartBarcodeScan.text = if (isBarcodeScanning) getString(R.string.button_stop_barcode_scan) else getString(R.string.button_start_barcode_scan)

        binding.buttonImportData.isEnabled = enableGlobalActions
        val canSaveOrExport = enableGlobalActions && (stockViewModel.uiState.value as? UiState.Success)?.allBooks?.isNotEmpty() == true
        binding.buttonSaveAndExport.isEnabled = canSaveOrExport
        binding.spinnerFilterStatus.isEnabled = enableGlobalActions
        // Untuk AutoCompleteTextView, kita mungkin ingin mengontrol TextInputLayout-nya juga
        binding.textFieldFilterStatus.isEnabled = enableGlobalActions
    }

    private fun handleSaveOpname() {
        val currentState = stockViewModel.uiState.value
        if (currentState !is UiState.Success || currentState.allBooks.isEmpty()) {
            showToast(getString(R.string.no_data_to_export_or_failed)) // Buat string resource
            return
        }
        if (currentState.isScanning || currentState.isBarcodeScanning) {
            showToast(getString(R.string.stop_scan_before_saving))
            return
        }

        // Anda bisa membuat dialog custom dengan EditText untuk nama opname jika diperlukan.
        // Untuk contoh ini, kita gunakan nama default berbasis tanggal.
        val defaultOpnameName = "Opname_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_opname_results_title)) // Buat string resource
            .setMessage(getString(R.string.save_confirmation_message_prefix, defaultOpnameName)) // Buat string resource
            // .setView(dialogView) // Jika Anda menggunakan dialog custom dengan EditText
            .setPositiveButton(getString(R.string.save_and_export_results)) { _, _ -> // Buat string resource
                // val opnameName = editTextOpnameName.text.toString().ifBlank { defaultOpnameName } // Dari dialog custom
                saveSession(defaultOpnameName, exportToTxt = true)
            }
            .setNeutralButton(getString(R.string.save_only)) { _, _ -> // Buat string resource
                saveSession(defaultOpnameName, exportToTxt = false)
            }
            .setNegativeButton(getString(R.string.cancel), null) // Buat string resource
            .show()
    }

    private fun saveSession(opnameName: String, exportToTxt: Boolean) {
        stockViewModel.saveCurrentOpnameSession(opnameName, exportToTxt) { reportId, success, filePath ->
            if (success) {
                var message = if (reportId != null) getString(R.string.opname_saved_report_id, reportId) // Buat string
                else getString(R.string.save_opname_success) // Buat string

                if (exportToTxt && !filePath.isNullOrEmpty()) {
                    message += "\n${getString(R.string.export_success_path_public, filePath)}" // Buat string
                } else if (exportToTxt && filePath.isNullOrEmpty()) {
                    message += "\n${getString(R.string.export_error_unexpected)}" // Buat string
                }
                showToast(message, Toast.LENGTH_LONG)
                // ViewModel harusnya mereset state ke Initial atau memuat sesi baru setelah penyimpanan berhasil
                // Ini akan otomatis direfleksikan oleh observer uiState
            } else {
                showToast(getString(R.string.save_opname_failed), Toast.LENGTH_LONG) // Buat string
            }
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("StockOpnameActivity", "onDestroy called.")
        // Jika ViewModel Anda memerlukan pembersihan manual (misalnya, melepaskan resource SDK eksternal)
        // dan tidak ditangani di onCleared() ViewModel, lakukan di sini.
        // Namun, biasanya onCleared() di ViewModel adalah tempat yang tepat.
    }
}


