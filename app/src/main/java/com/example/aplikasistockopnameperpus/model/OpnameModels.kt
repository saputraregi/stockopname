package com.example.aplikasistockopnameperpus.model

import com.example.aplikasistockopnameperpus.data.database.BookMaster // Import BookMaster

data class BookOpname(
    val bookMaster: BookMaster,
    var isFound: Boolean,
    var scanTime: Long?,
    var scanMethod: ScanMethod?
    // Anda mungkin ingin menambahkan properti `statusScanOpname` di sini jika ingin
    // memisahkannya dari BookMaster dalam konteks opname, atau tetap mengandalkan
    // `bookMaster.statusScanOpname` yang diupdate oleh ViewModel.
    // Untuk saat ini, kita akan mengandalkan `bookMaster.statusScanOpname`.
)

enum class ScanMethod(val displayName: String) {
    UHF("UHF"),
    BARCODE("Barcode")
}
