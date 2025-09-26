package com.example.aplikasistockopnameperpus.data.model // Atau package yang sesuai

import androidx.room.Embedded // Jika Anda ingin memetakan ke objek yang sudah ada
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem

// Pilihan 1: DTO dengan field-field individual (lebih kontrol atas nama kolom jika ada konflik)
// data class ReportItemData(
//     // Dari StockOpnameItem
//     val itemId: Long, // soi_id atau nama unik jika ada konflik
//     val itemReportId: Long, // soi_reportId
//     val itemBookMasterId: Long?, // soi_bookMasterId
//     val itemEpc: String?, // soi_rfidEpc
//     // ... field lain dari StockOpnameItem dengan prefix jika perlu ...

//     // Dari BookMaster
//     val bookId: Long?, // bm_id
//     val bookItemCode: String?, // bm_itemCode
//     val bookTitle: String?, // bm_title
//     // ... field lain dari BookMaster dengan prefix jika perlu ...
// )

// Pilihan 2: DTO dengan objek @Embedded (lebih sederhana jika tidak ada konflik nama kolom signifikan)
// Room akan mencoba memetakan kolom dari hasil query ke field di StockOpnameItem dan BookMaster.
// Anda mungkin perlu alias di query SQL jika ada nama kolom yang sama (selain ID).
data class ReportItemData(
    @Embedded(prefix = "soi_") // Tambahkan prefix untuk menghindari konflik nama kolom (misal jika kedua tabel punya kolom 'id')
    val stockOpnameItem: StockOpnameItem,

    @Embedded(prefix = "bm_")
    val bookMaster: BookMaster? // Nullable jika menggunakan LEFT JOIN dan mungkin tidak ada BookMaster
)
