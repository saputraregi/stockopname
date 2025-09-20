package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.rscja.deviceapi.entity.Gen2Entity
import com.rscja.deviceapi.entity.InventoryModeEntity
import com.rscja.deviceapi.interfaces.IUHF // Untuk konstanta bank jika diperlukan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val sdkManager: ChainwaySDKManager = (application as MyApplication).sdkManager

    companion object {
        private const val TAG = "SetupViewModel"
    }

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // --- 1. Output Power ---
    private val _currentPower = MutableLiveData<Int?>()
    val currentPower: LiveData<Int?> = _currentPower
    // Rentang daya bisa berbeda per perangkat, 5-30 adalah contoh umum.
    // Aplikasi demo Chainway menggunakan array string, yang kemudian di-parsen index + 1.
    // Jika reader Anda mendukung range lain, sesuaikan.
    private val _powerOptions = MutableLiveData<List<Int>>((1..33).toList()) // Contoh range lebih luas
    val powerOptions: LiveData<List<Int>> = _powerOptions

    // --- 2. Frequency/Region ---
    private val _currentFrequencyRegion = MutableLiveData<String?>()
    val currentFrequencyRegion: LiveData<String?> = _currentFrequencyRegion
    // Sesuaikan daftar ini dengan nama yang lebih cocok dengan konstanta di ChainwaySDKManager
    // dan urutan yang diinginkan di UI.
    private val _frequencyRegionOptions = MutableLiveData<List<String>>(
        listOf(
            "USA (FCC)", // Akan dipetakan ke REGION_USA_STANDARD
            "Europe (ETSI)", // Akan dipetakan ke REGION_ETSI_STANDARD
            "China (840-845MHz)", // Akan dipetakan ke REGION_CHINA_840_845
            "China (920-925MHz)", // Akan dipetakan ke REGION_CHINA_920_925
            "Korea",               // Akan dipetakan ke REGION_KOREA
            "Japan",               // Akan dipetakan ke REGION_JAPAN
            "South Africa (915-919MHz)",
            "New Zealand",
            "Morocco"
            // Tambahkan region lain yang didukung dari ChainwaySDKManager
        )
    )
    val frequencyRegionOptions: LiveData<List<String>> = _frequencyRegionOptions

    // --- 3. Frequency Hopping ---
    // SDK Chainway (setidaknya yang kita lihat) tidak memiliki getter untuk frekuensi hopping.
    // Pengaturan ini hanya bisa di-set. UI akan menampilkan nilai terakhir yang di-set atau default.
    private val _currentFreqHoppingType = MutableLiveData<String?>("Custom Float Value") // Default
    val currentFreqHoppingType: LiveData<String?> = _currentFreqHoppingType
    private val _currentFreqHoppingTableOrValue = MutableLiveData<String?>() // UI akan mengisi ini
    val currentFreqHoppingTableOrValue: LiveData<String?> = _currentFreqHoppingTableOrValue
    // Opsi ini hanya untuk UI, nilainya yang akan di-parse ke float
    private val _freqHoppingTableOptions = MutableLiveData<List<String>>(
        listOf("Custom Float Value") // Hanya satu opsi, user input angka
    )
    val freqHoppingTableOptions: LiveData<List<String>> = _freqHoppingTableOptions
    // Visibilitas mungkin tidak selalu true, bisa bergantung pada region.
    // Untuk beberapa region seperti USA, FreHop mungkin relevan.
    private val _isFreqHoppingContainerVisible = MutableLiveData<Boolean>(false)
    val isFreqHoppingContainerVisible: LiveData<Boolean> = _isFreqHoppingContainerVisible

    // --- 4. Protocol ---
    private val _currentProtocol = MutableLiveData<String?>()
    val currentProtocol: LiveData<String?> = _currentProtocol
    private val _protocolOptions = MutableLiveData<List<String>>(
        // UHFSetFragment hanya menunjukkan GEN2 (posisi 0). ISO18000-6B mungkin tidak umum/didukung.
        listOf("GEN2") // Paling umum dan kemungkinan satu-satunya yang didukung
    )
    val protocolOptions: LiveData<List<String>> = _protocolOptions

    // --- 5. RFLink Profile ---
    private val _currentRFLinkProfile = MutableLiveData<String?>()
    val currentRFLinkProfile: LiveData<String?> = _currentRFLinkProfile
    // Nama profil ini adalah contoh, nilai integer yang dikirim ke SDK adalah yang penting.
    // UHFSetFragment mengambil nilai integer dari arrayLinkValue yang didefinisikan di XML.
    // Kita perlu memetakan String UI ke nilai integer yang sesuai.
    private val _rfLinkProfileOptions = MutableLiveData<List<String>>(
        // Contoh nama dari aplikasi demo (misalnya R.array.arrayLink)
        // Nilai aktual yang dikirim ke SDK (misal 0, 1, 2, 4, 8) perlu dipetakan.
        listOf("RFLink Profile 0 (DSB_ASK, Tari 25us, DR 8, 250KHz)",   // Biasanya map ke 0
            "RFLink Profile 1 (PR_ASK, Tari 25us, DR 64/3, 250KHz)", // Biasanya map ke 1
            "RFLink Profile 2 (PR_ASK, Tari 6.25us, DR 64/3, 250KHz)",// Biasanya map ke 2
            "RFLink Profile 3 (DSB_ASK, Tari 25us, DR 8, 320KHz)")  // Biasanya map ke 3
        // Sesuaikan dengan yang ada di R.array.arrayLink dan mapping ke nilai integer yang benar
    )
    val rfLinkProfileOptions: LiveData<List<String>> = _rfLinkProfileOptions

    // --- 6. Memory Bank (Inventory Mode) ---
    private val _currentMemoryBank = MutableLiveData<String?>()
    val currentMemoryBank: LiveData<String?> = _currentMemoryBank
    private val _memoryBankOptions = MutableLiveData<List<String>>(
        listOf("EPC Only", "EPC+TID", "USER Bank", "RESERVED Bank")
        // Aplikasi demo juga ada "LED_TAG", bisa ditambahkan jika relevan
    )
    val memoryBankOptions: LiveData<List<String>> = _memoryBankOptions
    private val _currentMemoryBankOffset = MutableLiveData<String?>() // Gunakan String untuk EditText
    val currentMemoryBankOffset: LiveData<String?> = _currentMemoryBankOffset
    private val _currentMemoryBankLength = MutableLiveData<String?>() // Gunakan String untuk EditText
    val currentMemoryBankLength: LiveData<String?> = _currentMemoryBankLength
    private val _isMemoryBankDetailsVisible = MutableLiveData<Boolean>(false)
    val isMemoryBankDetailsVisible: LiveData<Boolean> = _isMemoryBankDetailsVisible

    // --- 7. Session Gen2 ---
    private val _currentSessionId = MutableLiveData<String?>()
    val currentSessionId: LiveData<String?> = _currentSessionId
    private val _sessionIdOptions = MutableLiveData<List<String>>(listOf("S0", "S1", "S2", "S3"))
    val sessionIdOptions: LiveData<List<String>> = _sessionIdOptions

    private val _currentInventoriedFlag = MutableLiveData<String?>() // Ini adalah 'Target' A atau B
    val currentInventoriedFlag: LiveData<String?> = _currentInventoriedFlag
    private val _inventoriedFlagOptions = MutableLiveData<List<String>>(listOf("TARGET A", "TARGET B"))
    val inventoriedFlagOptions: LiveData<List<String>> = _inventoriedFlagOptions

    private val _currentQValue = MutableLiveData<Int>(4) // Default Q dari UHFSetFragment (tidak eksplisit, tapi umum)
    val currentQValue: LiveData<Int> = _currentQValue
    val qValueOptions: List<Int> = (0..15).toList() // Q value biasanya 0-15

    // --- 8. Fast Inventory ---
    private val _isFastInventoryOpen = MutableLiveData<Boolean?>(false) // Default ke false
    val isFastInventoryOpen: LiveData<Boolean?> = _isFastInventoryOpen

    // --- 9. Additional Settings (Switches) ---
    // SDK tidak punya getter untuk ini, jadi nilai akan berdasarkan set terakhir atau default.
    private val _isTagFocusEnabled = MutableLiveData<Boolean>(false)
    val isTagFocusEnabled: LiveData<Boolean> = _isTagFocusEnabled
    private val _isFastIDEnabled = MutableLiveData<Boolean>(false)
    val isFastIDEnabled: LiveData<Boolean> = _isFastIDEnabled

    init {
        Log.d(TAG, "ViewModel initialized. SDK Manager: $sdkManager")
        sdkManager.onError = { message ->
            _isLoading.postValue(false)
            showToast("SDK Error: $message")
        }
        // Panggil loadAllInitialSettings dari Activity setelah UI siap dan reader terhubung
    }

    // --- Fungsi Pemetaan ---
    private fun mapRegionStringToInt(regionString: String?): Int? {
        return when (regionString) {
            "USA (FCC)" -> ChainwaySDKManager.REGION_USA_STANDARD
            "Europe (ETSI)" -> ChainwaySDKManager.REGION_ETSI_STANDARD
            "China (840-845MHz)" -> ChainwaySDKManager.REGION_CHINA_840_845
            "China (920-925MHz)" -> ChainwaySDKManager.REGION_CHINA_920_925
            "Korea" -> ChainwaySDKManager.REGION_KOREA
            "Japan" -> ChainwaySDKManager.REGION_JAPAN
            "South Africa (915-919MHz)" -> ChainwaySDKManager.REGION_SOUTH_AFRICA_915_919
            "New Zealand" -> ChainwaySDKManager.REGION_NEW_ZEALAND
            "Morocco" -> ChainwaySDKManager.REGION_MOROCCO
            // "Brazil" dan "Australia" tidak ada di konstanta SDK Manager Anda saat ini
            // Jika ditambahkan, sertakan mappingnya di sini.
            else -> {
                Log.w(TAG, "Region string tidak dikenal untuk pemetaan ke Int: $regionString")
                null
            }
        }
    }

    private fun mapRegionIntToString(regionInt: Int?): String? {
        return when (regionInt) {
            ChainwaySDKManager.REGION_USA_STANDARD -> "USA (FCC)"
            ChainwaySDKManager.REGION_ETSI_STANDARD -> "Europe (ETSI)"
            ChainwaySDKManager.REGION_CHINA_840_845 -> "China (840-845MHz)"
            ChainwaySDKManager.REGION_CHINA_920_925 -> "China (920-925MHz)"
            ChainwaySDKManager.REGION_KOREA -> "Korea"
            ChainwaySDKManager.REGION_JAPAN -> "Japan"
            ChainwaySDKManager.REGION_SOUTH_AFRICA_915_919 -> "South Africa (915-919MHz)"
            ChainwaySDKManager.REGION_NEW_ZEALAND -> "New Zealand"
            ChainwaySDKManager.REGION_MOROCCO -> "Morocco"
            else -> {
                Log.w(TAG, "Region int tidak dikenal untuk pemetaan ke String: $regionInt")
                // Bisa kembalikan nama default atau null
                "Unknown Region (Code: $regionInt)" // Atau null
            }
        }
    }

    private fun mapProtocolStringToInt(protocolString: String?): Int? {
        return when (protocolString) {
            "GEN2" -> ChainwaySDKManager.PROTOCOL_GEN2
            // "ISO18000-6B" -> Beberapa nilai int lain jika didukung
            else -> {
                Log.w(TAG, "Protokol string tidak dikenal: $protocolString")
                null
            }
        }
    }

    private fun mapProtocolIntToString(protocolInt: Int?): String? {
        return when (protocolInt) {
            ChainwaySDKManager.PROTOCOL_GEN2 -> "GEN2"
            else -> {
                Log.w(TAG, "Protokol int tidak dikenal: $protocolInt")
                "Unknown Protocol (Code: $protocolInt)" // Atau null
            }
        }
    }

    // Mapping RFLink perlu dicocokkan dengan arrayLinkValue di demo Chainway
    // Ini adalah contoh, Anda HARUS memverifikasi nilai integer yang benar.
    // Aplikasi demo Chainway memiliki R.array.arrayLinkValue: <item>0</item><item>1</item><item>2</item><item>3</item>
    private fun mapRFLinkStringToInt(rfLinkString: String?): Int? {
        val index = _rfLinkProfileOptions.value?.indexOf(rfLinkString)
        return when (index) {
            0 -> 0 // "RFLink Profile 0..." -> 0
            1 -> 1 // "RFLink Profile 1..." -> 1
            2 -> 2 // "RFLink Profile 2..." -> 2
            3 -> 3 // "RFLink Profile 3..." -> 3
            // Tambahkan mapping lain jika ada lebih banyak opsi
            else -> {
                Log.w(TAG, "RFLink string tidak dikenal: $rfLinkString (index: $index)")
                null
            }
        }
    }

    private fun mapRFLinkIntToString(rfLinkInt: Int?): String? {
        // Cari string berdasarkan nilai integer yang diketahui dari SDK.
        // Ini mengasumsikan nilai integer 0, 1, 2, 3 sesuai dengan urutan di _rfLinkProfileOptions.
        return when (rfLinkInt) {
            0 -> _rfLinkProfileOptions.value?.getOrNull(0)
            1 -> _rfLinkProfileOptions.value?.getOrNull(1)
            2 -> _rfLinkProfileOptions.value?.getOrNull(2)
            3 -> _rfLinkProfileOptions.value?.getOrNull(3)
            // Tambahkan mapping lain jika perlu
            else -> {
                Log.w(TAG, "RFLink int tidak dikenal: $rfLinkInt")
                "Unknown RFLink (Code: $rfLinkInt)" // Atau null
            }
        }
    }

    private fun mapSessionIdStringToInt(sessionIdString: String?): Int? {
        // UHFSetFragment menggunakan getSelectedItemPosition() -> 0, 1, 2, 3
        return when (sessionIdString) {
            "S0" -> 0
            "S1" -> 1
            "S2" -> 2
            "S3" -> 3
            else -> {
                Log.w(TAG, "Session ID string tidak dikenal: $sessionIdString")
                null
            }
        }
    }

    private fun mapSessionIdIntToString(sessionIdInt: Int?): String? {
        return when (sessionIdInt) {
            0 -> "S0"
            1 -> "S1"
            2 -> "S2"
            3 -> "S3"
            else -> {
                Log.w(TAG, "Session ID int tidak dikenal: $sessionIdInt")
                "Unknown Session (Code: $sessionIdInt)" // Atau null
            }
        }
    }

    private fun mapTargetFlagStringToInt(targetFlagString: String?): Int? {
        // UHFSetFragment menggunakan getSelectedItemPosition() -> 0 (Target A), 1 (Target B)
        return when (targetFlagString) {
            "TARGET A" -> 0
            "TARGET B" -> 1
            else -> {
                Log.w(TAG, "Target flag string tidak dikenal: $targetFlagString")
                null
            }
        }
    }

    private fun mapTargetFlagIntToString(targetFlagInt: Int?): String? {
        return when (targetFlagInt) {
            0 -> "TARGET A"
            1 -> "TARGET B"
            else -> {
                Log.w(TAG, "Target flag int tidak dikenal: $targetFlagInt")
                "Unknown Target (Code: $targetFlagInt)" // Atau null
            }
        }
    }
    // --- END Fungsi Pemetaan ---

    fun loadAllInitialSettings() {
        if (!sdkManager.isDeviceReady("uhf")) {
            showToast("Reader UHF tidak terhubung. Tidak dapat memuat pengaturan.")
            _isLoading.postValue(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            showToast("Memuat pengaturan awal dari reader...")

            // 1. Power
            sdkManager.getUhfPower()?.let { _currentPower.postValue(it) }

            // 2. Frequency/Region
            val regionInt = sdkManager.getUhfFrequencyModeInt()
            regionInt?.let {
                val regionString = mapRegionIntToString(it)
                _currentFrequencyRegion.postValue(regionString)
                // Tentukan visibilitas FreHop berdasarkan region (Contoh: hanya USA)
                _isFreqHoppingContainerVisible.postValue(it == ChainwaySDKManager.REGION_USA_STANDARD)
            }

            // 3. Frequency Hopping - SDK tidak punya getFreHop()
            // Biarkan UI dengan nilai default.

            // 4. Protocol
            sdkManager.getUhfProtocolInt()?.let { _currentProtocol.postValue(mapProtocolIntToString(it)) }

            // 5. RFLink Profile
            sdkManager.getUhfRFLinkInt()?.let { _currentRFLinkProfile.postValue(mapRFLinkIntToString(it)) }

            // 6. Memory Bank (Inventory Mode)
            val invMode = sdkManager.getUhfInventoryMode()
            if (invMode != null) {
                val modeType = invMode.mode
                var bankStringDisplay: String? = _memoryBankOptions.value?.firstOrNull() // Default
                var offsetDisplay: String? = null
                var lengthDisplay: String? = null
                var detailsVisible = false

                when (modeType) {
                    InventoryModeEntity.MODE_EPC -> bankStringDisplay = "EPC Only"
                    InventoryModeEntity.MODE_EPC_TID -> bankStringDisplay = "EPC+TID"
                    InventoryModeEntity.MODE_EPC_TID_USER -> {
                        bankStringDisplay = "USER Bank"
                        offsetDisplay = invMode.userOffset.toString()
                        lengthDisplay = invMode.userLength.toString()
                        detailsVisible = true
                    }
                    InventoryModeEntity.MODE_EPC_RESERVED -> {
                        bankStringDisplay = "RESERVED Bank"
                        offsetDisplay = invMode.reservedOffset.toString()
                        lengthDisplay = invMode.reservedLength.toString()
                        detailsVisible = true
                    }
                    // Tambahkan MODE_LED_TAG jika didukung dan ada di _memoryBankOptions
                    else -> Log.w(TAG, "Mode inventory tidak dikenal dari SDK: $modeType")
                }
                _currentMemoryBank.postValue(bankStringDisplay)
                _currentMemoryBankOffset.postValue(offsetDisplay)
                _currentMemoryBankLength.postValue(lengthDisplay)
                _isMemoryBankDetailsVisible.postValue(detailsVisible)
            } else {
                _currentMemoryBank.postValue(_memoryBankOptions.value?.firstOrNull())
                _isMemoryBankDetailsVisible.postValue(false)
            }

            // 7. Session Gen2
            val gen2Settings = sdkManager.getUhfGen2Settings()
            if (gen2Settings != null) {
                _currentSessionId.postValue(mapSessionIdIntToString(gen2Settings.querySession))
                _currentInventoriedFlag.postValue(mapTargetFlagIntToString(gen2Settings.queryTarget))
                _currentQValue.postValue(gen2Settings.q) // Muat Q value dari reader
            } else {
                _currentSessionId.postValue(_sessionIdOptions.value?.firstOrNull())
                _currentInventoriedFlag.postValue(_inventoriedFlagOptions.value?.firstOrNull())
                _currentQValue.postValue(4) // Default jika gagal get
            }

            // 8. Fast Inventory
            sdkManager.getUhfFastInventoryStatusInt()?.let {
                _isFastInventoryOpen.postValue(it == 1) // Asumsi 1 = Open/Enabled
            }

            // 9. TagFocus & FastID - SDK tidak punya fungsi 'get'.
            // Biarkan nilai default. Jika user pernah set, nilai itu yang akan ada di LiveData.

            _isLoading.postValue(false)
            showToast("Pengaturan awal selesai dimuat.")
        }
    }

    // --- Power ---
    fun getPower() {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            sdkManager.getUhfPower()?.let { power ->
                _currentPower.postValue(power)
                showToast("Daya saat ini: $power dBm")
            } ?: showToast("Gagal mendapatkan daya.")
            _isLoading.postValue(false)
        }
    }

    fun setPower(powerToSet: Int) {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            if (sdkManager.setUhfPower(powerToSet)) {
                _currentPower.postValue(powerToSet)
                showToast("Daya berhasil diatur ke $powerToSet dBm")
            } else {
                showToast("Gagal mengatur daya.")
            }
            _isLoading.postValue(false)
        }
    }

    // --- Frequency/Region ---
    fun getFrequencyRegion() {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            sdkManager.getUhfFrequencyModeInt()?.let { regionInt ->
                val regionString = mapRegionIntToString(regionInt)
                _currentFrequencyRegion.postValue(regionString)
                _isFreqHoppingContainerVisible.postValue(regionInt == ChainwaySDKManager.REGION_USA_STANDARD)
                showToast(if (regionString != null && regionString.startsWith("Unknown").not()) "Region saat ini: $regionString" else "Region dari SDK tidak dikenal (Kode: $regionInt).")
            } ?: showToast("Gagal mendapatkan region.")
            _isLoading.postValue(false)
        }
    }

    fun setFrequencyRegion(regionString: String) {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        val regionInt = mapRegionStringToInt(regionString)
        if (regionInt == null) {
            showToast("Pilihan region tidak valid untuk diatur.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            if (sdkManager.setUhfFrequencyModeInt(regionInt)) {
                _currentFrequencyRegion.postValue(regionString)
                _isFreqHoppingContainerVisible.postValue(regionInt == ChainwaySDKManager.REGION_USA_STANDARD)
                showToast("Region berhasil diatur ke $regionString")
            } else {
                showToast("Gagal mengatur region.")
            }
            _isLoading.postValue(false)
        }
    }

    // --- Frequency Hopping ---
    fun setFreqHopping(tableOrValue: String?) { // Type tidak lagi diperlukan jika hanya Custom Float
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        val freHopFloat = tableOrValue?.toFloatOrNull()
        if (freHopFloat == null) {
            showToast("Nilai Frekuensi Hopping (float) tidak valid.")
            return
        }
        Log.d(TAG, "Frekuensi Hopping akan diatur ke: $freHopFloat")

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            if (sdkManager.setUhfFreHop(freHopFloat)) {
                _currentFreqHoppingTableOrValue.postValue(tableOrValue)
                showToast("Frekuensi Hopping berhasil diatur.")
            } else {
                showToast("Gagal mengatur Frekuensi Hopping.")
            }
            _isLoading.postValue(false)
        }
    }

    fun getFreqHoppingSettings() {
        // SDK tidak mendukung get, cukup informasikan pengguna.
        showToast("Pengaturan Frekuensi Hopping tidak dapat dibaca dari reader. Menampilkan nilai terakhir yang di-set.")
    }

    // --- Protocol ---
    fun getProtocol() {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            sdkManager.getUhfProtocolInt()?.let { protocolInt ->
                val protocolString = mapProtocolIntToString(protocolInt)
                _currentProtocol.postValue(protocolString)
                showToast(if (protocolString != null && protocolString.startsWith("Unknown").not()) "Protokol saat ini: $protocolString" else "Protokol dari SDK tidak dikenal (Kode: $protocolInt).")
            } ?: showToast("Gagal mendapatkan protokol.")
            _isLoading.postValue(false)
        }
    }

    fun setProtocol(protocolString: String) {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        val protocolInt = mapProtocolStringToInt(protocolString)
        if (protocolInt == null) {
            showToast("Pilihan protokol tidak valid untuk diatur.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            if (sdkManager.setUhfProtocolInt(protocolInt)) {
                _currentProtocol.postValue(protocolString)
                showToast("Protokol berhasil diatur ke $protocolString")
            } else {
                showToast("Gagal mengatur protokol.")
            }
            _isLoading.postValue(false)
        }
    }

    // --- RFLink Profile ---
    fun getRFLinkProfile() {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            sdkManager.getUhfRFLinkInt()?.let { rfLinkInt ->
                val rfLinkString = mapRFLinkIntToString(rfLinkInt)
                _currentRFLinkProfile.postValue(rfLinkString)
                showToast(if (rfLinkString != null && rfLinkString.startsWith("Unknown").not()) "RFLink Profile: $rfLinkString" else "RFLink dari SDK tidak dikenal (Kode: $rfLinkInt).")
            } ?: showToast("Gagal mendapatkan RFLink Profile.")
            _isLoading.postValue(false)
        }
    }

    fun setRFLinkProfile(rfLinkString: String) {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        val rfLinkInt = mapRFLinkStringToInt(rfLinkString)
        if (rfLinkInt == null) {
            showToast("Pilihan RFLink Profile tidak valid untuk diatur.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            if (sdkManager.setUhfRFLinkInt(rfLinkInt)) {
                _currentRFLinkProfile.postValue(rfLinkString)
                showToast("RFLink Profile berhasil diatur ke $rfLinkString")
            } else {
                showToast("Gagal mengatur RFLink Profile.")
            }
            _isLoading.postValue(false)
        }
    }

    // --- Memory Bank (Inventory Mode) ---
    fun onMemoryBankSelected(bankString: String?) {
        _currentMemoryBank.value = bankString // Set nilai yang dipilih
        _isMemoryBankDetailsVisible.value = bankString == "USER Bank" || bankString == "RESERVED Bank"
        if (_isMemoryBankDetailsVisible.value == false) {
            _currentMemoryBankOffset.value = "" // Reset jika tidak visible
            _currentMemoryBankLength.value = ""
        }
    }

    fun getMemoryBankData() {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val invMode = sdkManager.getUhfInventoryMode()
            if (invMode != null) {
                val modeType = invMode.mode
                var bankStringDisplay: String? = _memoryBankOptions.value?.firstOrNull()
                var offsetDisplay: String? = ""
                var lengthDisplay: String? = ""
                var detailsVisible = false

                when (modeType) {
                    InventoryModeEntity.MODE_EPC -> bankStringDisplay = "EPC Only"
                    InventoryModeEntity.MODE_EPC_TID -> bankStringDisplay = "EPC+TID"
                    InventoryModeEntity.MODE_EPC_TID_USER -> {
                        bankStringDisplay = "USER Bank"
                        offsetDisplay = invMode.userOffset.toString()
                        lengthDisplay = invMode.userLength.toString()
                        detailsVisible = true
                    }
                    InventoryModeEntity.MODE_EPC_RESERVED -> {
                        bankStringDisplay = "RESERVED Bank"
                        offsetDisplay = invMode.reservedOffset.toString()
                        lengthDisplay = invMode.reservedLength.toString()
                        detailsVisible = true
                    }
                    else -> Log.w(TAG, "Mode inventory tidak dikenal dari SDK saat get: $modeType")
                }
                _currentMemoryBank.postValue(bankStringDisplay)
                _currentMemoryBankOffset.postValue(offsetDisplay)
                _currentMemoryBankLength.postValue(lengthDisplay)
                _isMemoryBankDetailsVisible.postValue(detailsVisible)
                showToast("Pengaturan Memory Bank (Inventory) berhasil diambil.")
            } else {
                showToast("Gagal mengambil pengaturan Memory Bank (Inventory).")
            }
            _isLoading.postValue(false)
        }
    }

    fun setMemoryBankInventoryMode(bankString: String?, offsetStr: String?, lengthStr: String?) {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            var success = false
            var operationAttempted = false

            when (bankString) {
                "EPC Only" -> { success = sdkManager.setUhfInventoryModeEpcOnly(); operationAttempted = true }
                "EPC+TID" -> { success = sdkManager.setUhfInventoryModeEpcAndTid(); operationAttempted = true }
                "USER Bank" -> {
                    val offset = offsetStr?.toIntOrNull()
                    val length = lengthStr?.toIntOrNull()
                    if (offset != null && length != null && offset >= 0 && length > 0) {
                        success = sdkManager.setUhfInventoryReadUserBank(offset, length)
                    } else {
                        withContext(Dispatchers.Main) { showToast("Offset dan Length (harus > 0) diperlukan untuk USER bank.") }
                    }
                    operationAttempted = true
                }
                "RESERVED Bank" -> {
                    val offset = offsetStr?.toIntOrNull()
                    val length = lengthStr?.toIntOrNull()
                    if (offset != null && length != null && offset >= 0 && length > 0) {
                        success = sdkManager.setUhfInventoryReadReservedBank(offset, length)
                    } else {
                        withContext(Dispatchers.Main) { showToast("Offset dan Length (harus > 0) diperlukan untuk RESERVED bank.") }
                    }
                    operationAttempted = true
                }
                else -> withContext(Dispatchers.Main) { showToast("Pilihan Memory Bank tidak dikenal.") }
            }

            if (success) {
                // Panggil getMemoryBankData untuk merefresh UI dengan state aktual
                // getMemoryBankData() akan menangani _isLoading.postValue(false) dan toast sukses
                withContext(Dispatchers.Main) {
                    _currentMemoryBank.value = bankString // Update LiveData utama dulu
                    // Detail offset/length akan diupdate oleh getMemoryBankData
                }
                getMemoryBankData() // Ini akan memuat ulang dan menampilkan toast sendiri
            } else {
                if (operationAttempted) { // Hanya tampilkan error jika operasi memang dicoba
                    showToast("Gagal mengatur Mode Inventory Memory Bank untuk $bankString.")
                }
                _isLoading.postValue(false) // Pastikan isLoading false jika gagal dan getMemoryBankData tidak dipanggil
            }
        }
    }

    // --- Session Gen2 ---
    fun getSessionGen2() {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            sdkManager.getUhfGen2Settings()?.let { gen2 ->
                _currentSessionId.postValue(mapSessionIdIntToString(gen2.querySession))
                _currentInventoriedFlag.postValue(mapTargetFlagIntToString(gen2.queryTarget))
                _currentQValue.postValue(gen2.q)
                showToast("Pengaturan Session Gen2 berhasil diambil.")
            } ?: showToast("Gagal mengambil pengaturan Session Gen2.")
            _isLoading.postValue(false)
        }
    }

    fun setSessionGen2(sessionIdString: String, inventoriedFlagString: String, qValueToSet: Int) {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        val sessionIdInt = mapSessionIdStringToInt(sessionIdString)
        val targetFlagInt = mapTargetFlagStringToInt(inventoriedFlagString)

        if (sessionIdInt == null || targetFlagInt == null) {
            showToast("Pilihan Session atau Target tidak valid untuk diatur.")
            return
        }
        if (qValueToSet !in 0..15) {
            showToast("Nilai Q harus antara 0 dan 15.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            if (sdkManager.setUhfGen2Settings(sessionIdInt, targetFlagInt, qValueToSet)) {
                _currentSessionId.postValue(sessionIdString)
                _currentInventoriedFlag.postValue(inventoriedFlagString)
                _currentQValue.postValue(qValueToSet) // Simpan juga Q value yang berhasil di-set
                showToast("Session Gen2 berhasil diatur.")
            } else {
                showToast("Gagal mengatur Session Gen2.")
            }
            _isLoading.postValue(false)
        }
    }

    fun onQValueChanged(q: Int) {
        if (q in 0..15) {
            _currentQValue.value = q
            // Tidak otomatis set ke reader, biarkan tombol "Set Session" yang melakukannya
        } else {
            showToast("Nilai Q tidak valid (0-15).")
        }
    }

    // --- Fast Inventory ---
    fun getFastInventoryStatus() {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            sdkManager.getUhfFastInventoryStatusInt()?.let { statusInt ->
                val isOpen = statusInt == 1
                _isFastInventoryOpen.postValue(isOpen)
                showToast("Status Fast Inventory: ${if (isOpen) "Open" else "Close"}")
            } ?: showToast("Gagal mendapatkan status Fast Inventory.")
            _isLoading.postValue(false)
        }
    }

    fun setFastInventoryStatus(isOpen: Boolean) {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            if (sdkManager.setUhfFastInventoryEnabled(isOpen)) {
                _isFastInventoryOpen.postValue(isOpen)
                showToast("Fast Inventory berhasil diatur ke ${if (isOpen) "Open" else "Close"}")
            } else {
                showToast("Gagal mengatur Fast Inventory.")
            }
            _isLoading.postValue(false)
        }
    }

    // --- TagFocus & FastID Switches ---
    fun setTagFocusEnabled(enabled: Boolean) {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val success = sdkManager.setUhfTagFocusEnabled(enabled)
            withContext(Dispatchers.Main) {
                if (success) {
                    _isTagFocusEnabled.value = enabled
                    showToast("TagFocus ${if (enabled) "diaktifkan" else "dinonaktifkan"}")
                } else {
                    showToast("Gagal mengatur TagFocus.")
                    // Tidak mengembalikan state UI, biarkan toggle di UI yang mengontrol
                }
                _isLoading.value = false
            }
        }
    }

    fun setFastIDEnabled(enabled: Boolean) {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val success = sdkManager.setUhfFastIDEnabled(enabled)
            withContext(Dispatchers.Main) {
                if (success) {
                    _isFastIDEnabled.value = enabled
                    showToast("FastID ${if (enabled) "diaktifkan" else "dinonaktifkan"}")
                } else {
                    showToast("Gagal mengatur FastID.")
                }
                _isLoading.value = false
            }
        }
    }

    fun getTagFocusStatus() {
        showToast("Status TagFocus tidak dapat diambil dari SDK. Menampilkan nilai yang di-set terakhir.")
    }

    fun getFastIDStatus() {
        showToast("Status FastID tidak dapat diambil dari SDK. Menampilkan nilai yang di-set terakhir.")
    }

    // --- Factory Reset ---
    fun performFactoryReset() {
        if (!sdkManager.isDeviceReady("uhf")) { showToast("Reader UHF tidak terhubung."); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            showToast("Melakukan Factory Reset...")
            val success = sdkManager.performUhfFactoryReset()
            if (success) {
                // loadAllInitialSettings akan menangani _isLoading.postValue(false) dan toast sukses
                loadAllInitialSettings()
            } else {
                withContext(Dispatchers.Main) { showToast("Factory Reset Gagal.") }
                _isLoading.postValue(false)
            }
        }
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    private fun showToast(message: String) {
        Log.d(TAG, message) // Tetap log untuk debugging
        _toastMessage.postValue(message) // Update LiveData untuk ditampilkan di UI
    }

    override fun onCleared() {
        super.onCleared()
        sdkManager.onError = null // Hapus listener untuk menghindari memory leak
        Log.d("SetupViewModel", "ViewModel Cleared.")
    }
}

