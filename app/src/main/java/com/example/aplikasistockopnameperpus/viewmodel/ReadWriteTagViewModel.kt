package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.aplikasistockopnameperpus.MyApplication // Pastikan import ini benar
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
// import com.rscja.deviceapi.RFIDWithUHFUART // Akan di-uncomment nanti jika diperlukan
// import com.rscja.deviceapi.entity.UHFTAGInfo // Akan di-uncomment nanti jika diperlukan

class ReadWriteTagViewModel(application: Application) : AndroidViewModel(application) {

    // Inisialisasi sdkManager dari instance MyApplication.
    // Pastikan MyApplication Anda memiliki properti 'sdkManager' (atau nama lain yang sesuai)
    // yang menyediakan ChainwaySDKManager yang sudah diinisialisasi.
    private val sdkManager: ChainwaySDKManager = (application as MyApplication).sdkManager

    // Deklarasikan uhfManager, akan diinisialisasi di blok init.
    // TODO SDK: Ganti 'Any?' dengan 'RFIDWithUHFUART?' jika tipe sebenarnya diketahui dan SDK di-uncomment.
    val uhfManager: Any?

    // === Untuk ReadTagFragment ===
    private val _lastReadEpc = MutableLiveData<String?>()
    val lastReadEpc: LiveData<String?> = _lastReadEpc

    private val _continuousEpcList = MutableLiveData<Set<String>>(emptySet())
    val continuousEpcList: LiveData<Set<String>> = _continuousEpcList

    private val _isReadingContinuous = MutableLiveData(false)
    val isReadingContinuous: LiveData<Boolean> = _isReadingContinuous

    private val _readError = MutableLiveData<String?>()
    val readError: LiveData<String?> = _readError

    // === Untuk WriteTagFragment ===
    private val _targetTagEpc = MutableLiveData<String?>()
    val targetTagEpc: LiveData<String?> = _targetTagEpc

    private val _writeStatus = MutableLiveData<Pair<Boolean, String?>>()
    val writeStatus: LiveData<Pair<Boolean, String?>> = _writeStatus

    private val _lockStatus = MutableLiveData<Pair<Boolean, String?>>()
    val lockStatus: LiveData<Pair<Boolean, String?>> = _lockStatus

    private val handler = Handler(Looper.getMainLooper())
    private var simulatedEpcCounter = 0

    init {
        // Inisialisasi uhfManager di sini setelah sdkManager dijamin sudah terinisialisasi.
        // Menambahkan try-catch untuk menangani potensi error saat inisialisasi modul.
        uhfManager = try {
            sdkManager.initializeModules()
        } catch (e: Exception) {
            Log.e("RWTViewModel", "Error initializing UHF Manager: ${e.message}", e)
            null // Kembalikan null jika ada error saat inisialisasi
        }

        if (uhfManager == null) {
            Log.w("RWTViewModel", "UHF Manager tidak tersedia atau gagal diinisialisasi saat ViewModel init.")
            // Pertimbangkan untuk mengirim event error ke UI di sini jika diperlukan.
            // _readError.value = "Gagal menginisialisasi modul UHF." // Contoh
        } else {
            Log.i("RWTViewModel", "UHF Manager berhasil diinisialisasi dan tersedia.")
            // TODO SDK: Set handler ke uhfManager jika SDK menggunakannya dan sudah di-uncomment.
            // Contoh jika uhfManager adalah RFIDWithUHFUART (setelah uncomment import):
            // if (uhfManager is com.rscja.deviceapi.RFIDWithUHFUART) {
            //     uhfManager.setHandler(tagHandlerViaSDK)
            // }
        }
    }

