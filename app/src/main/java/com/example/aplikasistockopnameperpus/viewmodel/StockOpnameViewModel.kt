package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.R // Untuk string resources
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus // Import Enum Anda
import com.example.aplikasistockopnameperpus.data.database.PairingStatus // Import Enum Anda
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.model.ScanMethod
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.util.Constants
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Asumsi User data class sederhana
data class User(val uid: String, val displayName: String? = null)

// Konstanta string untuk status item TIDAK DIKENAL di StockOpnameItem
const val UNKNOWN_ITEM_IN_REPORT_STATUS = "TIDAK_DIKENAL_DI_MASTER" // Atau gunakan string resource

sealed class StockOpnameUiState {
    data object Initial : StockOpnameUiState()
    data object Loading : StockOpnameUiState()
    data class Success(
        val displayedBooks: List<BookMasterDisplayWrapper>,
        val temporaryUnexpectedItemsForDisplay: List<StockOpnameItem>,
        val allBooksInCurrentSessionForCalc: List<BookMaster>,
        val totalBooksInMaster: Int,
        val foundBooksInSession: Int,
        val misplacedBooksInSession: Int,
        val missingBooksInSession: Int,
        val newOrUnexpectedBooksCount: Int,
        val isUhfScanning: Boolean,
        val isBarcodeScanning: Boolean,
        val lastScanMessage: String,
        val currentFilter: String,
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
    private val app = application // app adalah Application context

    private val defaultFilterText = app.getString(R.string.filter_all)
    private val defaultSessionNameFormat = Constants.EXPORT_DATE_FORMAT

    private val _uiState = MutableStateFlow<StockOpnameUiState>(StockOpnameUiState.Initial)
    val uiState: StateFlow<StockOpnameUiState> = _uiState.asStateFlow()

    private val _currentFilter = MutableStateFlow(defaultFilterText)
    private val _temporaryUnexpectedItems = MutableStateFlow<List<StockOpnameItem>>(emptyList())
    private val _currentUser = MutableStateFlow(User(uid = "default_user_id")) // Pastikan ini diinisialisasi dengan benar

    private val allBooksFromRepoInternal: StateFlow<List<BookMaster>> = bookRepository.getAllBookMastersFlow()
        .catch { e ->
            Log.e("StockOpnameViewModel", "Error collecting books from repo", e)
            _uiState.value = StockOpnameUiState.Error(app.getString(R.string.error_loading_books, e.message ?: "Unknown error"))
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

    // Fungsi pemetaan BookMaster ke BookMasterDisplayWrapper
    private fun mapBookMasterToDisplayWrapper(book: BookMaster): BookMasterDisplayWrapper {
        val context: Context = app // Gunakan context aplikasi

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
                displayOpnameStatusText = context.getString(R.string.opname_status_not_scanned)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_not_scanned_color)
            }
            OpnameStatus.FOUND_CORRECT_LOCATION -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_found_correct)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_found_correct_color)
                if (book.lastSeenTimestamp != null) {
                    displayLastSeenInfo = dateFormat.format(Date(book.lastSeenTimestamp!!))
                }
            }
            OpnameStatus.FOUND_WRONG_LOCATION -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_found_wrong_location)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_found_wrong_location_color)
                var seenInfo = ""
                if (!book.actualScannedLocation.isNullOrBlank()) {
                    seenInfo += context.getString(R.string.opname_last_seen_at_location_prefix, book.actualScannedLocation) + " "
                }
                if (book.lastSeenTimestamp != null) {
                    seenInfo += "(${dateFormat.format(Date(book.lastSeenTimestamp!!))})"
                }
                displayLastSeenInfo = seenInfo.trim().ifEmpty { null }
            }
            OpnameStatus.MISSING -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_missing)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_missing_color)
            }
            OpnameStatus.NEW_ITEM -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_new_item)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_new_item_color)
                if (book.lastSeenTimestamp != null) {
                    displayLastSeenInfo = context.getString(R.string.opname_detected_at_prefix, dateFormat.format(Date(book.lastSeenTimestamp!!)))
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

    private fun getStatusName(status: OpnameStatus): String { // Masih digunakan di saveCurrentOpnameSession
        return status.name // Atau konversi ke string yang lebih user-friendly jika perlu
    }

    private fun observeDataAndUpdateUi() {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            combine(
                allBooksFromRepoInternal,
                _currentFilter,
                _temporaryUnexpectedItems
            ) { booksFromMaster, filter, tempUnexpected ->

                val totalMaster = booksFromMaster.size
                val foundCount = booksFromMaster.count { it.opnameStatus == OpnameStatus.FOUND_CORRECT_LOCATION }
                val misplacedCount = booksFromMaster.count { it.opnameStatus == OpnameStatus.FOUND_WRONG_LOCATION }
                val missingCount = booksFromMaster.count { (it.opnameStatus == OpnameStatus.NOT_SCANNED || it.opnameStatus == OpnameStatus.MISSING) && !it.isNewOrUnexpected }
                val newItemsInMasterCount = booksFromMaster.count { it.isNewOrUnexpected || it.opnameStatus == OpnameStatus.NEW_ITEM }
                val newOrUnexpectedCount = newItemsInMasterCount + tempUnexpected.size

                val displayedBookWrappers = booksFromMaster.map { bookMaster ->
                    mapBookMasterToDisplayWrapper(bookMaster)
                }

                val (filteredBooksForDisplay, unexpectedItemsForDisplay) = filterAndSortData(
                    displayedBookWrappers,
                    tempUnexpected,
                    filter
                )

                val currentUiState = _uiState.value
                val newSessionName = (currentUiState as? StockOpnameUiState.Success)?.currentOpnameSessionName
                    ?: (app.getString(R.string.default_session_name_prefix) + SimpleDateFormat(defaultSessionNameFormat, Locale.getDefault()).format(Date()))
                val newSessionStartTime = (currentUiState as? StockOpnameUiState.Success)?.sessionStartTimeMillis ?: System.currentTimeMillis()
                val lastScanMsg = (currentUiState as? StockOpnameUiState.Success)?.lastScanMessage ?: app.getString(R.string.opname_session_not_started)

                StockOpnameUiState.Success(
                    displayedBooks = filteredBooksForDisplay,
                    temporaryUnexpectedItemsForDisplay = unexpectedItemsForDisplay,
                    allBooksInCurrentSessionForCalc = booksFromMaster,
                    totalBooksInMaster = totalMaster,
                    foundBooksInSession = foundCount,
                    misplacedBooksInSession = misplacedCount,
                    missingBooksInSession = missingCount,
                    newOrUnexpectedBooksCount = newOrUnexpectedCount,
                    isUhfScanning = (currentUiState as? StockOpnameUiState.Success)?.isUhfScanning ?: false,
                    isBarcodeScanning = (currentUiState as? StockOpnameUiState.Success)?.isBarcodeScanning ?: false,
                    lastScanMessage = if (booksFromMaster.isEmpty() && currentUiState is StockOpnameUiState.Initial) app.getString(R.string.start_opname_session) else lastScanMsg,
                    currentFilter = filter,
                    toastMessage = (currentUiState as? StockOpnameUiState.Success)?.toastMessage,
                    currentOpnameSessionName = newSessionName,
                    sessionStartTimeMillis = newSessionStartTime
                )
            }.catch { e ->
                Log.e("StockOpnameViewModel", "Error combining flows for UI update", e)
                _uiState.value = StockOpnameUiState.Error(app.getString(R.string.error_ui_update, e.message ?: "Unknown error"))
            }.collect { newSuccessState ->
                _uiState.value = newSuccessState
            }
        }
    }

    private fun setupSdkListeners() {
        sdkManager.onUhfTagScanned = { epc ->
            Log.d("StockOpnameViewModel", "SDK: onUhfTagScanned: $epc")
            if ((_uiState.value as? StockOpnameUiState.Success)?.isUhfScanning == true) {
                processScannedIdentifier(epc, ScanMethod.UHF, app.getString(R.string.default_scan_location_uhf))
            }
        }
        sdkManager.onBarcodeScanned = { barcodeData ->
            Log.d("StockOpnameViewModel", "SDK: onBarcodeScanned: $barcodeData")
            if ((_uiState.value as? StockOpnameUiState.Success)?.isBarcodeScanning == true) {
                processScannedIdentifier(barcodeData, ScanMethod.BARCODE, app.getString(R.string.default_scan_location_barcode))
            }
        }
        sdkManager.onError = { errorMessage ->
            Log.e("StockOpnameViewModel", "SDK: onError: $errorMessage")
            val newToastMessage = app.getString(R.string.sdk_error_prefix, errorMessage)
            _uiState.update { currentState ->
                if (currentState is StockOpnameUiState.Success) {
                    currentState.copy(
                        toastMessage = newToastMessage,
                        isUhfScanning = false, // Matikan status scan jika ada error SDK
                        isBarcodeScanning = false
                    )
                } else {
                    StockOpnameUiState.Error(newToastMessage) // Jika state bukan Success, tampilkan error
                }
            }
        }
        sdkManager.onUhfInventoryFinished = {
            Log.d("StockOpnameViewModel", "SDK: onUhfInventoryFinished")
            _uiState.update { currentState ->
                if (currentState is StockOpnameUiState.Success && currentState.isUhfScanning) {
                    currentState.copy(isUhfScanning = false, lastScanMessage = app.getString(R.string.uhf_scan_finished))
                } else currentState
            }
        }
    }

    fun startNewOpnameSession(sessionNameFromInput: String? = null) {
        viewModelScope.launch {
            _uiState.value = StockOpnameUiState.Loading
            Log.d("StockOpnameViewModel", "Starting new opname session...")
            try {
                bookRepository.resetAllBookOpnameStatusForNewSession()
                _temporaryUnexpectedItems.value = emptyList()

                val newSessionName = if (!sessionNameFromInput.isNullOrBlank()) {
                    sessionNameFromInput
                } else {
                    app.getString(R.string.default_session_name_prefix) +
                            SimpleDateFormat(defaultSessionNameFormat, Locale.getDefault()).format(Date())
                }
                val newSessionStartTime = System.currentTimeMillis()

                // Update UI state to reflect the new session.
                // Data dari allBooksFromRepoInternal akan otomatis me-refresh dan memicu observeDataAndUpdateUi
                // untuk mengisi displayedBooks, dll.
                _uiState.update { // Menggunakan _uiState.update untuk transisi yang aman
                    val currentSuccessState = _uiState.value as? StockOpnameUiState.Success // Coba ambil state sukses sebelumnya untuk beberapa nilai default
                    StockOpnameUiState.Success(
                        displayedBooks = currentSuccessState?.displayedBooks ?: emptyList(), //Akan diperbarui oleh flow
                        temporaryUnexpectedItemsForDisplay = emptyList(),
                        allBooksInCurrentSessionForCalc = currentSuccessState?.allBooksInCurrentSessionForCalc ?: emptyList(), //Akan diperbarui oleh flow
                        totalBooksInMaster = currentSuccessState?.totalBooksInMaster ?: 0, //Akan diperbarui oleh flow
                        foundBooksInSession = 0,
                        misplacedBooksInSession = 0,
                        missingBooksInSession = currentSuccessState?.missingBooksInSession ?: 0, //Akan diperbarui oleh flow
                        newOrUnexpectedBooksCount = 0,
                        isUhfScanning = false,
                        isBarcodeScanning = false,
                        lastScanMessage = app.getString(R.string.opname_session_started_with_name, newSessionName),
                        currentFilter = _currentFilter.value, // Pertahankan filter saat ini
                        toastMessage = null, // Hapus toast message sebelumnya
                        currentOpnameSessionName = newSessionName,
                        sessionStartTimeMillis = newSessionStartTime
                    )
                }
                Log.d("StockOpnameViewModel", "New opname session ready: $newSessionName. Waiting for data refresh from repository.")
                // Tidak perlu memanggil observeDataAndUpdateUi secara manual di sini,
                // karena perubahan di bookRepository akan memicu flow allBooksFromRepoInternal.
            } catch (e: Exception) {
                Log.e("StockOpnameViewModel", "Error starting new opname session", e)
                _uiState.value = StockOpnameUiState.Error(app.getString(R.string.error_starting_new_session, e.message ?: "Unknown error"))
            }
        }
    }
    // ... Sisa fungsi akan ada di bagian kedua
    fun updateCurrentOpnameSessionName(newName: String) {
        _uiState.update { currentState ->
            if (currentState is StockOpnameUiState.Success) {
                val validatedName = newName.ifBlank {
                    app.getString(R.string.default_session_name_prefix) +
                            SimpleDateFormat(defaultSessionNameFormat, Locale.getDefault()).format(Date(currentState.sessionStartTimeMillis))
                }
                currentState.copy(currentOpnameSessionName = validatedName)
            } else currentState
        }
    }

    private fun processScannedIdentifier(identifier: String, method: ScanMethod, actualScannedLocation: String) {
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
            var showToastForNewUnexpected = false

            if (book != null) {
                val bookTitle = book.title ?: app.getString(R.string.unknown_title)
                var newOpnameStatus = book.opnameStatus // Ini akan diupdate jika perlu

                if (book.opnameStatus == OpnameStatus.FOUND_CORRECT_LOCATION || book.opnameStatus == OpnameStatus.FOUND_WRONG_LOCATION) {
                    scanMessage = app.getString(R.string.scan_already_processed, method.displayName, identifier, bookTitle)
                } else {
                    val expectedLocationFromSlims = book.locationName ?: app.getString(R.string.unknown_location)
                    if (expectedLocationFromSlims.equals(actualScannedLocation, ignoreCase = true)) {
                        newOpnameStatus = OpnameStatus.FOUND_CORRECT_LOCATION
                        scanMessage = app.getString(R.string.scan_success_item_found_location, method.displayName, identifier, bookTitle, actualScannedLocation)
                    } else {
                        newOpnameStatus = OpnameStatus.FOUND_WRONG_LOCATION
                        scanMessage = app.getString(R.string.scan_success_item_misplaced, method.displayName, identifier, bookTitle, actualScannedLocation, expectedLocationFromSlims)
                    }

                    try {
                        when (method) {
                            ScanMethod.UHF -> {
                                bookRepository.updateBookOpnameStatusByRfid(
                                    rfidTag = book.rfidTagHex ?: identifier, // Sebaiknya pastikan book.rfidTagHex tidak null jika method UHF
                                    status = newOpnameStatus,
                                    timestamp = currentTimestamp,
                                    actualLocation = actualScannedLocation
                                )
                            }
                            ScanMethod.BARCODE -> {
                                bookRepository.updateBookOpnameStatusByItemCode(
                                    itemCode = book.itemCode ?: identifier, // Jika book.itemCode null, gunakan identifier
                                    status = newOpnameStatus,
                                    timestamp = currentTimestamp,
                                    actualLocation = actualScannedLocation
                                )
                            }
                        }
                        Log.i("StockOpnameViewModel", "BookMaster '$bookTitle' (ID: ${book.id}, Scanned via: $method) status updated to $newOpnameStatus")
                    } catch (e: Exception) {
                        Log.e("StockOpnameViewModel", "Failed to update book status for ${book.id} via $method", e)
                        scanMessage = app.getString(R.string.error_updating_book_status_in_db, bookTitle)
                        // Pertimbangkan untuk tidak mengubah newOpnameStatus jika update DB gagal
                    }
                }
            } else { // Buku TIDAK ditemukan di master data
                scanMessage = app.getString(R.string.scan_item_not_in_master, method.displayName, identifier)
                showToastForNewUnexpected = true
                Log.w("StockOpnameViewModel", "Identifier '$identifier' by $method not in master. Adding to temporary unexpected items.")

                val existingUnexpectedItem = _temporaryUnexpectedItems.value.find { tempItem ->
                    (method == ScanMethod.UHF && tempItem.rfidTagHexScanned == identifier) ||
                            (method == ScanMethod.BARCODE && tempItem.itemCodeMaster == identifier && tempItem.itemCodeMaster != null) // Pastikan itemCodeMaster tidak null untuk BARCODE
                }

                if (existingUnexpectedItem == null) {
                    val newUnexpectedItem = StockOpnameItem(
                        reportId = 0L, // Akan diisi saat menyimpan laporan
                        rfidTagHexScanned = if (method == ScanMethod.UHF) identifier else app.getString(R.string.new_barcode_item_rfid_placeholder_prefix) + identifier.take(20) + "_" + System.nanoTime(),
                        tidScanned = null,
                        itemCodeMaster = if (method == ScanMethod.BARCODE) identifier else null,
                        titleMaster = app.getString(R.string.unknown_item_title_prefix, identifier),
                        scanTimestamp = currentTimestamp,
                        status = UNKNOWN_ITEM_IN_REPORT_STATUS, // Status untuk item yang tidak ada di master
                        actualLocationIfDifferent = actualScannedLocation,
                        expectedLocationMaster = null, // Tidak diketahui karena tidak ada di master
                        isNewOrUnexpectedItem = true
                    )
                    _temporaryUnexpectedItems.update { it + newUnexpectedItem }
                } else {
                    scanMessage = app.getString(R.string.scan_unexpected_already_logged, identifier)
                    showToastForNewUnexpected = false // Tidak perlu toast jika sudah ada
                }
            }

            _uiState.update { currentState ->
                if (currentState is StockOpnameUiState.Success) {
                    currentState.copy(
                        lastScanMessage = scanMessage,
                        toastMessage = if (showToastForNewUnexpected) scanMessage else null // Hanya tampilkan toast jika item baru ditambahkan
                    )
                } else currentState
            }
        }
    }

    fun toggleUhfScan() {
        val currentState = _uiState.value
        if (currentState is StockOpnameUiState.Success) {
            val newIsScanning = !currentState.isUhfScanning
            if (newIsScanning) {
                sdkManager.startUhfInventory()
                Log.d("StockOpnameViewModel", "Starting UHF Scan")
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(
                        isUhfScanning = true,
                        isBarcodeScanning = false, // Matikan barcode scan jika UHF dimulai
                        lastScanMessage = app.getString(R.string.uhf_scan_starting)
                    )
                }
            } else {
                sdkManager.stopUhfInventory() // Ini akan memicu onUhfInventoryFinished jika SDK mendukungnya
                Log.d("StockOpnameViewModel", "Stopping UHF Scan (user action)")
                // Status isUhfScanning akan diupdate oleh onUhfInventoryFinished atau jika SDK tidak memicu, set di sini
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(
                        isUhfScanning = false, // Langsung set false
                        lastScanMessage = app.getString(R.string.uhf_scan_stopped_by_user)
                    )
                }
            }
        } else {
            _uiState.update { prevState ->
                val errorMessage = app.getString(R.string.error_session_not_ready_for_scan)
                if (prevState is StockOpnameUiState.Error) prevState.copy(message = errorMessage)
                else StockOpnameUiState.Error(errorMessage)
            }
        }
    }

    fun toggleBarcodeScan() {
        val currentState = _uiState.value
        if (currentState is StockOpnameUiState.Success) {
            val newIsScanning = !currentState.isBarcodeScanning
            if (newIsScanning) {
                sdkManager.startBarcodeScan()
                Log.d("StockOpnameViewModel", "Starting Barcode Scan")
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(
                        isBarcodeScanning = true,
                        isUhfScanning = false, // Matikan UHF scan jika barcode dimulai
                        lastScanMessage = app.getString(R.string.barcode_scan_starting)
                    )
                }
            } else {
                sdkManager.stopBarcodeScan()
                Log.d("StockOpnameViewModel", "Stopping Barcode Scan")
                _uiState.update {
                    (it as StockOpnameUiState.Success).copy(
                        isBarcodeScanning = false,
                        lastScanMessage = app.getString(R.string.barcode_scan_stopped_by_user)
                    )
                }
            }
        } else {
            _uiState.update { prevState ->
                val errorMessage = app.getString(R.string.error_session_not_ready_for_scan)
                if (prevState is StockOpnameUiState.Error) prevState.copy(message = errorMessage)
                else StockOpnameUiState.Error(errorMessage)
            }
        }
    }

    fun setFilter(filter: String) {
        _currentFilter.value = filter
    }

    private fun filterAndSortData(
        wrappedBooksFromMaster: List<BookMasterDisplayWrapper>,
        tempUnexpectedItemsInput: List<StockOpnameItem>,
        filter: String
    ): Pair<List<BookMasterDisplayWrapper>, List<StockOpnameItem>> {
        var filteredMastersResult: List<BookMasterDisplayWrapper> = wrappedBooksFromMaster
        var filteredUnexpectedResult: List<StockOpnameItem> = tempUnexpectedItemsInput

        when (filter) {
            app.getString(R.string.filter_all) -> { /* No filtering needed, show all */ }
            app.getString(R.string.filter_found) -> {
                filteredMastersResult = wrappedBooksFromMaster.filter { it.bookMaster.opnameStatus == OpnameStatus.FOUND_CORRECT_LOCATION }
                filteredUnexpectedResult = emptyList()
            }
            app.getString(R.string.filter_not_found_yet) -> {
                filteredMastersResult = wrappedBooksFromMaster.filter { it.bookMaster.opnameStatus == OpnameStatus.NOT_SCANNED && !it.bookMaster.isNewOrUnexpected }
                filteredUnexpectedResult = emptyList()
            }
            app.getString(R.string.filter_misplaced) -> {
                filteredMastersResult = wrappedBooksFromMaster.filter { it.bookMaster.opnameStatus == OpnameStatus.FOUND_WRONG_LOCATION }
                filteredUnexpectedResult = emptyList()
            }
            app.getString(R.string.filter_new_or_unexpected) -> {
                filteredMastersResult = wrappedBooksFromMaster.filter { it.bookMaster.isNewOrUnexpected || it.bookMaster.opnameStatus == OpnameStatus.NEW_ITEM }
                // Untuk filter ini, tampilkan semua temporary unexpected items. `filteredUnexpectedResult` sudah benar.
            }
            app.getString(R.string.filter_missing_final) -> {
                filteredMastersResult = wrappedBooksFromMaster.filter { it.bookMaster.opnameStatus == OpnameStatus.MISSING && !it.bookMaster.isNewOrUnexpected }
                filteredUnexpectedResult = emptyList()
            }
            else -> {
                Log.w("StockOpnameViewModel", "Unknown filter type: $filter, showing all.")
            }
        }
        val sortedMasters = filteredMastersResult.sortedBy { it.bookMaster.title }
        val sortedUnexpected = filteredUnexpectedResult.sortedBy { it.titleMaster }

        return Pair(sortedMasters, sortedUnexpected)
    }

    fun markSelectedMissing(bookIds: List<Long>) {
        if (bookIds.isEmpty()) return
        viewModelScope.launch {
            val currentTimestamp = System.currentTimeMillis()
            try {
                bookRepository.updateOpnameStatusForBookIds(
                    bookIds = bookIds,
                    newStatus = OpnameStatus.MISSING,
                    timestamp = currentTimestamp,
                    actualLocation = null // Untuk MISSING, lokasi aktual mungkin tidak relevan
                )
                _uiState.update { currentState ->
                    if (currentState is StockOpnameUiState.Success) {
                        currentState.copy(toastMessage = app.getString(R.string.items_marked_as_missing, bookIds.size))
                    } else currentState
                }
            } catch (e: Exception) {
                Log.e("StockOpnameViewModel", "Error marking books as missing", e)
                _uiState.update { currentState ->
                    if (currentState is StockOpnameUiState.Success) {
                        currentState.copy(toastMessage = app.getString(R.string.error_marking_missing, e.message))
                    } else StockOpnameUiState.Error(app.getString(R.string.error_marking_missing, e.message))
                }
            }
            // Data akan refresh otomatis karena allBooksFromRepoInternal mengamati Flow
        }
    }

    fun saveCurrentOpnameSession(onResult: (reportId: Long?, success: Boolean, message: String) -> Unit) {
        val currentStateValue = _uiState.value
        if (currentStateValue !is StockOpnameUiState.Success) {
            Log.w("StockOpnameViewModel", "Invalid state for saving. Current state: $currentStateValue")
            onResult(null, false, app.getString(R.string.error_invalid_session_for_saving))
            return
        }

        if (currentStateValue.isUhfScanning || currentStateValue.isBarcodeScanning) {
            val stopScanMsg = app.getString(R.string.stop_scan_before_saving)
            _uiState.update { if (it is StockOpnameUiState.Success) it.copy(toastMessage = stopScanMsg) else it }
            onResult(null, false, stopScanMsg)
            return
        }

        val userId = _currentUser.value.uid
        val sessionEndTime = System.currentTimeMillis()

        viewModelScope.launch {
            _uiState.update { if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = app.getString(R.string.saving_opname_session), toastMessage = app.getString(R.string.saving_message)) else it }

            val opnameSessionNameToSave = currentStateValue.currentOpnameSessionName
            Log.i("StockOpnameViewModel", "Attempting to save opname session: $opnameSessionNameToSave by user: $userId")

            try {
                // Tandai semua item yang masih NOT_SCANNED sebagai MISSING
                val booksStillNotScanned = currentStateValue.allBooksInCurrentSessionForCalc.filter {
                    it.opnameStatus == OpnameStatus.NOT_SCANNED && !it.isNewOrUnexpected
                }
                if (booksStillNotScanned.isNotEmpty()) {
                    val idsToMarkMissing = booksStillNotScanned.map { it.id }
                    bookRepository.updateOpnameStatusForBookIds(
                        bookIds = idsToMarkMissing,
                        newStatus = OpnameStatus.MISSING,
                        timestamp = sessionEndTime, // Gunakan waktu akhir sesi
                        actualLocation = null
                    )
                    Log.i("StockOpnameViewModel", "${idsToMarkMissing.size} items automatically marked as MISSING before saving report.")
                }

                // Ambil snapshot terbaru dari semua buku setelah potensi update ke MISSING
                val latestBooksFromDb = bookRepository.getAllBookMastersList() // Ambil list sekali saja

                val itemsFromMasterForReport: List<StockOpnameItem> = latestBooksFromDb
                    .filter { book -> book.opnameStatus != OpnameStatus.NOT_SCANNED || book.isNewOrUnexpected } // Filter yang relevan untuk laporan
                    .map { book ->
                        StockOpnameItem(
                            reportId = 0L, // Akan diisi oleh DAO saat insert report
                            rfidTagHexScanned = book.rfidTagHex ?: (app.getString(R.string.master_item_no_rfid_prefix) + (book.itemCode ?: "NO_CODE_${book.id}") + "_" + System.nanoTime().toString().take(6)),
                            tidScanned = book.tid,
                            itemCodeMaster = book.itemCode,
                            titleMaster = book.title,
                            scanTimestamp = book.lastSeenTimestamp ?: sessionEndTime,
                            status = getStatusName(book.opnameStatus),
                            actualLocationIfDifferent = if (book.opnameStatus == OpnameStatus.FOUND_WRONG_LOCATION || book.opnameStatus == OpnameStatus.NEW_ITEM) book.actualScannedLocation else null,
                            expectedLocationMaster = book.locationName,
                            isNewOrUnexpectedItem = book.isNewOrUnexpected || book.opnameStatus == OpnameStatus.NEW_ITEM //NEW_ITEM juga termasuk di sini
                        )
                    }

                // Ambil temporary unexpected items untuk ditambahkan ke laporan
                val temporaryUnexpectedItemsToSave = _temporaryUnexpectedItems.value.map { tempItem ->
                    tempItem.copy( // Pastikan semua field yang relevan untuk laporan diisi
                        scanTimestamp = tempItem.scanTimestamp ?: sessionEndTime, // Jika null, gunakan waktu akhir sesi
                        status = UNKNOWN_ITEM_IN_REPORT_STATUS // Sudah diset saat pembuatan, tapi pastikan lagi
                    )
                }

                val allItemsForReport = itemsFromMasterForReport + temporaryUnexpectedItemsToSave

                if (allItemsForReport.isEmpty()) {
                    Log.w("StockOpnameViewModel", "No items to save in the report for session: $opnameSessionNameToSave")
                    onResult(null, false, app.getString(R.string.error_no_items_to_save))
                    _uiState.update { if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = app.getString(R.string.opname_session_saved_no_items), toastMessage = null) else it }
                    return@launch
                }

                val totalItemsInMasterData = currentStateValue.totalBooksInMaster
                val itemsFoundCorrectLocation = allItemsForReport.count { it.status == OpnameStatus.FOUND_CORRECT_LOCATION.name }
                val itemsFoundWrongLocation = allItemsForReport.count { it.status == OpnameStatus.FOUND_WRONG_LOCATION.name }
                val itemsMissing = allItemsForReport.count { it.status == OpnameStatus.MISSING.name && !it.isNewOrUnexpectedItem }
                val newOrUnexpectedItemsCount = allItemsForReport.count { it.isNewOrUnexpectedItem || it.status == OpnameStatus.NEW_ITEM.name || it.status == UNKNOWN_ITEM_IN_REPORT_STATUS }

                val report = StockOpnameReport(
                    reportName = opnameSessionNameToSave,                 // Diubah dari sessionName
                    startTimeMillis = currentStateValue.sessionStartTimeMillis, // Diubah dari startTime
                    endTimeMillis = sessionEndTime,                       // Diubah dari endTime
                    userId = userId,
                    totalItemsExpected = totalItemsInMasterData,        // Dipetakan ke totalItemsExpected
                    totalItemsFound = itemsFoundCorrectLocation,      // Dipetakan ke totalItemsFound
                    totalItemsMisplaced = itemsFoundWrongLocation,    // Dipetakan ke totalItemsMisplaced
                    totalItemsMissing = itemsMissing,                   // Nama sudah cocok
                    totalItemsNewOrUnexpected = newOrUnexpectedItemsCount // Dipetakan ke totalItemsNewOrUnexpected
                )

                val newReportId = bookRepository.insertStockOpnameReportWithItems(report, allItemsForReport)

                if (newReportId > 0) {
                    val successMsg = app.getString(R.string.opname_session_saved_successfully, opnameSessionNameToSave)
                    Log.i("StockOpnameViewModel", "Opname session '$opnameSessionNameToSave' saved successfully. Report ID: $newReportId")
                    onResult(newReportId, true, successMsg)
                    _uiState.update { if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = successMsg, toastMessage = null) else it }
                    // Pertimbangkan untuk mereset sesi atau navigasi setelah penyimpanan berhasil
                    // Contoh: startNewOpnameSession() // Jika ingin langsung memulai sesi baru setelah simpan
                } else {
                    val errorMsg = app.getString(R.string.error_saving_opname_session_db)
                    Log.e("StockOpnameViewModel", "Failed to save opname session '$opnameSessionNameToSave' to database.")
                    onResult(null, false, errorMsg)
                    _uiState.update { if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = errorMsg, toastMessage = errorMsg) else it }
                }
            } catch (e: Exception) {
                Log.e("StockOpnameViewModel", "Exception while saving opname session: $opnameSessionNameToSave", e)
                val exceptionErrorMsg = app.getString(R.string.error_saving_opname_session_exception, e.localizedMessage ?: "Unknown exception")
                onResult(null, false, exceptionErrorMsg)
                _uiState.update {
                    if (it is StockOpnameUiState.Success) it.copy(lastScanMessage = exceptionErrorMsg, toastMessage = exceptionErrorMsg)
                    else StockOpnameUiState.Error(exceptionErrorMsg)
                }
            }
        }
    }

    fun clearToastMessage() {
        _uiState.update { currentState ->
            if (currentState is StockOpnameUiState.Success) {
                currentState.copy(toastMessage = null)
            } else currentState
        }
    }

    override fun onCleared() {
        super.onCleared()
        sdkManager.releaseResources() // Pastikan SDK dilepas dengan benar
        observerJob?.cancel() // Batalkan job observasi
        Log.d("StockOpnameViewModel", "ViewModel cleared and SDK released.")
    }
}