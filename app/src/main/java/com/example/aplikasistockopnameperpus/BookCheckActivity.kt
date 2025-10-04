package com.example.aplikasistockopnameperpus

import android.app.Application
import android.graphics.Typeface // Import untuk Typeface
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // Import untuk ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus
import com.example.aplikasistockopnameperpus.databinding.ActivityBookCheckBinding
import com.example.aplikasistockopnameperpus.viewmodel.BookCheckUiState
import com.example.aplikasistockopnameperpus.viewmodel.BookCheckViewModel
// Pastikan import factory Anda sudah benar dan di-uncomment jika perlu
//import com.example.aplikasistockopnameperpus.viewmodel.BookCheckViewModelFactory

// Hapus import yang tidak digunakan, misalnya:
// import androidx.compose.ui.semantics.text
// import androidx.glance.visibility
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager


class BookCheckActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookCheckBinding
    private val viewModel: BookCheckViewModel by viewModels {
        BookCheckViewModelFactory(
            application,
            (application as MyApplication).bookRepository,
            (application as MyApplication).sdkManager
        )
    }

    // Key code untuk tombol fisik (sesuaikan dengan perangkat Anda)
    private val TRIGGER_KEY_MAIN = 293 // Atau 239 jika itu yang benar
    private val TRIGGER_KEY_BACKUP = 139
    // HAPUS: PreferredScanMode dan currentPreferredScanMode tidak lagi diperlukan
    // karena tombol fisik akan mengikuti Switch UI.
    // private enum class PreferredScanMode { BARCODE, UHF }
    // private var currentPreferredScanMode = PreferredScanMode.UHF


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate: ViewBinding initialized.")

        setSupportActionBar(binding.toolbarBookCheck)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupListeners()
        observeViewModel()
        Log.d(TAG, "onCreate: Setup methods completed.")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupListeners() {
        // --- BARU: Listener untuk Switch Mode ---
        // Pastikan ID 'switchBookCheckScanMode' ada di activity_book_check.xml Anda
        binding.switchBookCheckScanMode.setOnCheckedChangeListener { _, isChecked ->
            // isChecked = true berarti mode UHF dipilih (Switch ON)
            // isChecked = false berarti mode Barcode dipilih (Switch OFF)
            viewModel.setBookCheckScanMode(isChecked)
            Log.d(TAG, "Switch mode UI diubah, isChecked (UHF): $isChecked")
        }
        // --- AKHIR BARU ---

        // --- BARU: Listener untuk tombol Scan/Stop tunggal ---
        // Pastikan ID 'buttonToggleBookCheckScan' ada di activity_book_check.xml Anda
        binding.buttonToggleBookCheckScan.setOnClickListener {
            Log.d(TAG, "Tombol UI Toggle Scan Buku diklik.")
            viewModel.toggleSelectedBookCheckScan()
        }
        // --- AKHIR BARU ---

        // HAPUS Listener untuk buttonScanBarcodeCheck dan buttonScanUhfCheck yang lama
        // binding.buttonScanBarcodeCheck.setOnClickListener { ... }
        // binding.buttonScanUhfCheck.setOnClickListener { ... }

        binding.buttonClearCheck.setOnClickListener {
            Log.d(TAG, "Tombol Clear diklik.")
            viewModel.clearPreviousResult()
        }
        Log.d(TAG, "setupListeners: Listeners set.")
    }

    // Di BookCheckActivity.kt

    private fun observeViewModel() {
        // Fungsi terpusat untuk mengupdate semua elemen UI yang bergantung pada state gabungan
        fun refreshAllUiElements() {
            val isUhfScanning = viewModel.isUhfScanningForCheckActive.value ?: false
            val isBarcodeScanning = viewModel.isBarcodeScanningForCheckActive.value ?: false
            val anyScanActive = isUhfScanning || isBarcodeScanning
            val currentMainUiState = viewModel.uiState.value

            Log.d(TAG, "UI Refresh - UHF: $isUhfScanning, Barcode: $isBarcodeScanning, AnyActive: $anyScanActive, MainState: ${currentMainUiState?.javaClass?.simpleName}")

            // Tombol Scan/Stop Utama
            binding.buttonToggleBookCheckScan.text = if (anyScanActive) {
                getString(R.string.button_stop_scan_book)
            } else {
                getString(R.string.button_start_scan_book)
            }
            binding.buttonToggleBookCheckScan.isEnabled = (currentMainUiState !is BookCheckUiState.Searching || anyScanActive)

            // Switch Mode
            binding.switchBookCheckScanMode.isEnabled = !anyScanActive

            // Tombol Clear
            val canShowClearButton = (currentMainUiState is BookCheckUiState.BookFound ||
                    currentMainUiState is BookCheckUiState.BookNotFound ||
                    currentMainUiState is BookCheckUiState.Error)
            binding.buttonClearCheck.isEnabled = canShowClearButton && !anyScanActive
            binding.buttonClearCheck.visibility = if (binding.buttonClearCheck.isEnabled) View.VISIBLE else View.GONE

            // ProgressBar
            binding.progressBarBookCheck.visibility = if (currentMainUiState is BookCheckUiState.Searching) View.VISIBLE else View.GONE

            // Detail Buku & Toast (dari fungsi yang sudah ada)
            if (currentMainUiState != null) { // Pastikan state tidak null
                updateUiWithNewToggle(currentMainUiState)
            }
        }

        // Observer untuk setiap LiveData yang relevan akan memanggil refreshAllUiElements
        viewModel.uiState.observe(this) { refreshAllUiElements() }
        viewModel.isUhfScanningForCheckActive.observe(this) { refreshAllUiElements() }
        viewModel.isBarcodeScanningForCheckActive.observe(this) { refreshAllUiElements() }

        // Observer untuk mode switch (mengatur tampilan switch dan labelnya)
        viewModel.isUhfModeSelectedByUi.observe(this) { isUhfSelectedByUi ->
            Log.d(TAG, "Observer: isUhfModeSelectedByUi changed to: $isUhfSelectedByUi")
            binding.switchBookCheckScanMode.isChecked = isUhfSelectedByUi
            val activeColor = ContextCompat.getColor(this, R.color.design_default_color_primary)
            val inactiveColor = ContextCompat.getColor(this, R.color.default_text_color)
            binding.labelScanModeUhfCheck.setTextColor(if (isUhfSelectedByUi) activeColor else inactiveColor)
            binding.labelScanModeUhfCheck.setTypeface(null, if (isUhfSelectedByUi) Typeface.BOLD else Typeface.NORMAL)
            binding.labelScanModeBarcodeCheck.setTextColor(if (!isUhfSelectedByUi) activeColor else inactiveColor)
            binding.labelScanModeBarcodeCheck.setTypeface(null, if (!isUhfSelectedByUi) Typeface.BOLD else Typeface.NORMAL)
        }

        // Observer untuk status message
        viewModel.statusMessage.observe(this) { message ->
            binding.textViewStatusBookCheck.text = message
        }
    }

    // updateUiWithNewToggle sekarang HANYA mengurus detail buku dan toast,
