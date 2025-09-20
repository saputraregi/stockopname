package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.rscja.deviceapi.entity.RadarLocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val sdkManager: ChainwaySDKManager = (application as MyApplication).sdkManager

    private val _detectedTags = MutableLiveData<List<RadarLocationEntity>>(emptyList())
    val detectedTags: LiveData<List<RadarLocationEntity>> = _detectedTags

    private val _isTracking = MutableLiveData<Boolean>(false)
    val isTracking: LiveData<Boolean> = _isTracking

    private val _readerAngle = MutableLiveData<Int>(0)
    val readerAngle: LiveData<Int> = _readerAngle

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _searchRangeProgress = MutableLiveData<Int>(5)
    val searchRangeProgress: LiveData<Int> = _searchRangeProgress

    // --- BARU: LiveData untuk EPC Target ---
    private val _targetEpcForRadar = MutableLiveData<String?>()
    val targetEpcForRadar: LiveData<String?> = _targetEpcForRadar
    // -----------------------------------------

    init {
        setupSdkManagerCallbacks()
    }

    private fun setupSdkManagerCallbacks() {
        sdkManager.onUhfRadarDataUpdated = { tags, angle ->
            _detectedTags.value = tags
            _readerAngle.value = angle
        }
        sdkManager.onUhfRadarError = { message ->
            _toastMessage.value = message
            _isTracking.value = false
            Log.e("RadarViewModel", "SDKManager Radar Error: $message")
        }
        sdkManager.onUhfRadarStarted = {
            _isTracking.value = true
            Log.d("RadarViewModel", "SDKManager Radar Started callback")
        }
        sdkManager.onUhfRadarStopped = {
            _isTracking.value = false
            _detectedTags.value = emptyList()
            _readerAngle.value = 0
            Log.d("RadarViewModel", "SDKManager Radar Stopped callback")
        }
    }

    fun initRadarFeature(context: Context) {
        if (!sdkManager.isDeviceReady("uhf")) {
            Log.d("RadarViewModel", "Reader UHF belum siap, akan diinisialisasi saat startTracking jika perlu.")
        } else {
            Log.d("RadarViewModel", "Reader UHF sudah siap.")
        }
    }

    // --- BARU: Fungsi untuk mengatur EPC target dari Activity ---
    fun setTargetEpc(epc: String?) {
        _targetEpcForRadar.value = epc
        // Jika sedang tracking dan EPC target berubah, mungkin perlu stop dan start ulang,
        // atau biarkan pengguna yang mengontrolnya. Untuk sekarang, hanya update nilai.
        // Jika ingin otomatis restart jika sedang tracking:
        // if (_isTracking.value == true && !epc.isNullOrBlank()) {
        //     stopTracking() // Hentikan dulu
        //     // Mungkin perlu delay sedikit sebelum start lagi
        //     viewModelScope.launch {
        //         delay(200)
        //         startTracking(getApplication<Application>().applicationContext) // Panggil startTracking yang baru
        //     }
        // }
    }
    // ----------------------------------------------------

    // Modifikasi startTracking agar menggunakan _targetEpcForRadar.value
    fun startTracking(context: Context) { // Hapus parameter targetEpc dari sini
        val currentTargetEpc = _targetEpcForRadar.value // Ambil dari LiveData

        if (_isTracking.value == true || sdkManager.isUhfRadarActive) {
            _toastMessage.value = "Pelacakan sudah berjalan."
            return
        }
        //if (currentTargetEpc.isNullOrBlank() && searchRangeProgress.value != 0) { // Jika target tidak kosong & bukan mode "cari semua"
        //    _toastMessage.value = "Masukkan atau pilih EPC target untuk dilacak."
        //    return
        //}

        _detectedTags.value = emptyList()
        _readerAngle.value = 0

        // Jika searchRangeProgress.value == 0 (atau nilai khusus yang menandakan "cari semua"),
        // maka targetEpc bisa null. Jika tidak, targetEpc harus ada.
        val epcToUseForSdk = if (searchRangeProgress.value == 0) null else currentTargetEpc

        Log.d("RadarViewModel", "Memulai tracking untuk EPC: $epcToUseForSdk (Original Target: $currentTargetEpc, Range: ${searchRangeProgress.value})")
        sdkManager.startUhfRadar(context.applicationContext, epcToUseForSdk)
    }

    fun stopTracking() {
        if (_isTracking.value == false && !sdkManager.isUhfRadarActive) {
            return
        }
        sdkManager.stopUhfRadar()
    }

    fun setSearchParameter(progress: Int) {
        val oldProgress = _searchRangeProgress.value
        _searchRangeProgress.value = progress
        val sdkParameter = 35 - progress
        viewModelScope.launch(Dispatchers.IO) {
            val success = sdkManager.setUhfRadarDynamicDistance(sdkParameter)
            if (success) {
                Log.d("RadarViewModel", "Parameter jarak dinamis radar berhasil diatur ke: $sdkParameter")
                // Jika sedang tracking dan progress diubah ke "cari semua" (misal progress == 0)
                // atau dari "cari semua" ke spesifik, mungkin perlu restart tracking.
                // Atau jika EPC target kosong dan progress bukan "cari semua".
                if (_isTracking.value == true) {
                    if ((oldProgress != 0 && progress == 0) || (oldProgress == 0 && progress != 0 && !_targetEpcForRadar.value.isNullOrBlank())) {
                        launch(Dispatchers.Main) {
                            _toastMessage.postValue("Mode pencarian diubah, memulai ulang radar...")
                            stopTracking()
                            delay(200) // Beri waktu SDK untuk stop
                            startTracking(getApplication<Application>().applicationContext)
                        }
                    }
                }
            } else {
                Log.e("RadarViewModel", "Gagal mengatur parameter jarak dinamis radar: $sdkParameter")
                _toastMessage.postValue("Gagal mengatur parameter jarak.")
            }
        }
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("RadarViewModel", "onCleared_isTracking: ${_isTracking.value}, isSdkRadarActive: ${sdkManager.isUhfRadarActive}")
        if (sdkManager.isUhfRadarActive) {
            sdkManager.stopUhfRadar()
        }
        sdkManager.onUhfRadarDataUpdated = null
        sdkManager.onUhfRadarError = null
        sdkManager.onUhfRadarStarted = null
        sdkManager.onUhfRadarStopped = null
        Log.d("RadarViewModel", "ViewModel cleared and listeners removed.")
    }
}

