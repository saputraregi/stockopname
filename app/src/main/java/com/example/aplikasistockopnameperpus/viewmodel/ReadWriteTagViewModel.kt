package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.aplikasistockopnameperpus.MyApplication
// import com.rscja.deviceapi.RFIDWithUHFUART // Akan di-uncomment nanti
// import com.rscja.deviceapi.entity.UHFTAGInfo // Akan di-uncomment nanti

class ReadWriteTagViewModel(application: Application) : AndroidViewModel(application) {

    private val myApp = application as MyApplication

    // TODO SDK: Ganti 'Any?' dengan 'RFIDWithUHFUART?'
    val uhfManager: Any? = myApp.getUhfManagerInstance()

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
        if (uhfManager == null) {
            Log.w("RWTViewModel", "Simulasi: UHF Manager tidak tersedia saat ViewModel init.")
            // Mungkin kirim event error ke UI jika perlu di tahap UI development
        } else {
            Log.i("RWTViewModel", "Simulasi: UHF Manager tersedia.")
            // TODO SDK: Set handler ke uhfManager jika SDK menggunakannya
            // (uhfManager as? RFIDWithUHFUART)?.setHandler(tagHandlerViaSDK)
        }
    }

    fun startReading(continuous: Boolean) {
        if (!myApp.isReaderOpened()) { // Menggunakan status dari MyApplication
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
            // Simulasi pembacaan tag secara berkala
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
                        if (currentList.size < 10) { // Batasi simulasi agar tidak бесконечный
                            handler.postDelayed(this, 1000) // Baca tag baru setiap 1 detik
                        } else {
                            stopContinuousReading() // Stop setelah beberapa tag
                            _readError.value = "Simulasi: Batas pembacaan kontinu tercapai."
                        }
                    }
                }
            }, 1000)
        } else { // Baca Tunggal
            Log.d("RWTViewModel", "Simulasi: Memulai baca tunggal.")
            // TODO SDK: Panggil uhfManager.inventorySingleTag() dan proses hasilnya
            handler.postDelayed({
                // Simulasi hasil pembacaan tunggal
                val success = Math.random() > 0.3 // 70% kemungkinan berhasil
                if (success) {
                    simulatedEpcCounter++
                    _lastReadEpc.value = "SIM_SINGLE_EPC_${String.format("%04d", simulatedEpcCounter)}"
                } else {
                    _lastReadEpc.value = "Tidak ada tag terdeteksi"
                    _readError.value = "Simulasi: Gagal membaca tag tunggal."
                }
                _isReadingContinuous.value = false // Selesai membaca tunggal
            }, 1500) // Delay untuk simulasi
        }
    }

    fun stopContinuousReading() {
        if (_isReadingContinuous.value == true) {
            Log.d("RWTViewModel", "Simulasi: Menghentikan baca kontinu.")
            // TODO SDK: Panggil uhfManager.stopInventory()
        }
        _isReadingContinuous.value = false
        handler.removeCallbacksAndMessages(null) // Hentikan semua simulasi callback
        if (_lastReadEpc.value == "Membaca...") {
            _lastReadEpc.value = if (_continuousEpcList.value.isNullOrEmpty()) null else "Dihentikan"
        }
    }

    fun readTargetTagForWrite() {
        if (!myApp.isReaderOpened()) {
            _writeStatus.value = Pair(false, "Simulasi: UHF Reader tidak terhubung.")
            return
        }
        _targetTagEpc.value = "Membaca target..."
        Log.d("RWTViewModel", "Simulasi: Membaca tag target untuk ditulis.")
        // TODO SDK: Implementasikan pembacaan tag tunggal untuk mendapatkan EPC target
        handler.postDelayed({
            val success = Math.random() > 0.2 // 80% kemungkinan berhasil
            if (success) {
                _targetTagEpc.value = "EPC_TARGET_${String.format("%03d", (Math.random() * 999).toInt())}"
            } else {
                _targetTagEpc.value = null // null menandakan gagal
                _writeStatus.value = Pair(false, "Simulasi: Tidak ada tag target terdeteksi.")
            }
        }, 1500)
    }

    fun writeEpcToTag(targetEpcFilter: String?, newEpcHex: String, accessPasswordHex: String) {
        if (!myApp.isReaderOpened()) {
            _writeStatus.value = Pair(false, "Simulasi: UHF Reader tidak terhubung.")
            return
        }
        Log.d("RWTViewModel", "Simulasi: Menulis EPC '$newEpcHex' ke target '$targetEpcFilter' dengan password '$accessPasswordHex'.")
        // TODO SDK: Implementasikan logika penulisan EPC ke tag
        handler.postDelayed({
            val success = Math.random() > 0.3 // 70% kemungkinan berhasil
            if (success) {
                _writeStatus.value = Pair(true, "Simulasi: Berhasil menulis EPC baru: $newEpcHex")
                _targetTagEpc.value = newEpcHex // Update EPC target ke yang baru
            } else {
                _writeStatus.value = Pair(false, "Simulasi: Gagal menulis EPC.")
            }
        }, 2000)
    }

    fun lockTagMemory(
        targetEpcFilter: String?,
        accessPasswordHex: String
        // TODO SDK: Tambahkan parameter tipe lock dan mode lock dari SDK
        // lockMemType: RFIDWithUHFUART.LockMemMode,
        // lockMode: RFIDWithUHFUART.LockMode
    ) {
        if (!myApp.isReaderOpened()) {
            _lockStatus.value = Pair(false, "Simulasi: UHF Reader tidak terhubung.")
            return
        }
        Log.d("RWTViewModel", "Simulasi: Mengunci tag '$targetEpcFilter' dengan password '$accessPasswordHex'.")
        // TODO SDK: Implementasikan logika penguncian tag
        handler.postDelayed({
            val success = Math.random() > 0.4 // 60% kemungkinan berhasil
            if (success) {
                _lockStatus.value = Pair(true, "Simulasi: Berhasil mengunci memori tag.")
            } else {
                _lockStatus.value = Pair(false, "Simulasi: Gagal mengunci memori tag.")
            }
        }, 2000)
    }

    override fun onCleared() {
        super.onCleared()
        stopContinuousReading() // Pastikan simulasi berhenti
        Log.d("ReadWriteTagViewModel", "ViewModel Cleared")
    }

    // TODO SDK: Handler ini akan menerima pesan dari SDK Chainway
    /*
    private val tagHandlerViaSDK = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                // Kasus-kasus dari SDK
                // Contoh:
                // SDK_MSG_TAG_INFO -> {
                // val uhfTagInfo = msg.obj as? UHFTAGInfo
                // ... proses uhfTagInfo ...
                // }
            }
        }
    }
    */
}

