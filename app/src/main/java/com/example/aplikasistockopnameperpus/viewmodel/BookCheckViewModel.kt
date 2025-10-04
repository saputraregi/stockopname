package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.model.ScanMethod
import com.example.aplikasistockopnameperpus.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class BookCheckUiState {
    data object Idle : BookCheckUiState()
    data object Searching : BookCheckUiState()
    data class BookFound(val book: BookMaster) : BookCheckUiState()
    data class BookNotFound(val searchedIdentifier: String) : BookCheckUiState()
    data class Error(val message: String) : BookCheckUiState()
}

class BookCheckViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val sdkManager: ChainwaySDKManager
) : AndroidViewModel(application) {

    private val _uiState = MutableLiveData<BookCheckUiState>(BookCheckUiState.Idle)
    val uiState: LiveData<BookCheckUiState> = _uiState

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _isUhfScanningForCheckActive = MutableLiveData(false)
    val isUhfScanningForCheckActive: LiveData<Boolean> = _isUhfScanningForCheckActive

    private val _isBarcodeScanningForCheckActive = MutableLiveData(false)
    val isBarcodeScanningForCheckActive: LiveData<Boolean> = _isBarcodeScanningForCheckActive

    private var currentActiveScanMethod: ScanMethod? = null

    private val _isUhfModeSelectedByUi = MutableLiveData(false) // false = Barcode, true = UHF
    val isUhfModeSelectedByUi: LiveData<Boolean> = _isUhfModeSelectedByUi

    private var singleUhfScanTimeoutJob: Job? = null
    private val SINGLE_SCAN_TIMEOUT_MS = 7000L

    companion object {
        private const val TAG = "BookCheckViewModel"
    }

    init {
        _statusMessage.value = getApplication<Application>().getString(R.string.status_ready_to_scan_book)
        _isUhfModeSelectedByUi.value = false // Default ke Barcode (Switch OFF)
        setupSdkListeners()
    }

    private fun setupSdkListeners() {
        sdkManager.onBarcodeScanned = { barcodeData ->
            Log.d(TAG, "CB: onBarcodeScanned - Data: $barcodeData, Active (VM): ${_isBarcodeScanningForCheckActive.value}, Method: $currentActiveScanMethod")
            // Pengecekan currentActiveScanMethod == ScanMethod.BARCODE masih penting
            if (_isBarcodeScanningForCheckActive.value == true && currentActiveScanMethod == ScanMethod.BARCODE) {
                // TIDAK PERLU sdkManager.stopBarcodeScan() di sini LAGI.
                // ChainwaySDKManager (dalam setupBarcodeListener-nya) seharusnya sudah memanggil
                // stopBarcodeScan() internalnya yang akan stopScan DAN close modul.
                // Jika Anda memanggilnya lagi di sini, itu bisa menyebabkan close() dipanggil dua kali
                // atau mencoba stop pada modul yang sudah distop/diclose.

                _isBarcodeScanningForCheckActive.postValue(false)
                val methodForProcessing = currentActiveScanMethod
                currentActiveScanMethod = null

                _statusMessage.postValue(getApplication<Application>().getString(R.string.status_processing_barcode, barcodeData))
                processIdentifier(barcodeData, methodForProcessing ?: ScanMethod.BARCODE)
            } else {
                Log.w(TAG, "BookCheck: Barcode scan received but ViewModel not in correct active scanning state (Active: ${_isBarcodeScanningForCheckActive.value}, Method: $currentActiveScanMethod).")
            }
        }

        sdkManager.onSingleUhfTagEpcRead = { epc, _ ->
            Log.d(TAG, "CB: onSingleUhfTagEpcRead - EPC: $epc, Active: ${_isUhfScanningForCheckActive.value}, Method: $currentActiveScanMethod")
            if (_isUhfScanningForCheckActive.value == true && currentActiveScanMethod == ScanMethod.UHF) {
                singleUhfScanTimeoutJob?.cancel()
                // SDK sudah berhenti sendiri (asumsi dari readSingleUhfTagEpcNearby)
                _isUhfScanningForCheckActive.postValue(false) // Update LiveData
                val methodForProcessing = currentActiveScanMethod
                currentActiveScanMethod = null // Bersihkan state internal

                _statusMessage.postValue(getApplication<Application>().getString(R.string.status_processing_epc, epc))
                processIdentifier(epc, methodForProcessing ?: ScanMethod.UHF)
            } else {
                Log.w(TAG, "BookCheck: Single UHF tag received but not in the correct active state.")
            }
        }

        sdkManager.onSingleUhfTagReadFailed = { errorMessage ->
            Log.d(TAG, "CB: onSingleUhfTagReadFailed - Error: $errorMessage, Active: ${_isUhfScanningForCheckActive.value}, Method: $currentActiveScanMethod")
            if (_isUhfScanningForCheckActive.value == true && currentActiveScanMethod == ScanMethod.UHF) {
                singleUhfScanTimeoutJob?.cancel()
                _isUhfScanningForCheckActive.postValue(false)
                currentActiveScanMethod = null
                _uiState.postValue(BookCheckUiState.Error(getApplication<Application>().getString(R.string.status_scan_failed, errorMessage)))
                _statusMessage.postValue(getApplication<Application>().getString(R.string.status_scan_failed, errorMessage))
            }
        }

        sdkManager.onError = { sdkErrorMessage ->
            Log.e(TAG, "CB: onError - Message: $sdkErrorMessage")
            var wasActive = false
            if (_isUhfScanningForCheckActive.value == true) {
                singleUhfScanTimeoutJob?.cancel()
                _isUhfScanningForCheckActive.postValue(false); wasActive = true
            }
            if (_isBarcodeScanningForCheckActive.value == true) {
                _isBarcodeScanningForCheckActive.postValue(false); wasActive = true
            }
            if (wasActive || _uiState.value is BookCheckUiState.Searching) {
                if (currentActiveScanMethod != null) currentActiveScanMethod = null
                _uiState.postValue(BookCheckUiState.Error(getApplication<Application>().getString(R.string.sdk_error_prefix, sdkErrorMessage)))
                _statusMessage.postValue(getApplication<Application>().getString(R.string.sdk_error_prefix, sdkErrorMessage))
            }
        }

        sdkManager.onUhfOperationStopped = {
            Log.d(TAG, "CB: onUhfOperationStopped - Active: ${_isUhfScanningForCheckActive.value}, Method: $currentActiveScanMethod")
            // Ini biasanya untuk inventory mode, tapi bisa juga sebagai fallback jika readSingle tidak bersih.
            if (currentActiveScanMethod == ScanMethod.UHF && _isUhfScanningForCheckActive.value == true) {
                singleUhfScanTimeoutJob?.cancel()
                _isUhfScanningForCheckActive.postValue(false)
                currentActiveScanMethod = null
                if (_uiState.value is BookCheckUiState.Searching) {
                    _uiState.postValue(BookCheckUiState.Idle)
                    _statusMessage.postValue(getApplication<Application>().getString(R.string.status_scan_stopped_or_finished))
                }
            }
        }
    }

    fun setBookCheckScanMode(isUhfSelectedFromUi: Boolean) {
        if (_isUhfModeSelectedByUi.value != isUhfSelectedFromUi) {
            Log.d(TAG, "VM: setBookCheckScanMode - To UHF: $isUhfSelectedFromUi")
            if (isUhfSelectedFromUi) {
                if (_isBarcodeScanningForCheckActive.value == true) {
                    Log.d(TAG, "VM: Mode UI diubah ke UHF saat Barcode aktif. Menghentikan Barcode scan.")
                    stopActiveBarcodeScan(true) // true untuk menandakan user initiated stop/switch
                }
            } else {
                if (_isUhfScanningForCheckActive.value == true) {
                    Log.d(TAG, "VM: Mode UI diubah ke Barcode saat UHF aktif. Menghentikan UHF scan.")
                    stopActiveUhfScan(true) // true untuk menandakan user initiated stop/switch
                }
            }
            _isUhfModeSelectedByUi.value = isUhfSelectedFromUi
        }
    }

    fun toggleSelectedBookCheckScan() {
        Log.d(TAG, "VM: toggleSelectedBookCheckScan - Mode UI UHF: ${_isUhfModeSelectedByUi.value}")
        if (_isUhfModeSelectedByUi.value == true) {
            toggleUhfScanForCheck()
        } else {
            toggleBarcodeScanForCheck()
        }
    }

    // Di BookCheckViewModel.kt
    private fun stopActiveBarcodeScan(userInitiatedStop: Boolean = false) {
        // Periksa flag ViewModel dulu
        if (_isBarcodeScanningForCheckActive.value == true || sdkManager.isBarcodeDeviceScanning) { // Periksa juga flag SDK jika ViewModel false tapi SDK mungkin masih aktif
            Log.d(TAG, "VM: stopActiveBarcodeScan - UserInitiated: $userInitiatedStop. VM Active: ${_isBarcodeScanningForCheckActive.value}, SDK Active: ${sdkManager.isBarcodeDeviceScanning}")

            sdkManager.stopBarcodeScan() // Panggil fungsi SDKManager yang sekarang akan stopScan DAN close

            // Update LiveData ViewModel setelah memanggil SDKManager
            if (_isBarcodeScanningForCheckActive.value == true) {
                _isBarcodeScanningForCheckActive.postValue(false)
            }
            if (currentActiveScanMethod == ScanMethod.BARCODE) {
                currentActiveScanMethod = null
            }

            if (userInitiatedStop && _uiState.value is BookCheckUiState.Searching) {
                Log.d(TAG, "VM: stopActiveBarcodeScan - User stop, UIState was Searching, setting to Idle.")
                // _uiState.value = BookCheckUiState.Idle // Ini akan ditangani oleh clearPreviousResult jika ini adalah bagian dari itu, atau oleh tombol Clear
                // Untuk user stop langsung, bisa set Idle jika tidak ada hasil yang mau ditampilkan.
                // Atau, jika ada hasil sebelumnya dan user stop scan baru, biarkan hasil sebelumnya.
                // Lebih aman untuk membiarkan UI state diatur oleh alur yang lebih besar (misal, tombol clear atau hasil scan baru).
                // Jika user stop saat searching, dan tidak ada hasil, baru kembali ke Idle.
                _uiState.postValue(BookCheckUiState.Idle) // Jika user stop saat searching, kembali ke Idle
                _statusMessage.postValue(getApplication<Application>().getString(R.string.status_scan_stopped_by_user))
            }
            Log.d(TAG, "VM: stopActiveBarcodeScan - Exiting. VM isBarcodeScanning: ${_isBarcodeScanningForCheckActive.value}, SDK isBarcodeDeviceScanning: ${sdkManager.isBarcodeDeviceScanning}")
        } else {
            Log.d(TAG, "VM: stopActiveBarcodeScan called but no barcode scan was considered active by ViewModel or SDK.")
            // Pastikan currentActiveScanMethod juga bersih jika memang tidak ada yang aktif
            if (currentActiveScanMethod == ScanMethod.BARCODE) {
                currentActiveScanMethod = null
            }
        }
    }

    // Lakukan penyesuaian serupa untuk stopActiveUhfScan jika perlu,
// meskipun UHF dengan readSingleUhfTagEpcNearby biasanya lebih bersih dalam hal stop.
    private fun stopActiveUhfScan(userInitiatedStop: Boolean = false) {
        if (_isUhfScanningForCheckActive.value == true) {
            Log.d(TAG, "VM: stopActiveUhfScan - UserInitiated: $userInitiatedStop")
            singleUhfScanTimeoutJob?.cancel()
            sdkManager.stopUhfOperation()
            _isUhfScanningForCheckActive.value = false
            if (currentActiveScanMethod == ScanMethod.UHF) currentActiveScanMethod = null

            if (userInitiatedStop && _uiState.value is BookCheckUiState.Searching) {
                Log.d(TAG, "VM: stopActiveUhfScan - User stop, UIState was Searching, setting to Idle.")
                _uiState.value = BookCheckUiState.Idle
                _statusMessage.value = getApplication<Application>().getString(R.string.status_scan_stopped_by_user)
            }
            Log.d(TAG, "VM: stopActiveUhfScan - Exiting. isUhfScanning: ${_isUhfScanningForCheckActive.value}")
        }
    }

    private fun toggleUhfScanForCheck() {
        Log.d(TAG, "VM: toggleUhfScanForCheck - Currently Active: ${_isUhfScanningForCheckActive.value}")
        if (_isUhfScanningForCheckActive.value == true) {
            stopActiveUhfScan(true) // User menekan "Stop"
        } else {
            // Memulai scan UHF
            clearPreviousResult() // PENTING: Hentikan scan lain (jika ada) dan reset state
            Log.d(TAG, "VM: Ensuring Barcode is stopped before starting UHF. Current SDK Barcode Scanning: ${sdkManager.isBarcodeDeviceScanning}")
            if (sdkManager.isBarcodeDeviceScanning) { // Periksa status SDK langsung
                sdkManager.stopBarcodeScan()
                // Mungkin perlu delay sangat singkat di sini jika stopBarcodeScan asinkron
                // viewModelScope.launch { delay(50) } // HATI-HATI: Hanya jika sangat perlu
            }
            if (!sdkManager.isDeviceReady("uhf")) {
                _statusMessage.value = getApplication<Application>().getString(R.string.reader_uhf_not_ready)
                _uiState.value = BookCheckUiState.Error(getApplication<Application>().getString(R.string.reader_uhf_not_ready))
                return
            }

            Log.d(TAG, "VM: Starting Single UHF Scan")
            sdkManager.readSingleUhfTagEpcNearby()
            _isUhfScanningForCheckActive.value = true
            currentActiveScanMethod = ScanMethod.UHF
            _uiState.value = BookCheckUiState.Searching // SET STATE SEARCHING
            _statusMessage.value = getApplication<Application>().getString(R.string.status_scanning_uhf_for_book_single)

            singleUhfScanTimeoutJob?.cancel()
            singleUhfScanTimeoutJob = viewModelScope.launch {
                delay(SINGLE_SCAN_TIMEOUT_MS)
                if (_isUhfScanningForCheckActive.value == true && currentActiveScanMethod == ScanMethod.UHF) {
                    Log.w(TAG, "Timeout waiting for single UHF tag scan.")
                    // sdkManager.stopUhfOperation(); // Panggil jika readSingle tidak otomatis stop/timeout
                    _isUhfScanningForCheckActive.postValue(false) // Pastikan flag false
                    if(currentActiveScanMethod == ScanMethod.UHF) currentActiveScanMethod = null
                    _uiState.postValue(BookCheckUiState.Error(getApplication<Application>().getString(R.string.status_scan_failed, "Timeout")))
                    _statusMessage.postValue(getApplication<Application>().getString(R.string.status_scan_failed, "Timeout"))
                }
            }
        }
    }

    // Di BookCheckViewModel.kt
    private fun toggleBarcodeScanForCheck() {
        Log.d(TAG, "VM: toggleBarcodeScanForCheck - Currently Active (VM): ${_isBarcodeScanningForCheckActive.value}, SDKManager isScanning: ${sdkManager.isBarcodeDeviceScanning}")

        if (_isBarcodeScanningForCheckActive.value == true) {
            stopActiveBarcodeScan(true) // User menekan "Stop"
        } else {
            clearPreviousResult()

            Log.d(TAG, "VM: Ensuring UHF is stopped before starting Barcode. Current SDK UHF Active: ${sdkManager.isUhfDeviceScanning || sdkManager.isUhfRadarActive}")
            if (sdkManager.isUhfDeviceScanning || sdkManager.isUhfRadarActive) {
                sdkManager.stopUhfOperation()
            }

            // TIDAK PERLU LAGI PENGECEKAN sdkManager.isDeviceReady("barcode") DI SINI.
            // Biarkan sdkManager.startBarcodeScan() yang mencoba open() dan melaporkan kegagalan jika ada.

            Log.d(TAG, "VM: Attempting to start Barcode Scan via SDKManager.")
            val startSuccess = sdkManager.startBarcodeScan() // Panggil dan tangkap hasilnya

            try {
                sdkManager.startBarcodeScan() // Panggil fungsi yang bertipe Unit

                // Jika pemanggilan di atas tidak melempar exception, kita anggap perintah start berhasil dikirim.
                // UI akan diperbarui secara optimis.
                _isBarcodeScanningForCheckActive.postValue(true)
                currentActiveScanMethod = ScanMethod.BARCODE
                _uiState.postValue(BookCheckUiState.Searching)
                _statusMessage.postValue(getApplication<Application>().getString(R.string.status_scanning_barcode_for_book))
                Log.i(TAG, "VM: Barcode scan initiated via SDKManager (optimistic update).")

            } catch (e: Exception) {
                // Jika sdkManager.startBarcodeScan() melempar exception (misalnya, saat open() gagal), tangkap di sini.
                Log.e(TAG, "VM: Exception when calling sdkManager.startBarcodeScan(): ${e.message}", e)
                _isBarcodeScanningForCheckActive.postValue(false)
                if (currentActiveScanMethod == ScanMethod.BARCODE) {
                    currentActiveScanMethod = null
                }
                val errorMessage = getApplication<Application>().getString(R.string.scanner_barcode_failed_to_start)
                _statusMessage.postValue(errorMessage)
                _uiState.postValue(BookCheckUiState.Error(errorMessage))
            }
        }
    }

    private fun processIdentifier(identifier: String, method: ScanMethod?) {
        Log.d(TAG, "VM: processIdentifier - ID: $identifier, Method: $method, Current UIState: ${uiState.value}")
        if (identifier.isBlank() || method == null) {
            _statusMessage.value = getApplication<Application>().getString(R.string.identifier_empty_received)
            if (_uiState.value !is BookCheckUiState.Idle) _uiState.value = BookCheckUiState.Idle // Hanya jika belum Idle
            // Pastikan scan terkait benar-benar berhenti
            if (method == ScanMethod.UHF && _isUhfScanningForCheckActive.value == true) stopActiveUhfScan()
            else if (method == ScanMethod.BARCODE && _isBarcodeScanningForCheckActive.value == true) stopActiveBarcodeScan()
            return
        }

        // _uiState sudah Searching dari fungsi toggle, tidak perlu diubah di sini
        _statusMessage.value = getApplication<Application>().getString(R.string.status_searching_book_with_id, identifier)

        viewModelScope.launch {
            try {
                val book: BookMaster? = when (method) {
                    ScanMethod.UHF -> bookRepository.getBookByRfidTag(identifier)
                    ScanMethod.BARCODE -> bookRepository.getBookByItemCode(identifier)
                }

                if (book != null) {
                    _statusMessage.value = getApplication<Application>().getString(R.string.status_book_found_colon_title, book.title ?: getApplication<Application>().getString(R.string.unknown_title))
                    _uiState.value = BookCheckUiState.BookFound(book) // LANGSUNG KE HASIL
                } else {
                    _statusMessage.value = getApplication<Application>().getString(R.string.status_book_not_found_with_id, identifier)
                    _uiState.value = BookCheckUiState.BookNotFound(identifier) // LANGSUNG KE HASIL
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing identifier '$identifier': ${e.message}", e)
                _statusMessage.value = getApplication<Application>().getString(R.string.error_processing_identifier, e.localizedMessage ?: "Unknown error")
                _uiState.value = BookCheckUiState.Error(e.localizedMessage ?: "Terjadi kesalahan saat memproses")
            }
            Log.d(TAG, "VM: processIdentifier finished. New UIState: ${uiState.value}")
        }
    }

    fun clearPreviousResult() {
        Log.d(TAG, "VM: clearPreviousResult called. Current BarcodeActive: ${_isBarcodeScanningForCheckActive.value}, UhfActive: ${_isUhfScanningForCheckActive.value}")

        // Panggil stopActive... yang akan memanggil sdkManager.stop... (yang juga close/release)
        stopActiveUhfScan(false)    // false karena ini dipanggil internal, bukan langsung dari aksi "Clear" UI
        stopActiveBarcodeScan(false) // false karena ini dipanggil internal

        currentActiveScanMethod = null

        // Selalu kembali ke Idle jika clearPreviousResult dipanggil,
        // karena tujuannya adalah membersihkan state untuk operasi berikutnya.
        _uiState.postValue(BookCheckUiState.Idle)
        _statusMessage.postValue(getApplication<Application>().getString(R.string.status_ready_to_scan_book))
        Log.d(TAG, "VM: clearPreviousResult finished. UIState: ${uiState.value}, BarcodeScanning: ${_isBarcodeScanningForCheckActive.value}, UhfScanning: ${_isUhfScanningForCheckActive.value}")
    }

    fun formatDisplayDate(timestamp: Long?): String {
        if (timestamp == null || timestamp == 0L) return getApplication<Application>().getString(R.string.text_not_available_short)
        return try {
            val sdf = SimpleDateFormat(Constants.DISPLAY_DATE_TIME_FORMAT, Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting date for BookCheck: $timestamp", e)
            getApplication<Application>().getString(R.string.text_not_available_short)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "VM: onCleared.")
        singleUhfScanTimeoutJob?.cancel()
        stopActiveUhfScan() // Tidak perlu userInitiatedStop true di sini
        stopActiveBarcodeScan()
        // SDK listener akan di-null-kan jika SDKManager dikelola dengan benar
    }
}
