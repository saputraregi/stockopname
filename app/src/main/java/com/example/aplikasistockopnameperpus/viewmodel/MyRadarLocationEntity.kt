package com.example.aplikasistockopnameperpus.viewmodel // SESUAIKAN JIKA PERLU

data class MyRadarLocationEntity(
    val tag: String,    // EPC dari tag
    val value: Int,     // Kekuatan sinyal atau jarak (misal RSSI)
    val angle: Int      // Sudut deteksi (0-359 derajat)
)