package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Jika MyApplication digunakan untuk mendapatkan SDKManager, pastikan importnya ada
// import com.example.aplikasistockopnameperpus.MyApplication
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
    // AWAITING_TAG_FOR_TID_READ, // Mungkin tidak lagi diperlukan jika langsung scan
    READING_TID,
    // TID_READ_AWAITING_EPC_CONFIRM, // Digabung ke READING_EPC_FOR_CONFIRMATION
    READING_EPC_FOR_CONFIRMATION,
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
    private val application: Application, // Bisa digunakan jika perlu context
    private val bookRepository: BookRepository,
    private val sdkManager: ChainwaySDKManager // Diinjeksi, pastikan Factory Anda benar
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
    private var currentTidReadFromTag: String? = null
    private var currentEpcReadFromTagForConfirmation: String? = null // Simpan EPC yang dibaca untuk konfirmasi

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
        sdkManager.onBarcodeScanned = barScanned@{ barcodeData ->
            if (_isLoading.value == true || _taggingState.value !in listOf(
                    TaggingState.IDLE,
                    TaggingState.PROCESS_SUCCESS,
                    TaggingState.PROCESS_FAILED,
                    TaggingState.ERROR_BOOK_NOT_FOUND,
                    TaggingState.ERROR_SDK // Izinkan scan baru setelah error SDK
                )) {
                _statusMessage.postValue("Proses lain sedang berjalan. Harap tunggu atau batalkan.")
                return@barScanned
            }
            _statusMessage.postValue("Barcode discan: $barcodeData")
            searchBookByItemCode(barcodeData)
        }

        sdkManager.onTagReadTidSuccess = { tid, epcCurrentlyOnTag ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.READING_TID) {
                Log.i(TAG, "SDK CB: TID Read Success. TID=$tid, EPC on Tag (if read with TID)='$epcCurrentlyOnTag'")
                currentTidReadFromTag = tid
                _displayedTid.postValue(tid)

                // Jika EPC juga terbaca bersamaan dengan TID (misalnya dari inventorySingleTag),
                // kita bisa langsung gunakan itu untuk konfirmasi.
                if (!epcCurrentlyOnTag.isNullOrBlank()) {
                    currentEpcReadFromTagForConfirmation = epcCurrentlyOnTag
                    _displayedEpc.postValue(epcCurrentlyOnTag)
                    promptUserForWriteConfirmation(tid, epcCurrentlyOnTag)
                } else {
                    // Jika hanya TID yang didapat, lanjutkan ke pembacaan EPC terpisah
                    initiateEpcReadForConfirmation(tid)
                }
            } else {
                Log.w(TAG, "SDK CB: onTagReadTidSuccess received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onTagReadTidFailed = { error ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.READING_TID) {
                onErrorOccurred("Gagal membaca TID: $error", TaggingState.PROCESS_FAILED)
            } else {
                Log.w(TAG, "SDK CB: onTagReadTidFailed received in unexpected state: ${_taggingState.value}")
            }
        }

        // Callback jika SDK berhasil membaca EPC tunggal (untuk konfirmasi setelah TID atau jika TID gagal dibaca bersamaan)
        sdkManager.onSingleUhfTagEpcRead = onSingleUhfTagEpcRead@{ epcRead, tidAssociated -> // Label eksplisit DIBUAT di sini
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.READING_EPC_FOR_CONFIRMATION) {
                Log.i(TAG, "SDK CB: Single EPC Read Success (for confirmation). EPC='$epcRead'. TID previously read: '$currentTidReadFromTag'. TID with this EPC read: '$tidAssociated'")
                _displayedEpc.postValue(epcRead)
                currentEpcReadFromTagForConfirmation = epcRead

                val tidForPrompt = currentTidReadFromTag
                if (tidForPrompt == null) {
                    Log.e(TAG, "TID tidak tersedia saat EPC dikonfirmasi. Ini seharusnya tidak terjadi.")
                    onErrorOccurred("Kesalahan internal: TID tidak ditemukan untuk konfirmasi.", TaggingState.PROCESS_FAILED)
                    return@onSingleUhfTagEpcRead // Sekarang label ini VALID
                }
                promptUserForWriteConfirmation(tidForPrompt, epcRead)
            } else {
                Log.w(TAG, "SDK CB: onSingleUhfTagEpcRead received in unexpected state: ${_taggingState.value}")
            }
        }
        // Callback jika SDK GAGAL membaca EPC tunggal (untuk konfirmasi)
        sdkManager.onSingleUhfTagReadFailed = { error -> // Callback ini perlu ada di SDKManager
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.READING_EPC_FOR_CONFIRMATION) {
                onErrorOccurred("Gagal membaca EPC untuk konfirmasi: $error", TaggingState.PROCESS_FAILED)
            } else {
                Log.w(TAG, "SDK CB: onSingleUhfTagReadFailed received in unexpected state: ${_taggingState.value}")
            }
        }


        sdkManager.onTagWriteSuccess = { writtenEpc ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.WRITING_EPC) {
                Log.i(TAG, "SDK CB: Tag Write Success. Written EPC: $writtenEpc. Associated TID: $currentTidReadFromTag")
                _displayedEpc.postValue(writtenEpc)

                val bookToUpdate = _currentBook.value
                val tidToSave = currentTidReadFromTag // TID yang dibaca SEBELUM penulisan
                if (bookToUpdate != null && tidToSave != null) {
                    saveRfidDataToDatabase(bookToUpdate, writtenEpc, tidToSave)
                } else {
                    Log.e(TAG, "onTagWriteSuccess: Data tidak lengkap. Book: $bookToUpdate, TID: $tidToSave")
                    onErrorOccurred("Data buku atau TID awal tidak lengkap untuk menyimpan.", TaggingState.PROCESS_FAILED)
                }
            } else {
                Log.w(TAG, "SDK CB: onTagWriteSuccess received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onTagWriteFailed = { error ->
            sdkOperationJob?.cancel()
            if (_taggingState.value == TaggingState.WRITING_EPC) {
                onErrorOccurred("Gagal menulis EPC: $error", TaggingState.PROCESS_FAILED)
            } else {
                Log.w(TAG, "SDK CB: onTagWriteFailed received in unexpected state: ${_taggingState.value}")
            }
        }

        sdkManager.onError = { errorMessage ->
            sdkOperationJob?.cancel()
            val currentState = _taggingState.value
            val nextStateOnError = when (currentState) {
                TaggingState.READING_TID,
                TaggingState.READING_EPC_FOR_CONFIRMATION,
                TaggingState.WRITING_EPC,
                TaggingState.BOOK_SEARCHING, // Jika error SDK saat mencari
                TaggingState.SAVING_TO_DB -> TaggingState.PROCESS_FAILED // Jika error SDK saat operasi krusial
                else -> TaggingState.ERROR_SDK
            }
            // Hanya panggil onErrorOccurred jika state saat ini bukan sudah error atau failed
            // untuk menghindari pembaruan status berulang yang tidak perlu.
            if (currentState != TaggingState.PROCESS_FAILED && currentState != TaggingState.ERROR_SDK) {
                onErrorOccurred("SDK Error: $errorMessage", nextStateOnError)
            } else {
                Log.w(TAG, "SDK onError: Error '$errorMessage' diterima, tapi state sudah $currentState. Tidak mengubah state.")
                _statusMessage.postValue("SDK Error: $errorMessage (State: $currentState)")
            }
        }

        sdkManager.onUhfOperationStopped = {
            Log.d(TAG, "SDK CB: onUhfOperationStopped. Current state: ${_taggingState.value}")
            // Jika operasi dihentikan oleh SDK atau secara eksternal,
            // dan kita sedang dalam proses aktif, anggap sebagai kegagalan atau timeout.
            if (_taggingState.value in listOf(TaggingState.READING_TID, TaggingState.READING_EPC_FOR_CONFIRMATION, TaggingState.WRITING_EPC)) {
                if (_isLoading.value == true) { // Jika kita masih mengharapkan operasi
                    Log.w(TAG, "Operasi UHF dihentikan saat ViewModel masih menunggu hasil.")
                    // onErrorOccurred("Operasi UHF dihentikan secara tak terduga.", TaggingState.PROCESS_FAILED)
                    // Hati-hati, ini bisa bertabrakan dengan timeout handling.
                    // Timeout akan memanggil stopUhfOperation juga.
                    // Lebih baik biarkan timeout yang menangani atau pastikan job dibatalkan.
                }
            }
        }
    }

    private fun onErrorOccurred(message: String, nextState: TaggingState = TaggingState.PROCESS_FAILED) {
        sdkOperationJob?.cancel() // Pastikan job dibatalkan saat error
        sdkManager.stopUhfOperation() // Coba hentikan operasi SDK jika ada yang aktif

        _isLoading.postValue(false)
        _statusMessage.postValue(message)
        _taggingState.postValue(nextState)
        Log.e(TAG, "Error Occurred: $message, Next State: $nextState")
    }

    fun searchBookByItemCode(itemCode: String) {
        if (itemCode.isBlank()) {
            _statusMessage.value = "Kode item tidak boleh kosong."
            _taggingState.value = TaggingState.IDLE
            return
        }
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
                        onErrorOccurred("Gagal mengkonversi kode item ke format EPC. Periksa format kode item.", TaggingState.ERROR_CONVERSION)
                    } else {
                        _statusMessage.postValue("Buku '${book.title}' ditemukan. Target EPC: $targetEpcHexToWrite")
                        Log.d(TAG, "Buku ditemukan: ${book.title}. Target EPC untuk ditulis: $targetEpcHexToWrite")
                        if (book.rfidTagHex.isNullOrBlank() || book.pairingStatus == PairingStatus.NOT_PAIRED || book.pairingStatus == PairingStatus.PAIRING_FAILED) {
                            _taggingState.postValue(TaggingState.BOOK_FOUND_UNTAGGED)
                            _statusMessage.postValue("Buku belum memiliki RFID. Klik 'Mulai Proses Tag'.")
                        } else {
                            _taggingState.postValue(TaggingState.BOOK_FOUND_ALREADY_TAGGED)
                            _statusMessage.postValue("Buku sudah memiliki RFID (${book.rfidTagHex}). Status: ${book.pairingStatus}. Klik 'Proses Ulang Tag' untuk menimpa.")
                        }
                    }
                } else {
                    _taggingState.postValue(TaggingState.ERROR_BOOK_NOT_FOUND)
                    _statusMessage.postValue("Buku dengan kode item '$itemCode' tidak ditemukan.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error mencari buku '$itemCode': ${e.message}", e)
                onErrorOccurred("Error database saat mencari buku: ${e.message}", TaggingState.PROCESS_FAILED)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    // Dipanggil dari UI ketika pengguna ingin memulai seluruh proses (baca TID -> konfirmasi EPC -> tulis)
    fun userRequestsStartTaggingProcess() {
        val book = _currentBook.value
        if (book == null || targetEpcHexToWrite == null) {
            onErrorOccurred("Buku atau target EPC belum disiapkan.", TaggingState.IDLE)
            return
        }
        if (_taggingState.value !in listOf(TaggingState.BOOK_FOUND_UNTAGGED, TaggingState.BOOK_FOUND_ALREADY_TAGGED)) {
            Log.w(TAG, "userRequestsStartTaggingProcess dipanggil pada state yang tidak tepat: ${_taggingState.value}")
            _statusMessage.postValue("State tidak valid untuk memulai proses tagging.")
            return
        }
        if (!sdkManager.isDeviceReady("uhf")) {
            onErrorOccurred("Reader UHF tidak siap atau tidak terhubung.", TaggingState.ERROR_SDK)
            return
        }
        if (sdkManager.isUhfDeviceScanning) {
            _statusMessage.postValue("Reader sedang sibuk. Hentikan operasi UHF lain dahulu.")
            return
        }

        // Langsung mulai pembacaan TID
        initiateTidRead()
    }


    private fun initiateTidRead() {
        // State AWAITING_TAG_FOR_TID_READ mungkin tidak lagi diperlukan jika langsung scan
        // if (_taggingState.value != TaggingState.AWAITING_TAG_FOR_TID_READ && _taggingState.value != TaggingState.BOOK_FOUND_UNTAGGED) {
        //     Log.w(TAG, "initiateTidRead dipanggil pada state yang tidak tepat: ${_taggingState.value}")
        //     return
        // }
        if (!sdkManager.isDeviceReady("uhf")) {
            onErrorOccurred("Reader UHF tidak siap.", TaggingState.ERROR_SDK)
            return
        }

        _isLoading.value = true
        currentTidReadFromTag = null
        currentEpcReadFromTagForConfirmation = null
        _displayedTid.value = null
        _displayedEpc.value = null
        _taggingState.value = TaggingState.READING_TID
        _statusMessage.value = "Dekatkan tag RFID ke reader untuk dibaca TID & EPC awalnya..."

        sdkOperationJob = viewModelScope.launch {
            // Kita akan menggunakan readTidFromNearbyTag yang di SDKManager-nya
            // memanggil inventorySingleTag() yang mengembalikan TID dan EPC jika ada.
            sdkManager.readTidFromNearbyTag() // Ini akan memicu onTagReadTidSuccess atau onTagReadTidFailed

            // Loop untuk menunggu callback mengubah state atau timeout
            val success = withTimeoutOrNull(SDK_OPERATION_TIMEOUT_MS) {
                while (isActive && _taggingState.value == TaggingState.READING_TID) {
                    delay(100) // Cek status secara periodik
                }
                _taggingState.value != TaggingState.READING_TID // Berhasil jika state sudah berubah
            }

            if (success == null && _taggingState.value == TaggingState.READING_TID) { // Timeout
                Log.w(TAG, "Timeout saat menunggu hasil pembacaan TID.")
                onErrorOccurred("Timeout saat membaca TID.", TaggingState.PROCESS_FAILED)
            } else if (success == false && _taggingState.value == TaggingState.READING_TID) { // Loop selesai tapi state belum berubah
                Log.w(TAG, "Loop pembacaan TID selesai tapi state belum maju oleh callback.")
                // Biasanya error sudah ditangani oleh onTagReadTidFailed jika itu penyebabnya.
                // Jika tidak, ini kondisi yang aneh, anggap gagal.
                onErrorOccurred("Gagal membaca TID (operasi tidak selesai).", TaggingState.PROCESS_FAILED)
            }
            // isLoading akan di-set false oleh onErrorOccurred atau oleh alur sukses berikutnya
        }
    }

    private fun initiateEpcReadForConfirmation(tidJustRead: String) {
        // Fungsi ini mungkin tidak lagi diperlukan jika onTagReadTidSuccess sudah mendapatkan EPC
        // bersamaan dengan TID. Namun, kita bisa tetap memilikinya sebagai fallback atau
        // jika kita ingin memastikan EPC dibaca ulang dengan filter TID (jika SDK mendukung).

        if (!sdkManager.isDeviceReady("uhf")) {
            onErrorOccurred("Reader UHF tidak siap.", TaggingState.ERROR_SDK)
            return
        }
        _isLoading.value = true
        _taggingState.value = TaggingState.READING_EPC_FOR_CONFIRMATION
        _statusMessage.value = "Membaca EPC dari tag (TID: $tidJustRead) untuk konfirmasi..."

        sdkOperationJob = viewModelScope.launch {
            // Gunakan readSingleUhfTagEpcNearby. Idealnya, jika kita hanya ingin membaca tag yang TID-nya
            // baru saja kita baca, tag tersebut masih yang paling dekat.
            // Filter by TID di SDK Manager saat ini tidak ada untuk pembacaan EPC saja.
            sdkManager.readSingleUhfTagEpcNearby() // Ini akan memicu onSingleUhfTagEpcRead atau onSingleUhfTagReadFailed

            val success = withTimeoutOrNull(SDK_OPERATION_TIMEOUT_MS) {
                while (isActive && _taggingState.value == TaggingState.READING_EPC_FOR_CONFIRMATION) {
                    delay(100)
                }
                _taggingState.value != TaggingState.READING_EPC_FOR_CONFIRMATION
            }

            if (success == null && _taggingState.value == TaggingState.READING_EPC_FOR_CONFIRMATION) {
                Log.w(TAG, "Timeout saat menunggu hasil pembacaan EPC untuk konfirmasi.")
                onErrorOccurred("Timeout saat membaca EPC untuk konfirmasi.", TaggingState.PROCESS_FAILED)
            } else if (success == false && _taggingState.value == TaggingState.READING_EPC_FOR_CONFIRMATION) {
                Log.w(TAG, "Loop pembacaan EPC selesai tapi state belum maju oleh callback.")
                onErrorOccurred("Gagal membaca EPC untuk konfirmasi (operasi tidak selesai).", TaggingState.PROCESS_FAILED)
            }
        }
    }

    private fun promptUserForWriteConfirmation(tidFromTag: String, currentEpcOnTag: String) {
        val book = _currentBook.value
        val targetEpc = targetEpcHexToWrite
        if (book == null || targetEpc == null) {
            onErrorOccurred("Data tidak lengkap untuk konfirmasi penulisan.", TaggingState.PROCESS_FAILED)
            return
        }

        // Simpan EPC yang baru saja dibaca untuk konfirmasi
        currentEpcReadFromTagForConfirmation = currentEpcOnTag

        _taggingState.postValue(TaggingState.EPC_CONFIRMED_AWAITING_WRITE)
        val message = StringBuilder()
        message.append("KONFIRMASI PENULISAN TAG:\n")
        message.append("-------------------------------------\n")
        message.append("Buku: ${book.title} (IC: ${book.itemCode})\n\n")
        message.append("Tag RFID Terdeteksi:\n")
        message.append("  TID: $tidFromTag\n")
        message.append("  EPC Saat Ini: $currentEpcOnTag\n\n")
        message.append("Akan Ditulis dengan EPC Baru:\n")
        message.append("  $targetEpc\n")
        message.append("-------------------------------------\n")

        if (currentEpcOnTag.isNotBlank() && currentEpcOnTag.equals(targetEpc, ignoreCase = true)) {
            message.append("PERHATIAN: EPC saat ini di tag SAMA dengan EPC target. Apakah Anda tetap ingin menulis ulang?\n\n")
        } else if (currentEpcOnTag.isNotBlank() && !book.rfidTagHex.isNullOrBlank() && !currentEpcOnTag.equals(book.rfidTagHex, ignoreCase = true)) {
            message.append("PERHATIAN: EPC saat ini di tag ($currentEpcOnTag) BERBEDA dengan EPC yang tercatat di database buku (${book.rfidTagHex}). Ini mungkin tag yang salah.\n\n")
        } else if (currentEpcOnTag.isBlank() && !book.rfidTagHex.isNullOrBlank()) {
            message.append("INFO: Tag yang terdeteksi tidak memiliki EPC (mungkin baru), buku ini sudah punya data EPC (${book.rfidTagHex}) di DB.\n\n")
        }


        message.append("Lanjutkan Penulisan?")
        _statusMessage.postValue(message.toString())
    }

    fun userConfirmsWriteOperation() {
        if (_taggingState.value != TaggingState.EPC_CONFIRMED_AWAITING_WRITE) {
            Log.w(TAG, "userConfirmsWriteOperation dipanggil pada state yang tidak tepat: ${_taggingState.value}")
            _statusMessage.postValue("State tidak valid untuk memulai penulisan.")
            return
        }
        val finalEpcToWrite = targetEpcHexToWrite
        val tidOfTag = currentTidReadFromTag // TID yang sudah dikonfirmasi
        // val epcCurrentlyOnTag = currentEpcReadFromTagForConfirmation // EPC yang ada di tag sebelum ditulis

        if (finalEpcToWrite == null || tidOfTag == null) {
            onErrorOccurred("EPC target atau TID tag belum siap untuk penulisan.", TaggingState.PROCESS_FAILED)
            return
        }

        Log.i(TAG, "Pengguna mengonfirmasi penulisan. Target EPC: $finalEpcToWrite untuk tag dengan TID: $tidOfTag")
        initiateWriteEpcToTag(finalEpcToWrite, tidOfTag /*, epcCurrentlyOnTag */)
    }

    // PENTING: Jika SDK memerlukan filter EPC lama untuk menulis, Anda perlu menyediakannya.
    // ChainwaySDKManager.writeUhfTag saat ini tidak menggunakan filter EPC lama,
    // jadi parameter currentEpcFilterOnTag mungkin tidak diperlukan untuk SDKManager.
    private fun initiateWriteEpcToTag(epcHexTarget: String, tidOfTagToVerify: String, currentEpcFilterOnTag: String? = null) {
        if (!sdkManager.isDeviceReady("uhf")) {
            onErrorOccurred("Reader UHF tidak siap.", TaggingState.ERROR_SDK)
            return
        }
        _isLoading.value = true
        _taggingState.value = TaggingState.WRITING_EPC
        _statusMessage.value = "Menulis EPC: $epcHexTarget ke tag (TID: $tidOfTagToVerify)..."
        _displayedEpc.value = null

        sdkOperationJob = viewModelScope.launch {
            // ChainwaySDKManager.writeUhfTag hanya butuh epcDataHex dan password (default)
            // Tidak menggunakan filter EPC lama atau TID untuk menargetkan penulisan.
            // Penulisan terjadi ke tag terdekat. Ini adalah risiko jika tag bergeser.
            // Idealnya, SDK punya cara menulis ke tag dengan TID tertentu.
            sdkManager.writeUhfTag(epcDataHex = epcHexTarget)

            val success = withTimeoutOrNull(SDK_OPERATION_TIMEOUT_MS) {
                while (isActive && _taggingState.value == TaggingState.WRITING_EPC) {
                    delay(100)
                }
                _taggingState.value != TaggingState.WRITING_EPC
            }

            if (success == null && _taggingState.value == TaggingState.WRITING_EPC) {
                Log.w(TAG, "Timeout saat menunggu hasil penulisan EPC.")
                onErrorOccurred("Timeout saat menulis EPC.", TaggingState.PROCESS_FAILED)
            } else if (success == false && _taggingState.value == TaggingState.WRITING_EPC) {
                Log.w(TAG, "Loop penulisan EPC selesai tapi state belum maju oleh callback.")
                onErrorOccurred("Gagal menulis EPC (operasi tidak selesai).", TaggingState.PROCESS_FAILED)
            }
            // isLoading akan di-set false oleh onErrorOccurred atau oleh saveRfidDataToDatabase
        }
    }


    private fun saveRfidDataToDatabase(bookToUpdate: BookMaster, epcSuccessfullyWritten: String, tidAssociatedWithWrite: String) {
        _taggingState.value = TaggingState.SAVING_TO_DB
        _statusMessage.value = "Menyimpan data pairing (EPC: $epcSuccessfullyWritten, TID: $tidAssociatedWithWrite) ke database..."
        _isLoading.value = true

        viewModelScope.launch {
            try {
                bookRepository.updateFullRfidDetailsForBook(
                    itemCode = bookToUpdate.itemCode,
                    newRfidTagHex = epcSuccessfullyWritten,
                    newTid = tidAssociatedWithWrite, // TID yang dibaca dari tag yang baru saja ditulis
                    newPairingStatus = PairingStatus.PAIRED_WRITE_SUCCESS,
                    pairingTimestamp = System.currentTimeMillis()
                )
                val updatedBook = bookRepository.getBookByItemCode(bookToUpdate.itemCode)
                _currentBook.postValue(updatedBook)
                _isLoading.postValue(false)
                _statusMessage.postValue("Data RFID untuk buku '${updatedBook?.title ?: bookToUpdate.title}' berhasil disimpan!")
                _taggingState.postValue(TaggingState.PROCESS_SUCCESS)
                Log.i(TAG, "Data RFID disimpan: ItemCode=${bookToUpdate.itemCode}, EPC=$epcSuccessfullyWritten, TID=$tidAssociatedWithWrite")

            } catch (e: Exception) {
                Log.e(TAG, "Error menyimpan data RFID ke DB untuk '${bookToUpdate.itemCode}': ${e.message}", e)
                try {
                    bookRepository.updateFullRfidDetailsForBook(
                        itemCode = bookToUpdate.itemCode,
                        newRfidTagHex = epcSuccessfullyWritten,
                        newTid = tidAssociatedWithWrite,
                        newPairingStatus = PairingStatus.PAIRED_WRITE_FAILED,
                        pairingTimestamp = System.currentTimeMillis()
                    )
                } catch (updateEx: Exception) {
                    Log.e(TAG, "Gagal update status ke PAIRED_WRITE_FAILED: ${updateEx.message}")
                }
                onErrorOccurred("Gagal menyimpan ke database: ${e.message}", TaggingState.PROCESS_FAILED)
            }
        }
    }

    private fun convertItemCodeToEpcHex(itemCode: String): String? {
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
            // Pad dengan '0' di KANAN jika lebih pendek, bukan '00'
            val finalHex = initialHex.padEnd(targetHexLength, '0')


            Log.d(TAG, "Konversi: ItemCode '$itemCode' -> Sanitized '$sanitizedItemCode' -> InitialHex '$initialHex' -> Final EPC HEX '$finalHex'")
            return if (finalHex.length == targetHexLength) finalHex else null
        } catch (e: Exception) {
            Log.e(TAG, "Error konversi itemCode '$itemCode' ke EPC hex: ${e.message}", e)
            return null
        }
    }

    fun triggerBarcodeScan() {
        if (_isLoading.value == true || _taggingState.value !in listOf(
                TaggingState.IDLE,
                TaggingState.PROCESS_SUCCESS,
                TaggingState.PROCESS_FAILED,
                TaggingState.ERROR_BOOK_NOT_FOUND,
                TaggingState.ERROR_SDK
            )) {
            _statusMessage.postValue("Operasi lain sedang berjalan.")
            return
        }
        if (!sdkManager.isDeviceReady("barcode")) {
            _statusMessage.postValue("Scanner barcode tidak siap.")
            return
        }
        _statusMessage.postValue("Mengaktifkan scan barcode...")
        sdkManager.startBarcodeScan()
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
        sdkOperationJob?.cancel()
        _isLoading.postValue(false)

        // Hentikan operasi SDK yang mungkin berjalan
        sdkManager.stopUhfOperation() // Ini akan menghentikan semua operasi UHF (inventory, read, write, radar)
        if (sdkManager.isBarcodeDeviceScanning) sdkManager.stopBarcodeScan()

        val bookCurrentlySelected = _currentBook.value != null
        Log.i(TAG, "Operasi dibatalkan pengguna. State saat ini: ${_taggingState.value}. Buku dipilih: $bookCurrentlySelected")

        if (bookCurrentlySelected && targetEpcHexToWrite != null) {
            val book = _currentBook.value!!
            if (book.rfidTagHex.isNullOrBlank() || book.pairingStatus == PairingStatus.NOT_PAIRED || book.pairingStatus == PairingStatus.PAIRING_FAILED) {
                _taggingState.postValue(TaggingState.BOOK_FOUND_UNTAGGED)
                _statusMessage.postValue("Operasi dibatalkan. Buku '${book.title}' siap untuk proses tagging baru.")
            } else {
                _taggingState.postValue(TaggingState.BOOK_FOUND_ALREADY_TAGGED)
                _statusMessage.postValue("Operasi dibatalkan. Buku '${book.title}' sudah memiliki RFID.")
            }
        } else {
            clearProcessAndPrepareForNext("Operasi dibatalkan. Silakan scan barcode buku.")
        }
    }

    fun clearProcessAndPrepareForNext(resetMessage: String? = "Silakan scan barcode buku berikutnya.") {
        sdkOperationJob?.cancel()
        sdkManager.stopUhfOperation()
        if (sdkManager.isBarcodeDeviceScanning) sdkManager.stopBarcodeScan()

        clearLocalCacheBeforeSearch()

        _statusMessage.value = resetMessage
        _taggingState.value = TaggingState.IDLE
        _isLoading.value = false
        Log.d(TAG, "Proses dibersihkan. Siap untuk item buku berikutnya.")
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "BookTaggingViewModel onCleared.")
        sdkOperationJob?.cancel() // Batalkan job coroutine jika masih berjalan

        // Tidak perlu memanggil sdkManager.releaseResources() di sini jika
        // SDKManager adalah singleton yang dikelola oleh Application dan
        // Application yang menangani lifecycle globalnya.
        // Cukup null-kan callback untuk mencegah memory leak dari ViewModel ini.
        sdkManager.onBarcodeScanned = null
        sdkManager.onTagReadTidSuccess = null
        sdkManager.onTagReadTidFailed = null
        sdkManager.onSingleUhfTagEpcRead = null
        sdkManager.onSingleUhfTagReadFailed = null
        sdkManager.onTagWriteSuccess = null
        sdkManager.onTagWriteFailed = null
        sdkManager.onError = null
        sdkManager.onUhfInventoryFinished = null // Meskipun tidak digunakan, baiknya dibersihkan
        sdkManager.onUhfOperationStopped = null
    }
}

