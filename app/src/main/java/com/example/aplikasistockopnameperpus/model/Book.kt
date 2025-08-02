package com.example.aplikasistockopnameperpus.model

data class Book(
    val itemCode: String,       // Item Code dari SLiMS (mis: barcode buku, ID utama)
    val title: String,
    val location: String,       // Lokasi fisik buku (dari data master SLiMS, mis: "Rak A1")
    var rfidTagHex: String? = null, // EPC dalam format HEX (hasil konversi itemCode), bisa null awalnya
    var tid: String? = null,        // (Opsional tapi direkomendasikan) ID unik dari chip tag RFID fisik, bisa null
    var expectedStatus: String = "Ada", // Status yang diharapkan (dari data master, mis: "Ada", "Dipinjam")
    var actualLocation: String? = null, // Lokasi aktual saat ditemukan (jika berbeda atau baru)
    var isScanned: Boolean = false, // True jika terdeteksi selama sesi scan stock opname ini
    var scanTimestamp: Long? = null // Waktu ketika buku terakhir di-scan saat stock opname
) {
    // Properti tambahan untuk mempermudah logika
    val isFound: Boolean
        get() = isScanned // Ditemukan dalam sesi scan stock opname saat ini

    /**
     * Menandakan apakah buku ini telah melalui proses pairing dan memiliki RFID tag yang terasosiasi.
     */
    val hasBeenTagged: Boolean
        get() = rfidTagHex != null

    /**
     * Menandakan apakah buku hilang.
     * Buku dianggap hilang jika:
     * 1. Sudah memiliki RFID tag (hasBeenTagged = true).
     * 2. Status yang diharapkannya adalah "Ada".
     * 3. Tidak terdeteksi dalam sesi scan stock opname saat ini (isScanned = false).
     */
    val isMissing: Boolean
        get() = hasBeenTagged && expectedStatus == "Ada" && !isScanned
}
