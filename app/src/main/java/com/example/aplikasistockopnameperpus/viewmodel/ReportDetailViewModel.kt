package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.model.FilterCriteria
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// UI State untuk Report Detail (bisa untuk loading global, error, atau pesan sukses)
sealed class ReportDetailGlobalState {
    data object Idle : ReportDetailGlobalState()
    data object Loading : ReportDetailGlobalState()
    data class Error(val message: String) : ReportDetailGlobalState()
    data class Success(val message: String? = null) : ReportDetailGlobalState() // Bisa untuk pesan sukses setelah filter
}

class ReportDetailViewModel(
    application: Application,
    val reportId: Long // Dijadikan public val agar Activity bisa akses jika perlu
) : AndroidViewModel(application) {

    // Akses Repository
    val bookRepository: BookRepository = (application as MyApplication).bookRepository

    // State untuk Global UI seperti loading atau error
    private val _globalState = MutableStateFlow<ReportDetailGlobalState>(ReportDetailGlobalState.Idle)
    val globalState: StateFlow<ReportDetailGlobalState> = _globalState.asStateFlow()

    // State untuk FilterCriteria yang aktif di halaman detail report
    private val _currentReportFilterCriteria = MutableStateFlow(FilterCriteria())
    val currentReportFilterCriteria: StateFlow<FilterCriteria> = _currentReportFilterCriteria.asStateFlow()

    // Flow untuk item report yang sudah difilter
    val filteredReportItems: StateFlow<List<StockOpnameItem>> = _currentReportFilterCriteria
        .debounce(300) // Tambahkan debounce untuk menghindari query berlebih saat filter cepat berubah (opsional)
        .flatMapLatest { criteria ->
            Log.d("ReportDetailVM", "Filter criteria changed: $criteria, for reportId: $reportId")
            _globalState.value = ReportDetailGlobalState.Loading // Tampilkan loading saat filter berubah atau load awal
            bookRepository.getFilteredReportItemsFlow(reportId, criteria)
                .catch { e ->
                    Log.e("ReportDetailVM", "Error loading/filtering report items", e)
                    _globalState.value = ReportDetailGlobalState.Error(
                        getApplication<Application>().getString(R.string.error_loading_report_items, e.message ?: "Unknown error")
                    )
                    emit(emptyList()) // Emit list kosong jika ada error
                }
        }
        .onEach { items ->
            // Setelah data diterima (baik dari load awal maupun filter)
            if (_globalState.value is ReportDetailGlobalState.Loading) { // Hanya ubah dari Loading ke Success/Idle
                _globalState.value = ReportDetailGlobalState.Idle // Atau Success jika ingin pesan
            }
            Log.d("ReportDetailVM", "Received ${items.size} filtered items for reportId: $reportId")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList() // Mulai dengan list kosong
        )

    init {
        Log.d("ReportDetailVM", "ViewModel initialized for reportId: $reportId")
        // Load data awal (tanpa filter eksplisit) saat ViewModel dibuat
        // Ini akan dipicu oleh initialValue dari _currentReportFilterCriteria
    }

    fun applyFilterToReportItems(newCriteria: FilterCriteria) {
        Log.d("ReportDetailVM", "Applying new filter criteria: $newCriteria")
        _currentReportFilterCriteria.value = newCriteria
    }

    /**
     * Membangun deskripsi teks dari filter yang aktif.
     * Ini bisa dipanggil oleh Activity untuk menampilkan informasi filter.
     */
    fun buildActiveFilterDescription(): String? {
        val criteria = _currentReportFilterCriteria.value
        if (criteria == FilterCriteria()) return null // Tidak ada filter aktif jika sama dengan default

        val filters = mutableListOf<String>()
        criteria.opnameStatus?.let { filters.add("Status: ${it.name}") }
        criteria.titleQuery?.takeIf { it.isNotBlank() }?.let { filters.add("Judul: '$it'") }
        criteria.itemCodeQuery?.takeIf { it.isNotBlank() }?.let { filters.add("Kode Item: '$it'") }
        criteria.locationQuery?.takeIf { it.isNotBlank() }?.let { filters.add("Lokasi: '$it'") }
        criteria.epcQuery?.takeIf { it.isNotBlank() }?.let { filters.add("EPC: '$it'") }
        criteria.isNewOrUnexpected?.let {
            filters.add(if (it) "Item Baru/Tak Terduga" else "Item Master Terdaftar")
        }

        return if (filters.isEmpty()) null else "Filter Aktif: ${filters.joinToString(", ")}"
    }
}