    fun startReading(continuous: Boolean) {
        // Pastikan sdkManager.connectDevices() dipanggil pada instance sdkManager yang sudah benar.
        // Juga, periksa apakah uhfManager tidak null sebelum menggunakannya lebih lanjut jika ada operasi yang bergantung padanya.
        if (uhfManager == null) {
            _readError.value = "Simulasi: Modul UHF tidak siap."
            Log.e("RWTViewModel", "startReading dipanggil tetapi uhfManager adalah null.")
            return
        }
        if (!sdkManager.connectDevices()) { // Menggunakan status dari sdkManager yang diambil dari MyApplication
            _readError.value = "Simulasi: UHF Reader tidak terhubung."
            return
        }
        _isReadingContinuous.value = continuous
        _lastReadEpc.value = "Membaca..."
        _readError.value = null

        if (continuous) {
            _continuousEpcList.value = emptySet() // Kosongkan list
            Log.d("RWTViewModel", "Simulasi: Memulai baca kontinu.")
            // TODO SDK: Panggil uhfManager.startInventoryTag()
            // Contoh: (uhfManager as? com.rscja.deviceapi.RFIDWithUHFUART)?.startInventoryTag()
            handler.postDelayed(object : Runnable {
                override fun run() {
                    if (_isReadingContinuous.value == true) {
                        simulatedEpcCounter++
                        val newEpc = "SIM_EPC_${String.format("%04d", simulatedEpcCounter)}"
                        _lastReadEpc.value = newEpc
                        val currentList = _continuousEpcList.value?.toMutableSet() ?: mutableSetOf()
                        if (currentList.add(newEpc)) {
                            _continuousEpcList.value = currentList
                        }
                        if (currentList.size < 10) { // Batasi simulasi
                            handler.postDelayed(this, 1000)
                        } else {
                            stopContinuousReading()
                            _readError.value = "Simulasi: Batas pembacaan kontinu tercapai."
                        }
                    }
                }
            }, 1000)
        } else { // Baca Tunggal
            Log.d("RWTViewModel", "Simulasi: Memulai baca tunggal.")
            // TODO SDK: Panggil uhfManager.inventorySingleTag() dan proses hasilnya
            // Contoh: val tagInfo = (uhfManager as? com.rscja.deviceapi.RFIDWithUHFUART)?.inventorySingleTag()
            handler.postDelayed({
                val success = Math.random() > 0.3
                if (success) {
                    simulatedEpcCounter++
                    _lastReadEpc.value = "SIM_SINGLE_EPC_${String.format("%04d", simulatedEpcCounter)}"
                } else {
                    _lastReadEpc.value = "Tidak ada tag terdeteksi"
                    _readError.value = "Simulasi: Gagal membaca tag tunggal."
                }
                _isReadingContinuous.value = false
            }, 1500)
        }
    }

    fun stopContinuousReading() {
        if (_isReadingContinuous.value == true) {
            Log.d("RWTViewModel", "Simulasi: Menghentikan baca kontinu.")
            // TODO SDK: Panggil uhfManager.stopInventory()
            // Contoh: (uhfManager as? com.rscja.deviceapi.RFIDWithUHFUART)?.stopInventory()
        }
        _isReadingContinuous.value = false
        handler.removeCallbacksAndMessages(null)
        if (_lastReadEpc.value == "Membaca...") {
            _lastReadEpc.value = if (_continuousEpcList.value.isNullOrEmpty()) null else "Dihentikan"
        }
    }

    fun readTargetTagForWrite() {
        if (uhfManager == null) {
            _writeStatus.value = Pair(false, "Simulasi: Modul UHF tidak siap.")
            Log.e("RWTViewModel", "readTargetTagForWrite dipanggil tetapi uhfManager adalah null.")
            return
        }
        if (!sdkManager.connectDevices()) {
            _writeStatus.value = Pair(false, "Simulasi: UHF Reader tidak terhubung.")
            return
        }
        _targetTagEpc.value = "Membaca target..."
        Log.d("RWTViewModel", "Simulasi: Membaca tag target untuk ditulis.")
        // TODO SDK: Implementasikan pembacaan tag tunggal untuk mendapatkan EPC target
        handler.postDelayed({
            val success = Math.random() > 0.2
            if (success) {
                _targetTagEpc.value = "EPC_TARGET_${String.format("%03d", (Math.random() * 999).toInt())}"
            } else {
                _targetTagEpc.value = null
                _writeStatus.value = Pair(false, "Simulasi: Tidak ada tag target terdeteksi.")
            }
        }, 1500)
    }

