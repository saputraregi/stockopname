package com.example.aplikasistockopnameperpus.model // atau lokasi model Anda

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RadarUiTag(
    val epc: String,
    val distanceValue: Int, // nilai 0-100 dari SDK
    val uiAngle: Int        // Sudut yang sudah dikonversi dan siap pakai untuk UI
) : Parcelable
