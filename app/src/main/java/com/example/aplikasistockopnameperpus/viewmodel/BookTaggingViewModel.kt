package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.PairingStatus // <-- IMPORT DITAMBAHKAN
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Status untuk proses tagging
enum class TaggingState {
    IDLE,
    BOOK_FOUND_UNTAGGED,
    BOOK_FOUND_ALREADY_TAGGED,
    AWAITING_TAG_PLACEMENT, // Untuk operasi tulis
    WRITING_EPC,
    READING_TID,
    SAVING_TO_DB,
    PROCESS_SUCCESS,
    PROCESS_FAILED,
    ERROR_BOOK_NOT_FOUND,
    ERROR_SDK,
    ERROR_CONVERSION
}

// object RfidPairingStatus bisa dihapus jika tidak lagi digunakan setelah beralih sepenuhnya ke enum PairingStatus
// object RfidPairingStatus {
// const val UNTAGGED = "BELUM_DITAG"
// const val TAGGED_SUCCESS = "BERHASIL_DITAG"
// }

class BookTaggingViewModel(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val sdkManager: ChainwaySDKManager
) : ViewModel() {

    private val _taggingState = MutableLiveData<TaggingState>(TaggingState.IDLE)
    val taggingState: LiveData<TaggingState> = _taggingState

    private val _currentBook = MutableLiveData<BookMaster?>()
    val currentBook: LiveData<BookMaster?> = _currentBook

    private val _scannedEpcDuringProcess = MutableLiveData<String?>()
    val scannedEpcDuringProcess: LiveData<String?> = _scannedEpcDuringProcess

    private val _statusMessage = MutableLiveData<String?>()
    val statusMessage: LiveData<String?> = _statusMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var targetEpcToWrite: String? = null
    private var currentTidRead: String? = null

    private var sdkOperationJob: Job? = null

    companion object {
        private const val SDK_OPERATION_TIMEOUT_MS = 10000L // 10 detik timeout
        private val TAG = BookTaggingViewModel::class.java.simpleName
    }

    init {
        setupSdkListeners()
    }

    private fun setupSdkListeners() {
        sdkManager.onBarcodeScanned = barScanned@{ barcodeData ->
            if (_isLoading.value == true || (_taggingState.value !in listOf(
                    TaggingState.IDLE,
                    TaggingState.PROCESS_SUCCESS,
                    TaggingState.PROCESS_FAILED,
                    TaggingState.ERROR_BOOK_NOT_FOUND,
                    TaggingState.ERROR_SDK,
                    TaggingState.ERROR_CONVERSION
                ))
            ) {
                _statusMessage.postValue("Proses lain sedang berjalan atau UI belum siap. Harap tunggu atau batalkan.")
                return@barScanned
            }
            _statusMessage.postValue("Barcode discan: $barcodeData")
            searchBookByItemCode(barcodeData)
        }

        sdkManager.onUhfTagScanned = { epc ->
            Log.d(TAG, "SDK onUhfTagScanned: $epc. Current state: ${_taggingState.value}")
            if (_taggingState.value == TaggingState.AWAITING_TAG_PLACEMENT) {
                _scannedEpcDuringProcess.postValue(epc)
            }
        }

        sdkManager.onUhfInventoryFinished = {
            Log.d(TAG, "SDK onUhfInventoryFinished. isLoading: ${_isLoading.value}, state: ${_taggingState.value}")
        }

        sdkManager.onError = { errorMessage ->
            sdkOperationJob?.cancel()
            onErrorOccurred("SDK Error: $errorMessage")
        }

        sdkManager.onTagWriteSuccess = { writtenEpc ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.WRITING_EPC) {
                Log.i(TAG, "TagWriteSuccess: $writtenEpc")
                _scannedEpcDuringProcess.postValue(writtenEpc)
                _statusMessage.postValue("Penulisan EPC $writtenEpc berhasil. Membaca TID...")
                proceedToReadTid(writtenEpc)
            } else {
                Log.w(TAG, "onTagWriteSuccess received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onTagWriteFailed = { error ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.WRITING_EPC) {
                onErrorOccurred("Gagal menulis EPC: $error", TaggingState.PROCESS_FAILED)
            } else {
                Log.w(TAG, "onTagWriteFailed received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onTagReadTidSuccess = { tid, epcOfTagRead ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.READING_TID) {
                Log.i(TAG, "onTagReadTidSuccess: TID=$tid, EPC Terbaca=$epcOfTagRead")
                currentTidRead = tid

                if (targetEpcToWrite != null && epcOfTagRead != null && targetEpcToWrite != epcOfTagRead) {
                    val warningMsg = "Peringatan: TID ($tid) dibaca dari tag dengan EPC berbeda ($epcOfTagRead), padahal target EPC adalah ($targetEpcToWrite)."
                    Log.w(TAG, warningMsg)
                }
                _statusMessage.postValue("Pembacaan TID ($tid) berhasil. Menyimpan data...")
                // Memastikan bookToUpdate adalah non-null sebelum memanggil saveRfidDataToDatabase
                val bookToUpdate = _currentBook.value
                if (bookToUpdate != null && targetEpcToWrite != null) {
                    saveRfidDataToDatabase(bookToUpdate, targetEpcToWrite!!, tid)
                } else {
                    Log.e(TAG, "onTagReadTidSuccess: currentBook or targetEpcToWrite is null. Cannot save.")
                    onErrorOccurred("Data buku tidak lengkap untuk menyimpan RFID.", TaggingState.PROCESS_FAILED)
                }
            } else {
                Log.w(TAG, "onTagReadTidSuccess received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onTagReadTidFailed = { error ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.READING_TID) {
                onErrorOccurred("Gagal membaca TID: $error", TaggingState.PROCESS_FAILED)
            } else {
                Log.w(TAG, "onTagReadTidFailed received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onUhfOperationStopped = {
            Log.d(TAG, "onUhfOperationStopped. Current state: ${_taggingState.value}")
            if (sdkOperationJob?.isActive == true &&
                (_taggingState.value == TaggingState.WRITING_EPC || _taggingState.value == TaggingState.READING_TID)
            ) {
                Log.w(TAG, "UHF operation stopped externally while waiting for write/read callback.")
            }
        }
    }

    private fun onErrorOccurred(message: String, nextState: TaggingState = TaggingState.ERROR_SDK) {
        _isLoading.postValue(false)
        _statusMessage.postValue(message)
        _taggingState.postValue(nextState)
        Log.e(TAG, message)
    }

    fun searchBookByItemCode(itemCode: String) {
        if (itemCode.isBlank()) {
            _statusMessage.value = "Kode item tidak boleh kosong."
            _taggingState.value = TaggingState.IDLE
            return
        }
        clearLocalCacheBeforeSearch()
        _isLoading.value = true
        _statusMessage.value = "Mencari buku dengan kode item: $itemCode..."

        viewModelScope.launch {
            try {
                val book = bookRepository.getBookByItemCode(itemCode)
                _currentBook.postValue(book)
                if (book != null) {
                    targetEpcToWrite = convertItemCodeToEpcHex(itemCode)
                    if (targetEpcToWrite == null) {
                        onErrorOccurred("Gagal mengkonversi kode item ke format EPC. Periksa format kode item.", TaggingState.ERROR_CONVERSION)
                        Log.e(TAG, "convertItemCodeToEpcHex returned null for itemCode: $itemCode")
                    } else {
                        // Menggunakan enum PairingStatus
                        if (book.rfidTagHex.isNullOrBlank() || book.pairingStatus == PairingStatus.NOT_PAIRED || book.pairingStatus == PairingStatus.PAIRING_FAILED) {
                            _taggingState.postValue(TaggingState.BOOK_FOUND_UNTAGGED)
                            _statusMessage.postValue("Buku '${book.title}' ditemukan. Siap ditag dengan EPC: $targetEpcToWrite")
                        } else {
                            _taggingState.postValue(TaggingState.BOOK_FOUND_ALREADY_TAGGED)
                            _statusMessage.postValue("Buku '${book.title}' sudah memiliki RFID (${book.rfidTagHex}). Status: ${book.pairingStatus}. Proses ulang akan menimpa dengan EPC: $targetEpcToWrite")
                        }
                    }
                } else {
                    _taggingState.postValue(TaggingState.ERROR_BOOK_NOT_FOUND)
                    _statusMessage.postValue("Buku dengan kode item '$itemCode' tidak ditemukan.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching book by item code '$itemCode': ${e.message}", e)
                onErrorOccurred("Error database saat mencari buku: ${e.message}", TaggingState.PROCESS_FAILED)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun startTaggingProcess() {
        val book = _currentBook.value
        val currentTargetEpc = targetEpcToWrite

        if (book == null) {
            _statusMessage.value = "Tidak ada buku yang dipilih untuk ditag."
            _taggingState.value = TaggingState.IDLE
            return
        }
        if (currentTargetEpc == null) {
            onErrorOccurred("Target EPC tidak valid (kemungkinan gagal konversi dari kode item).", TaggingState.ERROR_CONVERSION)
            return
        }
        if (!sdkManager.isDeviceReady("uhf")) {
            onErrorOccurred("Reader UHF tidak siap atau tidak terhubung.")
            return
        }

        if (sdkManager.isUhfDeviceScanning) {
            _statusMessage.value = "Reader sedang sibuk. Menghentikan operasi sebelumnya..."
            sdkManager.stopUhfOperation()
            viewModelScope.launch {
                delay(700)
                if (!sdkManager.isUhfDeviceScanning) {
                    initiateWriteOperation(currentTargetEpc)
                } else {
                    onErrorOccurred("Gagal menghentikan operasi UHF sebelumnya. Coba lagi.")
                }
            }
            return
        }
        initiateWriteOperation(currentTargetEpc)
    }

    private fun initiateWriteOperation(epcToWrite: String) {
        _isLoading.value = true
        _scannedEpcDuringProcess.value = null
        currentTidRead = null
        _taggingState.value = TaggingState.AWAITING_TAG_PLACEMENT
        _statusMessage.value = "Dekatkan tag ke reader untuk menulis EPC: $epcToWrite"

        sdkOperationJob = viewModelScope.launch {
            _taggingState.postValue(TaggingState.WRITING_EPC)
            _statusMessage.postValue("Menulis EPC: $epcToWrite ke tag...")

            val writeSuccessful = withTimeoutOrNull(SDK_OPERATION_TIMEOUT_MS) {
                sdkManager.writeUhfTag(epcToWrite = epcToWrite)
                var successFromCallback = false
                while (isActive && _taggingState.value == TaggingState.WRITING_EPC) {
                    if (_taggingState.value == TaggingState.READING_TID) {
                        successFromCallback = true
                        break
                    }
                    if (_taggingState.value == TaggingState.PROCESS_FAILED || _taggingState.value == TaggingState.ERROR_SDK) {
                        successFromCallback = false
                        break
                    }
                    delay(100)
                }
                if (isActive) successFromCallback else false
            }

            if (writeSuccessful == null && _taggingState.value == TaggingState.WRITING_EPC) {
                Log.w(TAG, "Write operation timed out for EPC: $epcToWrite")
                sdkManager.stopUhfOperation()
                onErrorOccurred("Operasi penulisan tag timeout.", TaggingState.PROCESS_FAILED)
            }
        }
    }

    private fun proceedToReadTid(writtenEpc: String) {
        _taggingState.value = TaggingState.READING_TID
        _statusMessage.value = "Membaca TID dari tag $writtenEpc..."

        sdkOperationJob = viewModelScope.launch {
            val readSuccessful = withTimeoutOrNull(SDK_OPERATION_TIMEOUT_MS) {
                sdkManager.readUhfTagTid(targetEpc = writtenEpc)
                var successFromCallback = false
                while (isActive && _taggingState.value == TaggingState.READING_TID) {
                    if (_taggingState.value == TaggingState.SAVING_TO_DB) {
                        successFromCallback = true
                        break
                    }
                    if (_taggingState.value == TaggingState.PROCESS_FAILED || _taggingState.value == TaggingState.ERROR_SDK) {
                        successFromCallback = false
                        break
                    }
                    delay(100)
                }
                if (isActive) successFromCallback else false
            }

            if (readSuccessful == null && _taggingState.value == TaggingState.READING_TID) {
                Log.w(TAG, "Read TID operation timed out for EPC: $writtenEpc")
                sdkManager.stopUhfOperation()
                onErrorOccurred("Operasi pembacaan TID timeout.", TaggingState.PROCESS_FAILED)
            }
        }
    }

    private fun saveRfidDataToDatabase(bookToUpdate: BookMaster, epc: String, tid: String) {
        // Pengecekan null untuk bookToUpdate sudah dilakukan di onTagReadTidSuccess sebelum memanggil ini
        _taggingState.value = TaggingState.SAVING_TO_DB
        _statusMessage.value = "Menyimpan data pairing ke database..." // Pesan disesuaikan
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // PENYESUAIAN DI SINI:
                // Memanggil updatePairingDetailsForBook, yang TIDAK menyimpan epc baru.
                // EPC (rfidTagHex) di BookMaster akan tetap seperti sebelumnya dari database.
                bookRepository.updatePairingDetailsForBook( // <--- PERUBAHAN DI SINI
                    itemCode = bookToUpdate.itemCode,
                    // newRfidTagHex = epc, // Parameter ini tidak ada di updatePairingDetailsForBook
                    newTid = tid,
                    newPairingStatus = PairingStatus.PAIRED_WRITE_SUCCESS, // Atau status yang lebih sesuai jika EPC tidak ditulis di sini
                    pairingTimestamp = System.currentTimeMillis()
                )

                // Ambil buku yang diperbarui untuk memastikan UI konsisten
                val updatedBook = bookRepository.getBookByItemCode(bookToUpdate.itemCode)
                _currentBook.postValue(updatedBook)

                _isLoading.postValue(false)
                // Pesan disesuaikan karena EPC mungkin tidak "baru" disimpan jika menggunakan fungsi ini
                _statusMessage.postValue("Data pairing (TID: $tid) untuk buku '${updatedBook?.title ?: bookToUpdate.title}' berhasil disimpan!")
                _taggingState.postValue(TaggingState.PROCESS_SUCCESS)
                Log.i(TAG, "Pairing details saved for itemCode: ${bookToUpdate.itemCode}, TID: $tid, Status: ${PairingStatus.PAIRED_WRITE_SUCCESS}. EPC in DB remains: ${updatedBook?.rfidTagHex}")

            } catch (e: Exception) {
                Log.e(TAG, "Error saving pairing details to DB for itemCode ${bookToUpdate.itemCode}: ${e.message}", e)
                // Set status buku ke gagal tulis jika penyimpanan DB gagal
                try {
                    // Jika Anda memiliki fungsi yang hanya update status:
                    // bookRepository.updatePairingStatusOnly(bookToUpdate.itemCode, PairingStatus.PAIRING_FAILED, System.currentTimeMillis())
                    // Atau gunakan yang ada jika rfidTagHex tidak masalah null
                    bookRepository.updatePairingDetailsForBook(
                        itemCode = bookToUpdate.itemCode,
                        newTid = tid, // TID mungkin sudah ada
                        newPairingStatus = PairingStatus.PAIRED_WRITE_FAILED, // Atau status yang lebih spesifik
                        pairingTimestamp = System.currentTimeMillis()
                    )
                } catch (updateEx: Exception) {
                    Log.e(TAG, "Failed to update pairing status to FAILED after DB save error: ${updateEx.message}")
                }
                onErrorOccurred("Gagal menyimpan ke database: ${e.message}", TaggingState.PROCESS_FAILED)
            }
        }
    }

    fun triggerBarcodeScan() {
        if (!sdkManager.isDeviceReady("barcode")) {
            _statusMessage.value = "Scanner barcode tidak siap."
            return
        }
        if (sdkManager.isUhfDeviceScanning || _isLoading.value == true) {
            _statusMessage.value = "Operasi lain sedang berjalan. Harap tunggu."
            return
        }
        _statusMessage.value = "Mengaktifkan scan barcode..."
        sdkManager.startBarcodeScan()
    }

    private fun convertItemCodeToEpcHex(itemCode: String): String? {
        try {
            if (itemCode.isBlank()) {
                Log.e(TAG, "Item code is blank, cannot convert to EPC.")
                return null
            }
            var processedItemCode = itemCode.replace("[^a-zA-Z0-9]".toRegex(), "")
            if (processedItemCode.length > 12) {
                processedItemCode = processedItemCode.substring(0, 12)
            } else if (processedItemCode.length < 12 && processedItemCode.isNotEmpty()) {
                processedItemCode = processedItemCode.padEnd(12, 'F')
            } else if (processedItemCode.isEmpty()){
                Log.e(TAG, "Processed Item code is empty after sanitization: '$itemCode'")
                return null
            }

            var hex = processedItemCode.map { char ->
                Integer.toHexString(char.code).padStart(2, '0')
            }.joinToString("").uppercase()

            if (hex.length < 24) {
                hex = hex.padEnd(24, '0')
            } else if (hex.length > 24) {
                hex = hex.substring(0, 24)
            }

            Log.d(TAG, "Converted itemCode '$itemCode' to EPC HEX: '$hex'")
            if (hex.length != 24) {
                Log.e(TAG, "EPC conversion for '$itemCode' resulted in invalid length: ${hex.length}. Hex: $hex")
                return null
            }
            return hex
        } catch (e: Exception) {
            Log.e(TAG, "Error converting itemCode '$itemCode' to EPC hex: ${e.message}", e)
            return null
        }
    }

    private fun clearLocalCacheBeforeSearch() {
        _currentBook.value = null
        _scannedEpcDuringProcess.value = null
        targetEpcToWrite = null
        currentTidRead = null
    }

    fun clearProcessAndPrepareForNext(resetMessage: String? = null) {
        sdkOperationJob?.cancel()
        sdkManager.stopUhfOperation()
        if (sdkManager.isBarcodeDeviceScanning) {
            sdkManager.stopBarcodeScan()
        }

        clearLocalCacheBeforeSearch()

        _statusMessage.value = resetMessage ?: "Silakan scan atau masukkan kode item buku berikutnya."
        _taggingState.value = TaggingState.IDLE
        _isLoading.value = false
        Log.d(TAG, "Process cleared. Ready for next item.")
    }

    fun cancelCurrentOperation() {
        sdkOperationJob?.cancel()
        sdkManager.stopUhfOperation()

        _isLoading.postValue(false)
        val previousState = when (_taggingState.value) {
            TaggingState.WRITING_EPC, TaggingState.READING_TID, TaggingState.AWAITING_TAG_PLACEMENT -> {
                if (_currentBook.value != null && targetEpcToWrite != null) {
                    val currentBookValue = _currentBook.value!!
                    // Menggunakan enum PairingStatus
                    if (currentBookValue.rfidTagHex.isNullOrBlank() || currentBookValue.pairingStatus == PairingStatus.NOT_PAIRED || currentBookValue.pairingStatus == PairingStatus.PAIRING_FAILED) {
                        TaggingState.BOOK_FOUND_UNTAGGED
                    } else {
                        TaggingState.BOOK_FOUND_ALREADY_TAGGED
                    }
                } else {
                    TaggingState.IDLE
                }
            }
            else -> _taggingState.value ?: TaggingState.IDLE
        }
        _taggingState.postValue(previousState)
        _statusMessage.postValue("Operasi dibatalkan. ${if (previousState != TaggingState.IDLE) "Buku saat ini masih dipilih." else "Siap untuk item baru."}")
        Log.i(TAG, "Current operation cancelled by user. State restored to: $previousState")
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "BookTaggingViewModel onCleared")
        clearProcessAndPrepareForNext("ViewModel cleared.")
        sdkManager.onBarcodeScanned = null
        sdkManager.onUhfTagScanned = null
        sdkManager.onUhfInventoryFinished = null
        sdkManager.onError = null
        sdkManager.onTagWriteSuccess = null
        sdkManager.onTagWriteFailed = null
        sdkManager.onTagReadTidSuccess = null
        sdkManager.onTagReadTidFailed = null
        sdkManager.onUhfOperationStopped = null
        // Pertimbangkan untuk memanggil sdkManager.releaseResources() jika ada dan diperlukan.
    }
}
