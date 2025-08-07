package com.example.aplikasistockopnameperpus.sdk

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log

// TODO SDK: Ganti ini dengan import aktual dari SDK Chainway Anda
// Contoh:
// import com.rscja.deviceapi.RFIDWithUHFUART
// import com.rscja.deviceapi.RFIDWithUHFUART.InventoryTagEnd // Jika ada callback untuk akhir inventaris
// import com.rscja.deviceapi.interfaces.IUHFInventoryListener // Sesuaikan dengan interface listener Anda
// import com.rscja.deviceapi.entity.UHFTAGInfo // Sesuaikan dengan entitas tag Anda
// import com.chainway.deviceapi.BarcodeScanner
// import com.chainway.deviceapi.scanner.ScanResultListener // Sesuaikan

class ChainwaySDKManager(private val application: Application) {

    // --- 1. Properti untuk State dan Instance SDK (TIDAK DIUBAH) ---
    // TODO SDK: Ganti dengan tipe SDK aktual dan inisialisasi di init
    // private var uhfReader: RFIDWithUHFUART? = null
    // private var barcodeScanner: BarcodeScanner? = null

    private var isUhfSdkInitialized: Boolean = false
    private var isBarcodeSdkInitialized: Boolean = false
    private var isUhfConnected: Boolean = false // Status koneksi aktual
    private var isBarcodeConnected: Boolean = false // Status koneksi aktual

    var isUhfDeviceScanning: Boolean = false
        private set
    var isBarcodeDeviceScanning: Boolean = false
        private set

    // --- 2. Callback untuk Berkomunikasi dengan ViewModel (TIDAK DIUBAH nama yang sudah ada) ---
    var onUhfTagScanned: ((epc: String) -> Unit)? = null
    var onBarcodeScanned: ((barcodeData: String) -> Unit)? = null
    var onError: ((message: String) -> Unit)? = null
    var onUhfInventoryFinished: (() -> Unit)? = null
    var onDeviceStatusChanged: ((isConnected: Boolean, deviceType: String) -> Unit)? = null // Callback baru untuk status

    // --- CALLBACK TAMBAHAN untuk operasi tulis dan baca TID ---
    // BARU: Callback jika penulisan tag EPC berhasil
    var onTagWriteSuccess: ((writtenEpc: String) -> Unit)? = null
    // BARU: Callback jika penulisan tag EPC gagal
    var onTagWriteFailed: ((errorMessage: String) -> Unit)? = null
    // BARU: Callback jika pembacaan TID berhasil
    var onTagReadTidSuccess: ((tid: String, epcOfTagRead: String?) -> Unit)? = null
    // BARU: Callback jika pembacaan TID gagal
    var onTagReadTidFailed: ((errorMessage: String) -> Unit)? = null
    // BARU: Callback umum ketika operasi UHF (inventory, write, read) dihentikan/selesai
    var onUhfOperationStopped: (() -> Unit)? = null


    companion object { // BARU: Menambahkan TAG untuk logging konsisten
        private val TAG = ChainwaySDKManager::class.java.simpleName
    }

    // --- 3. Blok Inisialisasi & Koneksi Awal (Logika internal, nama fungsi tidak diubah) ---
    init {
        Log.d(TAG, "ChainwaySDKManager constructor called.") // Menggunakan TAG
        initializeModules()
    }

