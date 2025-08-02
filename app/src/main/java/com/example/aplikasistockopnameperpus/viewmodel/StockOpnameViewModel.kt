package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.model.ScanMethod
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Asumsi User data class sederhana
data class User(val uid: String, val displayName: String? = null)

sealed class StockOpnameUiState {
    data object Initial : StockOpnameUiState()
    data object Loading : StockOpnameUiState()
    data class Success(
        val displayedBooks: List<BookMaster>,
        val allBooksInCurrentSession: List<BookMaster>,
        val totalBooksInMaster: Int,
        val foundBooksInSession: Int,
        val misplacedBooksInSession: Int,
        val missingBooksInSession: Int,
        val newOrUnexpectedBooksCount: Int = 0, // Menggunakan BookMaster.isNewOrUnexpected
        val isUhfScanning: Boolean,
        val isBarcodeScanning: Boolean,
        val lastScanMessage: String,
        val currentFilter: String = "Semua",
        val toastMessage: String? = null,
        val currentOpnameSessionName: String = "Sesi Default",
        val sessionStartTimeMillis: Long = System.currentTimeMillis()
    ) : StockOpnameUiState()

    data class Error(val message: String) : StockOpnameUiState()
}

class StockOpnameViewModel(application: Application) : AndroidViewModel(application) {

    private val bookRepository: BookRepository = (application as MyApplication).bookRepository
    private val sdkManager: ChainwaySDKManager = (application as MyApplication).sdkManager
    private val app = application

    private val _uiState = MutableStateFlow<StockOpnameUiState>(StockOpnameUiState.Initial)
    val uiState: StateFlow<StockOpnameUiState> = _uiState.asStateFlow()

    private val _currentFilter = MutableStateFlow("Semua")

    // Placeholder User - Sesuaikan dengan implementasi Anda
    private val _currentUser = MutableStateFlow(User(uid = "default_user"))
    // val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val allBooksFromRepo: StateFlow<List<BookMaster>> = bookRepository.getAllBookMastersFlow()
        .catch { e ->
            Log.e("StockOpnameViewModel", "Error collecting books from repo", e)
            _uiState.value = StockOpnameUiState.Error("Gagal memuat data buku: ${e.message}")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var observerJob: Job? = null
    private var temporaryUnexpectedItems = mutableListOf<StockOpnameItem>()


    init {
        Log.d("StockOpnameViewModel", "ViewModel initialized.")
        setupSdkListeners()
        observeBookDataAndUpdateUi()
        // Anda bisa memanggil startNewOpnameSession() di sini atau dari UI
    }

    private fun observeBookDataAndUpdateUi() {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            allBooksFromRepo.combine(_currentFilter) { books, filter ->
                val filteredBooksForDisplay = filterAndSortBooks(books, filter)
                val allBooksForSessionCalculation = books

                val totalMaster = allBooksForSessionCalculation.size
                val foundCount = allBooksForSessionCalculation.count { it.scanStatus == "DITEMUKAN" }
                val misplacedCount = allBooksForSessionCalculation.count { it.scanStatus == "LOKASI_SALAH" }
                val missingCount = allBooksForSessionCalculation.count {
                    it.scanStatus == "BELUM_DITEMUKAN_SESI_INI" || it.scanStatus.isNullOrBlank()
                }
                val newOrUnexpectedCount = allBooksForSessionCalculation.count { it.isNewOrUnexpected == true } +
                        temporaryUnexpectedItems.count { it.status == "TIDAK_DITEMUKAN_DI_MASTER" }


                val currentSuccessState = _uiState.value as? StockOpnameUiState.Success
                StockOpnameUiState.Success(
                    displayedBooks = filteredBooksForDisplay,
                    allBooksInCurrentSession = allBooksForSessionCalculation,
                    totalBooksInMaster = totalMaster,
                    foundBooksInSession = foundCount,
                    misplacedBooksInSession = misplacedCount,
                    missingBooksInSession = missingCount,
                    newOrUnexpectedBooksCount = newOrUnexpectedCount,
                    isUhfScanning = currentSuccessState?.isUhfScanning ?: false,
                    isBarcodeScanning = currentSuccessState?.isBarcodeScanning ?: false,
                    lastScanMessage = currentSuccessState?.lastScanMessage ?: app.getString(R.string.start_opname_session),
                    currentFilter = filter,
                    toastMessage = null,
                    currentOpnameSessionName = currentSuccessState?.currentOpnameSessionName ?: "Sesi ${System.currentTimeMillis()}",
                    sessionStartTimeMillis = currentSuccessState?.sessionStartTimeMillis ?: System.currentTimeMillis()
                )
            }.catch { e ->
                Log.e("StockOpnameViewModel", "Error combining flows", e)
                _uiState.value = StockOpnameUiState.Error("Terjadi kesalahan internal: ${e.message}")
            }.collect { newSuccessState ->
                _uiState.value = newSuccessState
            }
        }
    }

