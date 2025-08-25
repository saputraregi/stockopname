package com.example.aplikasistockopnameperpus.sdk

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.rscja.deviceapi.RFIDWithUHFUART // Pastikan import ini ada jika tipe parameter lock menggunakan enum dari sini
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.barcode.BarcodeDecoder
import com.rscja.barcode.BarcodeFactory
import com.rscja.deviceapi.entity.BarcodeEntity

class ChainwaySDKManager(private val application: Application) {

    // --- 1. Properti untuk State dan Instance SDK ---
    private var uhfReader: RFIDWithUHFUART? = null
    private var barcodeDecoder: BarcodeDecoder? = null

    private var isUhfSdkInitialized: Boolean = false
    private var isBarcodeSdkInitialized: Boolean = false
    private var isUhfConnected: Boolean = false
    private var isBarcodeConnected: Boolean = false

    var isUhfDeviceScanning: Boolean = false
        private set
    var isBarcodeDeviceScanning: Boolean = false
        private set

    // --- 2. Callback untuk Berkomunikasi dengan ViewModel ---
    var onUhfTagScanned: ((epc: String) -> Unit)? = null
    var onBarcodeScanned: ((barcodeData: String) -> Unit)? = null
    var onError: ((message: String) -> Unit)? = null
    var onUhfInventoryFinished: (() -> Unit)? = null
    var onDeviceStatusChanged: ((isConnected: Boolean, deviceType: String) -> Unit)? = null

    var onTagWriteSuccess: ((writtenEpc: String) -> Unit)? = null
    var onTagWriteFailed: ((errorMessage: String) -> Unit)? = null
    var onTagReadTidSuccess: ((tid: String, epcOfTagRead: String?) -> Unit)? = null
    var onTagReadTidFailed: ((errorMessage: String) -> Unit)? = null

    var onSingleUhfTagEpcRead: ((epc: String) -> Unit)? = null
    var onSingleUhfTagReadFailed: ((errorMessage: String) -> Unit)? = null

    var onTagLockSuccess: (() -> Unit)? = null
    var onTagLockFailed: ((errorMessage: String) -> Unit)? = null

    var onUhfOperationStopped: (() -> Unit)? = null

    companion object {
        private val TAG = ChainwaySDKManager::class.java.simpleName
        // Contoh konstanta jika Anda sudah tahu nilai integer dari dokumentasi
        // const val BANK_EPC_INT = 1 // Misalnya, jika SDK mendefinisikan bank EPC sebagai 1
        // const val BANK_TID_INT = 2 // Misalnya
        // const val ACTION_LOCK_INT = 0 // Misalnya
    }

    init {
        Log.d(TAG, "ChainwaySDKManager constructor called.")
        initializeModules()
    }

