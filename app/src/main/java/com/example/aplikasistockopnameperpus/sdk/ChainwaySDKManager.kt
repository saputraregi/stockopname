package com.example.aplikasistockopnameperpus.sdk

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.Gen2Entity
import com.rscja.deviceapi.entity.InventoryModeEntity
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.barcode.BarcodeDecoder
import com.rscja.barcode.BarcodeFactory
import com.rscja.deviceapi.entity.BarcodeEntity
import com.rscja.deviceapi.entity.RadarLocationEntity
import com.rscja.deviceapi.interfaces.ConnectionStatus
import com.rscja.deviceapi.interfaces.IUHF
import com.rscja.deviceapi.interfaces.IUHFRadarLocationCallback
import com.example.aplikasistockopnameperpus.model.RadarUiTag

object ErrorCodeManage {
    // ... (Implementasi ErrorCodeManage.getMessage tetap sama seperti yang terakhir kita diskusikan)
    // Konstanta berdasarkan UhfBase$ErrorCode.java
    private const val SUCCESS = 0
    private const val FAILURE = -1
    private const val ERROR_NO_TAG = 1
    private const val ERROR_INSUFFICIENT_PRIVILEGES = 2
    private const val ERROR_MEMORY_OVERRUN = 3
    private const val ERROR_MEMORY_LOCK = 4
    private const val ERROR_TAG_NO_REPLY = 5
    private const val ERROR_PASSWORD_IS_INCORRECT = 6
    private const val ERROR_RESPONSE_BUFFER_OVERFLOW = 7
    private const val ERROR_NO_ENOUGH_POWER_ON_TAG = 11
    private const val ERROR_OPERATION_FAILED = 255
    private const val ERROR_SEND_FAIL = 252
    private const val ERROR_RECV_FAIL = 253

    fun getMessage(errorCode: Int?): String {
        if (errorCode == null) {
            Log.w("ErrorCodeManage", "Received null error code.")
            return "Unknown SDK error (code not available)"
        }
        val message = when (errorCode) {
            SUCCESS -> "Operation successful"
            FAILURE -> "Operation failed (general failure, code: -1)"
            ERROR_NO_TAG -> "No tag found or detected"
            ERROR_INSUFFICIENT_PRIVILEGES -> "Insufficient privileges for the operation"
            ERROR_MEMORY_OVERRUN -> "Memory overrun"
            ERROR_MEMORY_LOCK -> "Memory is locked"
            ERROR_TAG_NO_REPLY -> "Tag did not reply"
            ERROR_PASSWORD_IS_INCORRECT -> "Access password incorrect"
            ERROR_RESPONSE_BUFFER_OVERFLOW -> "Response buffer overflow"
            ERROR_NO_ENOUGH_POWER_ON_TAG -> "Insufficient power on tag"
            ERROR_OPERATION_FAILED -> "Operation failed (specific, code: 255)"
            ERROR_SEND_FAIL -> "Failed to send command to UHF module"
            ERROR_RECV_FAIL -> "Failed to receive response from UHF module"
            else -> "Unknown or Unmapped SDK Error (Code: $errorCode)"
        }
        Log.d("ErrorCodeManage", "Mapping Error Code: $errorCode -> Message: '$message'")
        return message
    }
}

class ChainwaySDKManager(private val application: Application) {

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
    var isUhfRadarActive: Boolean = false
        private set

    // ... (Callback-callback properti tetap sama) ...
    var onUhfTagScanned: ((epc: String) -> Unit)? = null
    var onBarcodeScanned: ((barcodeData: String) -> Unit)? = null
    var onError: ((message: String) -> Unit)? = null
    var onUhfInventoryFinished: (() -> Unit)? = null
    var onDeviceStatusChanged: ((isConnected: Boolean, deviceType: String) -> Unit)? = null
    var onTagWriteSuccess: ((writtenEpc: String) -> Unit)? = null
    var onTagWriteFailed: ((errorMessage: String) -> Unit)? = null
    var onTagReadTidSuccess: ((tid: String, epcOfTagRead: String?) -> Unit)? = null
    var onTagReadTidFailed: ((errorMessage: String) -> Unit)? = null
    var onSingleUhfTagEpcRead: ((epc: String, tidOfTagRead: String?) -> Unit)? = null
    var onSingleUhfTagReadFailed: ((errorMessage: String) -> Unit)? = null
    var onTagLockSuccess: (() -> Unit)? = null
    var onTagLockFailed: ((errorMessage: String) -> Unit)? = null
    var onUhfOperationStopped: (() -> Unit)? = null
    var onUhfRadarDataUpdated: ((tags: List<RadarUiTag>) -> Unit)? = null
    var onUhfRadarError: ((message: String) -> Unit)? = null
    var onUhfRadarStarted: (() -> Unit)? = null
    var onUhfRadarStopped: (() -> Unit)? = null