    private fun setupSdkListeners() {
        sdkManager.onUhfTagScanned = { epc ->
            Log.d("StockOpnameViewModel", "SDK: onUhfTagScanned: $epc")
            if ((_uiState.value as? StockOpnameUiState.Success)?.isUhfScanning == true) {
                // Asumsi lokasi default untuk scan UHF, bisa dibuat lebih dinamis
                processScannedIdentifier(epc, ScanMethod.UHF, "Lokasi Scan UHF")
            }
        }
        sdkManager.onBarcodeScanned = { barcodeData ->
            Log.d("StockOpnameViewModel", "SDK: onBarcodeScanned: $barcodeData")
            if ((_uiState.value as? StockOpnameUiState.Success)?.isBarcodeScanning == true) {
                processScannedIdentifier(barcodeData, ScanMethod.BARCODE, "Lokasi Scan Barcode")
            }
        }
        sdkManager.onError = { errorMessage ->
            Log.e("StockOpnameViewModel", "SDK: onError: $errorMessage")
            _uiState.update { currentState ->
                val newToastMessage = app.getString(R.string.sdk_error_prefix, errorMessage)
                if (currentState is StockOpnameUiState.Success) {
                    currentState.copy(
                        toastMessage = newToastMessage,
                        isUhfScanning = false,
                        isBarcodeScanning = false
                    )
                } else {
                    StockOpnameUiState.Error(newToastMessage)
                }
            }
        }
        sdkManager.onUhfInventoryFinished = {
            Log.d("StockOpnameViewModel", "SDK: onUhfInventoryFinished")
            _uiState.update { currentState ->
                if (currentState is StockOpnameUiState.Success) {
                    currentState.copy(isUhfScanning = false, lastScanMessage = app.getString(R.string.uhf_scan_finished))
                } else currentState
            }
        }
    }

    fun startNewOpnameSession(sessionName: String? = null) {
        viewModelScope.launch {
            _uiState.value = StockOpnameUiState.Loading
            Log.d("StockOpnameViewModel", "Starting new opname session, resetting scan statuses...")
            try {
                bookRepository.resetAllBookScanStatusForNewSession()
                temporaryUnexpectedItems.clear() // Bersihkan item tak terduga dari sesi sebelumnya
                val newSessionName = sessionName ?: "Sesi Opname ${System.currentTimeMillis()}"
                val newSessionStartTime = System.currentTimeMillis()

                _uiState.value = StockOpnameUiState.Success(
                    displayedBooks = emptyList(),
                    allBooksInCurrentSession = emptyList(),
                    totalBooksInMaster = 0,
                    foundBooksInSession = 0,
                    misplacedBooksInSession = 0,
                    missingBooksInSession = 0,
                    newOrUnexpectedBooksCount = 0,
                    isUhfScanning = false,
                    isBarcodeScanning = false,
                    lastScanMessage = app.getString(R.string.opname_session_started),
                    currentFilter = _currentFilter.value,
                    toastMessage = null,
                    currentOpnameSessionName = newSessionName,
                    sessionStartTimeMillis = newSessionStartTime
                )
                observeBookDataAndUpdateUi() // Muat ulang data buku
                Log.d("StockOpnameViewModel", "Scan statuses reset. Opname session ready: $newSessionName.")
            } catch (e: Exception) {
                Log.e("StockOpnameViewModel", "Error resetting scan statuses", e)
                _uiState.value = StockOpnameUiState.Error("Gagal memulai sesi baru: ${e.message}")
            }
        }
    }

