package com.example.aplikasistockopnameperpus.viewmodel // Ganti dengan package Anda

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Ganti Any? dengan tipe interface/kelas reader Anda nanti
class SetupViewModel(private val simulatedReader: Any?) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // --- 1. Output Power ---
    private val _currentPower = MutableLiveData<Int?>()
    val currentPower: LiveData<Int?> = _currentPower
    private val _powerOptions = MutableLiveData<List<Int>>()
    val powerOptions: LiveData<List<Int>> = _powerOptions

    // --- 2. Frequency/Region ---
    private val _currentFrequencyRegion = MutableLiveData<String?>()
    val currentFrequencyRegion: LiveData<String?> = _currentFrequencyRegion
    private val _frequencyRegionOptions = MutableLiveData<List<String>>()
    val frequencyRegionOptions: LiveData<List<String>> = _frequencyRegionOptions

    // --- 3. Frequency Hopping ---
    private val _currentFreqHoppingType = MutableLiveData<String?>() // "US", "Others"
    val currentFreqHoppingType: LiveData<String?> = _currentFreqHoppingType
    private val _currentFreqHoppingTable = MutableLiveData<String?>()
    val currentFreqHoppingTable: LiveData<String?> = _currentFreqHoppingTable
    private val _freqHoppingTableOptions = MutableLiveData<List<String>>()
    val freqHoppingTableOptions: LiveData<List<String>> = _freqHoppingTableOptions
    private val _isFreqHoppingContainerVisible = MutableLiveData<Boolean>(true) // Contoh, sesuaikan
    val isFreqHoppingContainerVisible: LiveData<Boolean> = _isFreqHoppingContainerVisible

    // --- 4. Protocol ---
    private val _currentProtocol = MutableLiveData<String?>()
    val currentProtocol: LiveData<String?> = _currentProtocol
    private val _protocolOptions = MutableLiveData<List<String>>()
    val protocolOptions: LiveData<List<String>> = _protocolOptions

    // --- 5. RFLink Profile ---
    private val _currentRFLinkProfile = MutableLiveData<String?>()
    val currentRFLinkProfile: LiveData<String?> = _currentRFLinkProfile
    private val _rfLinkProfileOptions = MutableLiveData<List<String>>()
    val rfLinkProfileOptions: LiveData<List<String>> = _rfLinkProfileOptions

    // --- 6. Memory Bank (Inventory) ---
    private val _currentMemoryBank = MutableLiveData<String?>()
    val currentMemoryBank: LiveData<String?> = _currentMemoryBank
    private val _memoryBankOptions = MutableLiveData<List<String>>()
    val memoryBankOptions: LiveData<List<String>> = _memoryBankOptions
    private val _currentMemoryBankOffset = MutableLiveData<Int?>()
    val currentMemoryBankOffset: LiveData<Int?> = _currentMemoryBankOffset
    private val _currentMemoryBankLength = MutableLiveData<Int?>()
    val currentMemoryBankLength: LiveData<Int?> = _currentMemoryBankLength
    private val _isMemoryBankDetailsVisible = MutableLiveData<Boolean>(false)
    val isMemoryBankDetailsVisible: LiveData<Boolean> = _isMemoryBankDetailsVisible

    // --- 7. Session Gen2 ---
    private val _currentSessionId = MutableLiveData<String?>()
    val currentSessionId: LiveData<String?> = _currentSessionId
    private val _sessionIdOptions = MutableLiveData<List<String>>()
    val sessionIdOptions: LiveData<List<String>> = _sessionIdOptions
    private val _currentInventoriedFlag = MutableLiveData<String?>() // Target A/B
    val currentInventoriedFlag: LiveData<String?> = _currentInventoriedFlag
    private val _inventoriedFlagOptions = MutableLiveData<List<String>>()
    val inventoriedFlagOptions: LiveData<List<String>> = _inventoriedFlagOptions

    // --- 8. Fast Inventory ---
    private val _isFastInventoryOpen = MutableLiveData<Boolean?>() // true for Open, false for Close
    val isFastInventoryOpen: LiveData<Boolean?> = _isFastInventoryOpen

    // --- 9. Additional Settings (Switches) ---
    private val _isTagFocusEnabled = MutableLiveData<Boolean>(false)
    val isTagFocusEnabled: LiveData<Boolean> = _isTagFocusEnabled
    private val _isFastIDEnabled = MutableLiveData<Boolean>(false)
    val isFastIDEnabled: LiveData<Boolean> = _isFastIDEnabled


    init {
        Log.d("SetupViewModel", "ViewModel initialized. Simulated reader: $simulatedReader")
        // Initialize options for spinners/AutoCompleteTextViews
        _powerOptions.value = (5..30).toList() // dBm
        _frequencyRegionOptions.value = listOf("FCC (US)", "ETSI (EU)", "CHINA", "KOREA", "BRAZIL", "AUSTRALIA")
        _freqHoppingTableOptions.value = listOf("Table 1", "Table 2", "Custom") // Contoh
        _protocolOptions.value = listOf("GEN2", "ISO18000-6B", "IPX") // Contoh, sesuaikan dengan reader Anda
        _rfLinkProfileOptions.value = listOf("Profile 0 (High Throughput)", "Profile 1 (Dense Reader)", "Profile 2 (Resistant)")
        _memoryBankOptions.value = listOf("RESERVED", "EPC", "TID", "USER")
        _sessionIdOptions.value = listOf("S0", "S1", "S2", "S3")
        _inventoriedFlagOptions.value = listOf("TARGET A", "TARGET B")

        loadAllInitialSettings()
    }

    fun loadAllInitialSettings() {
        viewModelScope.launch {
            _isLoading.postValue(true)
            showToast("Simulasi: Memuat semua pengaturan awal...")
            delay(1500) // Simulate network delay

            // Simulate fetching initial values from reader
            _currentPower.postValue(20)
            _currentFrequencyRegion.postValue(_frequencyRegionOptions.value?.firstOrNull())
            _currentFreqHoppingType.postValue("US") // "US" or "Others"
            _currentFreqHoppingTable.postValue(_freqHoppingTableOptions.value?.firstOrNull())
            _currentProtocol.postValue(_protocolOptions.value?.firstOrNull())
            _currentRFLinkProfile.postValue(_rfLinkProfileOptions.value?.firstOrNull())
            _currentMemoryBank.postValue("EPC")
            onMemoryBankSelected("EPC") // Update visibility
            _currentMemoryBankOffset.postValue(2)
            _currentMemoryBankLength.postValue(6)
            _currentSessionId.postValue(_sessionIdOptions.value?.firstOrNull())
            _currentInventoriedFlag.postValue(_inventoriedFlagOptions.value?.firstOrNull())
            _isFastInventoryOpen.postValue(true) // Open
            _isTagFocusEnabled.postValue(false)
            _isFastIDEnabled.postValue(false)

            _isLoading.postValue(false)
            showToast("Simulasi: Pengaturan awal berhasil dimuat.")
        }
    }

    // --- Power ---
    fun getPower() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(300); _currentPower.value = 22; showToast("Simulasi: Daya diambil: 22 dBm")
            _isLoading.value = false
        }
    }
    fun setPower(powerToSet: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500); _currentPower.value = powerToSet; showToast("Simulasi: Daya diatur ke $powerToSet dBm")
            _isLoading.value = false
        }
    }

    // --- Frequency/Region ---
    fun getFrequencyRegion() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(300); _currentFrequencyRegion.value = _frequencyRegionOptions.value?.getOrNull(1) ?: "ETSI (EU)"; showToast("Simulasi: Region diambil: ${_currentFrequencyRegion.value}")
            _isLoading.value = false
        }
    }
    fun setFrequencyRegion(region: String) {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500); _currentFrequencyRegion.value = region; showToast("Simulasi: Region diatur ke $region")
            _isLoading.value = false
        }
    }

    // --- Frequency Hopping ---
    fun setFreqHopping(type: String, table: String) {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500)
            _currentFreqHoppingType.value = type
            _currentFreqHoppingTable.value = table
            showToast("Simulasi: Freq Hopping diatur: Tipe=$type, Tabel=$table")
            _isLoading.value = false
        }
    }
    // Get Freq Hopping (jika SDK mendukung pembacaan individual)
    fun getFreqHoppingSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(300)
            // _currentFreqHoppingType.value = "US" // Dari reader
            // _currentFreqHoppingTable.value = "Table 1" // Dari reader
            showToast("Simulasi: Pengaturan Freq Hopping diambil.")
            _isLoading.value = false
        }
    }


    // --- Protocol ---
    fun setProtocol(protocol: String) {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500); _currentProtocol.value = protocol; showToast("Simulasi: Protokol diatur ke $protocol")
            _isLoading.value = false
        }
    }
    // Get Protocol (jika SDK mendukung pembacaan individual)
    fun getProtocol() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(300)
            _currentProtocol.value = _protocolOptions.value?.firstOrNull() // Simulasi dari reader
            showToast("Simulasi: Protocol diambil: ${_currentProtocol.value}")
            _isLoading.value = false
        }
    }


    // --- RFLink Profile ---
    fun getRFLinkProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(300); _currentRFLinkProfile.value = _rfLinkProfileOptions.value?.getOrNull(0) ?: "Profile 0"; showToast("Simulasi: RFLink Profile diambil: ${_currentRFLinkProfile.value}")
            _isLoading.value = false
        }
    }
    fun setRFLinkProfile(profile: String) {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500); _currentRFLinkProfile.value = profile; showToast("Simulasi: RFLink Profile diatur ke $profile")
            _isLoading.value = false
        }
    }

    // --- Memory Bank ---
    fun onMemoryBankSelected(bank: String?) {
        // Tampilkan/sembunyikan detail offset/length berdasarkan bank yang dipilih
        // Contoh: hanya USER dan EPC yang memerlukan offset/length untuk dibaca/ditulis
        _isMemoryBankDetailsVisible.value = bank == "USER" || bank == "EPC"
    }

    fun getMemoryBankData(bank: String?, offset: Int?, length: Int?) {
        viewModelScope.launch {
            _isLoading.value = true
            delay(400)
            _currentMemoryBank.value = bank
            if (_isMemoryBankDetailsVisible.value == true) {
                _currentMemoryBankOffset.value = offset ?: 0
                _currentMemoryBankLength.value = length ?: 6
            }
            showToast("Simulasi: Data Memory Bank ($bank) diambil.")
            _isLoading.value = false
        }
    }
    fun setMemoryBankData(bank: String?, offset: Int?, length: Int?, dataToWrite: String) { // dataToWrite mungkin array byte
        viewModelScope.launch {
            _isLoading.value = true
            delay(600)
            // Logika simulasi penulisan
            showToast("Simulasi: Data Memory Bank ($bank) diatur. Offset: $offset, Length: $length")
            _isLoading.value = false
        }
    }

    // --- Session Gen2 ---
    fun getSessionGen2() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(300)
            _currentSessionId.value = _sessionIdOptions.value?.getOrNull(1) ?: "S1"
            _currentInventoriedFlag.value = _inventoriedFlagOptions.value?.getOrNull(0) ?: "TARGET A"
            showToast("Simulasi: Session Gen2 diambil: Session=${_currentSessionId.value}, Target=${_currentInventoriedFlag.value}")
            _isLoading.value = false
        }
    }
    fun setSessionGen2(sessionId: String, inventoriedFlag: String) {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500)
            _currentSessionId.value = sessionId
            _currentInventoriedFlag.value = inventoriedFlag
            showToast("Simulasi: Session Gen2 diatur: Session=$sessionId, Target=$inventoriedFlag")
            _isLoading.value = false
        }
    }

    // --- Fast Inventory ---
    fun getFastInventoryStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(300); _isFastInventoryOpen.value = false; showToast("Simulasi: Status Fast Inventory diambil: Close")
            _isLoading.value = false
        }
    }
    fun setFastInventoryStatus(isOpen: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500); _isFastInventoryOpen.value = isOpen; showToast("Simulasi: Fast Inventory diatur ke ${if (isOpen) "Open" else "Close"}")
            _isLoading.value = false
        }
    }

    // --- TagFocus & FastID Switches ---
    fun setTagFocusEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Biasanya, switch langsung apply, tapi SDK mungkin punya command terpisah
            // Atau, bisa jadi hanya dibaca saat inventory.
            _isLoading.value = true
            delay(200); _isTagFocusEnabled.value = enabled; showToast("Simulasi: TagFocus ${if (enabled) "diaktifkan" else "dinonaktifkan"}")
            _isLoading.value = false
            // Jika SDK memerlukan perintah set eksplisit:
            // SDK.setTagFocus(enabled)
        }
    }
    fun setFastIDEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            delay(200); _isFastIDEnabled.value = enabled; showToast("Simulasi: FastID ${if (enabled) "diaktifkan" else "dinonaktifkan"}")
            _isLoading.value = false
            // SDK.setFastID(enabled)
        }
    }
    // Getters untuk TagFocus & FastID jika SDK mendukung pembacaan statusnya
    fun getTagFocusStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(200); _isTagFocusEnabled.value = false // ambil dari SDK
            showToast("Simulasi: Status TagFocus diambil.")
            _isLoading.value = false
        }
    }
    fun getFastIDStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(200); _isFastIDEnabled.value = true // ambil dari SDK
            showToast("Simulasi: Status FastID diambil.")
            _isLoading.value = false
        }
    }


    // --- Factory Reset ---
    fun performFactoryReset() {
        viewModelScope.launch {
            _isLoading.value = true
            showToast("Simulasi: Melakukan Factory Reset...")
            delay(2000) // Simulate reset process
            // Setelah reset, idealnya semua pengaturan di-fetch ulang atau di-set ke default pabrik
            loadAllInitialSettings() // Atau panggil fungsi spesifik untuk set ke default
            _isLoading.value = false
            showToast("Simulasi: Factory Reset Selesai.")
        }
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    private fun showToast(message: String) {
        Log.d("SetupViewModel", message) // Log untuk debug
        _toastMessage.postValue(message)
    }
}
