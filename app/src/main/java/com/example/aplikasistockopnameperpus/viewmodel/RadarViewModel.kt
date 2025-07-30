package com.example.aplikasistockopnameperpus.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Import SDK Chainway
// import com.rscja.deviceapi.RFIDWithUHFUART // Atau kelas utama UHF reader Anda
// import com.rscja.deviceapi.entity.RadarLocationEntity
// import com.rscja.deviceapi.interfaces.IUHFRadarLocationCallback
// import com.rscja.deviceapi.exception.ConfigurationException

// --- DATA CLASS UNTUK SDK (CONTOH JIKA SDK ANDA BERBEDA) ---
// Jika SDK Anda tidak memiliki RadarLocationEntity, buat data class serupa
// -----------------------------------------------------------


class RadarViewModel : ViewModel() {

    // Ganti MyRadarLocationEntity dengan kelas dari SDK Anda, misal: com.rscja.deviceapi.entity.RadarLocationEntity
    private val _detectedTags = MutableLiveData<List<MyRadarLocationEntity>>(emptyList())
    val detectedTags: LiveData<List<MyRadarLocationEntity>> = _detectedTags

    private val _isTracking = MutableLiveData<Boolean>(false)
    val isTracking: LiveData<Boolean> = _isTracking

    private val _readerAngle = MutableLiveData<Int>(0)
    val readerAngle: LiveData<Int> = _readerAngle

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _searchRangeProgress = MutableLiveData<Int>(5) // Nilai awal sesuai layout
    val searchRangeProgress: LiveData<Int> = _searchRangeProgress


    // TODO: Inisialisasi instance UHF Reader dari SDK Chainway Anda
    // private var uhfReader: RFIDWithUHFUART? = null // Contoh
    private var isUhfReaderInitialized = false
    private var trackingJob: Job? = null


    // Panggil ini dari Activity (misal di onCreate atau saat dibutuhkan)
    fun initUhfReader(context: Context) {
        if (isUhfReaderInitialized) return
        try {
            // uhfReader = RFIDWithUHFUART.getInstance() // Atau cara inisialisasi SDK Anda
            // isUhfReaderInitialized = uhfReader?.init(context) == true // Contoh
            // if (isUhfReaderInitialized) {
            //     _toastMessage.postValue("Reader UHF berhasil diinisialisasi")
            //     // Set default power atau parameter lain jika perlu
            //     // uhfReader?.setPower(30) // Contoh
            // } else {
            //     _toastMessage.postValue("Gagal inisialisasi reader UHF")
            // }
            Log.d("RadarViewModel", "SIMULASI: Reader UHF diinisialisasi.")
            isUhfReaderInitialized = true // HAPUS INI JIKA SUDAH ADA SDK ASLI
            _toastMessage.postValue("SIMULASI: Reader UHF berhasil diinisialisasi")


        } catch (e: Exception) { // Ganti dengan ConfigurationException jika itu yang dilempar SDK
            Log.e("RadarViewModel", "Error initializing UHF Reader", e)
            _toastMessage.postValue("Error: ${e.message}")
            isUhfReaderInitialized = false
        }
    }


