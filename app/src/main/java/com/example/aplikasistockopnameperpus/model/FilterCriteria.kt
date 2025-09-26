package com.example.aplikasistockopnameperpus.model // atau package yang sesuai

import android.os.Parcelable
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus // Sesuaikan dengan path OpnameStatus Anda
import kotlinx.parcelize.Parcelize

@Parcelize
data class FilterCriteria(
    val opnameStatus: OpnameStatus? = null,
    val titleQuery: String? = null,
    val itemCodeQuery: String? = null,
    val locationQuery: String? = null,
    val epcQuery: String? = null,
    val scanStartDateMillis: Long? = null,
    val scanEndDateMillis: Long? = null,
    // Tambahkan field lain yang ingin Anda filter, misalnya:
    // val authorQuery: String? = null,
    // val publisherQuery: String? = null,
    // val yearQuery: String? = null,
    val isNewOrUnexpected: Boolean? = null, // Untuk filter item baru/tak terduga
    // Opsional: flag untuk pencarian tepat (exact match)
    // val isExactMatchTitle: Boolean = false,
    // val isExactMatchItemCode: Boolean = false
) : Parcelable