    companion object {
        private val TAG = ChainwaySDKManager::class.java.simpleName

        // Konstanta Region Frekuensi UHF berdasarkan analisis UHFSetFragment.java
        // dari aplikasi demo Chainway.

        /** China Standard (840-845MHz) */
        const val REGION_CHINA_840_845 = 0x01
        /** China Standard (920-925MHz) */
        const val REGION_CHINA_920_925 = 0x02
        /** ETSI Standard (Europe) */
        const val REGION_ETSI_STANDARD = 0x04
        /** United States Standard */
        const val REGION_USA_STANDARD = 0x08
        /** Korea */
        const val REGION_KOREA = 0x16 // Sesuai dengan getMode() di UHFSetFragment
        /** Japan */
        const val REGION_JAPAN = 0x32 // Sesuai dengan getMode() di UHFSetFragment
        /** South Africa 915-919MHz */
        const val REGION_SOUTH_AFRICA_915_919 = 0x33
        /** New Zealand */
        const val REGION_NEW_ZEALAND = 0x34
        /** Morocco */
        const val REGION_MOROCCO = 0x80

        // Tambahkan region lain jika Anda menemukannya di R.string.xxx_Standard
        // atau jika SDK mendukungnya dengan kode berbeda.

        // Default jika tidak ada yang cocok (mengikuti logika getMode())
        const val REGION_DEFAULT = REGION_USA_STANDARD // Atau nilai lain yang sesuai

        // Konstanta Protokol (Jika belum ada di IUHF dan diperlukan)
        const val PROTOCOL_GEN2 = 0 // Umumnya 0 untuk ISO18000-6C / EPC Gen2
        // UHFSetFragment menggunakan SpinnerAgreement.getSelectedItemPosition()
        // yang biasanya dimulai dari 0.
        private const val SDK_FRONT_OFFSET = 0
    }

    private var sdkRawRadarAngle: Int = 0

    private val radarCallbackInternal = object : IUHFRadarLocationCallback {
        override fun getAngleValue(angle: Int) {
            // Fungsi ini sengaja dikosongkan karena kita menggunakan sudut dari setiap tag
        }

        override fun getLocationValue(list: MutableList<RadarLocationEntity>?) {
            if (list == null) {
                Handler(Looper.getMainLooper()).post { onUhfRadarDataUpdated?.invoke(emptyList()) }
                return
            }

            val processedTags = list.map { sdkTag ->
                val rawAngle = sdkTag.angle
                val normalizedAngle = rawAngle - SDK_FRONT_OFFSET
                val finalUiAngle = -normalizedAngle

                RadarUiTag(
                    epc = sdkTag.tag,
                    distanceValue = sdkTag.value,
                    uiAngle = finalUiAngle
                )
            }

            // Logging ini SANGAT PENTING untuk debug. Jangan dihapus.
            if (processedTags.isNotEmpty()) {
                val firstTag = processedTags.first()
                Log.d(TAG, "Radar Data: Ditemukan ${processedTags.size} tag. Contoh Tag Pertama -> EPC: ${firstTag.epc}, Sudut UI Final: ${firstTag.uiAngle}")
            }

            Handler(Looper.getMainLooper()).post {
                onUhfRadarDataUpdated?.invoke(processedTags)
            }
        }
    }
// ...

// Pastikan callback di Activity/ViewModel Anda mengharapkan sudut yang sudah disesuaikan:
// var onUhfRadarDataUpdated: ((tags: List<RadarLocationEntity>, adjustedUiAngle: Int) -> Unit)? = null

    init {
        Log.d(TAG, "ChainwaySDKManager constructor called.")
    }

    private fun getUhfLastErrorString(): String {
        return try {
            val errorCode = uhfReader?.getErrCode()
            ErrorCodeManage.getMessage(errorCode)
        } catch (e: Exception) {
            "Error getting SDK error message: ${e.message}"
        }
    }

    // Fungsi initUhfRadarIfNeeded DIPINDAHKAN KE ATAS startUhfRadar
    private fun initUhfRadarIfNeeded(contextPassed: Context): Boolean {
        if (!isDeviceReady("uhf")) {
            initializeModules() // Ini akan mencoba menginisialisasi dan menghubungkan
            if (!isDeviceReady("uhf")) { // Cek lagi setelah upaya
                Log.e(TAG, "UHF Reader tidak siap untuk radar setelah upaya inisialisasi.")
                Handler(Looper.getMainLooper()).post { onUhfRadarError?.invoke("UHF Reader tidak siap.") }
                return false
            }
        }
        return true
    }