    fun updateCurrentOpnameSessionName(newName: String) {
        _uiState.update { currentState ->
            if (currentState is StockOpnameUiState.Success) {
                currentState.copy(currentOpnameSessionName = newName.ifBlank { "Sesi Default" })
            } else currentState
        }
    }

    private fun processScannedIdentifier(identifier: String, method: ScanMethod, actualLocationIfKnown: String) {
        if (identifier.isBlank()) {
            Log.w("StockOpnameViewModel", "Empty identifier received from $method, ignoring.")
            return
        }

        viewModelScope.launch {
            val book: BookMaster? = when (method) {
                ScanMethod.UHF -> bookRepository.getBookByRfidTag(identifier)
                ScanMethod.BARCODE -> bookRepository.getBookByItemCode(identifier)
            }

            val currentTimestamp = System.currentTimeMillis()
            var scanMessage: String
            var newStatusForMaster: String? = null // Status untuk diupdate ke BookMaster
            var newStatusForOpnameItem: String // Status untuk StockOpnameItem
            var isNewOrUnexpectedInMaster = false
            var itemForReport: StockOpnameItem? = null


            if (book != null) { // Buku ditemukan di master
                val bookTitle = book.title
                isNewOrUnexpectedInMaster = book.isNewOrUnexpected ?: false

                if (book.scanStatus == "DITEMUKAN" || book.scanStatus == "LOKASI_SALAH") {
                    scanMessage = app.getString(R.string.scan_already_processed, method.displayName, identifier, bookTitle)
                    newStatusForOpnameItem = book.scanStatus!! // Gunakan status yang sudah ada untuk item laporan
                } else {
                    if (book.expectedLocation.equals(actualLocationIfKnown, ignoreCase = true)) {
                        newStatusForMaster = "DITEMUKAN"
                        scanMessage = app.getString(R.string.scan_success_item_found_location, method.displayName, identifier, bookTitle, actualLocationIfKnown)
                    } else {
                        newStatusForMaster = "LOKASI_SALAH"
                        scanMessage = app.getString(R.string.scan_success_item_misplaced, method.displayName, identifier, bookTitle, actualLocationIfKnown, book.expectedLocation ?: "N/A")
                    }
                    newStatusForOpnameItem = newStatusForMaster // Sama untuk item laporan
                }

                // Update BookMaster jika status berubah
                if (newStatusForMaster != null && newStatusForMaster != book.scanStatus) {
                    when (method) {
                        ScanMethod.UHF -> bookRepository.updateBookScanStatusByRfid(
                            rfidTag = identifier,
                            status = newStatusForMaster,
                            timestamp = currentTimestamp,
                            actualLocation = actualLocationIfKnown
                        )
                        ScanMethod.BARCODE -> bookRepository.updateBookScanStatusByItemCode(
                            itemCode = identifier,
                            status = newStatusForMaster,
                            timestamp = currentTimestamp,
                            actualLocation = actualLocationIfKnown
                        )
                    }
                    Log.i("StockOpnameViewModel", "BookMaster ${book.title} status updated to $newStatusForMaster")
                }

                itemForReport = StockOpnameItem(
                    reportId = 0L, // Akan diisi oleh repository
                    rfidTagHexScanned = book.rfidTagHex ?: (if (method == ScanMethod.BARCODE) "N/A_BC_${identifier}" else identifier),
                    tidScanned = book.tid,
                    itemCodeMaster = book.itemCode,
                    titleMaster = book.title,
                    scanTimestamp = currentTimestamp,
                    status = newStatusForOpnameItem,
                    actualLocationIfDifferent = if (book.expectedLocation.equals(actualLocationIfKnown, ignoreCase = true)) null else actualLocationIfKnown
                )

            } else { // Buku/identifier tidak ditemukan di master
                newStatusForOpnameItem = "TIDAK_DITEMUKAN_DI_MASTER"
                scanMessage = app.getString(R.string.scan_item_not_in_master, method.displayName, identifier)
                isNewOrUnexpectedInMaster = true // Tandai sebagai baru/tak terduga
                Log.w("StockOpnameViewModel", "Identifier not in master: $identifier by $method. Marked as unexpected.")

                // Buat item untuk laporan, tandai sebagai tidak dikenal
                itemForReport = StockOpnameItem(
                    reportId = 0L,
                    rfidTagHexScanned = if (method == ScanMethod.UHF) identifier else "N/A_BC_UNKNOWN_${identifier.take(20)}", // Handle barcode scan
                    tidScanned = null, // Tidak diketahui karena tidak ada di master
                    itemCodeMaster = if (method == ScanMethod.BARCODE) identifier else null, // Simpan barcode jika itu yang discan
                    titleMaster = "Item Tidak Dikenal",
                    scanTimestamp = currentTimestamp,
                    status = newStatusForOpnameItem,
                    actualLocationIfDifferent = actualLocationIfKnown // Selalu catat lokasinya
                )
                // Tambahkan ke list sementara untuk UI, akan dimasukkan ke DB saat simpan sesi
                temporaryUnexpectedItems.removeAll { it.rfidTagHexScanned == itemForReport.rfidTagHexScanned } // Hapus jika sudah ada, untuk update
                temporaryUnexpectedItems.add(itemForReport)

                // Opsional: Jika ingin langsung update BookMaster saat item baru ditemukan (meskipun tidak ideal tanpa info lengkap)
                // Anda bisa membuat objek BookMaster baru dan menyimpannya
                // bookRepository.insertOrUpdateBookMaster(BookMaster(itemCode = "UNKNOWN_...", title = "Item Tidak Dikenal", rfidTagHex = identifier, isNewOrUnexpected = true, scanStatus = "DITEMUKAN", lastSeenTimestamp = currentTimestamp, actualScannedLocation = actualLocationIfKnown))
            }

            _uiState.update { currentState ->
                if (currentState is StockOpnameUiState.Success) {
                    currentState.copy(
                        lastScanMessage = scanMessage,
                        toastMessage = if (isNewOrUnexpectedInMaster && book == null) scanMessage else null // Toast hanya untuk item yg benar2 baru
                    )
                } else currentState
            }
            // Panggil lagi untuk update statistik newOrUnexpectedBooksCount
            observeBookDataAndUpdateUi()
        }
    }

