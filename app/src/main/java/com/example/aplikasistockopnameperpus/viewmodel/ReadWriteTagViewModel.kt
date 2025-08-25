package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
// import android.os.Handler // Tidak lagi dibutuhkan untuk simulasi
// import android.os.Looper // Tidak lagi dibutuhkan untuk simulasi
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
// Jika Anda perlu menggunakan tipe enum dari SDK untuk parameter lock di ViewModel (opsional):
// import com.rscja.deviceapi.RFIDWithUHFUART

class ReadWriteTagViewModel(application: Application) : AndroidViewModel(application) {

    private val sdkManager: ChainwaySDKManager = (application as MyApplication).sdkManager

    // Tidak lagi memerlukan instance uhfManager langsung di sini, semua melalui sdkManager.
    // val uhfManager: Any? // DIHAPUS

    // === LiveData untuk UI ===
    private val _lastReadEpc = MutableLiveData<String?>()
    val lastReadEpc: LiveData<String?> = _lastReadEpc

    private val _continuousEpcList = MutableLiveData<Set<String>>(emptySet())
    val continuousEpcList: LiveData<Set<String>> = _continuousEpcList

    private val _isReadingContinuous = MutableLiveData(false)
    val isReadingContinuous: LiveData<Boolean> = _isReadingContinuous

    private val _readError = MutableLiveData<String?>() // Untuk error terkait operasi baca
    val readError: LiveData<String?> = _readError

    private val _targetTagEpc = MutableLiveData<String?>() // EPC dari tag yang dibaca untuk operasi tulis/lock
    val targetTagEpc: LiveData<String?> = _targetTagEpc

    private val _writeStatus = MutableLiveData<Pair<Boolean, String?>>() // Status operasi tulis (sukses/gagal, pesan)
    val writeStatus: LiveData<Pair<Boolean, String?>> = _writeStatus

    private val _lockStatus = MutableLiveData<Pair<Boolean, String?>>() // Status operasi lock
    val lockStatus: LiveData<Pair<Boolean, String?>> = _lockStatus

    // LiveData untuk mengontrol status loading umum jika diperlukan
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData untuk status kesiapan reader (opsional, bisa digunakan oleh Fragment)
    private val _isReaderReady = MutableLiveData<Boolean>(sdkManager.isDeviceReady("uhf"))
    val isReaderReady: LiveData<Boolean> = _isReaderReady


    init {
        Log.d("RWTViewModel", "ViewModel initialized. Setting up SDK listeners.")
        setupSdkListeners()
        // Update status reader awal
        _isReaderReady.postValue(sdkManager.isDeviceReady("uhf"))
    }

