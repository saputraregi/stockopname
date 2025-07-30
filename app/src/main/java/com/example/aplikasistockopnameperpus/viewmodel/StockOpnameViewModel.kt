package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
// Impor dari lokasi yang benar
import com.example.aplikasistockopnameperpus.data.database.BookMaster // Entitas Room
import com.example.aplikasistockopnameperpus.model.BookOpname // Dari OpnameModels.kt
import com.example.aplikasistockopnameperpus.model.ScanMethod // Dari OpnameModels.kt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Definisi UiState tetap di sini atau dipindahkan ke file model juga bisa
sealed class UiState {
    data object Initial : UiState()
    data object Loading : UiState()
    data class Success(
        val allBooks: List<BookOpname>,
        val displayedBooks: List<BookOpname>,
        val totalBooks: Int,
        val foundBooks: Int,
        val missingBooks: Int,
        val isScanning: Boolean,
        val isBarcodeScanning: Boolean,
        val lastScanMessage: String,
        val currentFilter: String = "Semua",
        val toastMessage: String? = null
    ) : UiState()
    data class Error(val message: String) : UiState()
}

class StockOpnameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val sdkManager: ChainwaySDKManager =
        (application as MyApplication).chainwaySDKManager

    // Ini akan diisi dari database (Room) melalui Repository nantinya
    // Untuk sekarang, kita masih menggunakan simulasi
    private var allMasterBooksList: List<BookMaster> = emptyList()
    private var currentOpnameSessionBooks: MutableMap<String, BookOpname> = mutableMapOf() // Key: rfidTagHex atau itemCode

    private val app = application

    init {
        Log.d("StockOpnameViewModel", "ViewModel initialized.")
        setupSdkListeners()
    }

    private fun setupSdkListeners() {
        sdkManager.onUhfTagScanned = { epc -> // epc adalah rfidTagHex
            Log.d("StockOpnameViewModel", "onUhfTagScanned: $epc")
            processScannedIdentifier(epc, ScanMethod.UHF)
        }
        sdkManager.onBarcodeScanned = { barcodeData -> // barcodeData adalah itemCode
            Log.d("StockOpnameViewModel", "onBarcodeScanned: $barcodeData")
            processScannedIdentifier(barcodeData, ScanMethod.BARCODE)
        }
        sdkManager.onError = { errorMessage ->
            Log.e("StockOpnameViewModel", "onError from SDK: $errorMessage")
            _uiState.update { currentState ->
                val newToastMessage = app.getString(R.string.sdk_error_prefix, errorMessage)
                if (currentState is UiState.Success) {
                    currentState.copy(
                        toastMessage = newToastMessage,
                        isScanning = false,
                        isBarcodeScanning = false
                    )
                } else {
                    // Jika state Error, mungkin tampilkan toast di Activity berdasarkan message dari Error state
                    UiState.Error(newToastMessage) // Atau update dengan pesan error baru
                }
            }
        }
        sdkManager.onUhfInventoryFinished = {
            Log.d("StockOpnameViewModel", "onUhfInventoryFinished")
            _uiState.update { currentState ->
                if (currentState is UiState.Success) {
                    currentState.copy(isScanning = false, lastScanMessage = app.getString(R.string.uhf_scan_finished))
                } else {
                    currentState
                }
            }
        }
    }

    fun loadInitialMasterData() {
        if (_uiState.value is UiState.Loading || (_uiState.value is UiState.Success && allMasterBooksList.isNotEmpty())) {
            Log.d("StockOpnameViewModel", "Skipping loadInitialMasterData: already loading or data exists.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            Log.d("StockOpnameViewModel", "Loading initial master data...")

            // --- SIMULASI LOAD DATA MASTER ---
            // Ganti ini dengan pemanggilan ke Repository untuk mendapatkan data dari Room
            kotlinx.coroutines.delay(1000) // Simulasi delay

            // Menggunakan field dari BookMaster entitas Room
            allMasterBooksList = listOf(
                BookMaster(id = 1, itemCode = "BC001", title = "Pemrograman Java Lanjut", expectedLocation = "Rak A1", rfidTagHex = "EPC001", scanStatus = "Belum Scan", lastSeenTimestamp = null /* properti lain dari BookMaster entitas */),
                BookMaster(id = 2, itemCode = "BC002", title = "Belajar Kotlin untuk Android", expectedLocation = "Rak B2", rfidTagHex = "EPC002", scanStatus = "Belum Scan", lastSeenTimestamp = null),
                BookMaster(id = 3, itemCode = "BC003", title = "Dasar-Dasar Jaringan Komputer", expectedLocation = "Rak C3", rfidTagHex = "EPC003", scanStatus = "Belum Scan", lastSeenTimestamp = null),
                BookMaster(id = 4, itemCode = "BC004", title = "Algoritma dan Struktur Data", expectedLocation = "Rak A1", rfidTagHex = "EPC004", scanStatus = "Belum Scan", lastSeenTimestamp = null), // Contoh tanpa itemCode berbeda jika EPC adalah primary key identifikasi
                BookMaster(id = 5, itemCode = "BC005", title = "Manajemen Proyek Perangkat Lunak", expectedLocation = "Rak D1", rfidTagHex = "TAGNONHEX005", scanStatus = "Belum Scan", lastSeenTimestamp = null) // Contoh tanpa rfidTagHex yang valid (atau rfidTagHex null jika diizinkan oleh DB)
            )
            // --- AKHIR SIMULASI ---

            initializeNewOpnameSession()
        }
    }

    private fun initializeNewOpnameSession() {
        currentOpnameSessionBooks.clear()
        allMasterBooksList.forEach { master ->
            // Kunci unik: rfidTagHex jika ada, jika tidak itemCode.
            // Pastikan salah satu dari ini unik dan tidak null untuk setiap buku master.
            // Atau gunakan master.id.toString() jika selalu unik dan tersedia.
            val key = master.rfidTagHex.takeIf { !it.isNullOrBlank() } ?: master.itemCode
            if (key.isNotBlank()) { // Hanya tambahkan jika ada kunci yang valid
                currentOpnameSessionBooks[key] = BookOpname(
                    bookMaster = master.copy(scanStatus = "Belum Scan", lastSeenTimestamp = null), // Reset status untuk sesi baru
                    isFound = false,
                    scanTime = null,
                    scanMethod = null
                )
            } else {
                Log.w("StockOpnameViewModel", "Book with title '${master.title}' lacks a valid key (rfidTagHex or itemCode) and will be skipped.")
            }
        }

        val initialBooks = currentOpnameSessionBooks.values.toList()
        _uiState.value = UiState.Success(
            allBooks = initialBooks,
            displayedBooks = filterAndSortBooks(initialBooks, "Semua"),
            totalBooks = allMasterBooksList.size, // atau currentOpnameSessionBooks.size jika ada yang diskip
            foundBooks = 0,
            missingBooks = currentOpnameSessionBooks.size, // Berdasarkan buku yang masuk sesi
            isScanning = false,
            isBarcodeScanning = false,
            lastScanMessage = app.getString(R.string.opname_session_started),
            currentFilter = "Semua"
        )
        Log.d("StockOpnameViewModel", "New opname session initialized. Total books in session: ${currentOpnameSessionBooks.size}")
    }

    private fun processScannedIdentifier(identifier: String, method: ScanMethod) {
        if (identifier.isBlank()) {
            Log.w("StockOpnameViewModel", "Empty identifier received from $method, ignoring.")
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                if (currentState !is UiState.Success) return@update currentState

                var foundBookOpname: BookOpname? = null
                var keyForMapUpdate: String? = null // Kunci yang digunakan di currentOpnameSessionBooks

                when (method) {
                    ScanMethod.UHF -> {
                        // Cari berdasarkan rfidTagHex. Kunci map juga harus rfidTagHex jika ini yang ditemukan.
                        foundBookOpname = currentOpnameSessionBooks.values.find { it.bookMaster.rfidTagHex == identifier }
                        keyForMapUpdate = identifier // Jika ditemukan by UHF, identifier adalah rfidTagHex
                    }
                    ScanMethod.BARCODE -> {
                        // Cari berdasarkan itemCode. Kunci map juga harus itemCode jika ini yang ditemukan.
                        foundBookOpname = currentOpnameSessionBooks.values.find { it.bookMaster.itemCode == identifier }
                        keyForMapUpdate = identifier // Jika ditemukan by Barcode, identifier adalah itemCode
                        // Jika kunci map Anda bisa jadi rfidTagHex meskipun scan by barcode (jika rfidTagHex ada),
                        // Anda perlu logika tambahan untuk menemukan kunci yang benar.
                        // Untuk simple, kita asumsikan jika scan by barcode, key-nya adalah barcode (itemCode).
                        // Namun, jika itemCode tidak unik di map jika rfidTagHex juga jadi key, ini bisa jadi masalah.
                        // Solusi lebih baik adalah menggunakan ID unik buku (master.id) sebagai key di map.
                        // Untuk sekarang, kita lanjutkan dengan asumsi identifier adalah key yang benar.
                    }
                }

                var newLastScanMessage: String

                if (foundBookOpname != null && keyForMapUpdate != null && currentOpnameSessionBooks.containsKey(keyForMapUpdate)) {
                    if (!foundBookOpname.isFound) {
                        val updatedBookMaster = foundBookOpname.bookMaster.copy(
                            scanStatus = "Ditemukan",
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                        val updatedBookOpname = foundBookOpname.copy(
                            isFound = true,
                            scanTime = System.currentTimeMillis(),
                            scanMethod = method,
                            bookMaster = updatedBookMaster
                        )
                        currentOpnameSessionBooks[keyForMapUpdate] = updatedBookOpname // Update map dengan BookOpname baru
                        newLastScanMessage = app.getString(R.string.scan_success_item_found, method.displayName, identifier, updatedBookOpname.bookMaster.title)
                        Log.i("StockOpnameViewModel", "Book found: ${updatedBookOpname.bookMaster.title} by $method")
                    } else {
                        newLastScanMessage = app.getString(R.string.scan_already_found, method.displayName, identifier, foundBookOpname.bookMaster.title)
                        Log.i("StockOpnameViewModel", "Book already found: ${foundBookOpname.bookMaster.title}")
                    }
                } else {
                    newLastScanMessage = app.getString(R.string.scan_item_not_in_master, method.displayName, identifier)
                    Log.w("StockOpnameViewModel", "Identifier not in master or key mismatch: $identifier by $method")
                }

                val updatedBooksList = currentOpnameSessionBooks.values.toList()
                val newFoundCount = updatedBooksList.count { it.isFound }

                currentState.copy(
                    allBooks = updatedBooksList,
                    displayedBooks = filterAndSortBooks(updatedBooksList, currentState.currentFilter),
                    foundBooks = newFoundCount,
                    missingBooks = currentOpnameSessionBooks.size - newFoundCount,
                    lastScanMessage = newLastScanMessage,
                    toastMessage = if (foundBookOpname == null) newLastScanMessage else null
                )
            }
        }
    }


    fun toggleUhfScan() {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            if (currentState.isScanning) {
                // HENTIKAN SCAN UHF
                // uhfManager.stopInventory() // Contoh
                Log.d("ViewModel", "Stopping UHF Scan")
                _uiState.update {
                    (it as UiState.Success).copy(
                        isScanning = false,
                        lastScanMessage = "Scan UHF dihentikan."
                    )
                }
            } else {
                // MULAI SCAN UHF
                // Pastikan reader terhubung dan siap
                // uhfManager.startInventory { epc -> onUhfTagReceived(epc) } // Contoh
                Log.d("ViewModel", "Starting UHF Scan")
                _uiState.update {
                    (it as UiState.Success).copy(
                        isScanning = true,
                        lastScanMessage = "Memindai UHF..."
                    )
                }
            }
        } else if (currentState is UiState.Initial) {
            // Mungkin perlu inisialisasi atau penanganan khusus jika scan dimulai dari Initial state
            _uiState.value = UiState.Error("Silakan muat data master terlebih dahulu atau pastikan reader siap.")
        }
        // Tambahkan penanganan untuk state lain jika perlu
    }

    fun toggleBarcodeScan() {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            if (currentState.isBarcodeScanning) {
                // HENTIKAN SCAN BARCODE
                // barcodeScanner.stop() // Contoh
                Log.d("ViewModel", "Stopping Barcode Scan")
                _uiState.update {
                    (it as UiState.Success).copy(
                        isBarcodeScanning = false,
                        lastScanMessage = "Scan Barcode dihentikan."
                    )
                }
            } else {
                // MULAI SCAN BARCODE (mungkin membuka activity kamera atau mengaktifkan listener)
                // barcodeScanner.start { barcodeData -> onBarcodeReceived(barcodeData) } // Contoh
                Log.d("ViewModel", "Starting Barcode Scan")
                _uiState.update {
                    (it as UiState.Success).copy(
                        isBarcodeScanning = true,
                        lastScanMessage = "Memindai Barcode..."
                    )
                }
            }
        } else if (currentState is UiState.Initial) {
            _uiState.value = UiState.Error("Silakan muat data master terlebih dahulu.")
        }
        // Tambahkan penanganan untuk state lain jika perlu
    }

    // Callback saat tag UHF diterima (dipanggil oleh uhfManager)
    private fun onUhfTagReceived(epc: String) {
        // Logika untuk memproses EPC, update data buku, update UI state
        // Pastikan untuk memeriksa apakah isScanning masih true
        if ((_uiState.value as? UiState.Success)?.isScanning == true) {
            // ... proses tag ...
            // _uiState.update { ... }
        }
    }

    // Callback saat barcode diterima (dipanggil oleh barcodeScanner)
    private fun onBarcodeReceived(barcodeData: String) {
        // Logika untuk memproses barcode, update data buku, update UI state
        // Pastikan untuk memeriksa apakah isBarcodeScanning masih true
        if ((_uiState.value as? UiState.Success)?.isBarcodeScanning == true) {
            // ... proses barcode ...
            // _uiState.update { ... }
        }
    }


    fun setFilter(filter: String) {
        _uiState.update { currentState ->
            if (currentState is UiState.Success) {
                currentState.copy(
                    displayedBooks = filterAndSortBooks(currentState.allBooks, filter),
                    currentFilter = filter
                )
            } else {
                currentState
            }
        }
    }

    private fun filterAndSortBooks(books: List<BookOpname>, filter: String): List<BookOpname> {
        val filtered = when (filter) {
            "Semua" -> books
            "Ditemukan" -> books.filter { it.isFound }
            "Belum Scan" -> books.filter { !it.isFound && it.bookMaster.scanStatus == "Belum Scan" } // Lebih spesifik
            else -> books
        }
        return filtered.sortedWith(compareBy<BookOpname> { !it.isFound }.thenBy { it.bookMaster.title }) // Menggunakan 'title'
    }

    fun saveCurrentOpnameSession(opnameName: String, exportToTxt: Boolean, onResult: (reportId: Long?, success: Boolean, filePath: String?) -> Unit) {
        val currentStateValue = _uiState.value // Ambil nilai state sekali
        if (currentStateValue !is UiState.Success || currentOpnameSessionBooks.isEmpty()) {
            Log.w("StockOpnameViewModel", "No data to save or invalid state.")
            onResult(null, false, null)
            return
        }
        // Gunakan nilai state yang sudah diambil
        if (currentStateValue.isScanning || currentStateValue.isBarcodeScanning) {
            _uiState.update { // Tetap update _uiState untuk toast
                if (it is UiState.Success) it.copy(toastMessage = app.getString(R.string.stop_scan_before_saving)) else it
            }
            onResult(null, false, null)
            return
        }

        viewModelScope.launch {
            Log.i("StockOpnameViewModel", "Attempting to save opname session: $opnameName, Export: $exportToTxt")
            // --- SIMULASI LOGIKA PENYIMPANAN ---
            // Di sini Anda akan mengganti dengan logika penyimpanan ke Room (melalui Repository)
            // dan logika ekspor file yang sebenarnya.
            // Contoh: val reportId = repository.saveOpnameReport(opnameName, currentOpnameSessionBooks.values.toList())
            //         repository.saveOpnameDetails(reportId, currentOpnameSessionBooks.values.toList())
            kotlinx.coroutines.delay(1500)
            val mockReportId = System.currentTimeMillis() % 10000
            var mockFilePath: String? = null

            if (exportToTxt) {
                mockFilePath = "/storage/emulated/0/Download/${opnameName.replace(":", "-")}.txt"
                Log.i("StockOpnameViewModel", "Simulating export to: $mockFilePath")
                // Implementasi penulisan ke file:
                // try {
                //     val file = File(mockFilePath)
                //     file.bufferedWriter().use { out ->
                //         currentOpnameSessionBooks.values.forEach { bookOpname ->
                //             out.write("Judul: ${bookOpname.bookMaster.title}, Status: ${if(bookOpname.isFound) "Ditemukan" else "Belum Ditemukan"}, EPC: ${bookOpname.bookMaster.rfidTagHex}\n")
                //         }
                //     }
                // } catch (e: IOException) {
                //     Log.e("StockOpnameViewModel", "Error exporting to TXT", e)
                //     mockFilePath = null // Gagal export
                // }
            }
            Log.i("StockOpnameViewModel", "Opname session '$opnameName' saved with ID $mockReportId. Exported to: $mockFilePath")
            onResult(mockReportId, true, mockFilePath)
            // --- AKHIR SIMULASI ---

            // Reset untuk sesi baru setelah berhasil menyimpan
            initializeNewOpnameSession()
            _uiState.update { s ->
                if(s is UiState.Success) s.copy(toastMessage = app.getString(R.string.opname_session_saved_and_reset, opnameName))
                else s
            }
        }
    }

    fun toastMessageShown() {
        _uiState.update { currentState ->
            if (currentState is UiState.Success && currentState.toastMessage != null) {
                currentState.copy(toastMessage = null)
            } else if (currentState is UiState.Error) { // Jika ingin kembali ke Initial setelah error toast
                // UiState.Initial // Atau biarkan Error jika ingin user tahu ada error terakhir
                currentState // Atau biarkan saja jika Error state punya arti lain
            } else {
                currentState
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("StockOpnameViewModel", "ViewModel cleared. Releasing SDK resources.")
        sdkManager.releaseResources()
    }
}