    fun initializeModules() {
        Log.i(TAG, "Initializing SDK modules...")
        // Inisialisasi UHF
        try {
            if (uhfReader == null) {
                uhfReader = RFIDWithUHFUART.getInstance()
                Log.d(TAG, "RFIDWithUHFUART.getInstance() called.")
            }
            val currentStatus = try { uhfReader?.getConnectStatus() } catch (e: Exception) { null }

            if (currentStatus == ConnectionStatus.CONNECTED && isUhfSdkInitialized) {
                isUhfConnected = true
                Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(true, "UHF") }
                Log.i(TAG, "UHF Reader already initialized and connected.")
            } else {
                Log.d(TAG, "Attempting uhfReader.init(). Current status: $currentStatus, isUhfSdkInitialized: $isUhfSdkInitialized")
                val initSuccess = uhfReader?.init(application.applicationContext) ?: false
                isUhfSdkInitialized = true

                if (initSuccess) {
                    val postInitStatus = try { uhfReader?.getConnectStatus() } catch (e: Exception) { null }
                    isUhfConnected = (postInitStatus == ConnectionStatus.CONNECTED)
                    if (isUhfConnected) {
                        setupUhfListener()
                        Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(true, "UHF") }
                        Log.i(TAG, "UHF Reader SDK initialized and connected successfully.")
                    } else {
                        Handler(Looper.getMainLooper()).post { onError?.invoke("UHF SDK initialized, but device not connected (status: $postInitStatus). ${getUhfLastErrorString()}") }
                        Log.e(TAG, "UHF SDK initialized, but device not connected (status: $postInitStatus). ${getUhfLastErrorString()}")
                    }
                } else {
                    isUhfConnected = false
                    Handler(Looper.getMainLooper()).post { onError?.invoke("Gagal inisialisasi modul UHF. ${getUhfLastErrorString()}") }
                    Log.e(TAG, "Failed to initialize UHF Reader SDK. ${getUhfLastErrorString()}")
                }
            }
        } catch (e: ConfigurationException) {
            isUhfSdkInitialized = false; isUhfConnected = false
            Handler(Looper.getMainLooper()).post { onError?.invoke("Error Konfigurasi SDK UHF: ${e.message}") }
            Log.e(TAG, "ConfigurationException during UHF SDK getInstance or init", e)
        } catch (e: Exception) {
            isUhfSdkInitialized = false; isUhfConnected = false
            Handler(Looper.getMainLooper()).post { onError?.invoke("Error saat inisialisasi modul UHF: ${e.message}") }
            Log.e(TAG, "Exception during UHF SDK module initialization", e)
        }

        // Inisialisasi Barcode
        try {
            if (barcodeDecoder == null) {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder()
            }
            if (!isBarcodeSdkInitialized || !isBarcodeConnected) {
                val barcodeOpenSuccess = barcodeDecoder?.open(application.applicationContext) ?: false
                isBarcodeSdkInitialized = true
                isBarcodeConnected = barcodeOpenSuccess
                if (barcodeOpenSuccess) {
                    setupBarcodeListener()
                    Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(true, "Barcode") }
                    Log.i(TAG, "Barcode Scanner SDK initialized and opened successfully.")
                } else {
                    Handler(Looper.getMainLooper()).post { onError?.invoke("Gagal membuka modul Barcode.") }
                    Log.e(TAG, "Failed to open Barcode Scanner SDK.")
                }
            } else {
                Log.i(TAG, "Barcode Scanner SDK already initialized and connected.")
            }
        } catch (e: Exception) {
            isBarcodeSdkInitialized = false; isBarcodeConnected = false
            Handler(Looper.getMainLooper()).post { onError?.invoke("Error kritis saat inisialisasi modul Barcode: ${e.message}") }
            Log.e(TAG, "Exception during Barcode Scanner SDK module initialization", e)
        }
    }

    fun connectDevices(): Boolean {
        if (!isUhfConnected || !isBarcodeConnected) {
            Log.w(TAG, "One or more devices not connected. Calling initializeModules(). UHF: $isUhfConnected, Barcode: $isBarcodeConnected")
            initializeModules()
        }
        return isUhfConnected || isBarcodeConnected
    }

    fun isDeviceReady(type: String = "any"): Boolean {
        val uhfEffectivelyReady = if (uhfReader != null && isUhfSdkInitialized) {
            try {
                uhfReader?.getConnectStatus() == ConnectionStatus.CONNECTED
            } catch (e: Exception) {
                Log.w(TAG, "Exception checking UHF connect status for isDeviceReady: ${e.message}")
                false
            }
        } else { false }
        if (isUhfConnected != uhfEffectivelyReady) { isUhfConnected = uhfEffectivelyReady }

        val barcodeEffectivelyReady = isBarcodeConnected && barcodeDecoder != null
        return when (type.lowercase()) {
            "uhf" -> uhfEffectivelyReady
            "barcode" -> barcodeEffectivelyReady
            "any" -> uhfEffectivelyReady || barcodeEffectivelyReady
            else -> false
        }
    }

    // Di ChainwaySDKManager.kt
    // Di ChainwaySDKManager.kt
    fun startUhfInventory() {
        if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Not ready for inventory.") }; return }
        if (isBarcodeDeviceScanning) { Handler(Looper.getMainLooper()).post { onError?.invoke("Hentikan scan barcode dahulu.")}; return }
        if (isUhfRadarActive) {
            Handler(Looper.getMainLooper()).post { onError?.invoke("Hentikan radar dahulu sebelum inventory.") }; return
        }
        if (isUhfDeviceScanning) {
            Log.w(TAG, "UHF inventory scan sudah berjalan (mungkin dari instance/panggilan lain).");
            return
        }

        // --- INI ADALAH PERUBAHAN YANG SANGAT PENTING ---
        Log.d(TAG, "Setting inventory callback to 'inventoryCallback' before starting inventory.")
        uhfReader?.setInventoryCallback(inventoryCallback) // Pastikan 'inventoryCallback' adalah implementasi IUHFInventoryCallback Anda yang benar
        // ---------------------------------------------

        Log.d(TAG, "Attempting to call uhfReader.startInventoryTag(). uhfReader is null: ${uhfReader == null}")
        val started = uhfReader?.startInventoryTag()
        Log.d(TAG, "uhfReader.startInventoryTag() returned: $started")

        if (started == true) {
            isUhfDeviceScanning = true
            Log.i(TAG, "Inventarisasi UHF SDK DIMULAI.")
        } else {
            isUhfDeviceScanning = false
            // ... (blok error Anda) ...
        }
    }

    private val inventoryCallback = object : IUHFInventoryCallback {
        override fun callback(uhfTagInfo: UHFTAGInfo?) { // Nama parameter mungkin berbeda di SDK Anda, sesuaikan
            if (uhfTagInfo != null && !uhfTagInfo.epc.isNullOrEmpty()) {
                Log.d(TAG, "SDK_CALLBACK (inventoryCallback): EPC: ${uhfTagInfo.epc}")
                Handler(Looper.getMainLooper()).post {
                    onUhfTagScanned?.invoke(uhfTagInfo.epc)
                }
            } else {
                Log.w(TAG, "SDK_CALLBACK (inventoryCallback): uhfTagInfo is null or EPC is empty.")
            }
        }
        // Jika ada metode lain di IUHFInventoryCallback (seperti onInventoryStop), implementasikan juga
        // Berdasarkan definisi Anda, sepertinya hanya ada satu metode `callback`.
        // SDK Anda di RFIDWithUHFUART.java juga memiliki metode onInventoryStop di IUHFInventoryCallback yang diimplementasikan di UhfBase.
        // Jadi, callback object Anda mungkin perlu seperti ini jika itu adalah IUHFInventoryCallback yang sama yang digunakan oleh SDK:
    }

    fun writeUhfTag(epcDataHex: String, currentEpcFilter: String? = null, passwordAccess: String = "00000000") {
        if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onTagWriteFailed?.invoke("UHF not ready for write") }; return }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) { Handler(Looper.getMainLooper()).post { onTagWriteFailed?.invoke("Device busy") }; return }
        val hexToWrite = epcDataHex.replace(" ", "").uppercase()
        if (hexToWrite.length != 24 || !hexToWrite.matches(Regex("^[0-9A-Fa-f]*$"))) { Handler(Looper.getMainLooper()).post { onTagWriteFailed?.invoke("Format EPC tidak valid: $hexToWrite") }; return }
        if (uhfReader?.isInventorying() == true) {
            Log.w(TAG, "Inventory is running. Stopping before writing tag.")
            uhfReader?.stopInventory()
            try { Thread.sleep(100) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
        isUhfDeviceScanning = true
        Log.d(TAG, "Attempting to write EPC: $hexToWrite")
        val bankEpc = IUHF.Bank_EPC
        val ptrWordEpc = 2
        val lenWordEpc = hexToWrite.length / 4
        val success = uhfReader?.writeData(passwordAccess, bankEpc, ptrWordEpc, lenWordEpc, hexToWrite)
        if (success == true) { Handler(Looper.getMainLooper()).post { onTagWriteSuccess?.invoke(hexToWrite) } }
        else { Handler(Looper.getMainLooper()).post { onTagWriteFailed?.invoke("Gagal menulis EPC. ${getUhfLastErrorString()}") } }
        isUhfDeviceScanning = false
        Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
    }

    fun readSingleUhfTagEpcNearby() {
        if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onSingleUhfTagReadFailed?.invoke("UHF not ready") }; return }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) { Handler(Looper.getMainLooper()).post { onSingleUhfTagReadFailed?.invoke("Device busy") }; return }
        if (uhfReader?.isInventorying() == true) {
            Log.w(TAG, "Inventory is running. Stopping before single tag read.")
            uhfReader?.stopInventory()
            try { Thread.sleep(100) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
        isUhfDeviceScanning = true
        Log.d(TAG, "Attempting to read single EPC from nearby tag.")
        val tagInfo = uhfReader?.inventorySingleTag()
        if (tagInfo != null && !tagInfo.epc.isNullOrEmpty()) { Handler(Looper.getMainLooper()).post { onSingleUhfTagEpcRead?.invoke(tagInfo.epc, tagInfo.tid) } }
        else { val errorMsg = if (tagInfo != null) "EPC kosong/tidak terbaca." else "Tag tidak ditemukan. ${getUhfLastErrorString()}"; Handler(Looper.getMainLooper()).post { onSingleUhfTagReadFailed?.invoke(errorMsg) } }
        isUhfDeviceScanning = false
        Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
    }

    fun readTidFromTagWithEpc(epcToFilterHex: String?, passwordAccess: String = "00000000", tidReadLengthInWords: Int = 6) {
        Log.d(TAG, "readTidFromTagWithEpc called. EPC: $epcToFilterHex, Length: $tidReadLengthInWords words")
        if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke("UHF not ready for TID read.") }; return }
        if (epcToFilterHex.isNullOrBlank() || epcToFilterHex.length != 24) { Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke("Valid 24-char EPC filter required. Received: '$epcToFilterHex'") }; return }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) { Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke("Device busy") }; return }
        if (uhfReader?.isInventorying() == true) {
            Log.i(TAG, "Stopping active UHF inventory before reading TID...")
            uhfReader?.stopInventory()
            try { Thread.sleep(200) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke("Operation interrupted.") }; return }
        }
        isUhfDeviceScanning = true
        try {
            val filterBankSelection = IUHF.Bank_EPC
            val filterPtrBit: Int = 32
            val filterLengthBit: Int = epcToFilterHex.length * 4
            val filterDataHex: String = epcToFilterHex
            val targetBankRead = IUHF.Bank_TID
            val targetPtrWord: Int = 0
            Log.i(TAG, "Reading TID. Filter EPC: $filterDataHex (Bank:$filterBankSelection, PtrBit:$filterPtrBit, LenBit:$filterLengthBit)")
            Log.i(TAG, "Target Read: TID Bank:$targetBankRead, PtrWord:$targetPtrWord, LenWord:$tidReadLengthInWords")
            val tidDataHex = uhfReader?.readData(passwordAccess, filterBankSelection, filterPtrBit, filterLengthBit, filterDataHex, targetBankRead, targetPtrWord, tidReadLengthInWords)
            if (tidDataHex != null) {
                Log.i(TAG, "SDK readData (filter) for TID returned: '$tidDataHex'")
                if (tidDataHex.isNotBlank() && !tidDataHex.all { it == '0' }) { Handler(Looper.getMainLooper()).post { onTagReadTidSuccess?.invoke(tidDataHex, epcToFilterHex) } }
                else { val errorMsg = "Gagal baca TID. SDK return empty/zero: '$tidDataHex'. ${getUhfLastErrorString()}"; Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke(errorMsg) } }
            } else { val errorMsg = "Gagal baca TID. SDK return null. ${getUhfLastErrorString()}"; Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke(errorMsg) } }
        } catch (e: Exception) {
            val errorMsg = "Exception during filtered TID read: ${e.message}"; Log.e(TAG, errorMsg, e); Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke(errorMsg) }
        } finally {
            isUhfDeviceScanning = false; Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
        }
    }

    fun readTidFromNearbyTag(passwordAccess: String = "00000000", tidReadLengthInWords: Int = 6) {
        if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke("UHF not ready") }; return }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) { Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke("Device busy") }; return }
        if (uhfReader?.isInventorying() == true) {
            Log.w(TAG, "Inventory is running. Stopping before reading nearby TID.")
            uhfReader?.stopInventory(); try { Thread.sleep(100) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
        isUhfDeviceScanning = true
        var tidData: String? = null; var epcOfTag: String? = null
        try {
            val tagInfo = uhfReader?.inventorySingleTag()
            if (tagInfo != null) { epcOfTag = tagInfo.epc; tidData = tagInfo.tid; Log.i(TAG, "Nearby tag: EPC='${epcOfTag}', Reported TID='${tidData}'") }
            if (!tidData.isNullOrBlank() && !tidData.all { it == '0' }) { Handler(Looper.getMainLooper()).post { onTagReadTidSuccess?.invoke(tidData, epcOfTag) } }
            else { val errorMsg = if (tagInfo != null) "TID kosong/tidak terbaca (nearby). ${getUhfLastErrorString()}" else "Tag tidak ditemukan (nearby). ${getUhfLastErrorString()}"; Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke(errorMsg) } }
        } catch (e: Exception) { val errorMsg = "Exception reading TID (nearby): ${e.message}"; Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke(errorMsg) }
        } finally { isUhfDeviceScanning = false; Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() } }
    }

    // Di ChainwaySDKManager.kt

    // Callback yang sudah ada:
    // var onTagReadTidSuccess: ((tid: String, epcAssociated: String?) -> Unit)? = null // epcAssociated bisa jadi EPC yang digunakan untuk filter
    // var onTagReadTidFailed: ((errorMessage: String) -> Unit)? = null

    fun readTidUsingEpcFilter(
        epcToFilterHex: String,
        // Anda mungkin tidak butuh password di sini jika default,
        // atau bisa tambahkan jika readData membutuhkannya & tidak ada default di SDK.
        // Untuk Chainway, password biasanya diperlukan untuk readData.
        passwordAccess: String = "00000000", // Default password
        tidReadLengthInWords: Int = 6 // Default 6 words untuk TID (12 bytes / 24 hex chars)
    ) {
        if (!isDeviceReady("uhf")) {
            Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke("UHF not ready for TID read (filter).") }
            return
        }
        if (epcToFilterHex.isBlank() || epcToFilterHex.length != 24) { // Validasi panjang EPC
            Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke("Invalid EPC format for TID read filter.") }
            return
        }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) { // Atau hanya isUhfDeviceScanning jika tidak ingin bentrok dengan radar
            Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke("Device busy, cannot read TID (filter).") }
            return
        }
        if (uhfReader?.isInventorying() == true) {
            Log.w(TAG, "Inventory is running. Stopping before filtered TID read.")
            uhfReader?.stopInventory()
            try { Thread.sleep(100) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }

        Log.i(TAG, "Attempting to read TID using EPC filter: $epcToFilterHex")
        val tidDataHex = uhfReader?.readData(
            passwordAccess, // Password
            IUHF.Bank_EPC,  // Bank untuk filter
            32,             // Pointer awal filter (setelah PC word) dalam bit
            epcToFilterHex.length * 4, // Panjang data filter dalam bit
            epcToFilterHex, // Data EPC untuk filter
            IUHF.Bank_TID,  // Bank yang akan dibaca (TID)
            0,              // Alamat awal baca di TID bank (word)
            tidReadLengthInWords  // Jumlah word yang akan dibaca
        )

        Log.i(TAG, "SDK readData (filter) for TID returned: '$tidDataHex'")

        if (tidDataHex != null && tidDataHex.isNotBlank() && !tidDataHex.all { it == '0' }) {
            // EPC yang digunakan untuk filter bisa dikirim kembali sebagai epcAssociated
            Handler(Looper.getMainLooper()).post { onTagReadTidSuccess?.invoke(tidDataHex, epcToFilterHex) }
        } else {
            val errorMsg = if (tidDataHex == null) "Tag tidak merespons atau error SDK. ${getUhfLastErrorString()}"
            else "TID yang dibaca kosong atau tidak valid."
            Handler(Looper.getMainLooper()).post { onTagReadTidFailed?.invoke(errorMsg) }
        }
        // Operasi ini singkat, tidak perlu set isUhfDeviceScanning = false secara eksplisit di sini
        // kecuali Anda menyetelnya true di awal fungsi ini.
        // Jika Anda menambahkan isUhfDeviceScanning = true di awal, jangan lupa set false dan panggil onUhfOperationStopped
    }


    fun lockUhfTag(targetEpc: String?, passwordAccess: String, lockBank: Int, lockAction: Int) {
        if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onTagLockFailed?.invoke("UHF not ready for lock") }; return }
        if (isUhfDeviceScanning || isBarcodeDeviceScanning) { Handler(Looper.getMainLooper()).post { onTagLockFailed?.invoke("Device busy") }; return }
        if (uhfReader?.isInventorying() == true) {
            Log.w(TAG, "Inventory is running. Stopping before lock operation.")
            uhfReader?.stopInventory(); try { Thread.sleep(100) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
        isUhfDeviceScanning = true
        Log.i(TAG, "Attempting lock: TargetEPC=$targetEpc, BankMask=$lockBank, ActionMask=$lockAction. AccessPwd: $passwordAccess")
        var success = false
        try {
            val banksToModify = ArrayList<Int>() // ArrayList<Integer> in Java
            // TODO: VERIFIKASI PEMETAAN lockBank dan lockAction ke parameter generateLockCode.
            // Ini adalah placeholder dan mungkin perlu logika yang lebih kompleks
            // tergantung bagaimana Anda ingin UI mengontrol bank dan aksi lock.
            // Contoh: if (lockBank == IUHF.LockBank_EPC) banksToModify.add(IUHF.LockBank_EPC)
            // Untuk sekarang, asumsikan lockBank adalah bank yang akan di-pass langsung.
            banksToModify.add(lockBank)

            val lockPayloadString = uhfReader?.generateLockCode(banksToModify, lockAction)
            if (lockPayloadString != null) {
                if (!targetEpc.isNullOrBlank()) {
                    val filterBankForLock = IUHF.Bank_EPC
                    val filterPtrForLock = 32
                    val filterLenForLock = targetEpc.length * 4
                    success = uhfReader?.lockMem(passwordAccess, filterBankForLock, filterPtrForLock, filterLenForLock, targetEpc, lockPayloadString) ?: false
                } else {
                    success = uhfReader?.lockMem(passwordAccess, lockPayloadString) ?: false
                }
            } else { Log.e(TAG, "Gagal membuat lock payload.") }
        } catch (e: Exception) { Log.e(TAG, "Exception selama operasi lock: ${e.message}", e); success = false }
        if (!success) { Handler(Looper.getMainLooper()).post { onTagLockFailed?.invoke("Gagal melakukan lock. ${getUhfLastErrorString()}") } }
        else { Handler(Looper.getMainLooper()).post { onTagLockSuccess?.invoke() } }
        isUhfDeviceScanning = false
        Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }
    }

    fun startUhfRadar(context: Context, targetEpc: String?) {
        if (!initUhfRadarIfNeeded(context)) return
        if (isUhfRadarActive) { Log.w(TAG, "Radar UHF sudah aktif."); return }
        if (isUhfDeviceScanning && !isUhfRadarActive) { Handler(Looper.getMainLooper()).post { onUhfRadarError?.invoke("Hentikan inventory dahulu.") }; return }
        if (uhfReader?.isInventorying() == true) {
            Log.w(TAG, "Inventory is running. Stopping before starting radar.")
            uhfReader?.stopInventory(); try { Thread.sleep(100) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }

        // Penyesuaian di sini:
        sdkRawRadarAngle = 0 // Reset sudut mentah SDK saat memulai radar baru

        val bank = IUHF.Bank_EPC
        val address = 32
        // val tidak perlu lagi di sini jika currentRadarAngle sudah diganti namanya
        // menjadi sdkRawRadarAngle dan merupakan variabel kelas.

        Log.d(TAG, "Starting radar. Initial sdkRawRadarAngle set to 0.")
        val success = uhfReader?.startRadarLocation(context, targetEpc, bank, address, radarCallbackInternal)

        if (success == true) {
            isUhfRadarActive = true
            isUhfDeviceScanning = true // Radar juga dianggap sebagai operasi "scanning" pada perangkat UHF
            Handler(Looper.getMainLooper()).post { onUhfRadarStarted?.invoke() }
            Log.i(TAG, "Pelacakan radar UHF DIMULAI untuk EPC: $targetEpc")
        } else {
            isUhfRadarActive = false
            isUhfDeviceScanning = false
            val errorMsg = "Gagal memulai radar. ${getUhfLastErrorString()}"
            Handler(Looper.getMainLooper()).post { onUhfRadarError?.invoke(errorMsg) }
            Log.e(TAG, errorMsg)
        }
    }

    fun stopUhfRadar() {
        if (!isUhfRadarActive) { Log.w(TAG, "Radar UHF tidak aktif."); return }
        Log.d(TAG, "Attempting to stop UHF Radar.")
        val result = uhfReader?.stopRadarLocation()
        // uhfReader?.stopInventory() // Opsional: coba tambahkan ini untuk melihat apakah berpengaruh

        isUhfRadarActive = false
        isUhfDeviceScanning = false // Ini sudah benar
        Log.i(TAG, "Perintah stop radar dikirim. Hasil SDK: $result. UHF Radar DIHENTIKAN.")
        Handler(Looper.getMainLooper()).post { onUhfRadarStopped?.invoke() }
        if (result != true) { Handler(Looper.getMainLooper()).post { onUhfRadarError?.invoke("SDK gagal stop radar. ${getUhfLastErrorString()}") } }

        // Pertimbangkan juga untuk me-null-kan callback radar di sini jika SDK tidak melakukannya
        // uhfReader?.setRadarLocationCallback(null) // Atau apa pun metode untuk clear callback radar
    }

    fun setUhfRadarDynamicDistance(parameter: Int): Boolean {
        if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onUhfRadarError?.invoke("UHF: Gagal Set Jarak Radar (Not Ready)") }; return false }
        val success = uhfReader?.setDynamicDistance(parameter)
        if (success == true) { Log.i(TAG, "Jarak dinamis radar diatur: $parameter") }
        else { val errorMsg = "SDK gagal set jarak dinamis radar. ${getUhfLastErrorString()}"; Handler(Looper.getMainLooper()).post { onUhfRadarError?.invoke(errorMsg) }; Log.e(TAG, errorMsg) }
        return success ?: false
    }

    fun stopUhfOperation() {
        if (isUhfRadarActive) { stopUhfRadar() }
        else if (isUhfDeviceScanning) {
            uhfReader?.stopInventory(); isUhfDeviceScanning = false
            Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke(); onUhfInventoryFinished?.invoke() }; Log.i(TAG, "Operasi UHF inventory dihentikan.")
        } else { Handler(Looper.getMainLooper()).post { onUhfOperationStopped?.invoke() }; Log.i(TAG, "Tidak ada operasi UHF aktif untuk dihentikan.") }
    }

    fun startBarcodeScan() {
        if (!isDeviceReady("barcode")) { Handler(Looper.getMainLooper()).post { onError?.invoke("Barcode: Not ready.")}; return }
        if (isUhfDeviceScanning) { Handler(Looper.getMainLooper()).post { onError?.invoke("Hentikan operasi UHF dahulu.")}; return }
        if (isBarcodeDeviceScanning) { Log.w(TAG, "Barcode scan sudah berjalan."); return }
        val success = barcodeDecoder?.startScan();Log.i(TAG, "Metode startBarcodeScan() SDK Manager dipanggil.")
        if (success == true) { isBarcodeDeviceScanning = true; Log.i(TAG, "Scan barcode DIMULAI.") }
        else { Handler(Looper.getMainLooper()).post { onError?.invoke("Gagal memulai scan barcode.")}; Log.e(TAG, "Gagal memulai scan barcode.") }
    }
    fun stopBarcodeScan() {
        if (isBarcodeDeviceScanning) { // Hanya jika memang sedang aktif
            barcodeDecoder?.stopScan()     // Perintah SDK untuk berhenti
            isBarcodeDeviceScanning = false // Set status internal
            Log.i(TAG, "Scan barcode DIHENTIKAN (otomatis setelah decode/atau oleh pengguna).")
            // ...
        }
    }

    fun getUhfPower(): Int? { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Get Power (Not Ready)")}; return null }; return try { uhfReader?.power } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Get Power (${e.message})")}; null } }
    fun setUhfPower(power: Int): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set Power (Not Ready)")}; return false }; return try { uhfReader?.setPower(power) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set Power (${e.message})")}; false } }
    fun getUhfFrequencyModeInt(): Int? { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Get Freq (Not Ready)")}; return null }; return try { uhfReader?.frequencyMode } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Get Freq (${e.message})")}; null } }
    fun setUhfFrequencyModeInt(regionInt: Int): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set Freq (Not Ready)")}; return false }; return try { uhfReader?.setFrequencyMode(regionInt) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set Freq (${e.message})")}; false } }
    fun setUhfFreHop(frequencyValue: Float): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set FreHop (Not Ready)")}; return false }; return try { uhfReader?.setFreHop(frequencyValue) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set FreHop (${e.message})")}; false } }
    fun getUhfProtocolInt(): Int? { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Get Proto (Not Ready)")}; return null }; return try { uhfReader?.protocol } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Get Proto (${e.message})")}; null } }
    fun setUhfProtocolInt(protocolInt: Int): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set Proto (Not Ready)")}; return false }; return try { uhfReader?.setProtocol(protocolInt) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set Proto (${e.message})")}; false } }
    fun getUhfRFLinkInt(): Int? { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Get RFLink (Not Ready)")}; return null }; return try { uhfReader?.rfLink } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Get RFLink (${e.message})")}; null } }
    fun setUhfRFLinkInt(rfLinkInt: Int): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set RFLink (Not Ready)")}; return false }; return try { uhfReader?.setRFLink(rfLinkInt) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set RFLink (${e.message})")}; false } }
    fun getUhfInventoryMode(): InventoryModeEntity? { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Get InvMode (Not Ready)")}; return null }; return try { uhfReader?.getEPCAndTIDUserMode() } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Get InvMode (${e.message})")}; null } }
    fun setUhfInventoryModeEpcOnly(): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set InvMode EPC (Not Ready)")}; return false }; return try { uhfReader?.setEPCMode() ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set InvMode EPC (${e.message})")}; false } }
    fun setUhfInventoryModeEpcAndTid(): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set InvMode EPC+TID (Not Ready)")}; return false }; return try { uhfReader?.setEPCAndTIDMode() ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set InvMode EPC+TID (${e.message})")}; false } }
    fun setUhfInventoryReadUserBank(offset: Int, length: Int): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set InvMode User (Not Ready)")}; return false }; return try { uhfReader?.setEPCAndTIDUserMode(offset, length) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set InvMode User (${e.message})")}; false } }
    fun setUhfInventoryReadReservedBank(offset: Int, length: Int): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set InvMode Reserved (Not Ready)")}; return false }; return try { val modeEntity = InventoryModeEntity.Builder().setMode(InventoryModeEntity.MODE_EPC_RESERVED).setReservedOffset(offset).setReservedLength(length).build(); uhfReader?.setEPCAndTIDUserMode(modeEntity) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set InvMode Reserved (${e.message})") }; false } }
    fun getUhfGen2Settings(): Gen2Entity? { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Get Gen2 (Not Ready)")}; return null }; return try { uhfReader?.getGen2() } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Get Gen2 (${e.message})")}; null } }
    fun setUhfGen2Settings(session: Int, target: Int, qValue: Int = 4): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set Gen2 (Not Ready)")}; return false }; return try { val gen2Entity = uhfReader?.getGen2() ?: Gen2Entity(); gen2Entity.querySession = session; gen2Entity.queryTarget = target; gen2Entity.q = qValue; uhfReader?.setGen2(gen2Entity) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set Gen2 (${e.message})") }; false } }
    fun getUhfFastInventoryStatusInt(): Int? { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Get FastInv (Not Ready)")}; return null }; return try { uhfReader?.fastInventoryMode } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Get FastInv (${e.message})")}; null } }
    fun setUhfFastInventoryEnabled(enabled: Boolean): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set FastInv (Not Ready)")}; return false }; return try { uhfReader?.setFastInventoryMode(enabled) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set FastInv (${e.message})")}; false } }
    fun setUhfTagFocusEnabled(enabled: Boolean): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set TagFocus (Not Ready)")}; return false }; return try { uhfReader?.setTagFocus(enabled) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set TagFocus (${e.message})")}; false } }
    fun setUhfFastIDEnabled(enabled: Boolean): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail Set FastID (Not Ready)")}; return false }; return try { uhfReader?.setFastID(enabled) ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err Set FastID (${e.message})")}; false } }
    fun performUhfFactoryReset(): Boolean { if (!isDeviceReady("uhf")) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Fail FactoryReset (Not Ready)")}; return false }; return try { uhfReader?.factoryReset() ?: false } catch (e: Exception) { Handler(Looper.getMainLooper()).post { onError?.invoke("UHF: Err FactoryReset (${e.message})")}; false } }

    fun getUhfReaderInstance(): RFIDWithUHFUART? { return if (isDeviceReady("uhf")) uhfReader else null }

    private fun setupUhfListener() {
        uhfReader?.setInventoryCallback(object : IUHFInventoryCallback {
            override fun callback(tagInfo: UHFTAGInfo?) {
                tagInfo?.epc?.let { epc ->
                    if (epc.isNotEmpty()) {
                        if (isUhfDeviceScanning && !isUhfRadarActive) {
                            Handler(Looper.getMainLooper()).post { onUhfTagScanned?.invoke(epc) }
                        }
                    }
                }
            }
            // HAPUS onInventoryStop() karena tidak ada di IUHFInventoryCallback standar
            // override fun onInventoryStop() { /* ... */ }
        })
    }

    private fun setupBarcodeListener() {
        // Di dalam setupBarcodeListener()
        barcodeDecoder?.setDecodeCallback(object : BarcodeDecoder.DecodeCallback {
            override fun onDecodeComplete(barcodeEntity: BarcodeEntity?) {
                Handler(Looper.getMainLooper()).post {
                    Log.d(TAG, "INTERNAL SDK CB: onDecodeComplete - ResultCode: ${barcodeEntity?.resultCode}") // LOG BARU
                    if (barcodeEntity?.resultCode == BarcodeDecoder.DECODE_SUCCESS) {
                        barcodeEntity.barcodeData?.let { data ->
                            if (data.isNotEmpty()) {
                                Log.i(TAG, "BARCODE DECODED (SDK Manager): '$data'") // LOG BARU
                                onBarcodeScanned?.invoke(data)
                                Log.i(TAG, "Callback onBarcodeScanned (VM) CALLED with: '$data'") // LOG BARU
                            } else {
                                Log.w(TAG, "BARCODE DECODED (SDK Manager): Data is empty.")
                            }
                        } ?: Log.w(TAG, "BARCODE DECODED (SDK Manager): BarcodeData is null.")
                    } else {
                        val errorMsg = "Barcode scan failed. ResultCode: ${barcodeEntity?.resultCode}"
                        Log.e(TAG, "BARCODE ERROR (SDK Manager): $errorMsg")
                        onError?.invoke(errorMsg)
                    }
                    // Hentikan scan jika ini adalah operasi sekali tembak
                    if (isBarcodeDeviceScanning) {
                        Log.d(TAG, "Stopping barcode scan from onDecodeComplete as it's a single shot.")
                        stopBarcodeScan() // Panggil fungsi stop Anda yang sudah ada
                    }
                }
            }
            // override fun onError(errorCode: Int, message: String?) {
            //     Log.e(TAG, "BARCODE SCAN ERROR (SDK Manager CB): $errorCode - $message")
            //     Handler(Looper.getMainLooper()).post {
            //         onError?.invoke("Barcode Decode Error: $message ($errorCode)")
            //         isBarcodeDeviceScanning = false
            //     }
            // }
        })
        Log.i(TAG, "BarcodeDecoder DecodeCallback has been SET via setupBarcodeListener().")
    }

    fun releaseResources() {
        Log.i(TAG, "Releasing ChainwaySDKManager resources...")
        if (isUhfRadarActive) { stopUhfRadar() }
        else if (isUhfDeviceScanning) { uhfReader?.stopInventory(); isUhfDeviceScanning = false }
        if (isBarcodeDeviceScanning) { stopBarcodeScan() }
        if (uhfReader != null) {
            uhfReader?.setInventoryCallback(null)
            try { uhfReader?.free(); Log.i(TAG, "UHF Reader free() called.") }
            catch (e: Exception) { Log.e(TAG, "Exception during uhfReader.free()", e) }
            uhfReader = null
        }
        isUhfSdkInitialized = false; isUhfConnected = false
        Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(false, "UHF") }
        if (barcodeDecoder != null) {
            try { barcodeDecoder?.setDecodeCallback(null); barcodeDecoder?.close(); Log.i(TAG, "Barcode Scanner closed.") }
            catch (e: Exception) { Log.e(TAG, "Exception during barcodeDecoder cleanup", e) }
            barcodeDecoder = null
        }
        isBarcodeSdkInitialized = false; isBarcodeConnected = false
        Handler(Looper.getMainLooper()).post { onDeviceStatusChanged?.invoke(false, "Barcode") }
        Log.i(TAG, "All SDK Manager resources released.")
    }
}