    fun toggleUhfScan() {
        val currentState = _uiState.value
        if (currentState is StockOpnameUiState.Success) {
            val newIsScanning = !currentState.isUhfScanning
            // Asumsi sederhana: jika sdkManager ada, maka ready. IDEALNYA, ChainwaySDKManager punya method isReady() sendiri.
            if (newIsScanning && sdkManager != null /* && sdkManager.isReady() */) {
                sdkManager.startUhfInventory()
                Log.d("StockOpnameViewModel", "Starting UHF Scan")
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(
                        isUhfScanning = true,
                        isBarcodeScanning = false,
                        lastScanMessage = app.getString(R.string.uhf_scan_starting)
                    )
                }
            } else if (!newIsScanning && sdkManager != null) {
                sdkManager.stopUhfInventory()
                Log.d("StockOpnameViewModel", "Stopping UHF Scan")
                // Pesan 'scan finished' akan dihandle oleh callback onUhfInventoryFinished
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(
                        isUhfScanning = false,
                        lastScanMessage = app.getString(R.string.uhf_scan_stopped)
                    )
                }
            } else if (newIsScanning && sdkManager == null) {
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(toastMessage = "SDK UHF tidak tersedia.")
                }
            }
        } else {
            _uiState.value = StockOpnameUiState.Error("Sesi belum siap untuk scan UHF.")
        }
    }

    fun toggleBarcodeScan() {
        val currentState = _uiState.value
        if (currentState is StockOpnameUiState.Success) {
            val newIsScanning = !currentState.isBarcodeScanning
            // Asumsi sederhana: jika sdkManager ada, maka ready.
            if (newIsScanning && sdkManager != null /* && sdkManager.isReady() */ ) {
                sdkManager.startBarcodeScan()
                Log.d("StockOpnameViewModel", "Starting Barcode Scan")
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(
                        isBarcodeScanning = true,
                        isUhfScanning = false,
                        lastScanMessage = app.getString(R.string.barcode_scan_starting)
                    )
                }
            } else if (!newIsScanning && sdkManager != null) {
                sdkManager.stopBarcodeScan()
                Log.d("StockOpnameViewModel", "Stopping Barcode Scan")
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(
                        isBarcodeScanning = false,
                        lastScanMessage = app.getString(R.string.barcode_scan_stopped)
                    )
                }
            } else if (newIsScanning && sdkManager == null) {
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(toastMessage = "SDK Barcode tidak tersedia.")
                }
            }
        } else {
            _uiState.value = StockOpnameUiState.Error("Sesi belum siap untuk scan Barcode.")
        }
    }

    fun setFilter(filter: String) {
        _currentFilter.value = filter
    }

    private fun filterAndSortBooks(books: List<BookMaster>, filter: String): List<BookMaster> {
        val filtered = when (filter) {
            "Semua" -> books
            "Ditemukan" -> books.filter { it.scanStatus == "DITEMUKAN" }
            "Belum Ditemukan" -> books.filter { it.scanStatus == "BELUM_DITEMUKAN_SESI_INI" || it.scanStatus.isNullOrBlank() && it.isNewOrUnexpected != true }
            "Lokasi Salah" -> books.filter { it.scanStatus == "LOKASI_SALAH" }
            "Baru/Tak Terduga" -> books.filter { it.isNewOrUnexpected == true } // Menggunakan field dari BookMaster
            else -> books
        }
        return filtered.sortedBy { it.title }
    }

    fun saveCurrentOpnameSession(
        onResult: (reportId: Long?, success: Boolean, message: String) -> Unit
    ) {
        val currentStateValue = _uiState.value
        if (currentStateValue !is StockOpnameUiState.Success) {
            Log.w("StockOpnameViewModel", "Invalid state for saving. Current state: $currentStateValue")
            onResult(null, false, "Sesi tidak valid atau belum dimulai.")
            return
        }

        if (currentStateValue.isUhfScanning || currentStateValue.isBarcodeScanning) {
            val stopScanMsg = app.getString(R.string.stop_scan_before_saving)
            _uiState.update { if (it is StockOpnameUiState.Success) it.copy(toastMessage = stopScanMsg) else it }
            onResult(null, false, stopScanMsg)
            return
        }

        val userId = _currentUser.value?.uid ?: "unknown_user_on_save"
        if (userId == "unknown_user_on_save" || userId.isBlank()) {
            Log.e("StockOpnameViewModel", "User ID tidak diketahui, tidak bisa menyimpan sesi.")
            onResult(null, false, "ID Pengguna tidak diketahui. Gagal menyimpan.")
            return
        }

        viewModelScope.launch {
            _uiState.update { if (it is StockOpnameUiState.Success) it.copy(toastMessage = "Menyimpan sesi opname...") else it }

            val opnameSessionNameToSave = currentStateValue.currentOpnameSessionName.ifBlank { "Sesi ${System.currentTimeMillis()}" }
            Log.i("StockOpnameViewModel", "Attempting to save opname session: $opnameSessionNameToSave by user: $userId")

            try {
                // Buat StockOpnameItems dari buku di master
                val itemsFromMasterForReport: List<StockOpnameItem> = currentStateValue.allBooksInCurrentSession
                    .filter { book -> // Hanya item yang discan atau relevan
                        !book.scanStatus.isNullOrBlank() && book.scanStatus != "BELUM_DITEMUKAN_SESI_INI"
                    }
                    .mapNotNull { book ->
                        // Pastikan ada rfidTagHex jika buku dari master dan statusnya bukan 'baru'
                        // Jika buku itu 'isNewOrUnexpected' tapi ada di allBooksInCurrentSession, mungkin sudah ditambahkan manual
                        if (book.rfidTagHex != null || book.isNewOrUnexpected == true) {
                            StockOpnameItem(
                                reportId = 0L, // Akan diisi oleh repository
                                rfidTagHexScanned = book.rfidTagHex ?: "NEW_${book.itemCode.take(20)}", // Jika baru dan tdk ada RFID, buat ID sementara
                                tidScanned = book.tid,
                                itemCodeMaster = book.itemCode,
                                titleMaster = book.title,
                                scanTimestamp = book.lastSeenTimestamp ?: System.currentTimeMillis(),
                                status = book.scanStatus!!, // Tidak null karena sudah difilter
                                actualLocationIfDifferent = if (book.expectedLocation.equals(book.actualScannedLocation, ignoreCase = true)) null else book.actualScannedLocation
                            )
                        } else {
                            Log.w("StockOpnameViewModel", "Book ${book.itemCode} skipped for report item, missing RFID and not marked new.")
                            null
                        }
                    }

                // Gabungkan dengan item tak terduga yang sudah dikumpulkan
                val allItemsForReport = (itemsFromMasterForReport + temporaryUnexpectedItems).distinctBy { it.rfidTagHexScanned }


                if (allItemsForReport.isEmpty() && currentStateValue.foundBooksInSession == 0 && currentStateValue.newOrUnexpectedBooksCount == 0) {
                    val noDataMsg = "Tidak ada data item yang dipindai untuk disimpan."
                    Log.w("StockOpnameViewModel", noDataMsg)
                    _uiState.update { if (it is StockOpnameUiState.Success) it.copy(toastMessage = noDataMsg) else it }
                    onResult(0L, false, noDataMsg)
                    return@launch
                }

                val reportDetails = StockOpnameReport(
                    // reportId auto generate
                    reportName = opnameSessionNameToSave,
                    startTimeMillis = currentStateValue.sessionStartTimeMillis,
                    endTimeMillis = System.currentTimeMillis(),
                    totalItemsExpected = currentStateValue.totalBooksInMaster,
                    totalItemsFound = currentStateValue.foundBooksInSession, // Ini dari kalkulasi BookMaster
                    totalItemsMissing = currentStateValue.missingBooksInSession, // Ini dari kalkulasi BookMaster
                    totalItemsNewOrUnexpected = currentStateValue.newOrUnexpectedBooksCount // Ini dari kalkulasi BookMaster + temporary
                )


                val reportId = bookRepository.saveFullStockOpnameSession(
                    reportDetails = reportDetails,
                    itemsInSession = allItemsForReport
                )

                if (reportId > 0) {
                    val successMessage = app.getString(R.string.opname_session_saved_successfully, opnameSessionNameToSave)
                    Log.i("StockOpnameViewModel", successMessage)
                    _uiState.update { if (it is StockOpnameUiState.Success) it.copy(toastMessage = successMessage) else it }
                    onResult(reportId, true, successMessage)
                    startNewOpnameSession() // Reset untuk sesi berikutnya
                } else {
                    val failMessage = "Gagal menyimpan sesi opname ke database."
                    Log.e("StockOpnameViewModel", failMessage)
                    _uiState.update { if (it is StockOpnameUiState.Success) it.copy(toastMessage = failMessage) else it }
                    onResult(0L, false, failMessage)
                }

            } catch (e: Exception) {
                val errorMsg = "Terjadi kesalahan saat menyimpan: ${e.message}"
                Log.e("StockOpnameViewModel", "Error saving opname session", e)
                _uiState.update { if (it is StockOpnameUiState.Success) it.copy(toastMessage = errorMsg) else it }
                onResult(0L, false, errorMsg)
            }
        }
    }

    fun toastMessageShown() {
        _uiState.update { currentState ->
            if (currentState is StockOpnameUiState.Success) {
                currentState.copy(toastMessage = null)
            } else currentState
        }
    }

    override fun onCleared() {
        super.onCleared()
        observerJob?.cancel()
        sdkManager.releaseResources() // Pastikan sdkManager punya fungsi release()
        Log.d("StockOpnameViewModel", "ViewModel cleared and SDK released.")
    }
}
