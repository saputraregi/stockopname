package com.example.aplikasistockopnameperpus.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.nio.charset.StandardCharsets
import java.util.Locale

@Entity(
    tableName = "book_master",
    indices = [
        Index(value = ["itemCode"], unique = true), // Kode item buku dari SLiMS, harus unik
        // rfidTagHex mungkin tidak perlu unik jika ada skenario itemCode sama di-pair ke tag berbeda (meski jarang)
        // Jika itemCode ke rfidTagHex adalah 1-to-1 dan itemCode unik, maka rfidTagHex juga akan unik.
        Index(value = ["rfidTagHex"], unique = true)
    ]
)
data class BookMaster(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // --- Informasi Utama dari SLiMS (Dipilih dan disesuaikan tipenya) ---
    val itemCode: String,        // SLiMS: item_code (object -> String)
    val title: String,           // SLiMS: title (object -> String)
    val callNumber: String? = null,    // SLiMS: call_number (float64 -> String?)
    val collectionType: String? = null,// SLiMS: coll_type_name (object -> String?)
    val inventoryCode: String? = null, // SLiMS: inventory_code (object -> String?)
    val receivedDate: String? = null,  // SLiMS: received_date (object -> String?, simpan sebagai string apa adanya)
    val locationName: String?,   // SLiMS: location_name (object -> String?)
    val orderDate: String? = null,     // SLiMS: order_date (object -> String?, simpan sebagai string apa adanya)
    val slimsItemStatus: String? = null, // SLiMS: item_status_name (float64 -> String?)
    val siteName: String? = null,      // SLiMS: site (float64 -> String?)
    val source: String? = null,        // SLiMS: source (int64 -> String?, karena bisa saja ada non-angka atau kita tidak butuh sbg Int)
    val price: String? = null,         // SLiMS: price (int64 -> String?, bisa jadi ada mata uang, lebih aman sbg String)
    val priceCurrency: String? = null, // SLiMS: price_currency (object -> String?)
    val invoiceDate: String? = null,   // SLiMS: invoice_date (object -> String?, simpan sebagai string apa adanya)
    val inputDate: String? = null,     // SLiMS: input_date (object -> String?, simpan sebagai string apa adanya)
    val lastUpdate: String? = null,    // SLiMS: last_update (object -> String?, simpan sebagai string apa adanya)
    // val author: String? = null, // (SANGAT REKOMENDASI jika bisa didapatkan dari data bibliografi SLiMS)

    // --- Informasi RFID ---
    var rfidTagHex: String?, // Akan diisi dari itemCode yang di-hex-kan
    var tid: String? = null,         // Akan diisi saat pairing dan write

    // --- Timestamp dan Status Aplikasi ---
    val importTimestamp: Long = System.currentTimeMillis(), // Waktu data ini diimpor/dibuat
    var pairingTimestamp: Long? = null,
    var pairingStatus: PairingStatus = PairingStatus.NOT_PAIRED, // Menggunakan Enum untuk status pairing

    // --- Informasi untuk Sesi Stock Opname (diisi aplikasi) ---
    var lastSeenTimestamp: Long? = null,
    var actualScannedLocation: String? = null, // Lokasi buku saat terakhir di-scan
    var opnameStatus: OpnameStatus = OpnameStatus.NOT_SCANNED, // Menggunakan Enum untuk status opname
    var isNewOrUnexpected: Boolean = false // Jika buku ini tidak ada di master list awal sesi opname
)

// Enum untuk Status Pairing
enum class PairingStatus {
    NOT_PAIRED,      // Belum pernah coba di-pair
    PAIRING_PENDING, // Proses pairing sedang berlangsung atau dijadwalkan
    PAIRED_WRITE_PENDING, // Sudah di-pair, menunggu write EPC
    PAIRED_WRITE_SUCCESS, // Berhasil di-pair dan EPC ditulis
    PAIRED_WRITE_FAILED,  // Berhasil di-pair, tapi EPC gagal ditulis
    PAIRING_FAILED      // Gagal melakukan pairing (misal, tag tidak responsif)
}

// Enum untuk Status Stock Opname
enum class OpnameStatus {
    NOT_SCANNED,       // Belum ter-scan dalam sesi opname saat ini
    FOUND_CORRECT_LOCATION, // Ditemukan di lokasi yang diharapkan
    FOUND_WRONG_LOCATION,   // Ditemukan, tapi di lokasi yang salah
    MISSING,                // Tidak ditemukan setelah sesi opname selesai (ditandai manual atau by system)
    NEW_ITEM                // Item baru yang terdeteksi dan tidak ada di master awal
}

// Fungsi utilitas untuk konversi String ke Hex EPC 128-bit (32 karakter hex)
// Ini adalah contoh sederhana, Anda mungkin perlu menyesuaikannya agar sesuai dengan
// standar EPC atau kebutuhan spesifik Anda, terutama padding dan panjang.
fun String.toEPC96Hex(): String {
    val bytes = this.toByteArray(StandardCharsets.UTF_8)
    val hexChars = bytes.joinToString("") { "%02x".format(it) }
    // EPC 96 bit = 12 byte = 24 karakter hex.
    // Jika lebih pendek, tambahkan padding (misalnya '0' di akhir).
    // Jika lebih panjang, potong. Ini adalah strategi sederhana.
    val targetHexLength = 24 // Target panjang untuk 96 bit EPC
    return if (hexChars.length >= targetHexLength) {
        hexChars.substring(0, targetHexLength)
    } else {
        hexChars.padEnd(targetHexLength, '0')
    }
}
