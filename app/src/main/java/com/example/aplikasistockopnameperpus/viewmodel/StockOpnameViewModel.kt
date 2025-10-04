package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.size
//import androidx.compose.ui.test.filter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
//import androidx.preference.isNotEmpty
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.model.FilterCriteria // PASTIKAN IMPORT INI ADA
import com.example.aplikasistockopnameperpus.model.ScanMethod
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.util.Constants
import com.example.aplikasistockopnameperpus.util.RealtimeStreamManager // Import baru
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest // PENTING
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class User(val uid: String, val displayName: String? = null)

const val UNKNOWN_ITEM_IN_REPORT_STATUS = "TIDAK_DIKENAL_DI_MASTER"

sealed class StockOpnameUiState {
    data object Initial : StockOpnameUiState()
    data object Loading : StockOpnameUiState()
    data class Success(
        val displayedBooks: List<BookMasterDisplayWrapper>,
        val temporaryUnexpectedItemsForDisplay: List<StockOpnameItem>,
        val allBooksInCurrentSessionForCalc: List<BookMaster>, // Ini akan menjadi buku yang sudah difilter
        val totalBooksInMaster: Int, // Pertimbangkan apakah ini total sebelum atau sesudah filter
        val foundBooksInSession: Int,
        val missingBooksInSession: Int,
        val newOrUnexpectedBooksCount: Int,
        val isUhfScanning: Boolean,
        val isBarcodeScanning: Boolean,
        val lastScanMessage: String,
        val currentFilter: String, // Akan direpresentasikan dari FilterCriteria
        val toastMessage: String? = null,
        val currentOpnameSessionName: String,
        val sessionStartTimeMillis: Long
    ) : StockOpnameUiState()
    data class Error(val message: String) : StockOpnameUiState()
}

data class BookMasterDisplayWrapper(
    val bookMaster: BookMaster,
    val displayPairingStatusText: String,
    val displayPairingStatusColor: Int,
    val displayOpnameStatusText: String,
    val displayOpnameStatusColor: Int,
    val displayLastSeenInfo: String?,
    val displayExpectedLocation: String,
    val isSelectableForMissing: Boolean
)

class StockOpnameViewModel(application: Application) : AndroidViewModel(application) {

    private val bookRepository: BookRepository = (application as MyApplication).bookRepository
    private val sdkManager: ChainwaySDKManager = (application as MyApplication).sdkManager
    private val realtimeStreamManager: RealtimeStreamManager = (application as MyApplication).realtimeStreamManager // Ganti dengan getApplication()
    private val sentEpcCache = mutableSetOf<String>()

    private val defaultSessionNameFormat = Constants.EXPORT_DATE_FORMAT

    private val _uiState = MutableStateFlow<StockOpnameUiState>(StockOpnameUiState.Initial)
    val uiState: StateFlow<StockOpnameUiState> = _uiState.asStateFlow()

    // State untuk FilterCriteria baru
    private val _currentFilterCriteria = MutableStateFlow(FilterCriteria())
    val currentFilterCriteria: StateFlow<FilterCriteria> = _currentFilterCriteria.asStateFlow()

    // _currentFilter (filter string lama) DIHAPUS
    // private val _currentFilter = MutableStateFlow(getApplication<Application>().getString(R.string.filter_all))

    private val _temporaryUnexpectedItems = MutableStateFlow<List<StockOpnameItem>>(emptyList())
    private val _currentUser = MutableStateFlow(User(uid = "default_user_id"))

