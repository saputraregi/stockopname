package com.example.aplikasistockopnameperpus.util

import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class RealtimeStreamManager {

    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS) // Menjaga koneksi tetap hidup
        .build()

    private var webSocket: WebSocket? = null

    // Gunakan StateFlow untuk status yang bisa diobservasi dari mana saja
    private val _connectionStatus = MutableStateFlow("Terputus")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val scope = MainScope() // Gunakan MainScope untuk update UI

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            scope.launch {
                _connectionStatus.value = "Terhubung"
                Log.i("RealtimeStreamManager", "Koneksi WebSocket terbuka.")
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            scope.launch {
                _connectionStatus.value = "Gagal: ${t.message}"
                Log.e("RealtimeStreamManager", "Koneksi gagal", t)
                webSocket = null
            }
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            scope.launch {
                _connectionStatus.value = "Terputus"
                Log.i("RealtimeStreamManager", "Koneksi ditutup: $reason")
                webSocket = null
            }
        }

        override fun onMessage(ws: WebSocket, text: String) {
            // Logika jika menerima pesan dari PC (saat ini tidak digunakan)
            Log.d("RealtimeStreamManager", "Menerima pesan dari PC: $text")
        }
    }

    fun connect(ipAddress: String, port: Int) {
        if (webSocket != null) {
            Log.w("RealtimeStreamManager", "Koneksi sudah ada, putuskan dulu.")
            disconnect()
        }
        _connectionStatus.value = "Menghubungkan..."
        val request = Request.Builder().url("ws://$ipAddress:$port").build()
        webSocket = client.newWebSocket(request, webSocketListener)
    }

    fun sendData(data: String) {
        if (webSocket?.send(data) == true) {
            Log.d("RealtimeStreamManager", "Mengirim data: $data")
        } else {
            // Bisa terjadi jika koneksi terputus tiba-tiba
            scope.launch { _connectionStatus.value = "Terputus (gagal kirim)" }
            Log.w("RealtimeStreamManager", "Gagal mengirim data, koneksi tidak aktif.")
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        scope.launch { _connectionStatus.value = "Terputus" }
    }
}
