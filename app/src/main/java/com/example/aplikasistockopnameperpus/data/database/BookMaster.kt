package com.example.aplikasistockopnameperpus.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "book_master",
    indices = [
        Index(value = ["rfidTagHex"], unique = true), // EPC
        Index(value = ["itemCode"], unique = true)    // Kode item buku
    ]
)
data class BookMaster(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemCode: String,        // Kode item unik buku (misal: ISBN, kode internal)
    val title: String,
    val author: String? = null,
    val publisher: String? = null,
    val yearPublished: Int? = null,
    val category: String? = null,
    val expectedLocation: String? = null, // Lokasi seharusnya buku berada
    val rfidTagHex: String,      // EPC Tag dalam format HEX (dari RFID tag)
    val tid: String? = null,         // TID dari RFID tag (jika diperlukan untuk validasi)
    val lastImportTimestamp: Long = System.currentTimeMillis(),
    var lastSeenTimestamp: Long? = null, // Kapan terakhir terlihat saat scan
    var scanStatus: String? = null     // Misal: "DITEMUKAN", "BELUM_DITEMUKAN" (untuk sesi opname)
)