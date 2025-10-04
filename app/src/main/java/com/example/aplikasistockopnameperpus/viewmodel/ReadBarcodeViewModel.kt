package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.util.RealtimeStreamManager

class ReadBarcodeViewModel(application: Application) : AndroidViewModel(application) {

    private val sdkManager: ChainwaySDKManager = (application as MyApplication).sdkManager
    private val realtimeStreamManager: RealtimeStreamManager = (application as MyApplication).realtimeStreamManager

    // Set untuk mencegah barcode yang sama dikirim berulang kali dalam sesi ini
    private val sentBarcodeCache = mutableSetOf<String>()

    private val _scannedBarcodesList = MutableLiveData<List<String>>(emptyList())
    val scannedBarcodesList: LiveData<List<String>> = _scannedBarcodesList

    private val _lastScannedBarcode = MutableLiveData<String?>()
    val lastScannedBarcode: LiveData<String?> = _lastScannedBarcode

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        setupSdkListeners()
    }

    fun setupSdkListeners() {
        // Beri nama eksplisit pada lambda ini, misal: 'barcodeListener'
        sdkManager.onBarcodeScanned = barcodeListener@{ barcode ->
            // Cek cache sebelum mengirim
            if (sentBarcodeCache.contains(barcode)) {
                // Jika sudah pernah dikirim di sesi ini, hentikan proses scan tapi jangan kirim lagi
                _isLoading.postValue(false)

                // Ganti baris yang error dengan ini:
                return@barcodeListener
            }

            // Update UI
            _lastScannedBarcode.postValue(barcode)

            // Tambahkan barcode ke daftar yang ditampilkan di UI
            val currentList = _scannedBarcodesList.value?.toMutableList() ?: mutableListOf()
            currentList.add(barcode)
            _scannedBarcodesList.postValue(currentList)

            _isLoading.postValue(false)

            // Kirim data ke PC karena ini adalah barcode baru
            realtimeStreamManager.sendData(barcode)

            // Catat barcode ini agar tidak dikirim lagi di sesi ini
            sentBarcodeCache.add(barcode)
        }
    }

    fun startBarcodeScan() {
        if (_isLoading.value == true) return
        _isLoading.value = true
        // Tidak perlu clear cache di sini, biarkan cache hidup selama activity aktif
        sdkManager.startBarcodeScan()
    }

    fun stopBarcodeScan() {
        if (_isLoading.value == false) return
        sdkManager.stopBarcodeScan()
        _isLoading.value = false
    }

    // Fungsi untuk membersihkan daftar saat pengguna menekan tombol clear
    fun clearScanHistory() {
        _scannedBarcodesList.value = emptyList()
        _lastScannedBarcode.value = null
        // Kosongkan juga cache agar semua item bisa dikirim ulang
        sentBarcodeCache.clear()
    }

    override fun onCleared() {
        super.onCleared()
        if (_isLoading.value == true) {
            sdkManager.stopBarcodeScan()
        }
        Log.d("ReadBarcodeViewModel", "ViewModel cleared.")
    }
}