    fun initializeModules() { // Nama fungsi tidak diubah
        Log.i(TAG, "Initializing SDK modules...") // Menggunakan TAG
        try {
            // TODO SDK: Inisialisasi instance UHF Reader
            // uhfReader = RFIDWithUHFUART.getInstance()
            // isUhfSdkInitialized = uhfReader?.init(application.applicationContext) ?: false
            // if (isUhfSdkInitialized) {
            //     setupUhfListener() // Panggil setup listener (nama fungsi tidak diubah)
            //     Log.i(TAG, "UHF Reader SDK initialized successfully.")
            //     isUhfConnected = true
            //     onDeviceStatusChanged?.invoke(isUhfConnected, "UHF")
            // } else {
            //     Log.e(TAG, "Failed to initialize UHF Reader SDK.")
            //     onError?.invoke("Gagal inisialisasi modul UHF.")
            //     isUhfConnected = false
            //     onDeviceStatusChanged?.invoke(isUhfConnected, "UHF")
            // }

            // TODO SDK: Inisialisasi instance Barcode Scanner
            // barcodeScanner = BarcodeScanner.getInstance()
            // isBarcodeSdkInitialized = barcodeScanner?.open(application.applicationContext) ?: false
            // if (isBarcodeSdkInitialized) {
            //     setupBarcodeListener() // Panggil setup listener (nama fungsi tidak diubah)
            //     Log.i(TAG, "Barcode Scanner SDK initialized successfully.")
            //     isBarcodeConnected = true
            //     onDeviceStatusChanged?.invoke(isBarcodeConnected, "Barcode")
            // } else {
            //     Log.e(TAG, "Failed to initialize Barcode Scanner SDK.")
            //     onError?.invoke("Gagal inisialisasi modul Barcode.")
            //     isBarcodeConnected = false
            //     onDeviceStatusChanged?.invoke(isBarcodeConnected, "Barcode")
            // }

            // Untuk FOKUS UI (Simulasi jika TODO SDK belum diimplementasikan):
            if (true) { // Ganti 'true' dengan kondisi jika SDK tidak diinisialisasi
                Log.i(TAG, "Mode simulasi aktif untuk SDK initialization.")
                isUhfSdkInitialized = true
                isBarcodeSdkInitialized = true
                isUhfConnected = true
                isBarcodeConnected = true
                setupUhfListener()      // Panggil juga dalam simulasi
                setupBarcodeListener()  // Panggil juga dalam simulasi
                onDeviceStatusChanged?.invoke(true, "UHF (Simulated)")
                onDeviceStatusChanged?.invoke(true, "Barcode (Simulated)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during SDK module initialization", e)
            onError?.invoke("Error kritis saat inisialisasi SDK: ${e.message}")
            isUhfConnected = false
            isBarcodeConnected = false
            onDeviceStatusChanged?.invoke(false, "UHF")
            onDeviceStatusChanged?.invoke(false, "Barcode")
        }
    }

    // Metode ini bisa dipanggil jika ada tombol "Connect/Disconnect" eksplisit di UI
    // atau jika SDK memerlukan koneksi ulang. (Nama fungsi tidak diubah)
    fun connectDevices(): Boolean {
        var uhfSuccess = isUhfConnected
        var barcodeSuccess = isBarcodeConnected

        Log.i(TAG, "Attempting to connect devices...") // Menggunakan TAG

        if (!isUhfConnected && isUhfSdkInitialized) {
            // TODO SDK: Panggil metode connect UHF reader jika ada (jika init() belum menghubungkan)
            // uhfSuccess = uhfReader?.connect() ?: false
            // if (uhfSuccess) {
            //     isUhfConnected = true
            //     Log.i(TAG, "UHF Reader connected successfully.")
            // } else {
            //     Log.e(TAG, "Failed to connect UHF Reader.")
            //     onError?.invoke("Gagal menghubungkan modul UHF.")
            // }
            // onDeviceStatusChanged?.invoke(isUhfConnected, "UHF")

            // Simulasi
            uhfSuccess = true; isUhfConnected = true; Log.i(TAG, "UHF (Simulated) connected.")
            onDeviceStatusChanged?.invoke(isUhfConnected, "UHF (Simulated)")
        }

        if (!isBarcodeConnected && isBarcodeSdkInitialized) {
            // TODO SDK: Panggil metode connect Barcode scanner jika ada (jika open() belum menghubungkan)
            // barcodeSuccess = barcodeScanner?.connect() ?: false
            // if (barcodeSuccess) {
            //     isBarcodeConnected = true
            //     Log.i(TAG, "Barcode Scanner connected successfully.")
            // } else {
            //     Log.e(TAG, "Failed to connect Barcode Scanner.")
            //     onError?.invoke("Gagal menghubungkan modul Barcode.")
            // }
            // onDeviceStatusChanged?.invoke(isBarcodeConnected, "Barcode")

            // Simulasi
            barcodeSuccess = true; isBarcodeConnected = true; Log.i(TAG, "Barcode (Simulated) connected.")
            onDeviceStatusChanged?.invoke(isBarcodeConnected, "Barcode (Simulated)")
        }

        return uhfSuccess && barcodeSuccess
    }

    fun disconnectDevices() { // Nama fungsi tidak diubah
        Log.i(TAG, "Disconnecting devices...") // Menggunakan TAG
        if (isUhfConnected) {
            if (isUhfDeviceScanning) stopUhfInventory() // Nama fungsi tidak diubah
            // TODO SDK: Panggil metode disconnect/close/powerOff UHF reader
            // uhfReader?.close()
            isUhfConnected = false
            Log.i(TAG, "UHF Reader disconnected.")
            onDeviceStatusChanged?.invoke(isUhfConnected, "UHF")
        }
        if (isBarcodeConnected) {
            if (isBarcodeDeviceScanning) stopBarcodeScan() // Nama fungsi tidak diubah
            // TODO SDK: Panggil metode disconnect/close/powerOff Barcode scanner
            // barcodeScanner?.close()
            isBarcodeConnected = false
            Log.i(TAG, "Barcode Scanner disconnected.")
            onDeviceStatusChanged?.invoke(isBarcodeConnected, "Barcode")
        }
    }


    // --- 4. Metode Publik untuk Mengontrol Operasi (API untuk ViewModel) (Nama fungsi yang sudah ada tidak diubah) ---

    fun isDeviceReady(type: String = "any"): Boolean { // Nama fungsi tidak diubah
        return when (type.lowercase()) {
            "uhf" -> isUhfSdkInitialized && isUhfConnected
            "barcode" -> isBarcodeSdkInitialized && isBarcodeConnected
            "any" -> (isUhfSdkInitialized && isUhfConnected) || (isBarcodeSdkInitialized && isBarcodeConnected)
            else -> false
        }
    }

    fun startUhfInventory() { // Nama fungsi tidak diubah
        if (!isDeviceReady("uhf")) {
            onError?.invoke("Modul UHF tidak siap atau tidak terhubung.")
            return
        }
        if (isBarcodeDeviceScanning) {
            onError?.invoke("Hentikan scan barcode terlebih dahulu.")
            return
        }
        if (isUhfDeviceScanning) {
            Log.w(TAG, "UHF scan sudah berjalan.")
            return
        }

        // TODO SDK: Panggil metode SDK untuk memulai scan UHF
        // val success = uhfReader?.startInventoryTag()
        // if (success == true) {
        //     isUhfDeviceScanning = true
        //     Log.i(TAG, "Inventarisasi UHF SDK dimulai.")
        // } else {
        //     onError?.invoke("Gagal memulai scan UHF dari SDK.")
        // }

        // Simulasi untuk FOKUS UI:
        isUhfDeviceScanning = true
        Log.i(TAG, "Simulasi: Inventarisasi UHF DIMULAI.")
        simulateUhfTag("SIM_EPC_123_${System.currentTimeMillis() % 1000}", 800)
        simulateUhfTag("SIM_EPC_456_${System.currentTimeMillis() % 1000}", 1500)
        simulateUhfTag("SIM_EPC_789_${System.currentTimeMillis() % 1000}", 2200)
    }

    fun stopUhfInventory() { // Nama fungsi tidak diubah
        if (!isUhfDeviceScanning) return
        if (!isDeviceReady("uhf")) {
            Log.w(TAG, "UHF tidak siap saat mencoba stop inventory.")
        }

        // TODO SDK: Panggil metode SDK untuk menghentikan scan UHF
        // uhfReader?.stopInventory()
        // isUhfDeviceScanning = false // Idealnya di-set oleh callback onInventoryStop dari SDK
        // Log.i(TAG, "Inventarisasi UHF SDK dihentikan (perintah dikirim).")
        // onUhfInventoryFinished?.invoke() // Panggil jika SDK tidak punya listener stop eksplisit
        // onUhfOperationStopped?.invoke() // BARU: Panggil juga callback umum

        // Simulasi untuk FOKUS UI:
        isUhfDeviceScanning = false
        Log.i(TAG, "Simulasi: Inventarisasi UHF DIHENTIKAN.")
        onUhfInventoryFinished?.invoke()
        onUhfOperationStopped?.invoke() // BARU: Panggil juga callback umum
    }

    fun startBarcodeScan() { // Nama fungsi tidak diubah
        if (!isDeviceReady("barcode")) {
            onError?.invoke("Modul Barcode tidak siap atau tidak terhubung.")
            return
        }
        if (isUhfDeviceScanning) {
            onError?.invoke("Hentikan scan UHF terlebih dahulu.")
            return
        }
        if (isBarcodeDeviceScanning) {
            Log.w(TAG, "Barcode scan sudah berjalan (mungkin mode kontinyu).")
        }

        // TODO SDK: Panggil metode SDK untuk memulai scan barcode
        // barcodeScanner?.trigger()
        // isBarcodeDeviceScanning = true
        // Log.i(TAG, "Scan barcode SDK dipicu.")

        // Simulasi untuk FOKUS UI:
        isBarcodeDeviceScanning = true
        Log.i(TAG, "Simulasi: Scan barcode DIMULAI/DIPICU.")
        Handler(Looper.getMainLooper()).postDelayed({
            if (isBarcodeDeviceScanning) { // Hapus '|| true' saat SDK aktif
                val barcodeData = "SIM_BARCODE_${System.currentTimeMillis() % 10000}"
                Log.d(TAG, "Simulasi: Barcode Diterima: $barcodeData")
                onBarcodeScanned?.invoke(barcodeData)
                isBarcodeDeviceScanning = false // Asumsi scan tunggal untuk simulasi
                Log.i(TAG, "Simulasi: Scan barcode selesai (tunggal).")
            }
        }, 1500)
    }

    fun stopBarcodeScan() { // Nama fungsi tidak diubah
        if (!isBarcodeDeviceScanning) return
        if (!isDeviceReady("barcode")) {
            Log.w(TAG, "Barcode tidak siap saat mencoba stop scan.")
        }

        // TODO SDK: Panggil metode SDK untuk menghentikan scan barcode
        // barcodeScanner?.stopScan()
        // isBarcodeDeviceScanning = false // Idealnya di-set false oleh callback SDK jika ada
        // Log.i(TAG, "Scan barcode SDK dihentikan (perintah dikirim).")

        // Simulasi untuk FOKUS UI:
        isBarcodeDeviceScanning = false
        Log.i(TAG, "Simulasi: Scan barcode DIHENTIKAN manual.")
    }

    // --- FUNGSI TAMBAHAN untuk operasi tulis dan baca tag ---
    /**
     * BARU: Fungsi untuk menulis EPC ke tag.
     * @param epcToWrite EPC yang akan ditulis.
     * @param currentEpc (Opsional) EPC saat ini dari tag yang ingin ditimpa (jika SDK mendukung filter).
     * @param passwordAccess (Opsional) Password untuk akses memori tag.
     */
    fun writeUhfTag(epcToWrite: String, currentEpc: String? = null, passwordAccess: String? = null) {
        if (!isDeviceReady("uhf")) {
            onError?.invoke("Modul UHF tidak siap untuk menulis tag.")
            onTagWriteFailed?.invoke("UHF not ready") // Panggil callback gagal
            return
        }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) {
            onError?.invoke("Device sedang sibuk, tidak bisa menulis tag sekarang.")
            onTagWriteFailed?.invoke("Device busy") // Panggil callback gagal
            return
        }

        Log.i(TAG, "Memulai penulisan EPC: $epcToWrite (Target EPC saat ini: ${currentEpc ?: "tidak ada"})")
        // TODO SDK: Panggil API SDK Chainway Anda untuk menulis EPC.
        // Contoh: uhfReader?.writeTag(targetEpc, newEpc, passwordAccess)
        // Berdasarkan hasil SDK, panggil:
        // onTagWriteSuccess?.invoke(epcYangBerhasilDitulis)
        // atau
        // onTagWriteFailed?.invoke("Pesan error dari SDK")
        // isUhfDeviceScanning = false // Set setelah operasi selesai
        // onUhfOperationStopped?.invoke() // Panggil setelah operasi selesai

        // --- MULAI SIMULASI untuk writeUhfTag ---
        isUhfDeviceScanning = true // Anggap operasi tulis seperti "scanning" sementara
        Log.d(TAG, "Simulasi: Penulisan EPC '$epcToWrite' dimulai...")
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isUhfDeviceScanning) {
                Log.w(TAG, "Simulasi: Penulisan EPC dibatalkan karena isUhfDeviceScanning false")
                onTagWriteFailed?.invoke("Simulated write cancelled")
                // onUhfOperationStopped?.invoke() // Sebaiknya dipanggil jika operasi memang dimulai dan dihentikan
                return@postDelayed
            }
            val success = true // Ganti dengan hasil SDK sebenarnya
            if (success) {
                Log.i(TAG, "Simulasi: Penulisan EPC '$epcToWrite' BERHASIL.")
                onTagWriteSuccess?.invoke(epcToWrite)
            } else {
                val errorMsg = "Simulated write failed"
                Log.e(TAG, "Simulasi: Penulisan EPC '$epcToWrite' GAGAL: $errorMsg")
                onTagWriteFailed?.invoke(errorMsg)
            }
            isUhfDeviceScanning = false // Operasi tulis selesai
            onUhfOperationStopped?.invoke() // Panggil setelah operasi selesai
        }, 2000)
        // --- AKHIR SIMULASI ---
    }

    /**
     * BARU: Fungsi untuk membaca TID (Tag Identifier) dari tag.
     * @param targetEpc (Opsional) EPC dari tag yang TID-nya ingin dibaca (jika SDK mendukung filter).
     * @param passwordAccess (Opsional) Password untuk akses memori tag.
     */
    fun readUhfTagTid(targetEpc: String? = null, passwordAccess: String? = null) {
        if (!isDeviceReady("uhf")) {
            onError?.invoke("Modul UHF tidak siap untuk membaca TID.")
            onTagReadTidFailed?.invoke("UHF not ready") // Panggil callback gagal
            return
        }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) {
            onError?.invoke("Device sedang sibuk, tidak bisa membaca TID sekarang.")
            onTagReadTidFailed?.invoke("Device busy") // Panggil callback gagal
            return
        }

        Log.i(TAG, "Memulai pembacaan TID (Target EPC: ${targetEpc ?: "tag terdekat"})")
        // TODO SDK: Panggil API SDK Chainway Anda untuk membaca TID.
        // Contoh: uhfReader?.readTagTID(targetEpc, passwordAccess)
        // Berdasarkan hasil SDK, panggil:
        // onTagReadTidSuccess?.invoke(tidYangDibaca, epcDariTagYangDibacaJikaAda)
        // atau
        // onTagReadTidFailed?.invoke("Pesan error dari SDK")
        // isUhfDeviceScanning = false // Set setelah operasi selesai
        // onUhfOperationStopped?.invoke() // Panggil setelah operasi selesai

        // --- MULAI SIMULASI untuk readUhfTagTid ---
        isUhfDeviceScanning = true // Anggap operasi baca seperti "scanning" sementara
        Log.d(TAG, "Simulasi: Pembacaan TID untuk EPC '${targetEpc ?: "any"}' dimulai...")
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isUhfDeviceScanning) {
                Log.w(TAG, "Simulasi: Pembacaan TID dibatalkan karena isUhfDeviceScanning false")
                onTagReadTidFailed?.invoke("Simulated TID read cancelled")
                return@postDelayed
            }
            val success = true // Ganti dengan hasil SDK sebenarnya
            if (success) {
                val simulatedTid = "SIM_TID_${System.currentTimeMillis() % 100000}"
                val epcFound = targetEpc ?: "SIM_EPC_FROM_TID_READ_${System.currentTimeMillis() % 1000}"
                Log.i(TAG, "Simulasi: Pembacaan TID BERHASIL. TID: $simulatedTid, EPC Terbaca: $epcFound")
                onTagReadTidSuccess?.invoke(simulatedTid, epcFound)
            } else {
                val errorMsg = "Simulated TID read failed"
                Log.e(TAG, "Simulasi: Pembacaan TID GAGAL: $errorMsg")
                onTagReadTidFailed?.invoke(errorMsg)
            }
            isUhfDeviceScanning = false // Operasi baca selesai
            onUhfOperationStopped?.invoke() // Panggil setelah operasi selesai
        }, 1800)
        // --- AKHIR SIMULASI ---
    }

    /**
     * BARU: Fungsi untuk menghentikan operasi UHF yang sedang berjalan
     * (baik itu inventory, write, atau read).
     * ViewModel akan memanggil ini saat timeout atau cancel.
     */
    fun stopUhfOperation() {
        Log.i(TAG, "Attempting to stop current UHF operation.")
        // Cek apakah ada operasi UHF yang "aktif" (menggunakan isUhfDeviceScanning sebagai indikator umum)
        if (isUhfDeviceScanning) {
            // TODO SDK: Panggil metode SDK yang sesuai untuk menghentikan operasi saat ini.
            // Ini mungkin sama dengan stopInventory() atau mungkin ada metode yang lebih general
            // untuk membatalkan operasi tulis/baca yang sedang berlangsung.
            // Contoh: uhfReader?.stop() atau uhfReader?.cancelOperation()
            // Penting: Pastikan bahwa setelah SDK menghentikan operasi,
            // callback yang relevan (onTagWriteFailed, onTagReadTidFailed, atau onInventoryStop)
            // juga dipicu oleh SDK jika memungkinkan, atau Anda set state secara manual di sini.

            // Simulasi:
            Log.i(TAG, "Simulasi: Menghentikan operasi UHF (inventory/write/read).")
            val wasInventory = true // Asumsi saja, Anda perlu tahu state sebenarnya
            // Jika Anda membedakan operasi, logika ini perlu lebih canggih.

            isUhfDeviceScanning = false // Set state bahwa tidak ada lagi scan/operasi
            onUhfOperationStopped?.invoke() // Panggil callback bahwa operasi dihentikan

            // Jika yang dihentikan adalah inventory, panggil juga onUhfInventoryFinished
            // agar konsisten dengan stopUhfInventory()
            if (wasInventory) { // Anda perlu cara untuk tahu apakah ini inventory
                // onUhfInventoryFinished?.invoke() // Sudah dipanggil oleh stopUhfInventory jika itu yang terjadi
            } else {
                // Jika ini adalah penghentian operasi tulis atau baca yang sedang berjalan,
                // Anda mungkin perlu memanggil onTagWriteFailed atau onTagReadTidFailed dengan pesan "dibatalkan".
                // onTagWriteFailed?.invoke("Operasi tulis dibatalkan")
                // onTagReadTidFailed?.invoke("Operasi baca TID dibatalkan")
            }
        } else {
            Log.i(TAG, "Tidak ada operasi UHF yang sedang berjalan untuk dihentikan (berdasarkan isUhfDeviceScanning).")
        }
    }


    // --- 5. Metode Privat untuk Setup Listener SDK dan Simulasi (Nama fungsi tidak diubah) ---

    private fun setupUhfListener() { // Nama fungsi tidak diubah
        // TODO SDK: Daftarkan listener ke instance uhfReader SDK Anda
        // uhfReader?.setInventoryListener(object : IUHFInventoryListener {
        //     override fun getInventoryData(uhfTagInfo: UHFTAGInfo?) {
        //         uhfTagInfo?.epc?.let { epc ->
        //             Handler(Looper.getMainLooper()).post {
        //                 onUhfTagScanned?.invoke(epc)
        //             }
        //         }
        //     }

        //     override fun onInventoryStop(reason: Int) {
        //         Handler(Looper.getMainLooper()).post {
        //             isUhfDeviceScanning = false
        //             Log.i(TAG, "Inventarisasi UHF SDK selesai via listener.")
        //             onUhfInventoryFinished?.invoke()
        //             onUhfOperationStopped?.invoke() // BARU: Panggil juga callback umum
        //         }
        //     }
        // })
        Log.d(TAG, "Simulasi: setupUhfListener dipanggil.") // Menggunakan TAG
    }

    private fun setupBarcodeListener() { // Nama fungsi tidak diubah
        // TODO SDK: Daftarkan listener ke instance barcodeScanner SDK Anda
        // barcodeScanner?.setScanResultListener(object : ScanResultListener {
        //     override fun onScanSuccess(barcodeType: Int, barcode: String?) {
        //         barcode?.let { data ->
        //             Handler(Looper.getMainLooper()).post {
        //                 onBarcodeScanned?.invoke(data)
        //                 isBarcodeDeviceScanning = false // Untuk scan tunggal
        //             }
        //         }
        //     }

        //     override fun onScanFailure(errorCode: Int, errorMessage: String?) {
        //         Handler(Looper.getMainLooper()).post {
        //             onError?.invoke("Barcode Scan Error SDK: $errorMessage (code: $errorCode)")
        //             isBarcodeDeviceScanning = false
        //         }
        //     }
        // })
        Log.d(TAG, "Simulasi: setupBarcodeListener dipanggil.") // Menggunakan TAG
    }

    private fun simulateUhfTag(epc: String, delay: Long) { // Nama fungsi tidak diubah
        Handler(Looper.getMainLooper()).postDelayed({
            if (isUhfDeviceScanning) {
                Log.d(TAG, "Simulasi: UHF Tag Diterima: $epc") // Menggunakan TAG
                onUhfTagScanned?.invoke(epc)
            }
        }, delay)
    }

    // --- 6. Metode untuk Melepaskan Resource (Nama fungsi tidak diubah) ---
    fun releaseResources() { // Nama fungsi tidak diubah
        Log.i(TAG, "Melepaskan resource ChainwaySDKManager.") // Menggunakan TAG
        if (isUhfDeviceScanning) {
            stopUhfInventory() // Panggil fungsi yang ada
        }
        if (isBarcodeDeviceScanning) {
            stopBarcodeScan() // Panggil fungsi yang ada
        }

        // TODO SDK: Lepaskan listener dan tutup/bebaskan resource SDK di sini
        // if (isUhfSdkInitialized && uhfReader != null) {
        //     uhfReader?.setInventoryListener(null)
        //     uhfReader?.free()
        //     isUhfSdkInitialized = false
        //     isUhfConnected = false
        //     Log.i(TAG, "UHF Reader resources released.")
        //     onDeviceStatusChanged?.invoke(isUhfConnected, "UHF")
        // }

        // if (isBarcodeSdkInitialized && barcodeScanner != null) {
        //     barcodeScanner?.setScanResultListener(null)
        //     barcodeScanner?.close()
        //     isBarcodeSdkInitialized = false
        //     isBarcodeConnected = false
        //     Log.i(TAG, "Barcode Scanner resources released.")
        //     onDeviceStatusChanged?.invoke(isBarcodeConnected, "Barcode")
        // }

        Log.i(TAG, "Simulasi: Resource SDK Manager dilepaskan.")
        // Untuk simulasi, reset status
        isUhfSdkInitialized = false; isUhfConnected = false; onDeviceStatusChanged?.invoke(false, "UHF (Simulated)")
        isBarcodeSdkInitialized = false; isBarcodeConnected = false; onDeviceStatusChanged?.invoke(false, "Barcode (Simulated)")
    }
}
