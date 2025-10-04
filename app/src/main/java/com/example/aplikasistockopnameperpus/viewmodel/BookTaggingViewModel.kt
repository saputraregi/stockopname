package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class TaggingState {
    IDLE,
    BOOK_SEARCHING,
    BOOK_FOUND_UNTAGGED,
    BOOK_FOUND_ALREADY_TAGGED,
    READING_INITIAL_EPC,       // State untuk membaca EPC awal dari tag terdekat
    READING_TID_WITH_FILTER,   // State untuk membaca TID menggunakan EPC yang sudah diketahui sebagai filter
    // READING_EPC_FOR_CONFIRMATION, // Bisa dipertimbangkan jika ada alur retry spesifik, untuk sekarang kita coba sederhanakan
    EPC_CONFIRMED_AWAITING_WRITE,
    WRITING_EPC,
    SAVING_TO_DB,
    PROCESS_SUCCESS,
    PROCESS_FAILED,
    ERROR_BOOK_NOT_FOUND,
    ERROR_SDK,
    ERROR_CONVERSION
}

class BookTaggingViewModel(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val sdkManager: ChainwaySDKManager
) : ViewModel() {

    private val _taggingState = MutableLiveData<TaggingState>(TaggingState.IDLE)
    val taggingState: LiveData<TaggingState> = _taggingState

    private val _currentBook = MutableLiveData<BookMaster?>()
    val currentBook: LiveData<BookMaster?> = _currentBook

    private val _displayedEpc = MutableLiveData<String?>()
    val displayedEpc: LiveData<String?> = _displayedEpc

    private val _displayedTid = MutableLiveData<String?>()
    val displayedTid: LiveData<String?> = _displayedTid

    private val _statusMessage = MutableLiveData<String?>()
    val statusMessage: LiveData<String?> = _statusMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var targetEpcHexToWrite: String? = null
    private var currentTidReadFromTag: String? = null // TID yang berhasil dibaca dari tag
    private var currentEpcReadFromTagForConfirmation: String? = null // EPC yang berhasil dibaca dari tag (EPC awal)

    private var sdkOperationJob: Job? = null

    companion object {
        private const val SDK_OPERATION_TIMEOUT_MS = 10000L // 10 detik timeout
        private val TAG = BookTaggingViewModel::class.java.simpleName
    }

    init {
        Log.d(TAG, "BookTaggingViewModel initialized")
        setupSdkListeners()
    }

    private fun setupSdkListeners() {
        // Di dalam setupSdkListeners()
        sdkManager.onBarcodeScanned = barScanned@{ barcodeData ->
            Log.d(TAG, "VIEWMODEL CB: onBarcodeScanned RECEIVED: $barcodeData. Current isLoading: ${_isLoading.value}, Current TaggingState: ${_taggingState.value}")

            // Kondisi utama: apakah aplikasi siap menerima input barcode baru?
            val canProcessBarcode = _taggingState.value in listOf(
                TaggingState.IDLE,
                TaggingState.PROCESS_SUCCESS,
                TaggingState.PROCESS_FAILED,
                TaggingState.ERROR_BOOK_NOT_FOUND,
                TaggingState.ERROR_SDK,
                TaggingState.ERROR_CONVERSION // Tambahkan jika relevan untuk bisa scan ulang
            )

            // Pengecekan isLoading mungkin lebih relevan untuk operasi yang *sedang berjalan aktif*
            // seperti pembacaan RFID atau penulisan. Untuk barcode scan, kita mungkin ingin
            // mengizinkannya jika state_nya valid, lalu searchBookByItemCode akan set isLoading-nya sendiri.
            if (!canProcessBarcode) {
                _statusMessage.postValue("Proses lain sedang berjalan atau state tidak valid. Harap tunggu atau batalkan.")
                Log.w(TAG, "Barcode scan ($barcodeData) ignored due to state: ${_taggingState.value} or loading: ${_isLoading.value}")
                // Jika Anda menghentikan scan barcode di SDK Manager setelah satu kali pindaian,
                // tidak perlu stop di sini. Jika scan barcode kontinu, mungkin perlu:
                // sdkManager.stopBarcodeScan()
                return@barScanned
            }

            // Jika sampai di sini, kita siap memproses barcode.
            // `searchBookByItemCode` akan mengatur _isLoading dan _taggingState yang sesuai.
            _statusMessage.postValue("Barcode discan: $barcodeData") // Atau pindahkan ini ke dalam searchBookByItemCode
            searchBookByItemCode(barcodeData)
        }


        // Callback setelah membaca EPC awal (dari readSingleUhfTagEpcNearby)
        sdkManager.onSingleUhfTagEpcRead = onSingleUhfTagEpcRead@{ epcRead, tidAssociatedWithEpc ->
            sdkOperationJob?.cancel()

            if (_taggingState.value == TaggingState.READING_INITIAL_EPC) {
                Log.i(TAG, "SDK CB: Initial EPC Read Success. EPC='$epcRead'. TID (jika ada dari EPC read)='$tidAssociatedWithEpc'")
                _displayedEpc.postValue(epcRead)
                currentEpcReadFromTagForConfirmation = epcRead // Simpan EPC ini sebagai EPC yang ada di tag

                if (epcRead.isNotBlank()) {
                    // Lanjutkan membaca TID menggunakan EPC ini sebagai filter
                    initiateTidReadWithFilter(epcRead)
                } else {
                    onErrorOccurred("EPC awal yang dibaca kosong.", TaggingState.PROCESS_FAILED)
                }
            }
            // else if (_taggingState.value == TaggingState.READING_EPC_FOR_CONFIRMATION) {
            // Logika ini mungkin tidak lagi sentral jika alur utama sudah mencakup konfirmasi EPC
            // }
            else {
                Log.w(TAG, "SDK CB: onSingleUhfTagEpcRead received in unexpected state: ${_taggingState.value}")
            }
        }

        // Callback jika GAGAL membaca EPC awal
        sdkManager.onSingleUhfTagReadFailed = { error ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.READING_INITIAL_EPC) {
                onErrorOccurred("Gagal membaca EPC awal dari tag: $error", TaggingState.PROCESS_FAILED)
            }
            // else if (_taggingState.value == TaggingState.READING_EPC_FOR_CONFIRMATION) { ... }
            else {
                Log.w(TAG, "SDK CB: onSingleUhfTagReadFailed received in unexpected state: ${_taggingState.value}")
            }
        }

        // Callback setelah membaca TID dengan filter EPC (dari readTidUsingEpcFilter)
        sdkManager.onTagReadTidSuccess = onTagReadTidSuccess@{ tid, epcUsedAsFilter ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.READING_TID_WITH_FILTER) {
                Log.i(TAG, "SDK CB: Filtered TID Read Success. TID=$tid, Filtered using EPC='$epcUsedAsFilter'")
                currentTidReadFromTag = tid
                _displayedTid.postValue(tid)

                val epcActuallyOnTag = currentEpcReadFromTagForConfirmation // Ini adalah EPC yang dibaca di tahap READING_INITIAL_EPC
                if (epcActuallyOnTag.isNullOrBlank()) {
                    onErrorOccurred("Kesalahan internal: EPC awal tidak ditemukan untuk konfirmasi.", TaggingState.PROCESS_FAILED)
                    return@onTagReadTidSuccess
                }

                _isLoading.postValue(false)
                promptUserForWriteConfirmation(tid, epcActuallyOnTag)
            } else {
                Log.w(TAG, "SDK CB: onTagReadTidSuccess (filtered) received in unexpected state: ${_taggingState.value}")
            }
        }

        // Callback jika GAGAL membaca TID dengan filter EPC
        sdkManager.onTagReadTidFailed = { error ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.READING_TID_WITH_FILTER) {
                onErrorOccurred("Gagal membaca TID (filter EPC): $error", TaggingState.PROCESS_FAILED)
            } else {
                Log.w(TAG, "SDK CB: onTagReadTidFailed (filtered) received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onTagWriteSuccess = { writtenEpc ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.WRITING_EPC) {
                Log.i(TAG, "SDK CB: Tag Write Success. Written EPC: $writtenEpc. Associated TID: $currentTidReadFromTag")
                _displayedEpc.postValue(writtenEpc) // Update displayed EPC dengan yang baru ditulis

                val bookToUpdate = _currentBook.value
                val tidToSave = currentTidReadFromTag
                if (bookToUpdate != null && tidToSave != null) {
                    saveRfidDataToDatabase(bookToUpdate, writtenEpc, tidToSave)
                } else {
                    Log.e(TAG, "onTagWriteSuccess: Data tidak lengkap untuk menyimpan. Book: $bookToUpdate, TID: $tidToSave")
                    onErrorOccurred("Data buku atau TID tidak lengkap untuk penyimpanan.", TaggingState.PROCESS_FAILED)
                }
            } else {
                Log.w(TAG, "SDK CB: onTagWriteSuccess received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onTagWriteFailed = { error ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.WRITING_EPC) {
                onErrorOccurred("Gagal menulis EPC ke tag: $error", TaggingState.PROCESS_FAILED)
            } else {
                Log.w(TAG, "SDK CB: onTagWriteFailed received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onError = { errorMessage ->
            sdkOperationJob?.cancel()
            val currentState = _taggingState.value
            // Tentukan state berikutnya berdasarkan state saat ini jika terjadi error SDK umum
            val nextStateOnError = when (currentState) {
                TaggingState.READING_INITIAL_EPC,
                TaggingState.READING_TID_WITH_FILTER,
                TaggingState.WRITING_EPC,
                TaggingState.BOOK_SEARCHING,
                TaggingState.SAVING_TO_DB -> TaggingState.PROCESS_FAILED
                else -> TaggingState.ERROR_SDK
            }
            if (currentState != TaggingState.PROCESS_FAILED && currentState != TaggingState.ERROR_SDK) {
                onErrorOccurred("SDK Error: $errorMessage", nextStateOnError)
            } else {
                Log.w(TAG, "SDK onError: Error '$errorMessage' diterima, tapi state sudah $currentState.")
                _statusMessage.postValue("SDK Error: $errorMessage (State: $currentState)")
            }
        }

        sdkManager.onUhfOperationStopped = {
            Log.d(TAG, "SDK CB: onUhfOperationStopped. Current state: ${_taggingState.value}")
            if (_taggingState.value in listOf(
                    TaggingState.READING_INITIAL_EPC,
                    TaggingState.READING_TID_WITH_FILTER,
                    TaggingState.WRITING_EPC)) {
                if (_isLoading.value == true) {
                    Log.w(TAG, "Operasi UHF dihentikan saat ViewModel masih menunggu hasil (state: ${_taggingState.value}).")
                    // Biarkan timeout handling yang ada di fungsi initiateXXX yang meng-cover ini,
                    // karena stopUhfOperation juga dipanggil oleh onErrorOccurred.
                }
            }
        }
    }

    private fun onErrorOccurred(message: String, nextState: TaggingState = TaggingState.PROCESS_FAILED) {
        sdkOperationJob?.cancel()
        sdkManager.stopUhfOperation()

        _isLoading.postValue(false)
        _statusMessage.postValue(message)
        _taggingState.postValue(nextState)
        Log.e(TAG, "Error Occurred: $message, Next State: $nextState")
    }

    fun searchBookByItemCode(itemCode: String) {
        if (itemCode.isBlank()) { /* ... */ return }
        clearLocalCacheBeforeSearch()
        _taggingState.value = TaggingState.BOOK_SEARCHING
        _isLoading.value = true
        _statusMessage.value = "Mencari buku dengan kode item: $itemCode..."

        viewModelScope.launch {
            try {
                val book = bookRepository.getBookByItemCode(itemCode)
                _currentBook.postValue(book)
                if (book != null) {
                    targetEpcHexToWrite = convertItemCodeToEpcHex(itemCode)
                    if (targetEpcHexToWrite == null) {
                        onErrorOccurred("Gagal konversi kode item ke EPC.", TaggingState.ERROR_CONVERSION)
                    } else {
                        _statusMessage.postValue("Buku '${book.title}' ditemukan. Target EPC: $targetEpcHexToWrite")
                        if (book.rfidTagHex.isNullOrBlank() || book.pairingStatus == PairingStatus.NOT_PAIRED || book.pairingStatus == PairingStatus.PAIRING_FAILED) {
                            _taggingState.postValue(TaggingState.BOOK_FOUND_UNTAGGED)
                            _statusMessage.postValue("Buku belum di-tag. Klik 'Mulai Proses Tag'.")
                        } else {
                            _taggingState.postValue(TaggingState.BOOK_FOUND_ALREADY_TAGGED)
                            _statusMessage.postValue("Buku sudah di-tag (${book.rfidTagHex}). Klik 'Proses Ulang Tag'.")
                        }
                    }
                } else {
                    onErrorOccurred("Buku dengan kode item '$itemCode' tidak ditemukan.", TaggingState.ERROR_BOOK_NOT_FOUND)
                }
            } catch (e: Exception) {
                onErrorOccurred("Error database: ${e.message}", TaggingState.PROCESS_FAILED)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun userRequestsStartTaggingProcess() {
        val book = _currentBook.value
        if (book == null || targetEpcHexToWrite == null) {
            onErrorOccurred("Buku atau target EPC belum siap.", TaggingState.IDLE)
            return
        }
        if (_taggingState.value !in listOf(TaggingState.BOOK_FOUND_UNTAGGED, TaggingState.BOOK_FOUND_ALREADY_TAGGED)) {
            _statusMessage.postValue("State tidak valid untuk memulai.")
            return
        }
        if (!sdkManager.isDeviceReady("uhf")) {
            onErrorOccurred("Reader UHF tidak siap.", TaggingState.ERROR_SDK)
            return
        }
        if (sdkManager.isUhfDeviceScanning || sdkManager.isUhfRadarActive) { // Cek juga radar
            _statusMessage.postValue("Reader sedang sibuk. Hentikan operasi UHF lain.")
            return
        }
        initiateInitialEpcRead() // Mulai dengan membaca EPC awal
    }

    private fun initiateInitialEpcRead() {
        if (!sdkManager.isDeviceReady("uhf")) {
            onErrorOccurred("Reader UHF tidak siap.", TaggingState.ERROR_SDK)
            return
        }
        _isLoading.value = true
        currentTidReadFromTag = null
        currentEpcReadFromTagForConfirmation = null
        _displayedTid.value = null
        _displayedEpc.value = null // EPC akan diisi oleh callback
        _taggingState.value = TaggingState.READING_INITIAL_EPC
        _statusMessage.value = "Dekatkan tag ke reader untuk dibaca EPC awalnya..."

        sdkOperationJob = viewModelScope.launch {
            sdkManager.readSingleUhfTagEpcNearby() // Akan memicu onSingleUhfTagEpcRead atau onSingleUhfTagReadFailed

            val success = withTimeoutOrNull(SDK_OPERATION_TIMEOUT_MS) {
                while (isActive && _taggingState.value == TaggingState.READING_INITIAL_EPC) {
                    delay(100)
                }
                _taggingState.value != TaggingState.READING_INITIAL_EPC
            }
            if (success == null && _taggingState.value == TaggingState.READING_INITIAL_EPC) {
                onErrorOccurred("Timeout membaca EPC awal.", TaggingState.PROCESS_FAILED)
            } else if (success == false && _taggingState.value == TaggingState.READING_INITIAL_EPC) {
                onErrorOccurred("Gagal membaca EPC awal (operasi tak selesai).", TaggingState.PROCESS_FAILED)
            }
        }
    }

    private fun initiateTidReadWithFilter(epcToFilter: String) {
        if (!sdkManager.isDeviceReady("uhf")) {
            onErrorOccurred("Reader UHF tidak siap.", TaggingState.ERROR_SDK)
            return
        }
        _isLoading.value = true
        // _displayedTid akan diisi oleh callback
        _taggingState.value = TaggingState.READING_TID_WITH_FILTER
        _statusMessage.value = "Membaca TID dari tag (EPC: $epcToFilter)..."

        sdkOperationJob = viewModelScope.launch {
            // Asumsi Anda punya fungsi ini di SDKManager yang memanggil readData dengan filter EPC untuk TID Bank
            sdkManager.readTidUsingEpcFilter(epcToFilterHex = epcToFilter)

            val success = withTimeoutOrNull(SDK_OPERATION_TIMEOUT_MS) {
                while (isActive && _taggingState.value == TaggingState.READING_TID_WITH_FILTER) {
                    delay(100)
                }
                _taggingState.value != TaggingState.READING_TID_WITH_FILTER
            }
            if (success == null && _taggingState.value == TaggingState.READING_TID_WITH_FILTER) {
                onErrorOccurred("Timeout membaca TID (filter).", TaggingState.PROCESS_FAILED)
            } else if (success == false && _taggingState.value == TaggingState.READING_TID_WITH_FILTER) {
                onErrorOccurred("Gagal membaca TID (filter, operasi tak selesai).", TaggingState.PROCESS_FAILED)
            }
        }
    }

    // Fungsi initiateTidRead() LAMA (yang menggunakan readTidFromNearbyTag) DIHAPUS atau TIDAK DIGUNAKAN LAGI untuk alur utama.
    // Fungsi initiateEpcReadForConfirmation() LAMA juga mungkin tidak lagi sentral.

    private fun promptUserForWriteConfirmation(tidFromTag: String, epcCurrentlyOnTag: String) {
        val book = _currentBook.value
        val targetEpc = targetEpcHexToWrite
        if (book == null || targetEpc == null) {
            onErrorOccurred("Data tidak lengkap untuk konfirmasi penulisan.", TaggingState.PROCESS_FAILED)
            return
        }

        // currentEpcReadFromTagForConfirmation sudah di-set saat EPC awal dibaca.
        // epcCurrentlyOnTag yang diterima di sini adalah EPC yang sama.
        // _displayedEpc juga sudah diupdate dengan epcCurrentlyOnTag.
        // _displayedTid sudah diupdate dengan tidFromTag.

        _taggingState.postValue(TaggingState.EPC_CONFIRMED_AWAITING_WRITE)
        val message = StringBuilder()
        message.append("KONFIRMASI PENULISAN TAG:\n")
        message.append("-------------------------------------\n")
        message.append("Buku: ${book.title} (IC: ${book.itemCode})\n\n")
        message.append("Tag RFID Terdeteksi:\n")
        message.append("  TID: $tidFromTag\n")
        message.append("  EPC Saat Ini di Tag: $epcCurrentlyOnTag\n\n") // Ini adalah currentEpcReadFromTagForConfirmation
        message.append("Akan Ditulis dengan EPC Baru:\n")
        message.append("  $targetEpc\n") // Ini adalah targetEpcHexToWrite
        message.append("-------------------------------------\n")

        if (epcCurrentlyOnTag.isNotBlank() && epcCurrentlyOnTag.equals(targetEpc, ignoreCase = true)) {
            message.append("PERHATIAN: EPC tag SAMA dengan target. Tetap tulis ulang?\n\n")
        } else if (epcCurrentlyOnTag.isNotBlank() && !book.rfidTagHex.isNullOrBlank() && !epcCurrentlyOnTag.equals(book.rfidTagHex, ignoreCase = true)) {
            message.append("PERHATIAN: EPC tag ($epcCurrentlyOnTag) BEDA dengan di DB (${book.rfidTagHex}). Tag salah?\n\n")
        } else if (epcCurrentlyOnTag.isBlank() && !book.rfidTagHex.isNullOrBlank()) {
            message.append("INFO: Tag tidak ber-EPC, tapi buku punya data EPC (${book.rfidTagHex}) di DB.\n\n")
        }
        message.append("Lanjutkan Penulisan?")
        _statusMessage.postValue(message.toString())
        _isLoading.postValue(false) // Hentikan loading setelah info siap ditampilkan
    }

    fun userConfirmsWriteOperation() {
        if (_taggingState.value != TaggingState.EPC_CONFIRMED_AWAITING_WRITE) {
            _statusMessage.postValue("State tidak valid untuk memulai penulisan.")
            return
        }
        val finalEpcToWrite = targetEpcHexToWrite
        val tidOfTag = currentTidReadFromTag

        if (finalEpcToWrite == null || tidOfTag == null) {
            onErrorOccurred("EPC target atau TID tag belum siap untuk penulisan.", TaggingState.PROCESS_FAILED)
            return
        }
        Log.i(TAG, "Pengguna mengonfirmasi penulisan. Target EPC: $finalEpcToWrite untuk tag TID: $tidOfTag")
        // Untuk currentEpcFilterOnTag, kita bisa gunakan currentEpcReadFromTagForConfirmation
        // jika SDK Manager Anda memerlukan EPC lama sebagai filter saat menulis (opsional).
        initiateWriteEpcToTag(finalEpcToWrite, tidOfTag, currentEpcReadFromTagForConfirmation)
    }

    private fun initiateWriteEpcToTag(epcHexTarget: String, tidOfTagToVerify: String, currentEpcFilterOnTag: String?) {
        if (!sdkManager.isDeviceReady("uhf")) {
            onErrorOccurred("Reader UHF tidak siap.", TaggingState.ERROR_SDK)
            return
        }
        _isLoading.value = true
        _taggingState.value = TaggingState.WRITING_EPC
        _statusMessage.value = "Menulis EPC: $epcHexTarget ke tag (TID: $tidOfTagToVerify)..."
        _displayedEpc.value = epcHexTarget // Tampilkan EPC yang sedang ditulis

        sdkOperationJob = viewModelScope.launch {
            // Jika SDKManager.writeUhfTag memerlukan filter EPC lama:
            // sdkManager.writeUhfTag(epcDataHex = epcHexTarget, oldEpcFilterHex = currentEpcFilterOnTag)
            // Jika tidak, cukup:
            sdkManager.writeUhfTag(epcDataHex = epcHexTarget)

            val success = withTimeoutOrNull(SDK_OPERATION_TIMEOUT_MS) {
                while (isActive && _taggingState.value == TaggingState.WRITING_EPC) {
                    delay(100)
                }
                _taggingState.value != TaggingState.WRITING_EPC
            }
            if (success == null && _taggingState.value == TaggingState.WRITING_EPC) {
                onErrorOccurred("Timeout saat menulis EPC.", TaggingState.PROCESS_FAILED)
            } else if (success == false && _taggingState.value == TaggingState.WRITING_EPC) {
                onErrorOccurred("Gagal menulis EPC (operasi tak selesai).", TaggingState.PROCESS_FAILED)
            }
        }
    }

    private fun saveRfidDataToDatabase(bookToUpdate: BookMaster, epcSuccessfullyWritten: String, tidAssociatedWithWrite: String) {
        _taggingState.value = TaggingState.SAVING_TO_DB
        _statusMessage.value = "Menyimpan data (EPC: $epcSuccessfullyWritten, TID: $tidAssociatedWithWrite)..."
        _isLoading.value = true

        viewModelScope.launch {
            try {
                bookRepository.updateFullRfidDetailsForBook(
                    itemCode = bookToUpdate.itemCode,
                    newRfidTagHex = epcSuccessfullyWritten,
                    newTid = tidAssociatedWithWrite,
                    newPairingStatus = PairingStatus.PAIRED_WRITE_SUCCESS,
                    pairingTimestamp = System.currentTimeMillis()
                )
                val updatedBook = bookRepository.getBookByItemCode(bookToUpdate.itemCode)
                _currentBook.postValue(updatedBook)
                _isLoading.postValue(false)
                _statusMessage.postValue("Data RFID untuk '${updatedBook?.title ?: bookToUpdate.title}' berhasil disimpan!")
                _taggingState.postValue(TaggingState.PROCESS_SUCCESS)
            } catch (e: Exception) {
                Log.e(TAG, "Error menyimpan data RFID ke DB: ${e.message}", e)
                try {
                    bookRepository.updateFullRfidDetailsForBook( // Coba update status gagal
                        itemCode = bookToUpdate.itemCode,
                        newRfidTagHex = bookToUpdate.rfidTagHex, // Kembalikan ke EPC lama jika ada
                        newTid = bookToUpdate.tid, // Kembalikan ke TID lama jika ada
                        newPairingStatus = PairingStatus.PAIRED_WRITE_FAILED,
                        pairingTimestamp = System.currentTimeMillis()
                    )
                } catch (updateEx: Exception) { /* ... */ }
                onErrorOccurred("Gagal menyimpan ke DB: ${e.message}", TaggingState.PROCESS_FAILED)
            }
        }
    }

    private fun convertItemCodeToEpcHex(itemCode: String): String? {
        // ... (implementasi Anda sudah ada dan terlihat baik)
        // Pastikan ini mengembalikan 24 karakter hex atau null
        try {
            if (itemCode.isBlank()) return null
            val sanitizedItemCode = itemCode.replace("[^a-zA-Z0-9]".toRegex(), "").uppercase()
            if (sanitizedItemCode.isEmpty()) return null
            val bytesToConvert = sanitizedItemCode.toByteArray(Charsets.US_ASCII).take(12)
            var initialHex = bytesToConvert.joinToString("") { String.format("%02X", it) }
            val targetHexLength = 24 // EPC-96
            if (initialHex.length > targetHexLength) {
                initialHex = initialHex.substring(0, targetHexLength)
            }
            val finalHex = initialHex.padEnd(targetHexLength, '0')
            return if (finalHex.length == targetHexLength) finalHex else null
        } catch (e: Exception) {
            Log.e(TAG, "Error konversi itemCode '$itemCode' ke EPC hex: ${e.message}", e)
            return null
        }
    }

    fun triggerBarcodeScan() {
        Log.d(TAG, "triggerBarcodeScan called. Current isLoading: ${isLoading.value}, Current TaggingState: ${taggingState.value}")

        // Pengecekan state dasar sebelum mencoba mengaktifkan hardware
        if (_taggingState.value !in listOf(
                TaggingState.IDLE,
                TaggingState.PROCESS_SUCCESS,
                TaggingState.PROCESS_FAILED,
                TaggingState.ERROR_BOOK_NOT_FOUND,
                TaggingState.ERROR_SDK,
                TaggingState.ERROR_CONVERSION
            )) {
            _statusMessage.postValue("Tidak dapat memulai scan barcode, proses lain sedang berjalan.")
            Log.w(TAG, "triggerBarcodeScan: Cannot start, operation already in progress or invalid state: ${_taggingState.value}")
            return
        }

        if (!sdkManager.isDeviceReady("barcode")) {
            _statusMessage.postValue("Scanner barcode tidak siap.")
            Log.w(TAG, "triggerBarcodeScan: Barcode scanner not ready.")
            return
        }

        // Tidak perlu set isLoading di sini, biarkan searchBookByItemCode yang mengaturnya.
        // Tidak perlu mengubah _taggingState di sini, biarkan onBarcodeScanned yang mengaturnya.
        _statusMessage.postValue("Mengaktifkan scan barcode...")
        Log.d(TAG, "Requesting SDK to start barcode scan.")
        sdkManager.startBarcodeScan() // SDK akan memanggil onBarcodeScanned
    }


    private fun clearLocalCacheBeforeSearch() {
        _currentBook.value = null
        targetEpcHexToWrite = null
        currentTidReadFromTag = null
        currentEpcReadFromTagForConfirmation = null
        _displayedTid.value = null
        _displayedEpc.value = null
    }

    fun cancelCurrentOperation() {
        // ... (implementasi Anda sudah ada dan terlihat baik)
        sdkOperationJob?.cancel()
        _isLoading.postValue(false)
        sdkManager.stopUhfOperation()
        if (sdkManager.isBarcodeDeviceScanning) sdkManager.stopBarcodeScan()

        val bookCurrentlySelected = _currentBook.value != null
        Log.i(TAG, "Operasi dibatalkan pengguna. State: ${_taggingState.value}. Buku dipilih: $bookCurrentlySelected")

        if (bookCurrentlySelected && targetEpcHexToWrite != null) {
            val book = _currentBook.value!!
            if (book.rfidTagHex.isNullOrBlank() || book.pairingStatus == PairingStatus.NOT_PAIRED || book.pairingStatus == PairingStatus.PAIRING_FAILED) {
                _taggingState.postValue(TaggingState.BOOK_FOUND_UNTAGGED)
                _statusMessage.postValue("Dibatalkan. Buku '${book.title}' siap ditag.")
            } else {
                _taggingState.postValue(TaggingState.BOOK_FOUND_ALREADY_TAGGED)
                _statusMessage.postValue("Dibatalkan. Buku '${book.title}' sudah punya RFID.")
            }
        } else {
            clearProcessAndPrepareForNext("Operasi dibatalkan. Scan barcode buku.")
        }
    }

    fun clearProcessAndPrepareForNext(resetMessage: String? = "Silakan scan barcode buku berikutnya.") {
        // ... (implementasi Anda sudah ada)
        sdkOperationJob?.cancel()
        sdkManager.stopUhfOperation()
        if (sdkManager.isBarcodeDeviceScanning) sdkManager.stopBarcodeScan()
        clearLocalCacheBeforeSearch()
        _statusMessage.value = resetMessage
        _taggingState.value = TaggingState.IDLE
        _isLoading.value = false
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "BookTaggingViewModel onCleared.")
        sdkOperationJob?.cancel()
        sdkManager.onBarcodeScanned = null
        sdkManager.onTagReadTidSuccess = null
        sdkManager.onTagReadTidFailed = null
        sdkManager.onSingleUhfTagEpcRead = null
        sdkManager.onSingleUhfTagReadFailed = null
        sdkManager.onTagWriteSuccess = null
        sdkManager.onTagWriteFailed = null
        sdkManager.onError = null
        sdkManager.onUhfInventoryFinished = null
        sdkManager.onUhfOperationStopped = null
    }
}
