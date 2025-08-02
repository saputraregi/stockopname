package com.example.aplikasistockopnameperpus.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "book_master",
    indices = [
        Index(value = ["rfidTagHex"], unique = true), // Mengizinkan banyak NULL, tapi nilai non-NULL harus unik
        Index(value = ["itemCode"], unique = true)    // Kode item buku dari SLiMS, harus unik
    ]
)
data class BookMaster(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Informasi Utama dari SLiMS
    val itemCode: String,        // Kode item unik buku dari SLiMS (misal: barcode, ID internal)
    val title: String,
    val author: String? = null,
    val publisher: String? = null,
    val yearPublished: Int? = null,
    val category: String? = null,
    val expectedLocation: String? = null, // Lokasi seharusnya buku berada (dari SLiMS)

    // Informasi RFID (diisi setelah proses pairing/write tag terpisah)
    var rfidTagHex: String? = null, // EPC Tag dalam format HEX, bisa null jika belum ditag
    var tid: String? = null,         // TID unik dari chip RFID tag, bisa null jika belum ditag

    // Timestamp dan Status
    val lastImportTimestamp: Long = System.currentTimeMillis(), // Waktu impor data awal atau update masif
    var rfidPairingTimestamp: Long? = null, // (REKOMENDASI) Waktu ketika RFID berhasil dipasangkan
    var rfidPairingStatus: String? = "BELUM_DITAG", // (REKOMENDASI) Status proses pairing RFID

    // Informasi untuk Sesi Stock Opname
    var lastSeenTimestamp: Long? = null, // Kapan terakhir terlihat saat sesi scan stock opname
    var actualScannedLocation: String? = null, // (REKOMENDASI) Lokasi aktual saat buku discan (jika fitur ini ada)
    var scanStatus: String? = null,     // Status dalam sesi stock opname (misal: "DITEMUKAN", "BELUM_DITEMUKAN_SESI_INI", "LOKASI_SALAH")
    var isNewOrUnexpected: Boolean? = false
)
