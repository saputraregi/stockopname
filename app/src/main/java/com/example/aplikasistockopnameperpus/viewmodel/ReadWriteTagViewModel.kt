package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
// import androidx.lifecycle.viewModelScope // Tidak digunakan secara langsung di sini
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager

class ReadWriteTagViewModel(application: Application) : AndroidViewModel(application) {

    private val sdkManager: ChainwaySDKManager = (application as MyApplication).sdkManager

    // === LiveData untuk UI ===
    private val _lastReadEpc = MutableLiveData<String?>()
    val lastReadEpc: LiveData<String?> = _lastReadEpc

    private val _continuousEpcList = MutableLiveData<Set<String>>(emptySet())
    val continuousEpcList: LiveData<Set<String>> = _continuousEpcList

    private val _isReadingContinuous = MutableLiveData(false)
    val isReadingContinuous: LiveData<Boolean> = _isReadingContinuous

    private val _readError = MutableLiveData<String?>()
    val readError: LiveData<String?> = _readError

    private val _targetTagEpc = MutableLiveData<String?>() // EPC dari tag yang dibaca untuk operasi tulis/lock
    val targetTagEpc: LiveData<String?> = _targetTagEpc

    private val _writeStatus = MutableLiveData<Pair<Boolean, String?>>() // Status operasi tulis (sukses/gagal, pesan)
    val writeStatus: LiveData<Pair<Boolean, String?>> = _writeStatus

    private val _lockStatus = MutableLiveData<Pair<Boolean, String?>>() // Status operasi lock
    val lockStatus: LiveData<Pair<Boolean, String?>> = _lockStatus

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isReaderReady = MutableLiveData<Boolean>(sdkManager.isDeviceReady("uhf"))
    val isReaderReady: LiveData<Boolean> = _isReaderReady


    init {
        Log.d(TAG, "ViewModel initialized. Setting up SDK listeners.")
        setupSdkListeners()
        _isReaderReady.postValue(sdkManager.isDeviceReady("uhf")) // Update awal
    }

