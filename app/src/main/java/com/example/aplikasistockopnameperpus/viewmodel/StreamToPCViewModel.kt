package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.util.RealtimeStreamManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class StreamToPcViewModel(application: Application) : AndroidViewModel(application) {

    private val realtimeStreamManager: RealtimeStreamManager = (application as MyApplication).realtimeStreamManager

    // Ambil status koneksi dari manager
    val connectionStatus = realtimeStreamManager.connectionStatus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Inisialisasi..."
    )

    fun connect(ipAddress: String) {
        // Port bisa di-hardcode atau dibuat bisa di-setting
        realtimeStreamManager.connect(ipAddress, 8765)
    }

    fun disconnect() {
        realtimeStreamManager.disconnect()
    }
}