    // Mengambil buku yang sudah difilter dari repository berdasarkan _currentFilterCriteria
    private val filteredBooksFromRepository: StateFlow<List<BookMaster>> = _currentFilterCriteria
        .flatMapLatest { criteria ->
            Log.d("StockOpnameViewModel", "Observing books with new filter criteria: $criteria")
            bookRepository.getBooksByFilterCriteria(criteria) // Fungsi ini HARUS ADA di BookRepository
        }
        .catch { e ->
            Log.e("StockOpnameViewModel", "Error collecting filtered books from repository", e)
            _uiState.value = StockOpnameUiState.Error(
                getApplication<Application>().getString(R.string.error_loading_books, e.message ?: "Unknown error")
            )
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var observerJob: Job? = null

    init {
        Log.d("StockOpnameViewModel", "ViewModel initialized.")
        setupSdkListeners()
        observeDataAndUpdateUi()
    }

    private fun mapBookMasterToDisplayWrapper(book: BookMaster): BookMasterDisplayWrapper {
        val context: Context = getApplication<Application>().applicationContext
        val displayPairingStatusText: String
        val displayPairingStatusColor: Int

        when (book.pairingStatus) {
            PairingStatus.NOT_PAIRED -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_not_paired)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_not_paired_color)
            }
            PairingStatus.PAIRING_PENDING -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_pending)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_pending_color)
            }
            PairingStatus.PAIRED_WRITE_PENDING -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_paired_write_pending)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_pending_color)
            }
            PairingStatus.PAIRED_WRITE_SUCCESS -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_paired_write_success)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_success_color)
            }
            PairingStatus.PAIRED_WRITE_FAILED -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_paired_write_failed)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_failed_color)
            }
            PairingStatus.PAIRING_FAILED -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_pairing_failed)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_failed_color)
            }
        }

        val displayOpnameStatusText: String
        val displayOpnameStatusColor: Int
        var displayLastSeenInfo: String? = null
        val dateFormat = SimpleDateFormat(Constants.DISPLAY_DATE_TIME_FORMAT, Locale.getDefault())

        when (book.opnameStatus) {
            OpnameStatus.NOT_SCANNED -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_not_scanned_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_not_scanned_color)
            }
            OpnameStatus.FOUND -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_found_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_found_correct_color)
                if (book.lastSeenTimestamp != null) {
                    var seenInfo = dateFormat.format(Date(book.lastSeenTimestamp!!))
                    if (!book.actualScannedLocation.isNullOrBlank()) {
                        seenInfo += " ${context.getString(R.string.opname_last_seen_at_location_prefix_short, book.actualScannedLocation)}"
                    }
                    displayLastSeenInfo = seenInfo.trim()
                }
            }
            OpnameStatus.MISSING -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_missing_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_missing_color)
            }
            OpnameStatus.NEW_ITEM -> { // Untuk tag/item baru di luar database
                displayOpnameStatusText = context.getString(R.string.opname_status_new_item_detected) // PERLU STRING BARU: misal "BARU TERDETEKSI"
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_new_item_color) // Bisa warna yang berbeda dari FOUND
                if (book.lastSeenTimestamp != null) {
                    var seenInfo = context.getString(R.string.opname_detected_at_prefix, dateFormat.format(Date(book.lastSeenTimestamp!!)))
                    if (!book.actualScannedLocation.isNullOrBlank()) {
                        seenInfo += " ${context.getString(R.string.opname_last_seen_at_location_prefix_short, book.actualScannedLocation)}"
                    }
                    displayLastSeenInfo = seenInfo.trim()
                }
            }
        }
        val displayExpectedLocation = book.locationName ?: context.getString(R.string.location_not_available)
        val isSelectableForMissing = book.opnameStatus == OpnameStatus.NOT_SCANNED && !book.isNewOrUnexpected

        return BookMasterDisplayWrapper(
            bookMaster = book,
            displayPairingStatusText = displayPairingStatusText,
            displayPairingStatusColor = displayPairingStatusColor,
            displayOpnameStatusText = displayOpnameStatusText,
            displayOpnameStatusColor = displayOpnameStatusColor,
            displayLastSeenInfo = displayLastSeenInfo,
            displayExpectedLocation = displayExpectedLocation,
            isSelectableForMissing = isSelectableForMissing
        )
    }

    private fun getStatusName(status: OpnameStatus): String {
        return status.name // Asumsi hanya nama enum
    }

    private fun observeDataAndUpdateUi() {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            combine(
                filteredBooksFromRepository, // Menggunakan Flow yang sudah difilter
                _temporaryUnexpectedItems
                // _currentFilterCriteria // Tidak perlu diobservasi di sini jika filteredBooksFromRepository sudah bergantung padanya
            ) { booksFromRepo, tempUnexpected -> // booksFromRepo adalah hasil filter

                // Logika untuk menghitung statistik berdasarkan `booksFromRepo`
                // TODO: Pertimbangkan apakah totalBooksInMaster harus total dari semua buku sebelum filter,
                // atau total dari buku yang cocok dengan filter. Saat ini dihitung dari hasil filter.
                val totalMasterInFiltered = booksFromRepo.count { !it.isNewOrUnexpected }
                val foundCountInFiltered = booksFromRepo.count { it.opnameStatus == OpnameStatus.FOUND && !it.isNewOrUnexpected }
                val missingCountInFiltered = booksFromRepo.count { (it.opnameStatus == OpnameStatus.NOT_SCANNED || it.opnameStatus == OpnameStatus.MISSING) && !it.isNewOrUnexpected }
                val newItemsInFilteredMasterCount = booksFromRepo.count { it.isNewOrUnexpected || it.opnameStatus == OpnameStatus.NEW_ITEM }
                // Hitung temporary unexpected items yang belum ada di booksFromRepo (yang sudah difilter)
                val distinctTempUnexpected = tempUnexpected.filter { tuItem ->
                    booksFromRepo.none { book ->
                        (book.rfidTagHex != null && book.rfidTagHex == tuItem.rfidTagHexScanned) ||
                                (book.itemCode != null && book.itemCode == tuItem.itemCodeMaster)
                    }
                }
                val newOrUnexpectedCount = newItemsInFilteredMasterCount + distinctTempUnexpected.size


                val displayedBookWrappers = booksFromRepo.map { bookMaster ->
                    mapBookMasterToDisplayWrapper(bookMaster)
                }

                val currentUiStateVal = _uiState.value
                val currentSuccessState = currentUiStateVal as? StockOpnameUiState.Success

                // Membuat representasi string dari filter aktif
                val activeFilterDescription = buildActiveFilterDescription(_currentFilterCriteria.value)


                StockOpnameUiState.Success(
                    displayedBooks = displayedBookWrappers,
                    temporaryUnexpectedItemsForDisplay = distinctTempUnexpected,
                    allBooksInCurrentSessionForCalc = booksFromRepo,
                    totalBooksInMaster = totalMasterInFiltered, // Sesuaikan jika ini harus total keseluruhan
                    foundBooksInSession = foundCountInFiltered,
                    missingBooksInSession = missingCountInFiltered,
                    newOrUnexpectedBooksCount = newOrUnexpectedCount,
                    isUhfScanning = currentSuccessState?.isUhfScanning ?: false,
                    isBarcodeScanning = currentSuccessState?.isBarcodeScanning ?: false,
                    lastScanMessage = currentSuccessState?.lastScanMessage ?: getApplication<Application>().getString(R.string.status_idle),
                    currentFilter = activeFilterDescription, // Menggunakan deskripsi dari FilterCriteria
                    toastMessage = null, // Akan di-handle oleh collector jika ada
                    currentOpnameSessionName = currentSuccessState?.currentOpnameSessionName ?: (getApplication<Application>().getString(R.string.default_session_name_prefix) + SimpleDateFormat(defaultSessionNameFormat, Locale.getDefault()).format(Date())),
                    sessionStartTimeMillis = currentSuccessState?.sessionStartTimeMillis ?: System.currentTimeMillis()
                )
            }.catch { e ->
                Log.e("StockOpnameViewModel", "Error in combine operator for UI state", e)
                _uiState.value = StockOpnameUiState.Error(
                    getApplication<Application>().getString(R.string.error_processing_data, e.message ?: "Unknown error")
                )
            }.collect { newSuccessState ->
                _uiState.value = newSuccessState
                // Handle toast message agar hanya ditampilkan sekali
                if (newSuccessState is StockOpnameUiState.Success && newSuccessState.toastMessage != null) {
                    viewModelScope.launch { // Delay kecil untuk memastikan UI sempat menampilkan sebelum reset
                        delay(100)
                        _uiState.update { currentState ->
                            if (currentState is StockOpnameUiState.Success) {
                                currentState.copy(toastMessage = null)
                            } else currentState
                        }
                    }
                }
            }
        }
    }

    private fun buildActiveFilterDescription(criteria: FilterCriteria): String {
        val parts = mutableListOf<String>()
        criteria.opnameStatus?.let { parts.add("Status: ${it.name}") }
        criteria.titleQuery?.let { parts.add("Judul: '$it'") }
        criteria.itemCodeQuery?.let { parts.add("Kode: '$it'") }
        criteria.locationQuery?.let { parts.add("Lokasi: '$it'") }
        criteria.epcQuery?.let { parts.add("EPC: '$it'") }
        criteria.isNewOrUnexpected?.let { if (it) parts.add("Item Baru/Tak Terduga") }

        return if (parts.isEmpty()) getApplication<Application>().getString(R.string.filter_all_active) // Tambahkan string ini
        else parts.joinToString(", ")
    }


    fun applyFilterCriteria(criteria: FilterCriteria) {
        Log.d("StockOpnameViewModel", "Applying filter criteria: $criteria")
        _currentFilterCriteria.value = criteria
    }

    private fun sendItemCodeToPc(epc: String) {
        if (sentEpcCache.contains(epc)) {
            return // Jika sudah, langsung keluar dan tidak melakukan apa-apa.
        }
        // Jalankan di coroutine agar tidak memblokir thread
        viewModelScope.launch {
            try {
                // Cari buku di database berdasarkan EPC
                val book = bookRepository.findBookByEpc(epc)

                // Jika buku ditemukan, kirim item_code-nya
                book?.let {
                    realtimeStreamManager.sendData(it.itemCode)
                    Log.i("StockOpnameViewModel", "Mengirim item_code '${it.itemCode}' dari EPC '$epc' ke PC.")
                    sentEpcCache.add(epc)
                } ?: Log.w("StockOpnameViewModel", "EPC '$epc' tidak ditemukan di DB, tidak ada data dikirim ke PC.")

            } catch (e: Exception) {
                Log.e("StockOpnameViewModel", "Error mencari EPC untuk streaming.", e)
            }
        }
    }

    private fun setupSdkListeners() {
        sdkManager.onUhfTagScanned = { epc ->
            Log.d("StockOpnameViewModel", "SDK CB: onUhfTagScanned: $epc")
            if ((_uiState.value as? StockOpnameUiState.Success)?.isUhfScanning == true) {
                processScannedIdentifier(epc, ScanMethod.UHF, getApplication<Application>().getString(R.string.default_scan_location_rfid))
                sendItemCodeToPc(epc)
            } else {
                Log.w("StockOpnameViewModel", "Received UHF tag for inventory but ViewModel not in UHF scanning state. Ignoring.")
            }
        }
        // --- MODIFIKASI DI SINI ---
        sdkManager.onBarcodeScanned = { barcodeData ->
            Log.d("StockOpnameViewModel", "SDK CB: onBarcodeScanned: $barcodeData")
            val currentSuccessState = _uiState.value as? StockOpnameUiState.Success

            // Periksa apakah ViewModel memang sedang dalam mode scan barcode
            if (currentSuccessState?.isBarcodeScanning == true) {
                // 1. Proses data barcode yang diterima
                processScannedIdentifier(barcodeData, ScanMethod.BARCODE, getApplication<Application>().getString(R.string.default_scan_location_barcode))
                realtimeStreamManager.sendData(barcodeData)
                // 2. Minta SDK Manager untuk menghentikan scan barcode
                // Ini dilakukan SEGERA setelah data diterima oleh ViewModel dan proses awal dimulai.
                Log.d("StockOpnameViewModel", "Barcode data ($barcodeData) received by VM. Requesting SDK Manager to STOP scan.")
                sdkManager.stopBarcodeScan() // Panggil stop di SDK Manager

                // 3. Update UI state di ViewModel untuk merefleksikan bahwa scan barcode sudah tidak aktif lagi
                _uiState.update { uiStateCurrent ->
                    if (uiStateCurrent is StockOpnameUiState.Success) {
                        Log.d("StockOpnameViewModel", "Updating UI state: isBarcodeScanning to false after successful scan.")
                        uiStateCurrent.copy(
                            isBarcodeScanning = false,
                            // Anda bisa juga memperbarui lastScanMessage di sini jika mau,
                            // tapi processScannedIdentifier mungkin sudah melakukannya.
                            // Misalnya: lastScanMessage = uiStateCurrent.lastScanMessage + " (Data diterima, scan berhenti)"
                            lastScanMessage = getApplication<Application>().getString(R.string.barcode_scan_finished_data_received, barcodeData) // Buat string resource ini
                        )
                    } else {
                        uiStateCurrent // Jika state bukan Success, jangan ubah
                    }
                }
            } else {
                Log.w("StockOpnameViewModel", "Received Barcode but ViewModel not in Barcode scanning state (isBarcodeScanning is false or state is not Success). Ignoring.")
                // Jika ViewModel tidak dalam mode scan barcode, tapi SDK mengirim data,
                // mungkin ada baiknya juga memastikan SDK Manager berhenti jika ini adalah perilaku tak terduga.
                // Namun, ini bisa menyebabkan konflik jika ada alasan lain SDK Manager mengirim data.
                // Untuk sekarang, kita fokus pada kasus di mana ViewModel memang mengharapkan scan.
                // sdkManager.stopBarcodeScan() // Pertimbangkan ini jika perlu "safety stop"
            }
        }
        // --- AKHIR MODIFIKASI ---
        sdkManager.onError = { errorMessage ->
            Log.e("StockOpnameViewModel", "SDK CB: onError: $errorMessage")
            val newToastMessage = getApplication<Application>().getString(R.string.sdk_error_prefix, errorMessage)
            _uiState.update { currentState ->
                if (currentState is StockOpnameUiState.Success) {
                    currentState.copy(
                        toastMessage = newToastMessage,
                        isUhfScanning = false, // Stop scanning on SDK error
                        isBarcodeScanning = false
                    )
                } else {
                    // Jika sudah error, tambahkan pesan baru, jika tidak, buat state Error baru
                    if (currentState is StockOpnameUiState.Error) currentState.copy(message = "${currentState.message}\n$newToastMessage")
                    else StockOpnameUiState.Error(newToastMessage)
                }
            }
        }
        sdkManager.onUhfInventoryFinished = {
            Log.d("StockOpnameViewModel", "SDK CB: onUhfInventoryFinished (for stock opname inventory)")
            _uiState.update { currentState ->
                if (currentState is StockOpnameUiState.Success && currentState.isUhfScanning) {
                    currentState.copy(
                        isUhfScanning = false,
                        lastScanMessage = getApplication<Application>().getString(R.string.uhf_scan_finished_or_stopped)
                    )
                } else currentState
            }
        }
        sdkManager.onUhfOperationStopped = { // Callback ini mungkin lebih umum
            Log.d("StockOpnameViewModel", "SDK CB: onUhfOperationStopped (general UHF stop)")
            _uiState.update { currentState ->
                if (currentState is StockOpnameUiState.Success && currentState.isUhfScanning) {
                    Log.i("StockOpnameViewModel", "onUhfOperationStopped: Ensuring isUhfScanning (inventory) is false.")
                    currentState.copy(isUhfScanning = false, lastScanMessage = getApplication<Application>().getString(R.string.uhf_scan_stopped) )
                } else currentState
            }
        }
    }

    fun startNewOpnameSession(sessionNameFromInput: String? = null) {
        viewModelScope.launch {
            _uiState.value = StockOpnameUiState.Loading
            Log.d("StockOpnameViewModel", "Starting new opname session...")
            try {
                if (sdkManager.isUhfDeviceScanning) {
                    sdkManager.stopUhfOperation() // Ini akan memicu onUhfOperationStopped atau onUhfInventoryFinished
                }
                if (sdkManager.isBarcodeDeviceScanning) {
                    sdkManager.stopBarcodeScan()
                }
                // Beri sedikit waktu agar callback SDK (jika ada) selesai sebelum melanjutkan
                delay(200)


                bookRepository.resetAllBookOpnameStatusesAndClearSessionData() // Ini akan memicu filteredBooksFromRepository
                _temporaryUnexpectedItems.value = emptyList()
                _currentFilterCriteria.value = FilterCriteria() // Reset filter

                val newSessionName = if (!sessionNameFromInput.isNullOrBlank()) {
                    sessionNameFromInput
                } else {
                    getApplication<Application>().getString(R.string.default_session_name_prefix) +
                            SimpleDateFormat(defaultSessionNameFormat, Locale.getDefault()).format(Date())
                }
                val newSessionStartTime = System.currentTimeMillis()

                // State awal Success akan di-emit oleh collector `observeDataAndUpdateUi`
                // setelah `filteredBooksFromRepository` memancarkan data awal (kosong atau semua dengan status NOT_SCANNED)
                // dan `_currentFilterCriteria` direset.
                // Kita bisa set pesan awal dan nama sesi di sini.
                _uiState.value = StockOpnameUiState.Success(
                    displayedBooks = emptyList(), // Akan diisi oleh collector
                    temporaryUnexpectedItemsForDisplay = emptyList(),
                    allBooksInCurrentSessionForCalc = emptyList(), // Akan diisi
                    totalBooksInMaster = 0, // Akan diisi oleh collector, mungkin perlu dihitung dari total master di DB
                    foundBooksInSession = 0,
                    missingBooksInSession = 0, // Akan diisi
                    newOrUnexpectedBooksCount = 0,
                    isUhfScanning = false,
                    isBarcodeScanning = false,
                    lastScanMessage = getApplication<Application>().getString(R.string.opname_session_started_with_name, newSessionName),
                    currentFilter = buildActiveFilterDescription(FilterCriteria()), // Filter default
                    toastMessage = getApplication<Application>().getString(R.string.toast_new_session_started),
                    currentOpnameSessionName = newSessionName,
                    sessionStartTimeMillis = newSessionStartTime
                )
                Log.d("StockOpnameViewModel", "New opname session ready: $newSessionName.")
            } catch (e: Exception) {
                Log.e("StockOpnameViewModel", "Error starting new opname session", e)
                _uiState.value = StockOpnameUiState.Error(
                    getApplication<Application>().getString(R.string.error_starting_new_session, e.message ?: "Unknown error")
                )
            }
        }
    }

    fun updateCurrentOpnameSessionName(newName: String) {
        _uiState.update { currentState ->
            if (currentState is StockOpnameUiState.Success) {
                val validatedName = newName.ifBlank {
                    getApplication<Application>().getString(R.string.default_session_name_prefix) +
                            SimpleDateFormat(defaultSessionNameFormat, Locale.getDefault()).format(Date(currentState.sessionStartTimeMillis))
                }
                currentState.copy(currentOpnameSessionName = validatedName)
            } else currentState
        }
    }

    // Di StockOpnameViewModel
    private fun processScannedIdentifier(identifier: String, method: ScanMethod, actualScannedLocationProvided: String) {
        if (identifier.isBlank()) { /* ... */ return }

        viewModelScope.launch {
            val book: BookMaster? = when (method) {
                ScanMethod.UHF -> bookRepository.getBookByRfidTag(identifier)
                ScanMethod.BARCODE -> bookRepository.getBookByItemCode(identifier)
            }
            Log.d("StockOpname_DEBUG", "processScannedIdentifier: ID=$identifier, Method=$method. Book from DB: ${if (book != null) "FOUND (ID: ${book.id}, Title: ${book.title}, OpStatus: ${book.opnameStatus}, isNew: ${book.isNewOrUnexpected})" else "NOT FOUND"}")


            val currentTimestamp = System.currentTimeMillis()
            var scanMessage: String
            var toastMessage: String? = null // Ganti nama showToastForNewUnexpected agar lebih jelas

            if (book != null) {
                // --- BUKU DIKENALI DI MASTER ---
                val bookTitle = book.title ?: getApplication<Application>().getString(R.string.unknown_title)
                val displayIdentifierForUi = book.itemCode ?: book.rfidTagHex ?: identifier

                // Logika baru untuk menangani status buku yang sudah ada
                when (book.opnameStatus) {
                    OpnameStatus.FOUND -> {
                        // Jika buku adalah item master yang sudah FOUND (bukan NEW_ITEM yang salah dilabeli FOUND)
                        if (!book.isNewOrUnexpected) {
                            scanMessage = getApplication<Application>().getString(R.string.last_scan_already_found, displayIdentifierForUi, bookTitle)
                            // Opsional: update lastSeenTimestamp jika perlu, meskipun sudah FOUND
                            // bookRepository.updateBookOpnameStatusByRfid(book.rfidTagHex ?: identifier, OpnameStatus.FOUND, currentTimestamp, actualScannedLocationProvided)
                        } else {
                            // Ini kasus aneh: buku ditandai isNewOrUnexpected TAPI statusnya FOUND.
                            // Seharusnya ini tidak terjadi jika insert awal benar.
                            // Untuk keamanan, kita anggap sebagai scan ulang item baru.
                            scanMessage = getApplication<Application>().getString(R.string.last_scan_unknown_already_logged, displayIdentifierForUi) // Gunakan displayIdentifierForUi jika itemCode-nya "NEW_EPC_..."
                            // Update timestamp & lokasi untuk item baru ini
                            bookRepository.updateBookOpnameStatusByRfid(book.rfidTagHex ?: identifier, OpnameStatus.NEW_ITEM, currentTimestamp, actualScannedLocationProvided)
                            Log.w("StockOpnameViewModel", "Book ${book.id} isNewOrUnexpected=true but OpStatus=FOUND. Treating as re-scan of NEW_ITEM. Updated timestamp/location.")
                        }
                    }
                    OpnameStatus.NEW_ITEM -> {
                        // Buku ini memang item baru yang sudah ada di DB. Hanya update timestamp/lokasi.
                        scanMessage = getApplication<Application>().getString(R.string.last_scan_unknown_already_logged, displayIdentifierForUi) // Gunakan displayIdentifierForUi
                        try {
                            // Update lastSeenTimestamp dan actualScannedLocation untuk item NEW_ITEM ini
                            when (method) {
                                ScanMethod.UHF -> bookRepository.updateBookOpnameStatusByRfid(book.rfidTagHex ?: identifier, OpnameStatus.NEW_ITEM, currentTimestamp, actualScannedLocationProvided)
                                ScanMethod.BARCODE -> bookRepository.updateBookOpnameStatusByItemCode(book.itemCode ?: identifier, OpnameStatus.NEW_ITEM, currentTimestamp, actualScannedLocationProvided)
                            }
                            Log.i("StockOpnameViewModel", "Re-scanned NEW_ITEM '${bookTitle}' (ID: ${book.id}). Updated timestamp/location.")
                        } catch (e: Exception) {
                            Log.e("StockOpnameViewModel", "Failed to update timestamp for re-scanned NEW_ITEM ${book.id}", e)
                            scanMessage = getApplication<Application>().getString(R.string.error_updating_book_status_in_db, bookTitle)
                        }
                    }
                    OpnameStatus.NOT_SCANNED, OpnameStatus.MISSING -> {
                        // Buku dari master (bukan NEW_ITEM) yang belum discan atau hilang, sekarang ditemukan
                        if (!book.isNewOrUnexpected) {
                            val newStatus = OpnameStatus.FOUND
                            scanMessage = getApplication<Application>().getString(R.string.last_scan_found, displayIdentifierForUi, bookTitle, actualScannedLocationProvided)
                            try {
                                when (method) {
                                    ScanMethod.UHF -> bookRepository.updateBookOpnameStatusByRfid(book.rfidTagHex ?: identifier, newStatus, currentTimestamp, actualScannedLocationProvided)
                                    ScanMethod.BARCODE -> bookRepository.updateBookOpnameStatusByItemCode(book.itemCode ?: identifier, newStatus, currentTimestamp, actualScannedLocationProvided)
                                }
                                Log.i("StockOpnameViewModel", "BookMaster '$bookTitle' (ID: ${book.id}) status updated to $newStatus at $actualScannedLocationProvided")
                            } catch (e: Exception) {
                                Log.e("StockOpnameViewModel", "Failed to update book status for ${book.id} to FOUND", e)
                                scanMessage = getApplication<Application>().getString(R.string.error_updating_book_status_in_db, bookTitle)
                            }
                        } else {
                            // Kasus aneh: isNewOrUnexpected=true tapi statusnya NOT_SCANNED/MISSING. Seharusnya langsung NEW_ITEM.
                            // Ini berarti ada yang salah saat insert awal. Kita akan treat sebagai NEW_ITEM baru.
                            // Sebaiknya, log error ini karena tidak seharusnya terjadi.
                            Log.e("StockOpnameViewModel", "Error: Book ${book.id} isNewOrUnexpected=true but status is ${book.opnameStatus}. Forcing to NEW_ITEM.")
                            val statusToSet = OpnameStatus.NEW_ITEM
                            scanMessage = getApplication<Application>().getString(R.string.last_scan_new_item_added, displayIdentifierForUi) // atau last_scan_unknown
                            try {
                                when (method) {
                                    ScanMethod.UHF -> bookRepository.updateBookOpnameStatusByRfid(book.rfidTagHex ?: identifier, statusToSet, currentTimestamp, actualScannedLocationProvided)
                                    ScanMethod.BARCODE -> bookRepository.updateBookOpnameStatusByItemCode(book.itemCode ?: identifier, statusToSet, currentTimestamp, actualScannedLocationProvided)
                                }
                            } catch (e: Exception) { /* ... */ }
                        }
                    }
                }
            } else {
                // --- EPC/BARCODE TIDAK DIKENALI DI MASTER ---
                scanMessage = getApplication<Application>().getString(R.string.last_scan_unknown, identifier)
                toastMessage = getApplication<Application>().getString(R.string.scan_item_not_in_master_new, method.displayName, identifier)
                Log.w("StockOpnameViewModel", "Identifier '$identifier' by $method not in master. Processing as new/unexpected.")

                // Cek _temporaryUnexpectedItems tidak diperlukan lagi jika kita selalu insert ke master
                // val isAlreadyTempUnexpected = _temporaryUnexpectedItems.value.any { ... }

                // Cek apakah sudah ada sebagai item BARU di master
                // Fungsi findNewOrUnexpectedItemByIdentifier harus spesifik mencari item yang isNewOrUnexpected = true DAN opnameStatus = NEW_ITEM
                val existingTrulyNewInMaster = bookRepository.findNewOrUnexpectedItemByIdentifier(identifier, method)

                if (existingTrulyNewInMaster == null) {
                    val newBookForMaster = BookMaster(
                        itemCode = if (method == ScanMethod.BARCODE) identifier else "NEW_EPC_${identifier.takeLast(6)}_${System.currentTimeMillis().toString().takeLast(4)}",
                        title = getApplication<Application>().getString(R.string.unknown_item_title_prefix, identifier),
                        rfidTagHex = if (method == ScanMethod.UHF) identifier else null,
                        opnameStatus = OpnameStatus.NEW_ITEM, // <--- BENAR
                        pairingStatus = PairingStatus.NOT_PAIRED,
                        lastSeenTimestamp = currentTimestamp,
                        actualScannedLocation = actualScannedLocationProvided,
                        locationName = actualScannedLocationProvided,
                        isNewOrUnexpected = true // <--- BENAR
                    )
                    Log.d("StockOpname_DEBUG", "Creating new BookMaster for DB: opnameStatus=${newBookForMaster.opnameStatus}, isNew=${newBookForMaster.isNewOrUnexpected}, EPC=${newBookForMaster.rfidTagHex}")
                    try {
                        bookRepository.insertOrUpdateBookMaster(newBookForMaster)
                        scanMessage = getApplication<Application>().getString(R.string.last_scan_new_item_added, identifier)
                    } catch (e: Exception) {
                        Log.e("StockOpnameViewModel", "Failed to insert new unexpected item to master: $identifier", e)
                        scanMessage = getApplication<Application>().getString(R.string.error_adding_new_item_to_db, identifier)
                    }
                } else {
                    // Sudah ada di master sebagai NEW_ITEM, cukup update timestamp/lokasi
                    scanMessage = getApplication<Application>().getString(R.string.last_scan_unknown_already_logged, identifier)
                    try {
                        when (method) {
                            ScanMethod.UHF -> bookRepository.updateBookOpnameStatusByRfid(existingTrulyNewInMaster.rfidTagHex ?: identifier, OpnameStatus.NEW_ITEM, currentTimestamp, actualScannedLocationProvided)
                            ScanMethod.BARCODE -> bookRepository.updateBookOpnameStatusByItemCode(existingTrulyNewInMaster.itemCode ?: identifier, OpnameStatus.NEW_ITEM, currentTimestamp, actualScannedLocationProvided)
                        }
                        Log.i("StockOpnameViewModel", "Re-scanned existing NEW_ITEM '${existingTrulyNewInMaster.title}' (ID: ${existingTrulyNewInMaster.id}). Updated timestamp/location.")
                    } catch (e: Exception) { /* ... */ }
                    toastMessage = null // Tidak perlu toast jika hanya update timestamp item baru yang sudah ada
                }
            }

            _uiState.update { currentState ->
                if (currentState is StockOpnameUiState.Success) {
                    currentState.copy(
                        lastScanMessage = scanMessage,
                        toastMessage = toastMessage // Gunakan toastMessage yang sudah di-set di atas
                    )
                } else currentState
            }
        }
    }

    fun toggleUhfScan() {
        val currentState = _uiState.value
        if (currentState is StockOpnameUiState.Success) {
            val isCurrentlyScanningInventory = currentState.isUhfScanning

            if (!isCurrentlyScanningInventory) {
                if (currentState.isBarcodeScanning) {
                    sdkManager.stopBarcodeScan() // Ini akan memicu callback jika ada
                    // _uiState.update { successState -> if (successState is StockOpnameUiState.Success) successState.copy(isBarcodeScanning = false) else successState }
                    Log.d("StockOpnameViewModel", "Barcode scan stopped to start UHF inventory.")
                    // Beri waktu callback SDK untuk memproses
                    viewModelScope.launch { delay(100) }

                }
                if (sdkManager.isUhfRadarActive) { // Cek status radar dari SDK Manager
                    val radarActiveMsg = getApplication<Application>().getString(R.string.error_radar_active_stop_first)
                    Log.w("StockOpnameViewModel", radarActiveMsg)
                    _uiState.update {
                        if (it is StockOpnameUiState.Success) it.copy(toastMessage = radarActiveMsg) else it
                    }
                    return
                }
                sentEpcCache.clear()
                Log.d("StockOpnameViewModel", "New UHF scan session started. Sent EPC cache cleared.")
                sdkManager.startUhfInventory()
                Log.d("StockOpnameViewModel", "Requesting to Start UHF Inventory Scan")
                _uiState.update {
                    if (it is StockOpnameUiState.Success) {
                        it.copy(
                            isUhfScanning = true,
                            isBarcodeScanning = false, // Pastikan barcode scan false
                            lastScanMessage = getApplication<Application>().getString(R.string.uhf_scan_starting)
                        )
                    } else it
                }
            } else {
                sdkManager.stopUhfOperation() // Ini akan memicu onUhfInventoryFinished atau onUhfOperationStopped
                Log.d("StockOpnameViewModel", "Requesting to Stop UHF Inventory Scan (user action)")
                // Update UI state akan dihandle oleh callback SDK
                _uiState.update { // Beri feedback langsung bahwa sedang berhenti
                    if (it is StockOpnameUiState.Success) {
                        it.copy(lastScanMessage = getApplication<Application>().getString(R.string.uhf_scan_stopping_user))
                    } else it
                }
            }
        } else {
            // Handle jika state bukan Success
            _uiState.update { prevState ->
                val errorMessage = getApplication<Application>().getString(R.string.error_session_not_ready_for_scan)
                if (prevState is StockOpnameUiState.Error) prevState.copy(message = errorMessage)
                else StockOpnameUiState.Error(errorMessage)
            }
        }
    }

    fun toggleBarcodeScan() {
        val currentState = _uiState.value
        if (currentState is StockOpnameUiState.Success) {
            val isCurrentlyScanningBarcode = currentState.isBarcodeScanning

            if (!isCurrentlyScanningBarcode) {
                if (currentState.isUhfScanning) {
                    sdkManager.stopUhfOperation() // Ini akan memicu callback SDK
                    Log.d("StockOpnameViewModel", "UHF inventory scan stopped to start Barcode scan.")
                    // Beri waktu callback SDK untuk memproses
                    viewModelScope.launch { delay(100) }
                }
                if (sdkManager.isUhfRadarActive) {
                    val radarActiveMsg = getApplication<Application>().getString(R.string.error_radar_active_stop_first)
                    Log.w("StockOpnameViewModel", radarActiveMsg)
                    _uiState.update {
                        if (it is StockOpnameUiState.Success) it.copy(toastMessage = radarActiveMsg) else it
                    }
                    return
                }
                sentEpcCache.clear()
                Log.d("StockOpnameViewModel", "New Barcode scan session started. Sent EPC cache cleared.")
                sdkManager.startBarcodeScan()
                Log.d("StockOpnameViewModel", "Requesting to Start Barcode Scan")
                _uiState.update {
                    if (it is StockOpnameUiState.Success) {
                        it.copy(
                            isBarcodeScanning = true,
                            isUhfScanning = false, // Pastikan UHF scan false
                            lastScanMessage = getApplication<Application>().getString(R.string.barcode_scan_starting)
                        )
                    } else it
                }
            } else {
                sdkManager.stopBarcodeScan() // Ini akan memicu callback SDK jika ada
                Log.d("StockOpnameViewModel", "Requesting to Stop Barcode Scan")
                _uiState.update { // Beri feedback langsung
                    if (it is StockOpnameUiState.Success) {
                        it.copy(
                            isBarcodeScanning = false,
                            lastScanMessage = getApplication<Application>().getString(R.string.barcode_scan_stopped_by_user)
                        )
                    } else it
                }
            }
        } else {
            _uiState.update { prevState ->
                val errorMessage = getApplication<Application>().getString(R.string.error_session_not_ready_for_scan)
                if (prevState is StockOpnameUiState.Error) prevState.copy(message = errorMessage)
                else StockOpnameUiState.Error(errorMessage)
            }
        }
    }

    // Fungsi filterAndSortData (filter string lama) TIDAK DIPERLUKAN LAGI
    // jika filter dilakukan oleh DAO. Dihapus.

    fun markSelectedMissing(bookIds: List<Long>) {
        if (bookIds.isEmpty()) return
        viewModelScope.launch {
            val currentTimestamp = System.currentTimeMillis()
            try {
                bookRepository.updateOpnameStatusForBookIds(bookIds, OpnameStatus.MISSING, currentTimestamp, null)
                _uiState.update { currentState ->
                    if (currentState is StockOpnameUiState.Success) {
                        currentState.copy(toastMessage = getApplication<Application>().getString(R.string.items_marked_as_missing, bookIds.size))
                    } else currentState
                }
            } catch (e: Exception) {
                Log.e("StockOpnameViewModel", "Error marking books as missing", e)
                _uiState.update { currentState ->
                    val errorMsg = getApplication<Application>().getString(R.string.error_marking_missing, e.message)
                    if (currentState is StockOpnameUiState.Success) currentState.copy(toastMessage = errorMsg)
                    else StockOpnameUiState.Error(errorMsg)
                }
            }
        }
    }

    fun saveCurrentOpnameSession(onResult: (reportId: Long?, success: Boolean, message: String) -> Unit) {
        val currentStateValue = _uiState.value
        if (currentStateValue !is StockOpnameUiState.Success) {
            Log.w("StockOpnameViewModel", "Invalid state for saving. Current state: $currentStateValue")
            onResult(null, false, getApplication<Application>().getString(R.string.error_invalid_session_for_saving))
            return
        }

        var wasScanning = false
        if (currentStateValue.isUhfScanning) {
            sdkManager.stopUhfOperation()
            wasScanning = true
            Log.i("StockOpnameViewModel", "UHF scan stopped before saving session.")
        }
        if (currentStateValue.isBarcodeScanning) {
            sdkManager.stopBarcodeScan()
            // _uiState.update { if (it is StockOpnameUiState.Success) it.copy(isBarcodeScanning = false) else it }
            wasScanning = true
            Log.i("StockOpnameViewModel", "Barcode scan stopped before saving session.")
        }

        if (wasScanning) {
            val stopScanMsg = getApplication<Application>().getString(R.string.scan_stopped_before_saving_wait)
            _uiState.update { if (it is StockOpnameUiState.Success) it.copy(toastMessage = stopScanMsg, lastScanMessage = stopScanMsg) else it }
            viewModelScope.launch {
                delay(500) // Beri waktu SDK callback untuk update state (isUhfScanning / isBarcodeScanning)
                proceedWithSavingSession(onResult)
            }
            return
        }
        proceedWithSavingSession(onResult)
    }

    private fun proceedWithSavingSession(onResult: (reportId: Long?, success: Boolean, message: String) -> Unit) {
        val latestSuccessState = _uiState.value as? StockOpnameUiState.Success
        if (latestSuccessState == null) {
            Log.e("StockOpnameViewModel", "Failed to get latest success state for saving.")
            onResult(null, false, getApplication<Application>().getString(R.string.error_invalid_session_for_saving))
            return
        }

        val userId = _currentUser.value.uid
        val sessionEndTime = System.currentTimeMillis()

        viewModelScope.launch {
            _uiState.update { if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = getApplication<Application>().getString(R.string.saving_opname_session), toastMessage = getApplication<Application>().getString(R.string.saving_message)) else it }

            val opnameSessionNameToSave = latestSuccessState.currentOpnameSessionName
            Log.i("StockOpnameViewModel", "Proceeding to save opname session: $opnameSessionNameToSave by user: $userId")

            try {
                // Ambil semua buku dari master (atau yang sesuai dengan filter "semua" jika diperlukan)
                // untuk menentukan mana yang MISSING
                val allMasterBooks = bookRepository.getBooksByFilterCriteria(FilterCriteria()) // Ambil semua untuk cek yang belum terscan
                    .stateIn(viewModelScope).value // Ambil nilai saat ini dari flow

                val booksStillNotScanned = allMasterBooks.filter {
                    it.opnameStatus == OpnameStatus.NOT_SCANNED && !it.isNewOrUnexpected
                }

                if (booksStillNotScanned.isNotEmpty()) {
                    val idsToMarkMissing = booksStillNotScanned.map { it.id }
                    bookRepository.updateOpnameStatusForBookIds(idsToMarkMissing, OpnameStatus.MISSING, sessionEndTime, null)
                    Log.i("StockOpnameViewModel", "${idsToMarkMissing.size} items automatically marked as MISSING before saving report.")
                }

                // Ambil data terbaru dari DB setelah update status MISSING
                // Ini akan mengambil semua buku yang statusnya bukan NOT_SCANNED (kecuali yang baru)
                // atau semua buku yang NEW_ITEM
                val booksForReport = bookRepository.getAllBookMastersList().filter { book ->
                    (book.opnameStatus != OpnameStatus.NOT_SCANNED && !book.isNewOrUnexpected) || book.isNewOrUnexpected
                }


                val itemsFromMasterForReport: List<StockOpnameItem> = booksForReport
                    .map { book ->
                        StockOpnameItem(
                            reportId = 0L, // Akan diisi oleh repository
                            rfidTagHexScanned = book.rfidTagHex ?: (getApplication<Application>().getString(R.string.master_item_no_rfid_prefix) + (book.itemCode ?: "NO_CODE_${book.id}")),
                            tidScanned = book.tid,
                            itemCodeMaster = book.itemCode,
                            titleMaster = book.title,
                            scanTimestamp = book.lastSeenTimestamp ?: sessionEndTime,
                            status = getStatusName(book.opnameStatus),
                            actualLocationIfDifferent = if (book.opnameStatus == OpnameStatus.FOUND || book.opnameStatus == OpnameStatus.NEW_ITEM) book.actualScannedLocation else null,
                            expectedLocationMaster = book.locationName,
                            isNewOrUnexpectedItem = book.isNewOrUnexpected || book.opnameStatus == OpnameStatus.NEW_ITEM
                        )
                    }

                // Ambil temporary unexpected items yang sudah ada
                val temporaryUnexpectedItemsToSave = _temporaryUnexpectedItems.value.map { tempItem ->
                    tempItem.copy(
                        scanTimestamp = tempItem.scanTimestamp ?: sessionEndTime,
                        status = UNKNOWN_ITEM_IN_REPORT_STATUS
                    )
                }

                // Gabungkan dan pastikan tidak ada duplikasi dari temporary yang mungkin sudah masuk ke master sebagai NEW_ITEM
                val allItemsForReport = (itemsFromMasterForReport + temporaryUnexpectedItemsToSave)
                    .distinctBy { it.rfidTagHexScanned ?: it.itemCodeMaster ?: java.util.UUID.randomUUID().toString() }


                if (allItemsForReport.isEmpty()) {
                    Log.w("StockOpnameViewModel", "No items to save in the report for session: $opnameSessionNameToSave")
                    onResult(null, false, getApplication<Application>().getString(R.string.error_no_items_to_save))
                    _uiState.update { if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = getApplication<Application>().getString(R.string.opname_session_saved_no_items), toastMessage = null) else it }
                    return@launch
                }

                // Hitung ulang totalItemsExpected dari semua buku di master data (tidak terfilter)
                val totalItemsInMasterDataFromDb = bookRepository.getTotalBookCountInMaster()


                val itemsFoundCount = allItemsForReport.count { it.status == OpnameStatus.FOUND.name && !it.isNewOrUnexpectedItem }
                val itemsMissingCount = allItemsForReport.count { it.status == OpnameStatus.MISSING.name && !it.isNewOrUnexpectedItem }
                val newOrUnexpectedItemsCount = allItemsForReport.count { it.isNewOrUnexpectedItem || it.status == OpnameStatus.NEW_ITEM.name || it.status == UNKNOWN_ITEM_IN_REPORT_STATUS }


                val report = StockOpnameReport(
                    reportName = opnameSessionNameToSave,
                    startTimeMillis = latestSuccessState.sessionStartTimeMillis,
                    endTimeMillis = sessionEndTime,
                    userId = userId,
                    totalItemsExpected = totalItemsInMasterDataFromDb, // Gunakan total dari DB
                    totalItemsFound = itemsFoundCount,
                    totalItemsMisplaced = 0,
                    totalItemsMissing = itemsMissingCount,
                    totalItemsNewOrUnexpected = newOrUnexpectedItemsCount
                )

                val newReportId = bookRepository.insertStockOpnameReportWithItems(report, allItemsForReport)

                if (newReportId > 0) {
                    val successMsg = getApplication<Application>().getString(R.string.opname_session_saved_successfully, opnameSessionNameToSave)
                    Log.i("StockOpnameViewModel", "Opname session '$opnameSessionNameToSave' saved successfully. Report ID: $newReportId")
                    onResult(newReportId, true, successMsg)
                    _uiState.update { if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = successMsg, toastMessage = null) else it }
                    // Pertimbangkan untuk memulai sesi baru
                    // startNewOpnameSession()
                } else {
                    val errorMsg = getApplication<Application>().getString(R.string.error_saving_opname_session_db)
                    Log.e("StockOpnameViewModel", "Failed to save opname session '$opnameSessionNameToSave' to database.")
                    onResult(null, false, errorMsg)
                    _uiState.update { if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = errorMsg, toastMessage = errorMsg) else it }
                }

            } catch (e: Exception) {
                Log.e("StockOpnameViewModel", "Exception while saving opname session: $opnameSessionNameToSave", e)
                val exceptionErrorMsg = getApplication<Application>().getString(R.string.error_saving_opname_session_exception, e.localizedMessage ?: "Unknown exception")
                onResult(null, false, exceptionErrorMsg)
                _uiState.update {
                    if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = exceptionErrorMsg, toastMessage = exceptionErrorMsg)
                    else StockOpnameUiState.Error(exceptionErrorMsg)
                }
            }
        }
    }


    fun clearToastMessage() { // Fungsi ini mungkin tidak lagi diperlukan jika toast di-handle di collector observeDataAndUpdateUi
        _uiState.update { currentState ->
            if (currentState is StockOpnameUiState.Success) {
                currentState.copy(toastMessage = null)
            } else currentState
        }
    }

    override fun onCleared() {
        super.onCleared()
        // sdkManager.releaseResources() // Pertimbangkan apakah ini perlu jika SDK dikelola oleh Application
        observerJob?.cancel()
        Log.d("StockOpnameViewModel", "ViewModel cleared.")
    }
}