    private fun setupSdkListeners() {
        sdkManager.onDeviceStatusChanged = { isConnected, deviceType ->
            if (deviceType == "UHF") {
                _isReaderReady.postValue(isConnected)
                if (!isConnected) {
                    stopAllOperationsAndNotify(getApplication<Application>().getString(R.string.error_reader_disconnected))
                }
            }
        }

        sdkManager.onUhfTagScanned = { epc -> // Untuk inventory kontinu
            if (_isReadingContinuous.value == true) {
                _lastReadEpc.postValue(epc)
                val currentList = _continuousEpcList.value?.toMutableSet() ?: mutableSetOf()
                if (currentList.add(epc)) {
                    _continuousEpcList.postValue(currentList)
                }
            }
        }

        // Callback dari readSingleUhfTagEpcNearby()
        sdkManager.onSingleUhfTagEpcRead = { epc, tid -> // tid bisa null
            _isLoading.postValue(false)
            // Cek apakah ini hasil dari readTargetTagForWrite atau startReading(false)
            if (_targetTagEpc.value == getApplication<Application>().getString(R.string.status_membaca_target)) {
                _targetTagEpc.postValue(epc)
                // Jika Anda ingin juga menampilkan TID yang terbaca bersamaan (jika ada) saat membaca target:
                val message = if (!tid.isNullOrBlank()) {
                    getApplication<Application>().getString(R.string.status_target_tag_found_with_tid, epc, tid)
                } else {
                    getApplication<Application>().getString(R.string.status_target_tag_found, epc)
                }
                _writeStatus.postValue(Pair(true, message)) // Pesan sukses untuk UI Write
            } else if (_isReadingContinuous.value == false) { // Hasil dari startReading(false)
                _lastReadEpc.postValue(epc)
                // Jika ingin menampilkan TID juga di UI Read Fragment:
                // _readError.postValue("TID: $tid") // Contoh, sesuaikan dengan UI Anda
            }
            _isReadingContinuous.postValue(false) // Pastikan ini disetel false untuk operasi baca tunggal
        }

        sdkManager.onSingleUhfTagReadFailed = { errorMessage ->
            _isLoading.postValue(false)
            _isReadingContinuous.postValue(false) // Pastikan ini disetel false
            if (_targetTagEpc.value == getApplication<Application>().getString(R.string.status_membaca_target)) {
                _targetTagEpc.postValue(null)
                _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.error_failed_to_read_target_tag, errorMessage)))
            } else { // Gagal saat startReading(false)
                _lastReadEpc.postValue(null)
                _readError.postValue(getApplication<Application>().getString(R.string.error_failed_to_read_single_tag, errorMessage))
            }
        }

        sdkManager.onUhfInventoryFinished = { // Untuk inventory kontinu
            _isReadingContinuous.postValue(false)
            _isLoading.postValue(false)
            // Logika untuk _lastReadEpc setelah inventory stop:
            if (_lastReadEpc.value == getApplication<Application>().getString(R.string.status_membaca)) { // Jika "Membaca..."
                _lastReadEpc.postValue(
                    if (_continuousEpcList.value.isNullOrEmpty()) {
                        getApplication<Application>().getString(R.string.status_no_tags_found_after_scan) // "Tidak ada tag ditemukan"
                    } else {
                        // Jika ada item, _lastReadEpc sudah berisi EPC terakhir.
                        // Mungkin tidak perlu mengubahnya, atau tampilkan pesan "Dihentikan".
                        getApplication<Application>().getString(R.string.uhf_scan_stopped)
                    }
                )
            }
            // Jika sudah ada EPC yang terbaca, _lastReadEpc tidak diubah di sini, biarkan EPC terakhir.
        }

        sdkManager.onError = { errorMessage ->
            _isLoading.postValue(false)
            if (_isReadingContinuous.value == true) _isReadingContinuous.postValue(false)

            // Berikan pesan error yang lebih spesifik jika memungkinkan,
            // atau biarkan Fragment yang menentukan konteks errornya.
            val genericSdkError = getApplication<Application>().getString(R.string.sdk_error_prefix, errorMessage)
            _readError.postValue(genericSdkError)
            _writeStatus.postValue(Pair(false, genericSdkError))
            _lockStatus.postValue(Pair(false, genericSdkError))
        }

        sdkManager.onTagWriteSuccess = { writtenEpc ->
            _isLoading.postValue(false)
            _writeStatus.postValue(Pair(true, getApplication<Application>().getString(R.string.status_write_epc_success, writtenEpc)))
            _targetTagEpc.postValue(writtenEpc) // EPC target sekarang adalah yang baru ditulis
            _lastReadEpc.postValue(writtenEpc) // Update juga lastReadEpc
        }

        sdkManager.onTagWriteFailed = { error ->
            _isLoading.postValue(false)
            _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.error_write_epc_failed, error)))
        }

        sdkManager.onTagLockSuccess = {
            _isLoading.postValue(false)
            _lockStatus.postValue(Pair(true, getApplication<Application>().getString(R.string.status_lock_tag_success)))
        }

        sdkManager.onTagLockFailed = { error ->
            _isLoading.postValue(false)
            _lockStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.error_lock_tag_failed, error)))
        }

        sdkManager.onUhfOperationStopped = {
            // Ini adalah callback umum. Pastikan state loading dan reading direset.
            if (_isLoading.value == true) _isLoading.postValue(false)
            if (_isReadingContinuous.value == true) _isReadingContinuous.postValue(false)
            Log.d(TAG, "onUhfOperationStopped: isLoading=${_isLoading.value}, isReadingContinuous=${_isReadingContinuous.value}")
        }
    }

    private fun stopAllOperationsAndNotify(errorMessage: String) {
        sdkManager.stopUhfOperation()
        _isReadingContinuous.postValue(false)
        _isLoading.postValue(false)
        _readError.postValue(errorMessage)
        _writeStatus.postValue(Pair(false, errorMessage))
        _lockStatus.postValue(Pair(false, errorMessage))
        _lastReadEpc.postValue(null) // Reset EPC terakhir
        _targetTagEpc.postValue(null) // Reset target
        _continuousEpcList.postValue(emptySet()) // Kosongkan daftar kontinu
    }

    fun startReading(continuous: Boolean) {
        if (_isReaderReady.value != true) {
            _readError.postValue(getApplication<Application>().getString(R.string.reader_not_ready))
            return
        }
        if (_isLoading.value == true) {
            _readError.postValue(getApplication<Application>().getString(R.string.error_operation_in_progress))
            return
        }

        _isLoading.postValue(true)
        _isReadingContinuous.postValue(continuous)
        _lastReadEpc.postValue(getApplication<Application>().getString(R.string.status_membaca))
        _readError.postValue(null)
        _targetTagEpc.postValue(null) // Reset target jika memulai pembacaan umum
        _writeStatus.postValue(Pair(false, null)) // Reset status tulis
        _lockStatus.postValue(Pair(false, null)) // Reset status lock

        if (continuous) {
            _continuousEpcList.value = emptySet()
            Log.d(TAG, "Starting continuous reading (inventory) via SDK.")
            sdkManager.startUhfInventory()
        } else { // Baca Tunggal EPC
            Log.d(TAG, "Starting single EPC read (nearby) via SDK.")
            sdkManager.readSingleUhfTagEpcNearby()
            // Hasil akan ditangani oleh onSingleUhfTagEpcRead / onSingleUhfTagReadFailed
        }
    }

    fun stopContinuousReading() {
        // Hanya hentikan jika memang sedang membaca (baik kontinu maupun proses loading lainnya)
        if (_isReadingContinuous.value == true || _isLoading.value == true) {
            Log.d(TAG, "Stopping read operation via SDK.")
            sdkManager.stopUhfOperation()
            // State _isReadingContinuous dan _isLoading akan diupdate oleh callback SDK
            // (onUhfInventoryFinished atau onUhfOperationStopped)
        } else {
            Log.d(TAG, "StopContinuousReading called, but not actively reading or loading.")
        }
    }

    fun readTargetTagForWrite() {
        if (_isReaderReady.value != true) {
            _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.reader_not_ready)))
            _targetTagEpc.postValue(null)
            return
        }
        if (_isLoading.value == true) {
            _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.error_operation_in_progress)))
            return
        }

        _isLoading.postValue(true)
        _isReadingContinuous.postValue(false) // Ini bukan pembacaan kontinu
        _targetTagEpc.postValue(getApplication<Application>().getString(R.string.status_membaca_target))
        _writeStatus.postValue(Pair(false, null))
        _readError.postValue(null)
        _lastReadEpc.postValue(null)
        _lockStatus.postValue(Pair(false, null))

        Log.d(TAG, "Reading target tag for write operation via SDK.")
        sdkManager.readSingleUhfTagEpcNearby()
        // Hasil akan ditangani oleh onSingleUhfTagEpcRead / onSingleUhfTagReadFailed
    }

    fun writeEpcToTag(targetEpcFilter: String?, newEpcHex: String, accessPasswordHex: String) {
        if (_isReaderReady.value != true) {
            _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.reader_not_ready)))
            return
        }

        // Gunakan targetEpcFilter jika ada, jika tidak, gunakan _targetTagEpc.value
        // Jika keduanya null/kosong, SDK Manager akan menulis ke tag terdekat (jika currentEpcFilter null)
        val actualTargetEpcForFilter = targetEpcFilter ?: _targetTagEpc.value

        if (newEpcHex.isBlank() || newEpcHex.length != 24 || !newEpcHex.matches(Regex("^[0-9A-Fa-f]*$"))) {
            _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.error_invalid_epc_format_specific_length, 24)))
            return
        }
        if (_isLoading.value == true) {
            _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.error_operation_in_progress)))
            return
        }

        _isLoading.postValue(true)
        _writeStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.status_writing_epc, newEpcHex)))
        Log.d(TAG, "Writing EPC '$newEpcHex' to target (filter: '$actualTargetEpcForFilter') via SDK.")

        sdkManager.writeUhfTag(
            epcDataHex = newEpcHex,
            currentEpcFilter = actualTargetEpcForFilter, // Teruskan filter yang sudah ditentukan
            passwordAccess = accessPasswordHex.ifEmpty { "00000000" }
        )
        // Hasilnya akan ditangani oleh onTagWriteSuccess / onTagWriteFailed
    }

    fun lockTagMemory(
        targetEpcFilter: String?, // EPC tag yang akan di-lock
        accessPasswordHex: String,
        lockBankInt: Int,      // Parameter dari UI, akan diteruskan ke SDK Manager
        lockActionInt: Int     // Parameter dari UI, akan diteruskan ke SDK Manager
    ) {
        if (_isReaderReady.value != true) {
            _lockStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.reader_not_ready)))
            return
        }

        // Untuk lock, EPC target (filter) biasanya wajib. Gunakan yang dari parameter atau yang sudah dibaca.
        val actualTargetEpcForLock = targetEpcFilter ?: _targetTagEpc.value

        if (actualTargetEpcForLock.isNullOrEmpty()) {
            _lockStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.error_no_target_epc_for_lock)))
            return
        }
        if (_isLoading.value == true) {
            _lockStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.error_operation_in_progress)))
            return
        }

        _isLoading.postValue(true)
        _lockStatus.postValue(Pair(false, getApplication<Application>().getString(R.string.status_locking_tag)))
        Log.d(TAG, "Locking tag '$actualTargetEpcForLock' via SDK. Bank: $lockBankInt, Action: $lockActionInt")

        // Perhatikan nama parameter di sdkManager.lockUhfTag
        sdkManager.lockUhfTag(
            targetEpc = actualTargetEpcForLock, // Pastikan ini tidak null di sini
            passwordAccess = accessPasswordHex.ifEmpty { "00000000" },
            lockBank = lockBankInt,     // Nama parameter di SDKManager adalah 'lockBank'
            lockAction = lockActionInt  // Nama parameter di SDKManager adalah 'lockAction'
        )
        // Hasilnya akan ditangani oleh onTagLockSuccess / onTagLockFailed
    }

    fun clearContinuousListFromUI() {
        _continuousEpcList.postValue(emptySet())
        // _lastReadEpc.postValue(null) // Opsional, tidak selalu perlu di-clear di sini
        Log.d(TAG, "Continuous EPC list cleared from ViewModel.")
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel Cleared. Stopping SDK operations and clearing listeners.")
        sdkManager.stopUhfOperation() // Hentikan operasi UHF jika ada

        // Membersihkan semua listener dari SDKManager
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
    }

    companion object {
        private const val TAG = "RWTViewModel" // Tag untuk logging ViewModel ini
    }
}

