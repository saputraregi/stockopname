package com.example.aplikasistockopnameperpus.model
// File: MenuItem.kt (misalnya di package model)
data class MenuItem(
    val title: String,
    val iconResId: Int, // Resource ID untuk ikon drawable
    val actionId: MenuAction // Enum atau konstanta untuk identifikasi aksi
)

// Enum untuk aksi menu (bisa Anda kembangkan)
//enum class MenuAction {
//    IMPORT_EXPORT,
//    STOCK_OPNAME,
//    REPORT,
//    READ_WRITE_TAG,
//    RADAR,
//    SETUP
    // Tambahkan aksi lain jika perlu
//}