    fun writeEpcToTag(targetEpcFilter: String?, newEpcHex: String, accessPasswordHex: String) {
        if (uhfManager == null) {
            _writeStatus.value = Pair(false, "Simulasi: Modul UHF tidak siap.")
            Log.e("RWTViewModel", "writeEpcToTag dipanggil tetapi uhfManager adalah null.")
            return
        }
        if (!sdkManager.connectDevices()) {
            _writeStatus.value = Pair(false, "Simulasi: UHF Reader tidak terhubung.")
            return
        }
        Log.d("RWTViewModel", "Simulasi: Menulis EPC '$newEpcHex' ke target '$targetEpcFilter' dengan password '$accessPasswordHex'.")
        // TODO SDK: Implementasikan logika penulisan EPC ke tag
        handler.postDelayed({
            val success = Math.random() > 0.3
            if (success) {
                _writeStatus.value = Pair(true, "Simulasi: Berhasil menulis EPC baru: $newEpcHex")
                _targetTagEpc.value = newEpcHex
            } else {
                _writeStatus.value = Pair(false, "Simulasi: Gagal menulis EPC.")
            }
        }, 2000)
    }

    fun lockTagMemory(
        targetEpcFilter: String?,
        accessPasswordHex: String
        // TODO SDK: Tambahkan parameter tipe lock dan mode lock dari SDK setelah uncomment
        // lockMemType: com.rscja.deviceapi.RFIDWithUHFUART.LockMemMode,
        // lockMode: com.rscja.deviceapi.RFIDWithUHFUART.LockMode
    ) {
        if (uhfManager == null) {
            _lockStatus.value = Pair(false, "Simulasi: Modul UHF tidak siap.")
            Log.e("RWTViewModel", "lockTagMemory dipanggil tetapi uhfManager adalah null.")
            return
        }
        if (!sdkManager.connectDevices()) {
            _lockStatus.value = Pair(false, "Simulasi: UHF Reader tidak terhubung.")
            return
        }
        Log.d("RWTViewModel", "Simulasi: Mengunci tag '$targetEpcFilter' dengan password '$accessPasswordHex'.")
        // TODO SDK: Implementasikan logika penguncian tag
        handler.postDelayed({
            val success = Math.random() > 0.4
            if (success) {
                _lockStatus.value = Pair(true, "Simulasi: Berhasil mengunci memori tag.")
            } else {
                _lockStatus.value = Pair(false, "Simulasi: Gagal mengunci memori tag.")
            }
        }, 2000)
    }

    fun clearContinuousListFromUI() {
        _continuousEpcList.value = emptySet()
        // Anda mungkin juga ingin mereset _lastReadEpc jika relevan
        // _lastReadEpc.value = null
        Log.d("RWTViewModel", "Daftar EPC kontinu dibersihkan dari UI.")
    }

    override fun onCleared() {
        super.onCleared()
        stopContinuousReading() // Pastikan simulasi berhenti
        Log.d("RWTViewModel", "ViewModel Cleared")
        // TODO SDK: Pertimbangkan untuk memanggil metode disconnect atau release pada sdkManager atau uhfManager jika ada
        // sdkManager.disconnect() // Contoh
        // (uhfManager as? com.rscja.deviceapi.RFIDWithUHFUART)?.free() // Contoh
    }

    // TODO SDK: Handler ini akan menerima pesan dari SDK Chainway jika diperlukan dan diaktifkan
    /*
    private val tagHandlerViaSDK = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: android.os.Message) { // Pastikan menggunakan android.os.Message
            super.handleMessage(msg)
            // Cek apakah uhfManager adalah instance yang benar sebelum menggunakannya
            // val currentUhfManager = uhfManager as? com.rscja.deviceapi.RFIDWithUHFUART ?: return

            // when (msg.what) {
                // Kasus-kasus dari SDK, contoh:
                // com.rscja.deviceapi.RFIDWithUHFUART.MSG_READ_TAG -> {
                //    val tagInfo = msg.obj as? com.rscja.deviceapi.entity.UHFTAGInfo
                //    tagInfo?.let {
                //        Log.d("RWTViewModel_SDK", "SDK Read EPC: ${it.epc}")
                //        _lastReadEpc.postValue(it.epc)
                //        if (_isReadingContinuous.value == true) {
                //            val currentList = _continuousEpcList.value?.toMutableSet() ?: mutableSetOf()
                //            if (currentList.add(it.epc)) {
                //                _continuousEpcList.postValue(currentList)
                //            }
                //        }
                //    }
                // }
                // Tambahkan case lain sesuai kebutuhan SDK
            // }
        }
    }
    */
}