    fun startTracking(context: Context, targetEpc: String?) {
        // Pastikan reader sudah diinisialisasi (panggil initUhfReader jika belum)
        if (!isUhfReaderInitialized) {
            initUhfReader(context) // Coba inisialisasi jika belum
            if (!isUhfReaderInitialized) {
                _toastMessage.postValue("Reader belum siap. Coba lagi.")
                return
            }
        }

        if (_isTracking.value == true) {
            _toastMessage.postValue("Pelacakan sudah berjalan.")
            return
        }

        _isTracking.postValue(true)
        _detectedTags.postValue(emptyList()) // Bersihkan data sebelumnya

        // --- IMPLEMENTASI DENGAN SDK CHAINWAY ASLI ---
        /*
        val callback = object : IUHFRadarLocationCallback {
            override fun getLocationValue(list: MutableList<RadarLocationEntity>?) {
                viewModelScope.launch {
                    _detectedTags.postValue(list ?: emptyList())
                }
            }

            override fun getAngleValue(angle: Int) {
                 viewModelScope.launch {
                    _readerAngle.postValue(angle)
                }
            }
        }

        // Parameter untuk startRadarLocation: context, targetEpc, bank, address
        // Sesuaikan bank dan address jika perlu. Bank_EPC dan 32 adalah umum.
        // val success = uhfReader?.startRadarLocation(context, targetEpc, IUHF.Bank_EPC, 32, callback)
        // if (success != true) {
        //     _isTracking.postValue(false)
        //     _toastMessage.postValue("Gagal memulai pelacakan radar.")
        // } else {
        //     _toastMessage.postValue("Pelacakan radar dimulai...")
        // }
        */

        // --- SIMULASI PELACAKAN (HAPUS JIKA MENGGUNAKAN SDK ASLI) ---
        trackingJob = viewModelScope.launch {
            Log.d("RadarViewModel", "SIMULASI: Pelacakan dimulai untuk EPC: $targetEpc")
            var simulatedAngle = 0
            repeat(100) { // Simulasi update data
                if (!_isTracking.value!!) return@launch // Hentikan jika sudah di-stop

                val simulatedTags = mutableListOf<MyRadarLocationEntity>()
                // Tambah tag target jika ada
                if (!targetEpc.isNullOrEmpty()) {
                    simulatedTags.add(
                        MyRadarLocationEntity(
                            tag = targetEpc,
                            value = (60..95).random(), // RSSI kuat
                            angle = (simulatedAngle - 10 + (0..20).random()) % 360
                        )
                    )
                }
                // Tambah beberapa tag acak lainnya
                for (i in 1..(2..5).random()) {
                    simulatedTags.add(
                        MyRadarLocationEntity(
                            tag = "EPC_RANDOM_$i",
                            value = (20..70).random(), // RSSI lebih lemah
                            angle = (0..359).random()
                        )
                    )
                }
                _detectedTags.postValue(simulatedTags)
                _readerAngle.postValue(simulatedAngle)

                simulatedAngle = (simulatedAngle + 15) % 360 // Rotasi simulasi
                delay(500) // Update setiap 0.5 detik
            }
            _isTracking.postValue(false) // Selesai simulasi
            Log.d("RadarViewModel", "SIMULASI: Pelacakan selesai.")
        }
        // --- AKHIR BLOK SIMULASI ---
    }

    fun stopTracking() {
        if (_isTracking.value == false) return

        // --- IMPLEMENTASI DENGAN SDK CHAINWAY ASLI ---
        // uhfReader?.stopRadarLocation()
        // _isTracking.postValue(false)
        // _toastMessage.postValue("Pelacakan radar dihentikan.")

        // --- SIMULASI (HAPUS JIKA MENGGUNAKAN SDK ASLI) ---
        trackingJob?.cancel()
        _isTracking.postValue(false)
        Log.d("RadarViewModel", "SIMULASI: Pelacakan dihentikan.")
        _toastMessage.postValue("SIMULASI: Pelacakan dihentikan.")
        // --- AKHIR BLOK SIMULASI ---
    }

    fun setSearchParameter(progress: Int) {
        _searchRangeProgress.postValue(progress)
        // Logika dari Chainway: int p = 35 - progress;
        // Misal progress dari 5 (min) sampai 30 (max) di SeekBar.
        // Jika progress = 5  -> p = 30
        // Jika progress = 30 -> p = 5
        // Nilai 'p' ini yang akan dikirim ke SDK
        val sdkParameter = 35 - progress
        // --- IMPLEMENTASI DENGAN SDK CHAINWAY ASLI ---
        // val success = uhfReader?.setDynamicDistance(sdkParameter)
        // if (success == true) {
        //     Log.d("RadarViewModel", "Parameter jarak dinamis diatur ke: $sdkParameter (progress: $progress)")
        // } else {
        //     _toastMessage.postValue("Gagal mengatur parameter jarak.")
        // }

        // --- SIMULASI (HAPUS JIKA MENGGUNAKAN SDK ASLI) ---
        Log.d("RadarViewModel", "SIMULASI: Parameter jarak dinamis diatur ke: $sdkParameter (dari progress: $progress)")
        // --- AKHIR BLOK SIMULASI ---
    }


    fun releaseUhfReader() {
        // --- IMPLEMENTASI DENGAN SDK CHAINWAY ASLI ---
        // uhfReader?.free()
        // isUhfReaderInitialized = false
        // uhfReader = null
        // Log.d("RadarViewModel", "Reader UHF dilepas.")

        // --- SIMULASI (HAPUS JIKA MENGGUNAKAN SDK ASLI) ---
        Log.d("RadarViewModel", "SIMULASI: Reader UHF dilepas.")
        isUhfReaderInitialized = false
        // --- AKHIR BLOK SIMULASI ---
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking() // Pastikan tracking berhenti jika ViewModel dihancurkan
        releaseUhfReader()
    }
}

