package com.example.aplikasistockopnameperpus.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "stock_opname_items",
    primaryKeys = ["reportId", "rfidTagHexScanned"], // Kombinasi unik
    foreignKeys = [
        ForeignKey(
            entity = StockOpnameReport::class,
            parentColumns = ["reportId"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE // Jika report dihapus, item terkait juga dihapus
        )
        // Anda bisa juga menambahkan ForeignKey ke BookMaster jika rfidTagHexScanned harus selalu merujuk ke master
        // Namun, ini bisa jadi rumit jika ada tag asing. Pertimbangkan kasusnya.
    ],
    indices = [Index(value = ["reportId"]), Index(value = ["rfidTagHexScanned"])]
)
data class StockOpnameItem(
    var reportId: Long, // Foreign key ke StockOpnameReport
    val rfidTagHexScanned: String, // EPC dari tag yang benar-benar di-scan
    val tidScanned: String? = null, // TID dari tag yang di-scan
    val itemCodeMaster: String?, // Kode item dari master jika cocok, null jika tag asing
    val titleMaster: String?,    // Judul dari master jika cocok
    val scanTimestamp: Long,
    val status: String, // Misal: "DITEMUKAN_MASTER", "TIDAK_DITEMUKAN_DI_MASTER" (tag asing), "MASTER_TIDAK_TERSCAN"
    val actualLocationIfDifferent: String? = null, // Jika lokasi berbeda dari master
    val expectedLocationMaster: String? = null, // Jika lokasi berbeda dari master
    val isNewOrUnexpectedItem: Boolean = false // Jika ditemukan baru atau tidak
)
