package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
// import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem // Tidak langsung digunakan jika DTO sudah ada
import com.example.aplikasistockopnameperpus.data.model.ReportItemData // Import DTO Anda
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.model.FilterCriteria
import com.example.aplikasistockopnameperpus.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// UI State untuk proses global di ReportDetailActivity
sealed class ReportDetailGlobalState {
    data object Idle : ReportDetailGlobalState()
    data object Loading : ReportDetailGlobalState()
    data class Error(val message: String) : ReportDetailGlobalState()
    data class Success(val message: String? = null) : ReportDetailGlobalState()
}

class ReportDetailViewModel(
    application: Application,
    val reportId: Long
) : AndroidViewModel(application) {

    private val bookRepository: BookRepository = (application as MyApplication).bookRepository

    private val _globalListState = MutableStateFlow<ReportDetailGlobalState>(ReportDetailGlobalState.Idle)
    val globalListState: StateFlow<ReportDetailGlobalState> = _globalListState.asStateFlow()

    private val _currentReportFilterCriteria = MutableStateFlow(FilterCriteria())
    val currentReportFilterCriteria: StateFlow<FilterCriteria> = _currentReportFilterCriteria.asStateFlow()

    suspend fun getReportHeaderDetails(reportId: Long): com.example.aplikasistockopnameperpus.data.database.StockOpnameReport? {
        return bookRepository.getReportById(reportId)
    }

    val filteredReportDisplayItems: StateFlow<List<BookMasterDisplayWrapper>> = _currentReportFilterCriteria
        .debounce(300)
        .flatMapLatest { criteria ->
            Log.d("ReportDetailVM", "Filter criteria changed: $criteria, for reportId: $reportId")
            _globalListState.value = ReportDetailGlobalState.Loading
            bookRepository.getFilteredReportDataWithDetailsFlow(reportId, criteria)
                .map { reportDataList ->
                    reportDataList.map { data ->
                        mapReportItemDataToDisplayWrapper(data, getApplication())
                    }
                }
                .catch { e ->
                    Log.e("ReportDetailVM", "Error loading/filtering report items", e)
                    _globalListState.value = ReportDetailGlobalState.Error(
                        getApplication<Application>().getString(R.string.error_loading_report_items, e.message ?: "Unknown error")
                    )
                    emit(emptyList())
                }
        }
        .onEach { displayWrappers ->
            if (_globalListState.value is ReportDetailGlobalState.Loading) {
                _globalListState.value = if (displayWrappers.isEmpty() && _currentReportFilterCriteria.value == FilterCriteria()) {
                    ReportDetailGlobalState.Error(getApplication<Application>().getString(R.string.message_no_items_for_this_report))
                } else {
                    ReportDetailGlobalState.Idle
                }
            }
            Log.d("ReportDetailVM", "Received ${displayWrappers.size} display wrappers for reportId: $reportId")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        Log.d("ReportDetailVM", "ViewModel initialized for reportId: $reportId")
    }

    fun applyFilterToReportItems(newCriteria: FilterCriteria) {
        Log.d("ReportDetailVM", "Applying new filter criteria: $newCriteria")
        _currentReportFilterCriteria.value = newCriteria
    }

    fun buildActiveFilterDescription(): String? {
        val criteria = _currentReportFilterCriteria.value
        if (criteria == FilterCriteria()) return null

        val context = getApplication<Application>()
        val filters = mutableListOf<String>()
        criteria.opnameStatus?.let { filters.add(context.getString(R.string.filter_desc_status, it.name)) }
        criteria.titleQuery?.takeIf { it.isNotBlank() }?.let { filters.add(context.getString(R.string.filter_desc_title, it)) }
        criteria.itemCodeQuery?.takeIf { it.isNotBlank() }?.let { filters.add(context.getString(R.string.filter_desc_item_code, it)) }
        criteria.locationQuery?.takeIf { it.isNotBlank() }?.let { filters.add(context.getString(R.string.filter_desc_location, it)) }
        criteria.epcQuery?.takeIf { it.isNotBlank() }?.let { filters.add(context.getString(R.string.filter_desc_epc, it)) }
        criteria.isNewOrUnexpected?.let {
            filters.add(if (it) context.getString(R.string.filter_desc_new_unexpected_true) else context.getString(R.string.filter_desc_new_unexpected_false))
        }
        return if (filters.isEmpty()) null else context.getString(R.string.filter_desc_active_prefix, filters.joinToString(", "))
    }

    private fun mapReportItemDataToDisplayWrapper(
        data: ReportItemData,
        context: Context
    ): BookMasterDisplayWrapper {
        val stockOpnameItem = data.stockOpnameItem
        val bookMaster = data.bookMaster

        val displayPairingStatusText: String
        val displayPairingStatusColor: Int
        when (bookMaster?.pairingStatus) {
            PairingStatus.NOT_PAIRED -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_not_paired)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_not_paired_color)
            }
            PairingStatus.PAIRING_PENDING -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_pending)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_pending_color)
            }
            PairingStatus.PAIRED_WRITE_PENDING -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_paired_write_pending)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_pending_color)
            }
            PairingStatus.PAIRED_WRITE_SUCCESS -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_paired_write_success)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_success_color)
            }
            PairingStatus.PAIRED_WRITE_FAILED -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_paired_write_failed)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_failed_color)
            }
            PairingStatus.PAIRING_FAILED -> {
                displayPairingStatusText = context.getString(R.string.pairing_status_pairing_failed)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.pairing_status_failed_color)
            }
            null -> {
                displayPairingStatusText = context.getString(R.string.status_not_applicable_short)
                displayPairingStatusColor = ContextCompat.getColor(context, R.color.default_text_color)
            }
        }

        val displayOpnameStatusText: String
        val displayOpnameStatusColor: Int
        val dateFormat = SimpleDateFormat(Constants.DISPLAY_DATE_TIME_FORMAT, Locale.getDefault())

        val currentOpnameStatusFromString = stockOpnameItem.status
        val currentOpnameStatus: OpnameStatus = try {
            OpnameStatus.valueOf(currentOpnameStatusFromString.uppercase(Locale.ROOT))
        } catch (e: Exception) { // IllegalArgumentException atau NullPointerException jika status null
            Log.w("ReportDetailVM", "Invalid or null status string in StockOpnameItem: '$currentOpnameStatusFromString'", e)
            if (stockOpnameItem.isNewOrUnexpectedItem) OpnameStatus.NEW_ITEM else OpnameStatus.NOT_SCANNED
        }

        when (currentOpnameStatus) {
            OpnameStatus.NOT_SCANNED -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_not_scanned_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_not_scanned_color)
            }
            OpnameStatus.FOUND -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_found_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_found_correct_color)
            }
            OpnameStatus.MISSING -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_missing_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_missing_color)
            }
            OpnameStatus.NEW_ITEM -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_new_item_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_new_item_color)
            }
        }

        var displayLastSeenInfo: String? = null
        stockOpnameItem.scanTimestamp?.let { // scanTimestamp adalah Long
            var seenInfo = dateFormat.format(Date(it))
            stockOpnameItem.actualLocationIfDifferent?.takeIf { loc -> loc.isNotBlank() }?.let { loc ->
                seenInfo += " ${context.getString(R.string.opname_last_seen_at_location_prefix_short, loc)}"
            }
            displayLastSeenInfo = seenInfo.trim()
        }

        val effectiveBookMaster = bookMaster ?: BookMaster(
            id = 0L, // Tidak ada ID BookMaster langsung dari StockOpnameItem
            itemCode = stockOpnameItem.itemCodeMaster ?: stockOpnameItem.rfidTagHexScanned ?: context.getString(R.string.identifier_unknown),
            title = stockOpnameItem.titleMaster ?: context.getString(R.string.title_unknown_item),
            locationName = stockOpnameItem.expectedLocationMaster ?: stockOpnameItem.actualLocationIfDifferent ?: context.getString(R.string.location_unknown),
            rfidTagHex = stockOpnameItem.rfidTagHexScanned,
            pairingStatus = PairingStatus.NOT_PAIRED,
            opnameStatus = currentOpnameStatus, // Gunakan hasil konversi enum
            isNewOrUnexpected = stockOpnameItem.isNewOrUnexpectedItem,
            lastSeenTimestamp = stockOpnameItem.scanTimestamp,
            actualScannedLocation = stockOpnameItem.actualLocationIfDifferent
        )

        return BookMasterDisplayWrapper(
            bookMaster = effectiveBookMaster,
            displayPairingStatusText = displayPairingStatusText,
            displayPairingStatusColor = displayPairingStatusColor,
            displayOpnameStatusText = displayOpnameStatusText,
            displayOpnameStatusColor = displayOpnameStatusColor,
            displayLastSeenInfo = displayLastSeenInfo,
            displayExpectedLocation = bookMaster?.locationName ?: stockOpnameItem.expectedLocationMaster ?: context.getString(R.string.location_not_applicable_short),
            isSelectableForMissing = false
        )
    }

    private val _exportTxtState = MutableStateFlow<ReportDetailGlobalState>(ReportDetailGlobalState.Idle)
    val exportTxtState: StateFlow<ReportDetailGlobalState> = _exportTxtState.asStateFlow()

    fun exportFilteredItemCodesToTxt(baseFileName: String) {
        val currentDisplayItems = filteredReportDisplayItems.value
        if (currentDisplayItems.isEmpty()) {
            _exportTxtState.value = ReportDetailGlobalState.Error(getApplication<Application>().getString(R.string.message_no_data_to_export))
            return
        }
        _exportTxtState.value = ReportDetailGlobalState.Loading
        viewModelScope.launch {
            val itemCodes = currentDisplayItems.mapNotNull {
                it.bookMaster.itemCode?.takeIf { code -> code.isNotBlank() && code != getApplication<Application>().getString(R.string.identifier_unknown) }
            }
            if (itemCodes.isEmpty()) {
                _exportTxtState.value = ReportDetailGlobalState.Error(getApplication<Application>().getString(R.string.message_no_valid_item_codes_to_export))
                return@launch
            }
            val content = itemCodes.joinToString(separator = "\n")
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${baseFileName.replace(" ", "_").replace(":", "")}_itemcodes_$timestamp.txt"
            try {
                val fileUri = writeTextToFile(getApplication(), fileName, content)
                if (fileUri != null) {
                    _exportTxtState.value = ReportDetailGlobalState.Success(getApplication<Application>().getString(R.string.message_export_txt_success, fileName))
                } else {
                    _exportTxtState.value = ReportDetailGlobalState.Error(getApplication<Application>().getString(R.string.message_export_txt_failed_save))
                }
            } catch (e: Exception) {
                Log.e("ReportDetailVM", "Error saat ekspor TXT", e)
                _exportTxtState.value = ReportDetailGlobalState.Error(getApplication<Application>().getString(R.string.message_export_txt_failed_exception, e.message))
            }
        }
    }



    private suspend fun writeTextToFile(context: Application, fileName: String, content: String): Uri? {
        return withContext(Dispatchers.IO) {
            val exportDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!exportDir.exists()) {
                if (!exportDir.mkdirs()) {
                    Log.e("ReportDetailVM", "Gagal membuat direktori unduhan.")
                    return@withContext null
                }
            }
            val file = File(exportDir, fileName)
            try {
                FileWriter(file).use { writer -> writer.write(content) }
                Log.i("ReportDetailVM", "TXT Exported to: ${file.absolutePath}")
                return@withContext Uri.fromFile(file)
            } catch (e: IOException) {
                Log.e("ReportDetailVM", "Error writing TXT file", e)
                return@withContext null
            }
        }
    }

    fun clearExportState() {
        if (_exportTxtState.value !is ReportDetailGlobalState.Idle && _exportTxtState.value !is ReportDetailGlobalState.Loading) {
            _exportTxtState.value = ReportDetailGlobalState.Idle
        }
    }

    fun clearGlobalListError() {
        if (_globalListState.value is ReportDetailGlobalState.Error) {
            _globalListState.value = ReportDetailGlobalState.Idle
        }
    }
}