    fun initializeModules() {
        Log.i(TAG, "Initializing SDK modules...")
        try {
            if (uhfReader == null) {
                uhfReader = RFIDWithUHFUART.getInstance()
            }
            if (!isUhfSdkInitialized) {
                isUhfSdkInitialized = uhfReader?.init(application.applicationContext) ?: false
                if (isUhfSdkInitialized) {
                    setupUhfListener()
                    Log.i(TAG, "UHF Reader SDK initialized successfully.")
                    isUhfConnected = true
                    Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(isUhfConnected, "UHF") }
                } else {
                    Log.e(TAG, "Failed to initialize UHF Reader SDK.")
                    Handler(Looper.getMainLooper()).post { onError?.invoke("Gagal inisialisasi modul UHF.") }
                    isUhfConnected = false
                    Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(isUhfConnected, "UHF") }
                }
            } else {
                Log.i(TAG, "UHF Reader SDK already initialized.")
                if(isUhfConnected) Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(true, "UHF") }
            }
        } catch (e: ConfigurationException) {
            Log.e(TAG, "ConfigurationException during UHF SDK initialization", e)
            Handler(Looper.getMainLooper()).post { onError?.invoke("Error Konfigurasi SDK UHF: ${e.message}") }
            isUhfSdkInitialized = false; isUhfConnected = false
            Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(isUhfConnected, "UHF") }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during UHF SDK module initialization", e)
            Handler(Looper.getMainLooper()).post { onError?.invoke("Error saat inisialisasi modul UHF: ${e.message}") }
            isUhfSdkInitialized = false; isUhfConnected = false
            Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(isUhfConnected, "UHF") }
        }

        try {
            if (barcodeDecoder == null) {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder()
            }
            if (!isBarcodeSdkInitialized) {
                val barcodeOpenSuccess = barcodeDecoder?.open(application.applicationContext) ?: false
                if (barcodeOpenSuccess) {
                    isBarcodeSdkInitialized = true
                    setupBarcodeListener()
                    Log.i(TAG, "Barcode Scanner SDK initialized and opened successfully.")
                    isBarcodeConnected = true
                    Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(isBarcodeConnected, "Barcode") }
                } else {
                    Log.e(TAG, "Failed to initialize or open Barcode Scanner SDK.")
                    Handler(Looper.getMainLooper()).post { onError?.invoke("Gagal inisialisasi modul Barcode.") }
                    isBarcodeSdkInitialized = false; isBarcodeConnected = false
                    Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(isBarcodeConnected, "Barcode") }
                }
            } else {
                Log.i(TAG, "Barcode Scanner SDK already initialized.")
                if(isBarcodeConnected) Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(true, "Barcode") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Barcode Scanner SDK module initialization", e)
            Handler(Looper.getMainLooper()).post { onError?.invoke("Error kritis saat inisialisasi modul Barcode: ${e.message}") }
            isBarcodeSdkInitialized = false; isBarcodeConnected = false
            Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(isBarcodeConnected, "Barcode") }
        }
    }

    fun connectDevices(): Boolean {
        if (!isUhfConnected || !isBarcodeConnected) {
            Log.i(TAG, "Attempting to re-initialize/connect modules via connectDevices().")
            initializeModules()
        }
        Log.i(TAG, "connectDevices called (current state: UHF=$isUhfConnected, Barcode=$isBarcodeConnected)")
        return isUhfConnected && isBarcodeConnected
    }

    fun disconnectDevices() {
        Log.i(TAG, "disconnectDevices called. Releasing resources...")
        releaseResources()
    }

    fun isDeviceReady(type: String = "any"): Boolean {
        return when (type.lowercase()) {
            "uhf" -> isUhfSdkInitialized && isUhfConnected && uhfReader != null
            "barcode" -> isBarcodeSdkInitialized && isBarcodeConnected && barcodeDecoder != null
            "any" -> (isUhfSdkInitialized && isUhfConnected && uhfReader != null) || (isBarcodeSdkInitialized && isBarcodeConnected && barcodeDecoder != null)
            else -> false
        }
    }

    fun startUhfInventory() {
        if (!isDeviceReady("uhf")) {
            Handler(Looper.getMainLooper()).post { onError?.invoke("Modul UHF tidak siap atau tidak terhubung.") }
            return
        }
        if (isBarcodeDeviceScanning) {
            Handler(Looper.getMainLooper()).post { onError?.invoke("Hentikan scan barcode terlebih dahulu.") }
            return
        }
        if (isUhfDeviceScanning) {
            Log.w(TAG, "UHF scan sudah berjalan.")
            return
        }
        val success = uhfReader?.startInventoryTag()
        if (success == true) {
            isUhfDeviceScanning = true
            Log.i(TAG, "Inventarisasi UHF SDK DIMULAI.")
        } else {
            isUhfDeviceScanning = false
            Log.e(TAG, "Gagal memulai scan UHF dari SDK.")
            Handler(Looper.getMainLooper()).post {
                onError?.invoke("Gagal memulai scan UHF dari SDK.")
                onUhfInventoryFinished?.invoke()
                onUhfOperationStopped?.invoke()
            }
        }
    }

    fun stopUhfInventory() {
        if (!isDeviceReady("uhf")) {
            Log.w(TAG, "UHF tidak siap saat mencoba stop inventory.")
            if (!isUhfDeviceScanning) {
                Handler(Looper.getMainLooper()).post {
                    onUhfInventoryFinished?.invoke()
                    onUhfOperationStopped?.invoke()
                }
            }
            return
        }
        if (!isUhfDeviceScanning) {
            Log.i(TAG, "UHF Inventory sudah tidak berjalan.")
            Handler(Looper.getMainLooper()).post {
                onUhfInventoryFinished?.invoke()
                onUhfOperationStopped?.invoke()
            }
            return
        }
        val stopped = uhfReader?.stopInventory()
        Log.i(TAG, "Perintah stopInventory() dikirim ke SDK, hasil: $stopped")
        isUhfDeviceScanning = false
        Handler(Looper.getMainLooper()).post {
            onUhfInventoryFinished?.invoke()
            onUhfOperationStopped?.invoke()
        }
        if (stopped != true) {
            Log.e(TAG, "SDK melaporkan gagal menghentikan inventory.")
        } else {
            Log.i(TAG, "Inventarisasi UHF SDK dihentikan.")
        }
    }

    fun startBarcodeScan() {
        if (!isDeviceReady("barcode")) {
            Handler(Looper.getMainLooper()).post { onError?.invoke("Modul Barcode tidak siap.") }
            return
        }
        if (isUhfDeviceScanning) {
            Handler(Looper.getMainLooper()).post { onError?.invoke("Hentikan scan UHF terlebih dahulu.") }
            return
        }
        if (isBarcodeDeviceScanning) {
            Log.w(TAG, "Barcode scan sudah aktif.")
            return
        }
        barcodeDecoder?.startScan()
        isBarcodeDeviceScanning = true
        Log.i(TAG, "Scan barcode SDK dipicu.")
    }

    fun stopBarcodeScan() {
        if (!isDeviceReady("barcode")) {
            Log.w(TAG, "Barcode tidak siap saat mencoba stop scan.")
            return
        }
        if (!isBarcodeDeviceScanning) {
            Log.i(TAG, "Barcode scan sudah tidak berjalan.")
            return
        }
        barcodeDecoder?.stopScan()
        isBarcodeDeviceScanning = false
        Log.i(TAG, "Scan barcode SDK dihentikan.")
    }

    fun writeUhfTag(epcToWrite: String, currentEpc: String? = null, passwordAccess: String = "00000000") {
        if (!isDeviceReady("uhf")) {
            Handler(Looper.getMainLooper()).post {
                onError?.invoke("Modul UHF tidak siap untuk menulis tag.")
                onTagWriteFailed?.invoke("UHF not ready")
            }
            return
        }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) {
            Handler(Looper.getMainLooper()).post {
                onError?.invoke("Device sedang sibuk, tidak bisa menulis tag sekarang.")
                onTagWriteFailed?.invoke("Device busy (scan active)")
            }
            return
        }
        Log.i(TAG, "Memulai penulisan EPC: $epcToWrite (Target EPC: ${currentEpc ?: "tidak spesifik"})")
        isUhfDeviceScanning = true

        val newEpcHex = epcToWrite.replace(" ", "").uppercase()
        val expectedLength = 24 // Asumsi EPC 96-bit (24 hex characters)
        if (newEpcHex.length != expectedLength || !newEpcHex.matches(Regex("^[0-9A-Fa-f]*$"))) {
            Log.e(TAG, "EPC to write is not valid hex or length (expected $expectedLength chars): $newEpcHex")
            Handler(Looper.getMainLooper()).post { onTagWriteFailed?.invoke("Format EPC tidak valid (HEX/panjang $expectedLength karakter).") }
            isUhfDeviceScanning = false
            Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
            return
        }

        // TODO: Implementasi filter/pemilihan tag berdasarkan currentEpc jika diperlukan oleh SDK
        // sebelum memanggil writeData. Contoh:
        // if (currentEpc != null) {
        //     if (uhfReader?.selectTag(currentEpc, passwordAccess, RFIDWithUHFUART.Bank_EPC, 0, 0) == false) { // Parameter contoh
        //         Log.e(TAG, "Gagal memilih tag target: $currentEpc")
        //         Handler(Looper.getMainLooper()).post { onTagWriteFailed?.invoke("Gagal memilih tag target.") }
        //         isUhfDeviceScanning = false
        //         Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
        //         return
        //     }
        // }

        val success = uhfReader?.writeData(
            passwordAccess,
            RFIDWithUHFUART.Bank_EPC, // Gunakan konstanta SDK jika tersedia, atau nilai integer yang sesuai (biasanya 1 untuk EPC)
            2, // Start address (word) untuk EPC biasanya 2
            newEpcHex.length / 4, // Count (words)
            newEpcHex
        )

        if (success == true) {
            Log.i(TAG, "Penulisan EPC '$newEpcHex' BERHASIL.")
            Handler(Looper.getMainLooper()).post { onTagWriteSuccess?.invoke(newEpcHex) }
        } else {
            val errorMsg = "Gagal menulis EPC '$newEpcHex' ke tag."
            Log.e(TAG, errorMsg)
            Handler(Looper.getMainLooper()).post { onTagWriteFailed?.invoke(errorMsg) }
        }
        isUhfDeviceScanning = false
        Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
    }

    fun readUhfTagTid(targetEpc: String? = null, passwordAccess: String = "00000000") {
        if (!isDeviceReady("uhf")) {
            Handler(Looper.getMainLooper()).post {
                onError?.invoke("Modul UHF tidak siap untuk membaca TID.")
                onTagReadTidFailed?.invoke("UHF not ready")
            }
            return
        }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) {
            Handler(Looper.getMainLooper()).post {
                onError?.invoke("Device sedang sibuk, tidak bisa membaca TID sekarang.")
                onTagReadTidFailed?.invoke("Device busy (scan active)")
            }
            return
        }
        Log.i(TAG, "Memulai pembacaan TID (Target EPC: ${targetEpc ?: "tag terdekat"})")
        isUhfDeviceScanning = true

        var tidRead: String? = null
        var epcOfTagRead: String? = null

        // TODO: Implementasi filter/pemilihan tag berdasarkan targetEpc jika diperlukan oleh SDK
        // Contoh: if (targetEpc != null) { uhfReader?.selectTag(...) }

        // Penting: Pastikan reader dikonfigurasi untuk membaca TID.
        // Ini mungkin perlu dilakukan sekali saat inisialisasi atau sebelum operasi ini.
        // Contoh potensial (perlu verifikasi dengan dokumentasi SDK Anda):
        // val queryTagGroup = uhfReader?.getQueryTagGroup()
        // queryTagGroup?.session = Session.S0 // atau session lain
        // queryTagGroup?.selected = UHFTAGInfo.PARAM_TID // atau flag yang sesuai untuk TID
        // queryTagGroup?.target = Target.A_TO_B // atau target lain
        // uhfReader?.setQueryTagGroup(queryTagGroup)
        // ATAU:
        // uhfReader?.setEPCAndTIDMode() // Jika SDK memiliki fungsi simpel seperti ini

        val tagInfo = uhfReader?.inventorySingleTag() // Mencoba membaca satu tag

        if (tagInfo != null && !tagInfo.tid.isNullOrEmpty()) {
            tidRead = tagInfo.tid
            epcOfTagRead = tagInfo.epc
            Log.i(TAG, "Pembacaan TID BERHASIL. TID: $tidRead, EPC Terbaca: $epcOfTagRead")
            Handler(Looper.getMainLooper()).post { onTagReadTidSuccess?.invoke(tidRead!!, epcOfTagRead) }
        } else if (tagInfo != null && (tagInfo.tid.isNullOrEmpty())) {
            val errorMsg = "Tag ditemukan (EPC: ${tagInfo.epc ?: "N/A"}) tetapi TID kosong/tidak terbaca. Pastikan mode pembacaan TID aktif."
            Log.w(TAG, errorMsg)
            Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke(errorMsg) }
        } else {
            val errorMsg = "Gagal membaca TID. Tidak ada tag yang ditemukan atau TID tidak tersedia."
            Log.e(TAG, errorMsg)
            Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke(errorMsg) }
        }
        isUhfDeviceScanning = false
        Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
    }

    fun readSingleUhfTagEpc() {
        if (!isDeviceReady("uhf")) {
            Handler(Looper.getMainLooper()).post {
                onError?.invoke("Modul UHF tidak siap untuk membaca tag tunggal.")
                onSingleUhfTagReadFailed?.invoke("UHF not ready")
            }
            return
        }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) {
            Handler(Looper.getMainLooper()).post {
                onError?.invoke("Device sedang sibuk, tidak bisa membaca tag tunggal sekarang.")
                onSingleUhfTagReadFailed?.invoke("Device busy (scan active)")
            }
            return
        }
        Log.i(TAG, "Memulai pembacaan EPC tag tunggal...")
        isUhfDeviceScanning = true

        val tagInfo = uhfReader?.inventorySingleTag()

        if (tagInfo != null && !tagInfo.epc.isNullOrEmpty()) {
            Log.i(TAG, "Pembacaan EPC tunggal BERHASIL. EPC: ${tagInfo.epc}")
            Handler(Looper.getMainLooper()).post { onSingleUhfTagEpcRead?.invoke(tagInfo.epc) }
        } else {
            val errorMsg = "Gagal membaca EPC tag tunggal. Tidak ada tag ditemukan atau EPC kosong."
            Log.e(TAG, errorMsg)
            Handler(Looper.getMainLooper()).post { onSingleUhfTagReadFailed?.invoke(errorMsg) }
        }
        isUhfDeviceScanning = false
        Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
    }

    fun lockUhfTag(
        targetEpc: String?,
        passwordAccess: String,
        lockBankInt: Int, // Terima integer untuk bank
        lockActionInt: Int  // Terima integer untuk action
    ) {
        if (!isDeviceReady("uhf")) {
            Handler(Looper.getMainLooper()).post {
                onError?.invoke("Modul UHF tidak siap untuk mengunci tag.")
                onTagLockFailed?.invoke("UHF not ready")
            }
            return
        }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) {
            Handler(Looper.getMainLooper()).post {
                onError?.invoke("Device sedang sibuk, tidak bisa mengunci tag sekarang.")
                onTagLockFailed?.invoke("Device busy (scan active)")
            }
            return
        }

        Log.i(TAG, "Memulai penguncian tag (Target EPC: ${targetEpc ?: "tag terdekat"}, BankInt: $lockBankInt, AksiInt: $lockActionInt)")
        isUhfDeviceScanning = true

        // TODO: Implementasi filter berdasarkan targetEpc jika SDK mendukungnya dan diperlukan.
        // Contoh: if (targetEpc != null) { if (uhfReader?.selectTag(...) == false) { /* handle error */ return; } }

        // Anda PERLU MERUJUK DOKUMENTASI SDK untuk mengetahui fungsi lock mana yang tepat
        // dan bagaimana parameter integer lockBankInt dan lockActionInt dipetakan.
        // Fungsi lockMem(String password, String lockCode) atau
        // lockMem(String var1, int var2, int var3, int var4, String var5, String var6)
        // atau uhfBlockPermalock(...) mungkin yang relevan.

        var success = false
        // --- AWAL BAGIAN YANG PERLU ANDA SESUAIKAN BERDASARKAN DOKUMENTASI SDK ---
        // Contoh Hipotetis 1: Jika lockMem(password, lockCode) yang digunakan
        // String lockCode = uhfReader?.generateLockCode(...); // Anda perlu tahu cara generate lockCode
        // if (lockCode != null) {
        //     success = uhfReader?.lockMem(passwordAccess, lockCode) ?: false
        // }

        // Contoh Hipotetis 2: Jika lockMem(String, int, int, int, String, String) yang digunakan
        // Dan Anda sudah tahu arti dari var2, var3, var4 dari dokumentasi
        // (misalnya var2=bank, var3=action, var4=mask/address)
        // success = uhfReader?.lockMem(passwordAccess, lockBankInt, lockActionInt, 0 /* contoh mask/address */, "", "") ?: false

        // Untuk sekarang, kita akan biarkan ini mengembalikan false karena implementasi SDK belum pasti.
        // HAPUS ATAU GANTI BAGIAN INI DENGAN PEMANGGILAN SDK YANG BENAR:
        Log.e(TAG, "Fungsi lock SDK yang sebenarnya belum diimplementasikan di ChainwaySDKManager. Merujuk ke dokumentasi Anda.")
        Handler(Looper.getMainLooper()).post { onTagLockFailed?.invoke("Fungsi lock SDK belum diimplementasikan dengan benar.") }
        // --- AKHIR BAGIAN YANG PERLU ANDA SESUAIKAN ---


        if (success) { // Hanya jika pemanggilan SDK yang sebenarnya berhasil
            Log.i(TAG, "Penguncian tag BERHASIL (perintah SDK sukses).")
            Handler(Looper.getMainLooper()).post { onTagLockSuccess?.invoke() }
        }
        // Jika 'success' tetap false karena implementasi belum ada, callback onTagLockFailed sudah dipanggil di atas.

        isUhfDeviceScanning = false
        Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
    }

    fun stopUhfOperation() {
        Log.i(TAG, "Attempting to stop current UHF operation (isUhfDeviceScanning: $isUhfDeviceScanning).")
        if (isUhfDeviceScanning) {
            val stopped = uhfReader?.stopInventory()
            Log.i(TAG, "stopInventory() dipanggil, hasil: $stopped.")
            isUhfDeviceScanning = false
            Handler(Looper.getMainLooper()).post {
                onUhfOperationStopped?.invoke()
                onUhfInventoryFinished?.invoke()
            }
            if (stopped == true) {
                Log.i(TAG, "Operasi UHF (kemungkinan inventory) dihentikan.")
            } else {
                Log.w(TAG, "Gagal mengirim perintah stopInventory() atau operasi bukan inventory (state tetap direset).")
            }
        } else {
            Log.i(TAG, "Tidak ada operasi UHF yang aktif untuk dihentikan.")
            Handler(Looper.getMainLooper()).post {
                onUhfOperationStopped?.invoke()
                onUhfInventoryFinished?.invoke()
            }
        }
    }

    fun getUhfReaderInstance(): RFIDWithUHFUART? {
        if (isDeviceReady("uhf")) {
            return uhfReader
        }
        return null
    }

    private fun setupUhfListener() {
        if (uhfReader == null) {
            Log.w(TAG, "UHF Reader instance is null, cannot set up IUHFInventoryCallback.")
            return
        }
        uhfReader?.setInventoryCallback(object : IUHFInventoryCallback {
            override fun callback(tagInfo: UHFTAGInfo?) {
                tagInfo?.epc?.let { epc ->
                    if (epc.isNotEmpty()) {
                        Handler(Looper.getMainLooper()).post {
                            onUhfTagScanned?.invoke(epc)
                        }
                    }
                }
            }
        })
        Log.i(TAG, "UHF IUHFInventoryCallback listener setup successfully.")
    }

    private fun setupBarcodeListener() {
        if (barcodeDecoder == null) {
            Log.w(TAG, "Barcode Decoder instance is null, cannot set up DecodeCallback.")
            return
        }
        barcodeDecoder?.setDecodeCallback(object : BarcodeDecoder.DecodeCallback {
            override fun onDecodeComplete(barcodeEntity: BarcodeEntity?) {
                Handler(Looper.getMainLooper()).post {
                    if (barcodeEntity?.resultCode == BarcodeDecoder.DECODE_SUCCESS) {
                        barcodeEntity.barcodeData?.let { data ->
                            if (data.isNotEmpty()) {
                                Log.i(TAG, "Barcode Scanned: $data")
                                onBarcodeScanned?.invoke(data)
                            } else {
                                Log.w(TAG, "Barcode scan success but data is empty.")
                            }
                        }
                    } else {
                        val errorMsg = "Barcode scan failed. Result code: ${barcodeEntity?.resultCode}"
                        Log.e(TAG, errorMsg)
                        onError?.invoke(errorMsg)
                    }
                    isBarcodeDeviceScanning = false
                }
            }
        })
        Log.i(TAG, "Barcode DecodeCallback listener setup successfully.")
    }

    fun releaseResources() {
        Log.i(TAG, "Releasing ChainwaySDKManager resources...")
        if (isUhfDeviceScanning) {
            uhfReader?.stopInventory()
            isUhfDeviceScanning = false
            Handler(Looper.getMainLooper()).post {
                onUhfInventoryFinished?.invoke()
                onUhfOperationStopped?.invoke()
            }
        }
        if (isBarcodeDeviceScanning) {
            barcodeDecoder?.stopScan()
            isBarcodeDeviceScanning = false
        }

        if (uhfReader != null) {
            uhfReader?.setInventoryCallback(null)
            uhfReader?.free() // Panggil free() sebelum men-null-kan instance
            uhfReader = null
            Log.i(TAG, "UHF Reader resources released.")
        }
        isUhfSdkInitialized = false
        isUhfConnected = false
        Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(false, "UHF") }

        if (barcodeDecoder != null) {
            barcodeDecoder?.setDecodeCallback(null)
            barcodeDecoder?.close()
            barcodeDecoder = null
            Log.i(TAG, "Barcode Scanner resources released.")
        }
        isBarcodeSdkInitialized = false
        isBarcodeConnected = false
        Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(false, "Barcode") }

        Log.i(TAG, "All SDK Manager resources have been released.")
    }
}
