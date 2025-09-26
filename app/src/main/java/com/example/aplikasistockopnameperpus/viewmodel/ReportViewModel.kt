package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.util.Log // Untuk logging error
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
// import androidx.lifecycle.viewModelScope // Tidak digunakan secara langsung di sini, tapi baik untuk ada jika ada operasi one-shot
import com.example.aplikasistockopnameperpus.data.database.AppDatabase
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.util.Date // Untuk ReportFilterCriteria

// Definisikan Kriteria Urut di luar kelas atau sebagai inner enum jika lebih sesuai
enum class ReportSortOrder {
    DATE_DESC, // Terbaru dulu (default)
    DATE_ASC,  // Terlama dulu
    NAME_ASC,  // Berdasarkan Nama A-Z
    NAME_DESC  // Berdasarkan Nama Z-A
}

// Definisikan Kriteria Filter
data class ReportFilterCriteria(
    val startDate: Date? = null,
    val endDate: Date? = null,
    val nameQuery: String? = null // Bisa null jika tidak ada filter nama
)

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val bookRepository: BookRepository

    // StateFlow untuk menyimpan kriteria urut saat ini
    private val _currentSortOrder = MutableStateFlow(ReportSortOrder.DATE_DESC)
    val currentSortOrder: StateFlow<ReportSortOrder> = _currentSortOrder.asStateFlow() // Expose sebagai StateFlow

    // StateFlow untuk menyimpan kriteria filter saat ini
    private val _currentFilterCriteria = MutableStateFlow<ReportFilterCriteria?>(null) // Awalnya null, tidak ada filter
    val currentFilterCriteria: StateFlow<ReportFilterCriteria?> = _currentFilterCriteria.asStateFlow()

    // Menggunakan Flow yang dikonversi ke LiveData untuk observasi di Activity
    // LiveData ini akan otomatis diperbarui ketika _currentSortOrder atau _currentFilterCriteria berubah
    val allStockOpnameReports: LiveData<List<StockOpnameReport>>

    init {
        // Mendapatkan instance DAO dari AppDatabase
        val bookMasterDao = AppDatabase.getDatabase(application).bookMasterDao()
        val stockOpnameReportDao = AppDatabase.getDatabase(application).stockOpnameReportDao() // DAO yang kita butuhkan
        val stockOpnameItemDao = AppDatabase.getDatabase(application).stockOpnameItemDao()

        // Inisialisasi BookRepository dengan semua DAO yang diperlukan
        bookRepository = BookRepository(bookMasterDao, stockOpnameReportDao, stockOpnameItemDao)

        // Mengambil Flow dari repository, yang akan bereaksi terhadap perubahan filter dan sort order
        allStockOpnameReports = combine(
            _currentSortOrder,
            _currentFilterCriteria
        ) { sortOrder, filterCriteria ->
            // Pair ini akan memicu flatMapLatest setiap kali sortOrder atau filterCriteria berubah
            Pair(sortOrder, filterCriteria)
        }.flatMapLatest { (sortOrder, filterCriteria) ->
            Log.d("ReportViewModel", "Mengambil laporan dengan filter: $filterCriteria dan urutan: $sortOrder")
            // Panggil fungsi di repository yang menerima sortOrder dan filterCriteria
            // Anda HARUS mengimplementasikan fungsi ini di BookRepository dan DAO yang sesuai.
            bookRepository.getFilteredAndSortedStockOpnameReportsFlow(filterCriteria, sortOrder)
        }.catch { exception ->
            Log.e("ReportViewModel", "Error saat mengambil laporan yang difilter/diurutkan", exception)
            emit(emptyList()) // Fallback ke daftar kosong jika terjadi error
        }.asLiveData() // Konversi hasil Flow ke LiveData
    }

    /**
     * Menerapkan urutan baru untuk daftar laporan.
     */
    fun applySortOrder(sortOrder: ReportSortOrder) {
        _currentSortOrder.value = sortOrder
    }

    /**
     * Menerapkan kriteria filter baru untuk daftar laporan.
     * Mengirimkan null akan menghapus filter (atau Anda bisa buat fungsi clearFilter terpisah).
     */
    fun applyFilterCriteria(filterCriteria: ReportFilterCriteria?) {
        _currentFilterCriteria.value = filterCriteria
    }

    /**
     * Menghapus semua filter dan mengembalikan urutan ke default.
     */
    fun clearFiltersAndSort() {
        _currentFilterCriteria.value = null
        _currentSortOrder.value = ReportSortOrder.DATE_DESC // Atur ke default Anda
    }

}