    private fun setupSdkListeners() {
        sdkManager.onDeviceStatusChanged = { isConnected, deviceType ->
            if (deviceType == "UHF") {
                _isReaderReady.postValue(isConnected)
                if (!isConnected) {
                    // Jika reader terputus, hentikan operasi yang mungkin berjalan
                    // dan beri tahu pengguna.
                    stopAllOperationsAndNotify("Koneksi reader UHF terputus.")
                }
            }
        }

        sdkManager.onUhfTagScanned = { epc ->
            if (_isReadingContinuous.value == true) { // Hanya proses jika sedang dalam mode baca kontinu
                _lastReadEpc.postValue(epc)
                val currentList = _continuousEpcList.value?.toMutableSet() ?: mutableSetOf()
                if (currentList.add(epc)) {
                    _continuousEpcList.postValue(currentList)
                }
            }
        }

        sdkManager.onSingleUhfTagEpcRead = { epc ->
            // Ini untuk hasil pembacaan EPC tunggal, misal untuk readTargetTagForWrite
            _isLoading.postValue(false) // Selesai loading
            _targetTagEpc.postValue(epc)
            _writeStatus.postValue(Pair(true, "Tag target ditemukan: $epc")) // Pesan sukses untuk UI
        }

        sdkManager.onSingleUhfTagReadFailed = { errorMessage ->
            _isLoading.postValue(false)
            _targetTagEpc.postValue(null) // Tidak ada target yang ditemukan
            _writeStatus.postValue(Pair(false, "Gagal membaca tag target: $errorMessage"))
        }

        sdkManager.onUhfInventoryFinished = {
            _isReadingContinuous.postValue(false)
            _isLoading.postValue(false)
            if (_lastReadEpc.value == getApplication<Application>().getString(R.string.status_membaca)) { // Bandingkan dengan string resource
                _lastReadEpc.postValue(
                    if (_continuousEpcList.value.isNullOrEmpty()) null
                    else getApplication<Application>().getString(R.string.uhf_scan_stopped) // Atau string "Dihentikan"
                )
            }
        }

        sdkManager.onError = { errorMessage ->
            _isLoading.postValue(false)
            // Error ini bisa berlaku untuk berbagai operasi, jadi update semua status error
            _readError.postValue("SDK Error: $errorMessage")
            _writeStatus.postValue(Pair(false, "SDK Error: $errorMessage"))
            _lockStatus.postValue(Pair(false, "SDK Error: $errorMessage"))
            // Pastikan operasi dihentikan jika ada error SDK yang fatal
            if (_isReadingContinuous.value == true) {
                _isReadingContinuous.postValue(false) // Hentikan UI pembacaan kontinu
            }
        }

        sdkManager.onTagWriteSuccess = { writtenEpc ->
            _isLoading.postValue(false)
            _writeStatus.postValue(Pair(true, "Berhasil menulis EPC baru: $writtenEpc"))
            _targetTagEpc.postValue(writtenEpc) // EPC target sekarang adalah yang baru ditulis
        }

        sdkManager.onTagWriteFailed = { error ->
            _isLoading.postValue(false)
            _writeStatus.postValue(Pair(false, "Gagal menulis EPC: $error"))
        }

        sdkManager.onTagLockSuccess = {
            _isLoading.postValue(false)
            _lockStatus.postValue(Pair(true, "Berhasil mengunci memori tag."))
        }

        sdkManager.onTagLockFailed = { error ->
            _isLoading.postValue(false)
            _lockStatus.postValue(Pair(false, "Gagal mengunci memori tag: $error"))
        }

        sdkManager.onUhfOperationStopped = {
            // Callback umum jika operasi UHF dihentikan (bisa karena sukses, gagal, atau dibatalkan)
            // Pastikan state loading dan reading direset jika belum.
            if (_isLoading.value == true) _isLoading.postValue(false)
            if (_isReadingContinuous.value == true) _isReadingContinuous.postValue(false)
        }
    }

    private fun stopAllOperationsAndNotify(errorMessage: String) {
        sdkManager.stopUhfOperation() // Hentikan operasi SDK apa pun
        _isReadingContinuous.postValue(false)
        _isLoading.postValue(false)
        _readError.postValue(errorMessage)
        _writeStatus.postValue(Pair(false, errorMessage))
        _lockStatus.postValue(Pair(false, errorMessage))
    }

    fun startReading(continuous: Boolean) {
        if (!_isReaderReady.value!!) { // Gunakan status reader dari LiveData
            _readError.postValue(getApplication<Application>().getString(R.string.reader_not_ready))
            return
        }
        if (_isLoading.value == true) {
            _readError.postValue("Operasi lain sedang berjalan.")
            return
        }

        _isLoading.postValue(true)
        _isReadingContinuous.postValue(continuous)
        _lastReadEpc.postValue(getApplication<Application>().getString(R.string.status_membaca))
        _readError.postValue(null) // Hapus error sebelumnya

        if (continuous) {
            _continuousEpcList.value = emptySet() // Kosongkan list untuk pembacaan kontinu baru
            Log.d("RWTViewModel", "Memulai baca kontinu via SDK.")
            sdkManager.startUhfInventory()
        } else { // Baca Tunggal EPC
            Log.d("RWTViewModel", "Memulai baca tunggal EPC via SDK.")
            // Menggunakan fungsi yang didedikasikan untuk membaca EPC tunggal dari SDK Manager
            sdkManager.readSingleUhfTagEpc()
            // Hasilnya akan ditangani oleh callback onSingleUhfTagEpcRead / onSingleUhfTagReadFailed
            // yang akan mengupdate _lastReadEpc dan _isLoading.
            // Untuk baca tunggal, _isReadingContinuous akan di-set false oleh onSingleUhfTagEpcRead/Failed,
            // atau oleh onUhfOperationStopped.
        }
    }

    fun stopContinuousReading() {
        // Hanya hentikan jika memang sedang membaca kontinu
        if (_isReadingContinuous.value == true || _isLoading.value == true) {
            Log.d("RWTViewModel", "Menghentikan operasi baca via SDK.")
            sdkManager.stopUhfOperation() // Ini akan memicu onUhfInventoryFinished atau onUhfOperationStopped
        }
        // State _isReadingContinuous dan _isLoading akan diupdate oleh callback SDK
    }