// karena elemen UI lain (tombol, progressbar) diurus oleh refreshAllUiElements.
    private fun updateUiWithNewToggle(state: BookCheckUiState) {
        binding.cardViewBookDetailsCheck.visibility = if (state is BookCheckUiState.BookFound) View.VISIBLE else View.GONE

        if (state is BookCheckUiState.BookFound) {
            displayBookDetails(state.book)
        }
        // Tidak perlu logika else di sini untuk cardViewBookDetailsCheck visibility,
        // karena sudah dihandle oleh refreshAllUiElements berdasarkan state BookFound.

        if (state is BookCheckUiState.Error) {
            Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
        }
        if (state is BookCheckUiState.BookNotFound) {
            Toast.makeText(this, getString(R.string.status_book_not_found) + " (ID: ${state.searchedIdentifier})", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayBookDetails(book: BookMaster) {
        // Implementasi displayBookDetails tetap sama seperti yang Anda berikan
        binding.textViewDetailTitle.text = book.title ?: getString(R.string.text_not_available_short)
        binding.textViewDetailItemCode.text = book.itemCode ?: getString(R.string.text_not_available_short)
        binding.textViewDetailLocation.text = book.locationName ?: getString(R.string.text_not_available_short)
        binding.textViewDetailRfidTag.text = book.rfidTagHex ?: getString(R.string.text_not_available_short)
        binding.textViewDetailTid.text = book.tid ?: getString(R.string.text_not_available_short)

        binding.textViewDetailPairingStatus.text = when (book.pairingStatus) {
            PairingStatus.NOT_PAIRED -> getString(R.string.pairing_status_not_paired)
            PairingStatus.PAIRED_WRITE_SUCCESS -> getString(R.string.pairing_status_paired_write_success)
            else -> book.pairingStatus.name
        }
        binding.textViewDetailOpnameStatus.text = when (book.opnameStatus) {
            OpnameStatus.NOT_SCANNED -> getString(R.string.opname_status_not_scanned)
            OpnameStatus.FOUND -> getString(R.string.opname_status_found)
            OpnameStatus.MISSING -> getString(R.string.opname_status_missing)
            OpnameStatus.NEW_ITEM -> getString(R.string.opname_status_new_item_detected_check)
            else -> book.opnameStatus.name
        }
        binding.textViewDetailLastSeen.text = viewModel.formatDisplayDate(book.lastSeenTimestamp) +
                if (!book.actualScannedLocation.isNullOrBlank()) " di ${book.actualScannedLocation}" else ""
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        if (keyCode == TRIGGER_KEY_MAIN || keyCode == TRIGGER_KEY_BACKUP) {
            if (event.repeatCount == 0) {
                Log.d(TAG, "Tombol fisik ditekan (keyCode: $keyCode). Memanggil handlePhysicalTrigger().")
                handlePhysicalTrigger()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handlePhysicalTrigger() {
        val mainUiState = viewModel.uiState.value
        // Ambil status scan langsung dari LiveData ViewModel
        val isUhfCurrentlyScanning = viewModel.isUhfScanningForCheckActive.value == true
        val isBarcodeCurrentlyScanning = viewModel.isBarcodeScanningForCheckActive.value == true
        val isAnyScanActive = isUhfCurrentlyScanning || isBarcodeCurrentlyScanning

        if (mainUiState is BookCheckUiState.Searching && !isAnyScanActive) {
            Toast.makeText(this, "Proses pencarian lain sedang berjalan.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Tombol fisik ditekan, tetapi proses (non-scan) lain sedang berjalan ($mainUiState).")
            return
        }

        if (isAnyScanActive) {
            Log.d(TAG, "Tombol fisik: Scan aktif terdeteksi. Memanggil toggleSelectedBookCheckScan untuk STOP.")
            viewModel.toggleSelectedBookCheckScan()
        } else {
            // Tidak ada scan aktif, kita akan memulai scan baru.
            val isUhfSelectedBySwitchInActivity = binding.switchBookCheckScanMode.isChecked
            Log.d(TAG, "Tombol fisik: Tidak ada scan aktif. Mode Switch UI saat ini (isUhf): $isUhfSelectedBySwitchInActivity. Menyetel mode di ViewModel.")
            viewModel.setBookCheckScanMode(isUhfSelectedBySwitchInActivity) // PASTIKAN INI ADA DAN BENAR

            Log.d(TAG, "Tombol fisik: Memanggil toggleSelectedBookCheckScan untuk START.")
            viewModel.toggleSelectedBookCheckScan()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "BookCheckActivity onPause. Memastikan scan aktif dihentikan.")
        if (viewModel.isUhfScanningForCheckActive.value == true || viewModel.isBarcodeScanningForCheckActive.value == true) {
            Log.d(TAG, "onPause: Scan aktif, memanggil toggleSelectedBookCheckScan untuk menghentikan.")
            viewModel.toggleSelectedBookCheckScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BookCheckActivity onDestroy.")
        // Pembersihan listener SDK biasanya ditangani di onCleared() ViewModel.
    }

    // Factory untuk BookCheckViewModel
    class BookCheckViewModelFactory(
        private val application: Application,
        private val bookRepository: BookRepository, // Pastikan BookRepository diimpor jika factory membutuhkannya
        private val sdkManager: ChainwaySDKManager // Pastikan ChainwaySDKManager diimpor jika factory membutuhkannya
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BookCheckViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return BookCheckViewModel(application, bookRepository, sdkManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private const val TAG = "BookCheckActivityLog" // TAG yang lebih unik untuk Activity
    }
}
