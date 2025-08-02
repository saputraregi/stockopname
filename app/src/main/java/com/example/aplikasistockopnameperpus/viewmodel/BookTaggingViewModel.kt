package com.example.aplikasistockopnameperpus.viewmodel // Sesuaikan package Anda

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import kotlinx.coroutines.launch

// Status untuk proses tagging
enum class TaggingState {
    IDLE,                       // Menunggu input kode item
    BOOK_FOUND_UNTAGGED,        // Buku ditemukan, belum ada RFID, siap untuk ditag
    BOOK_FOUND_ALREADY_TAGGED,  // Buku ditemukan, sudah punya RFID
    AWAITING_TAG_PLACEMENT,     // Menunggu tag diletakkan dekat reader (setelah tombol 'Mulai' diklik)
    WRITING_EPC,                // Sedang proses menulis EPC ke tag
    READING_TID,                // Sedang proses membaca TID dari tag
    SAVING_TO_DB,               // Sedang menyimpan data ke database
    PROCESS_SUCCESS,            // Seluruh proses berhasil
    PROCESS_FAILED,             // Proses gagal pada salah satu tahap
    ERROR_BOOK_NOT_FOUND,       // Kode item tidak ditemukan di database
    ERROR_SDK                     // Error terkait SDK
}

class BookTaggingViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val sdkManager: ChainwaySDKManager // Diasumsikan di-inject atau diakses sebagai singleton
) : AndroidViewModel(application) {

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

    private var targetEpcToWrite: String? = null // EPC yang akan ditulis, berasal dari itemCode

    init {
        setupSdkListeners()
    }

    private fun setupSdkListeners() {
        sdkManager.onBarcodeScanned = { barcodeData ->
            _statusMessage.postValue("Barcode discan: $barcodeData")
            searchBookByItemCode(barcodeData)
        }

        sdkManager.onUhfTagScanned = { epc ->
            // Logika ini lebih kompleks karena onUhfTagScanned bisa terpanggil kapan saja
            // Selama proses penulisan, kita mungkin ingin mengabaikan scan acak
            // atau menggunakannya untuk verifikasi.
            Log.d("BookTaggingVM", "SDK onUhfTagScanned: $epc. Current state: ${_taggingState.value}")

            when (_taggingState.value) {
                TaggingState.AWAITING_TAG_PLACEMENT, TaggingState.WRITING_EPC -> {
                    // Jika kita mengharapkan EPC spesifik setelah write, ini bisa jadi verifikasi.
                    // Atau, ini bisa jadi tag lain yang terdeteksi.
                    // Untuk Opsi A (Write kemudian Read TID), EPC ini mungkin EPC lama (jika tag bekas) atau yang baru ditulis.
                    _scannedEpcDuringProcess.postValue(epc)
                    // Jika write berhasil, SDK yang seharusnya memberitahu, bukan scan ini.
                }
                TaggingState.READING_TID -> {
                    _scannedEpcDuringProcess.postValue(epc) // EPC dari tag yang TID-nya dibaca
                }
                else -> {
                    // Tag terdeteksi di luar proses aktif, bisa diabaikan atau ditangani jika perlu
                    Log.i("BookTaggingVM", "Tag terdeteksi di luar proses tagging: $epc")
                }
            }
        }

        sdkManager.onUhfInventoryFinished = {
            // Mungkin tidak terlalu relevan untuk proses write per tag,
            // tapi bisa berguna jika ada mode "scan tag terdekat" sebelum write.
            Log.d("BookTaggingVM", "SDK onUhfInventoryFinished")
        }

        sdkManager.onError = { errorMessage ->
            _isLoading.postValue(false)
            _statusMessage.postValue("SDK Error: $errorMessage")
            _taggingState.postValue(TaggingState.ERROR_SDK)
            // Pertimbangkan untuk mereset state jika error SDK kritis
        }

        // TODO SDK: Anda perlu callback spesifik dari ChainwaySDKManager untuk hasil operasi writeTag dan readTid
        // Contoh callback yang mungkin Anda tambahkan di ChainwaySDKManager:
        // sdkManager.onTagWriteSuccess = { writtenEpc -> /* ... */ }
        // sdkManager.onTagWriteFailed = { error -> /* ... */ }
        // sdkManager.onTagReadTidSuccess = { tid -> /* ... */ }
        // sdkManager.onTagReadTidFailed = { error -> /* ... */ }
    }

    fun searchBookByItemCode(itemCode: String) {
        if (itemCode.isBlank()) {
            _statusMessage.value = "Kode item tidak boleh kosong."
            return
        }
        _isLoading.value = true
        _statusMessage.value = "Mencari buku dengan kode item: $itemCode..."
        viewModelScope.launch {
            try {
                val book = bookRepository.getBookByItemCode(itemCode)
                _currentBook.postValue(book)
                if (book != null) {
                    // TODO: Konversi itemCode ke format EPC HEX yang diinginkan
                    // Ini adalah placeholder, Anda perlu logika konversi yang sebenarnya.
                    targetEpcToWrite = convertItemCodeToEpcHex(itemCode)
                    if (targetEpcToWrite == null) {
                        _statusMessage.postValue("Gagal mengkonversi kode item ke EPC.")
                        _taggingState.postValue(TaggingState.PROCESS_FAILED)
                        _isLoading.postValue(false)
                        return@launch
                    }

                    if (book.rfidTagHex.isNullOrBlank() || book.rfidPairingStatus == "BELUM_DITAG") {
                        _taggingState.postValue(TaggingState.BOOK_FOUND_UNTAGGED)
                        _statusMessage.postValue("Buku '${book.title}' ditemukan. Siap untuk ditag.")
                    } else {
                        _taggingState.postValue(TaggingState.BOOK_FOUND_ALREADY_TAGGED)
                        _statusMessage.postValue("Buku '${book.title}' sudah memiliki RFID (${book.rfidTagHex}). Proses ulang akan menimpa.")
                    }
                } else {
                    _taggingState.postValue(TaggingState.ERROR_BOOK_NOT_FOUND)
                    _statusMessage.postValue("Buku dengan kode item '$itemCode' tidak ditemukan.")
                }
            } catch (e: Exception) {
                Log.e("BookTaggingVM", "Error searching book: ${e.message}", e)
                _statusMessage.postValue("Error database: ${e.message}")
                _taggingState.postValue(TaggingState.PROCESS_FAILED) // Atau state error DB spesifik
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun startTaggingProcess() {
        val book = _currentBook.value
        if (book == null || targetEpcToWrite == null) {
            _statusMessage.value = "Tidak ada buku yang dipilih atau target EPC tidak valid."
            _taggingState.value = TaggingState.IDLE
            return
        }

        if (!sdkManager.isDeviceReady("uhf")) {
            _statusMessage.value = "Reader UHF tidak siap atau tidak terhubung."
            _taggingState.value = TaggingState.ERROR_SDK
            return
        }

        _isLoading.value = true
        _scannedEpcDuringProcess.value = null // Bersihkan EPC dari proses sebelumnya
        _taggingState.value = TaggingState.AWAITING_TAG_PLACEMENT
        _statusMessage.value = "Letakkan tag RFID baru dekat reader, lalu tunggu..."

        viewModelScope.launch {
            // Simulasi penundaan untuk user meletakkan tag, atau SDK bisa langsung write
            // kotlinx.coroutines.delay(1000) // Opsional

            _taggingState.postValue(TaggingState.WRITING_EPC)
            _statusMessage.postValue("Menulis EPC: $targetEpcToWrite ke tag...")

            // TODO SDK: Panggil metode sdkManager untuk menulis tag.
            // Metode ini harus ASYNCHRONOUS dan memiliki callback sukses/gagal.
            // Contoh: sdkManager.writeUhfTag(epcToWrite = targetEpcToWrite!!)
            //
            // Untuk SEKARANG, kita SIMULASIKAN callback sukses setelah beberapa detik
            // Gantilah ini dengan implementasi SDK nyata dan callback-nya.

            // --- SIMULASI SDK WRITE ---
            kotlinx.coroutines.delay(2500) // Simulasi waktu operasi write
            val writeSuccess = true // Ganti dengan hasil callback SDK
            val writtenEpcFromSdk = targetEpcToWrite // Idealnya EPC yang berhasil ditulis dari SDK
            val writeErrorMessage = "Gagal menulis tag (simulasi)."

            if (writeSuccess && writtenEpcFromSdk != null) {
                _scannedEpcDuringProcess.postValue(writtenEpcFromSdk) // Simpan EPC yang (seolah) berhasil ditulis
                _statusMessage.postValue("Penulisan EPC $writtenEpcFromSdk berhasil. Membaca TID...")
                proceedToReadTid(writtenEpcFromSdk)
            } else {
                _isLoading.postValue(false)
                _statusMessage.postValue("Gagal menulis EPC: $writeErrorMessage")
                _taggingState.postValue(TaggingState.PROCESS_FAILED)
            }
            // --- AKHIR SIMULASI SDK WRITE ---
        }
    }

    private fun proceedToReadTid(writtenEpc: String) {
        _taggingState.value = TaggingState.READING_TID
        // TODO SDK: Panggil metode sdkManager untuk membaca TID dari tag yang baru ditulis.
        // Anda mungkin perlu menargetkan EPC yang baru ditulis jika SDK mendukungnya.
        // Atau, jika tag masih satu-satunya yang dekat, SDK mungkin bisa membacanya.
        // Metode ini juga harus ASYNCHRONOUS dan memiliki callback.
        // Contoh: sdkManager.readUhfTagTid(targetEpc = writtenEpc)
        //
        // SIMULASI SDK READ TID
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500) // Simulasi waktu operasi baca TID
            val readTidSuccess = true // Ganti dengan hasil callback SDK
            val tidFromSdk = "TID_SIM_${System.currentTimeMillis()}" // Ganti dengan TID dari SDK
            val readTidErrorMessage = "Gagal membaca TID (simulasi)."

            if (readTidSuccess && tidFromSdk != null) {
                _statusMessage.postValue("Pembacaan TID berhasil: $tidFromSdk. Menyimpan...")
                saveRfidDataToDatabase(writtenEpc, tidFromSdk)
            } else {
                _isLoading.postValue(false)
                _statusMessage.postValue("Gagal membaca TID: $readTidErrorMessage")
                // Keputusan: Apakah kita anggap gagal total, atau berhasil sebagian (EPC tertulis tapi TID tidak)?
                // Untuk saat ini, anggap gagal jika TID tidak terbaca setelah write berhasil.
                _taggingState.postValue(TaggingState.PROCESS_FAILED)
            }
        }
        // --- AKHIR SIMULASI SDK READ TID ---
    }

    private fun saveRfidDataToDatabase(epc: String, tid: String) {
        val bookToUpdate = _currentBook.value ?: return
        _taggingState.value = TaggingState.SAVING_TO_DB

        viewModelScope.launch {
            try {
                // Update BookMaster dengan informasi RFID baru
                // Dao Anda memiliki updateRfidDetailsByItemCode, kita bisa gunakan itu
                bookRepository.updateRfidDetailsForBook(
                    itemCode = bookToUpdate.itemCode,
                    newRfidTagHex = epc,
                    newTid = tid,
                    newPairingStatus = "BERHASIL_DITAG", // atau konstanta/enum
                    pairingTimestamp = System.currentTimeMillis()
                )

                // Refresh data buku setelah update
                val updatedBook = bookRepository.getBookByItemCode(bookToUpdate.itemCode)
                _currentBook.postValue(updatedBook)

                _isLoading.postValue(false)
                _statusMessage.postValue("RFID untuk buku '${bookToUpdate.title}' berhasil disimpan!")
                _taggingState.postValue(TaggingState.PROCESS_SUCCESS)

            } catch (e: Exception) {
                Log.e("BookTaggingVM", "Error saving RFID to DB: ${e.message}", e)
                _isLoading.postValue(false)
                _statusMessage.postValue("Gagal menyimpan ke database: ${e.message}")
                _taggingState.postValue(TaggingState.PROCESS_FAILED)
            }
        }
    }

    // Panggil ini dari tombol Scan Barcode di Activity
    fun triggerBarcodeScan() {
        if (!sdkManager.isDeviceReady("barcode")) {
            _statusMessage.value = "Scanner barcode tidak siap atau tidak terhubung."
            // _taggingState.value = TaggingState.ERROR_SDK // Opsional
            return
        }
        if (sdkManager.isUhfDeviceScanning) {
            _statusMessage.value = "Hentikan scan UHF terlebih dahulu."
            return
        }
        _statusMessage.value = "Mengaktifkan scan barcode..."
        sdkManager.startBarcodeScan()
    }


    // Fungsi placeholder, implementasikan logika konversi yang benar
    private fun convertItemCodeToEpcHex(itemCode: String): String? {
        // Logika konversi itemCode ke standar EPC HEX yang Anda gunakan.
        // Ini sangat bergantung pada format target Anda (misalnya, SGTIN, GDTI, dll.)
        // Contoh sangat sederhana (dan mungkin tidak benar untuk Anda):
        if (itemCode.length > 12) return null // Batas panjang misal
        return itemCode.padEnd(24, '0').uppercase() // Contoh kasar, perlu disesuaikan!
        // Atau jika Anda menggunakan library tertentu atau standar EPC tertentu:
        // return EpcConverter.itemCodeToHex(itemCode, Standard.SGTIN96)
    }

    fun clearProcessAndPrepareForNext() {
        _currentBook.value = null
        _scannedEpcDuringProcess.value = null
        targetEpcToWrite = null
        _statusMessage.value = "Silakan scan atau masukkan kode item buku berikutnya."
        _taggingState.value = TaggingState.IDLE
        _isLoading.value = false
        // Hentikan scan jika masih berjalan (misalnya jika user klik clear di tengah proses)
        if (sdkManager.isUhfDeviceScanning) sdkManager.stopUhfInventory()
        if (sdkManager.isBarcodeDeviceScanning) sdkManager.stopBarcodeScan()
    }

    override fun onCleared() {
        super.onCleared()
        // Penting untuk membersihkan listener SDK untuk menghindari memory leak
        // jika ViewModel di-destroy tapi SDK Manager masih ada (misal singleton)
        sdkManager.onBarcodeScanned = null
        sdkManager.onUhfTagScanned = null
        sdkManager.onUhfInventoryFinished = null
        sdkManager.onError = null
        Log.d("BookTaggingVM", "BookTaggingViewModel onCleared")
        // Pertimbangkan apakah SDK perlu di-stop/release jika halaman ini adalah satu-satunya pengguna
        // sdkManager.stopUhfInventory() // Hentikan jika sedang scan
    }
}
