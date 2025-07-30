package com.example.aplikasistockopnameperpus.model

data class Book(
    val rfidTagHex: String, // Kunci utama untuk scan (EPC dalam format HEX)
    val itemCode: String,   // Item Code (hasil konversi ASCII dari EPC atau ID utama)
    val title: String,
    val location: String,   // Lokasi fisik buku (dari data master)
    var expectedStatus: String = "Ada", // Status yang diharapkan (dari data master, mis: "Ada", "Dipinjam")
    var actualLocation: String? = null, // Lokasi aktual saat ditemukan (jika berbeda atau baru)
    var isScanned: Boolean = false, // True jika terdeteksi selama sesi scan ini
    var scanTimestamp: Long? = null // Waktu ketika buku terakhir di-scan
) {
    // Properti tambahan untuk mempermudah logika nanti
    val isFound: Boolean
        get() = isScanned

    val isMissing: Boolean
        get() = !isScanned // Asumsi sederhana, bisa lebih kompleks nanti
}