    fun readTargetTagForWrite() {
        if (!_isReaderReady.value!!) {
            _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.reader_not_ready)))
            _targetTagEpc.postValue(null)
            return
        }
        if (_isLoading.value == true) {
            _writeStatus.postValue(Pair(false, "Operasi lain sedang berjalan."))
            return
        }

        _isLoading.postValue(true)
        _targetTagEpc.postValue(getApplication<Application>().getString(R.string.status_membaca_target))
        _writeStatus.postValue(Pair(false, null)) // Hapus status tulis sebelumnya
        Log.d("RWTViewModel", "Membaca tag target untuk ditulis via SDK.")
        sdkManager.readSingleUhfTagEpc() // Gunakan fungsi baca EPC tunggal
        // Hasilnya akan ditangani oleh onSingleUhfTagEpcRead / onSingleUhfTagReadFailed
    }

    fun writeEpcToTag(targetEpcFilter: String?, newEpcHex: String, accessPasswordHex: String) {
        if (!_isReaderReady.value!!) {
            _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.reader_not_ready)))
            return
        }
        if (targetEpcFilter.isNullOrEmpty()) {
            _writeStatus.postValue(Pair(false, "EPC target belum ditentukan. Baca tag target terlebih dahulu."))
            return
        }
        if (_isLoading.value == true) {
            _writeStatus.postValue(Pair(false, "Operasi lain sedang berjalan."))
            return
        }

        _isLoading.postValue(true)
        _writeStatus.postValue(Pair(false, "Menulis EPC: $newEpcHex...")) // Status proses
        Log.d("RWTViewModel", "Menulis EPC '$newEpcHex' ke target '$targetEpcFilter' via SDK.")
        sdkManager.writeUhfTag(
            epcToWrite = newEpcHex,
            currentEpc = targetEpcFilter, // Untuk filter di SDK Manager jika diimplementasikan
            passwordAccess = accessPasswordHex.ifEmpty { "00000000" } // Default password jika kosong
        )
        // Hasilnya akan ditangani oleh onTagWriteSuccess / onTagWriteFailed
    }

    fun lockTagMemory(
        targetEpcFilter: String?,
        accessPasswordHex: String,
        lockBankInt: Int,      // Terima integer dari Fragment
        lockActionInt: Int     // Terima integer dari Fragment
    ) {
        if (!_isReaderReady.value!!) {
            _lockStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.reader_not_ready)))
            return
        }
        if (targetEpcFilter.isNullOrEmpty()) {
            _lockStatus.postValue(Pair(false, "EPC target belum ditentukan. Baca tag target terlebih dahulu."))
            return
        }
        if (_isLoading.value == true) {
            _lockStatus.postValue(Pair(false, "Operasi lain sedang berjalan."))
            return
        }

        _isLoading.postValue(true)
        _lockStatus.postValue(Pair(false, "Mengunci tag...")) // Status proses
        Log.d("RWTViewModel", "Mengunci tag '$targetEpcFilter' via SDK. Bank: $lockBankInt, Aksi: $lockActionInt")
        sdkManager.lockUhfTag(
            targetEpc = targetEpcFilter,
            passwordAccess = accessPasswordHex.ifEmpty { "00000000" },
            lockBankInt = lockBankInt,
            lockActionInt = lockActionInt
        )
        // Hasilnya akan ditangani oleh onTagLockSuccess / onTagLockFailed
    }

    fun clearContinuousListFromUI() {
        _continuousEpcList.postValue(emptySet())
        // _lastReadEpc.postValue(null) // Opsional, tergantung perilaku yang diinginkan
        Log.d("RWTViewModel", "Daftar EPC kontinu dibersihkan dari ViewModel.")
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("RWTViewModel", "ViewModel Cleared. Menghentikan operasi SDK dan membersihkan listener.")
        sdkManager.stopUhfOperation() // Pastikan semua operasi UHF dihentikan

        // Hapus semua listener dari sdkManager untuk mencegah memory leak
        sdkManager.onDeviceStatusChanged = null
        sdkManager.onUhfTagScanned = null
        sdkManager.onSingleUhfTagEpcRead = null
        sdkManager.onSingleUhfTagReadFailed = null
        sdkManager.onUhfInventoryFinished = null
        sdkManager.onError = null
        sdkManager.onTagWriteSuccess = null
        sdkManager.onTagWriteFailed = null
        sdkManager.onTagLockSuccess = null
        sdkManager.onTagLockFailed = null
        sdkManager.onUhfOperationStopped = null
        // Tidak perlu memanggil sdkManager.releaseResources() di sini jika itu
        // dikelola oleh Application's lifecycle (misalnya, di MyApplication.onTerminate())
        // atau oleh Activity utama yang bertanggung jawab atas koneksi awal.
    }
}
