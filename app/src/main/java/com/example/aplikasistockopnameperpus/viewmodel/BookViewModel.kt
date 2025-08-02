package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.data.database.AppDatabase // Sebaiknya diinject Repository
import com.example.aplikasistockopnameperpus.data.database.BookMaster
// import com.example.aplikasistockopnameperpus.data.database.BookMasterDao // Tidak langsung akses DAO
import com.example.aplikasistockopnameperpus.data.repository.BookRepository // Import Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Kelas untuk merepresentasikan UI State, bisa lebih kompleks
data class BookUiState(
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val selectedBookForPairing: BookMaster? = null,
    val pairingResult: PairingResult? = null // Untuk hasil pairing
)

data class PairingResult(
    val success: Boolean,
    val message: String,
    val bookMaster: BookMaster? = null // BookMaster yang sudah diupdate jika sukses
)

class BookViewModel(application: Application) : AndroidViewModel(application) {

    // Seharusnya di-inject, tapi untuk contoh ini kita instansiasi langsung
    private val bookRepository: BookRepository

    // LiveData untuk daftar semua buku (sesuai kode awal Anda)
    val allBooks: LiveData<List<BookMaster>>

    // StateFlow untuk daftar buku yang belum ditag (contoh penggunaan StateFlow)
    private val _untaggedBooks = MutableStateFlow<List<BookMaster>>(emptyList())
    val untaggedBooks: StateFlow<List<BookMaster>> = _untaggedBooks.asStateFlow()

    // StateFlow untuk UI state umum (loading, messages)
    private val _uiState = MutableStateFlow(BookUiState())
    val uiState: StateFlow<BookUiState> = _uiState.asStateFlow()

    // LiveData atau StateFlow untuk buku yang sedang dipilih untuk detail/operasi
    private val _selectedBook = MutableLiveData<BookMaster?>()
    val selectedBook: LiveData<BookMaster?> = _selectedBook

    // LiveData/StateFlow untuk hasil scan terakhir (jika ingin ditampilkan terpisah)
    private val _lastScannedBookInfo = MutableLiveData<String?>()
    val lastScannedBookInfo: LiveData<String?> = _lastScannedBookInfo


    init {
        val database = AppDatabase.getDatabase(application)
        // Instansiasi Repository (Idealnya menggunakan Dependency Injection seperti Hilt/Koin)
        bookRepository = BookRepository(
            database.bookMasterDao(),
            database.stockOpnameReportDao(), // Pastikan DAO ini ada
            database.stockOpnameItemDao()    // Pastikan DAO ini ada
        )

        allBooks = bookRepository.getAllBookMastersFlow().asLiveData() // Dari repository
        loadUntaggedBooks()
    }

    private fun loadUntaggedBooks() {
        viewModelScope.launch {
            bookRepository.getUntaggedBooksFlow()
                .catch { exception ->
                    _uiState.update { it.copy(userMessage = "Error memuat buku belum ditag: ${exception.message}") }
                }
                .collect { books ->
                    _untaggedBooks.value = books
                }
        }
    }

    fun setSelectedBook(book: BookMaster?) {
        _selectedBook.value = book
        _uiState.update { it.copy(selectedBookForPairing = book) } // Juga update di uiState jika relevan
    }

    /**
     * Menyisipkan atau memperbarui satu buku.
     */
    fun insertOrUpdateBook(book: BookMaster) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val resultId = bookRepository.insertOrUpdateBookMaster(book)
            if (resultId > 0) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Buku berhasil disimpan/diperbarui.") }
                // Refresh list jika perlu (Flow biasanya otomatis, tapi bisa jadi ada kasus)
            } else {
                _uiState.update { it.copy(isLoading = false, userMessage = "Gagal menyimpan buku.") }
            }
        }
    }

    /**
     * Menyisipkan daftar buku (misalnya dari impor Excel).
     */
    fun insertAllBooks(books: List<BookMaster>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            bookRepository.insertAllBookMasters(books)
            _uiState.update { it.copy(isLoading = false, userMessage = "${books.size} buku berhasil diimpor.") }
            // Flow akan otomatis update UI jika `allBooks` atau `untaggedBooks` diobservasi
        }
    }

    /**
     * Memperbarui detail RFID setelah proses pairing.
     */
    fun updateRfidDetailsAfterPairing(
        itemCode: String,
        newRfidTagHex: String?,
        newTid: String?,
        newPairingStatus: String,
        pairingTimestamp: Long?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                bookRepository.updateRfidDetailsForBook(
                    itemCode, newRfidTagHex, newTid, newPairingStatus, pairingTimestamp
                )
                val updatedBook = bookRepository.getBookByItemCode(itemCode) // Ambil buku yang sudah diupdate
                val success = newPairingStatus == "SUDAH_DITAG" && newRfidTagHex != null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = if (success) "Pairing RFID berhasil untuk $itemCode." else "Proses pairing selesai untuk $itemCode.",
                        pairingResult = PairingResult(success, if (success) "Pairing berhasil" else "Pairing selesai dengan status: $newPairingStatus", updatedBook)
                    )
                }
                loadUntaggedBooks() // Refresh daftar buku belum ditag
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = "Error update RFID: ${e.message}",
                        pairingResult = PairingResult(false, "Error: ${e.message}")
                    )
                }
            }
        }
    }

    /**
     * Memproses tag RFID yang terdeteksi selama sesi stock opname.
     */
    fun processScannedRfidTag(rfidTagHex: String, actualLocationDuringScan: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val book = bookRepository.getBookByRfidTag(rfidTagHex)
            if (book != null) {
                val currentTimestamp = System.currentTimeMillis()
                val statusScan: String
                val message: String

                if (book.expectedLocation.equals(actualLocationDuringScan, ignoreCase = true)) {
                    statusScan = "DITEMUKAN"
                    message = "Buku '${book.title}' (${book.itemCode}) DITEMUKAN di lokasi yang benar."
                } else {
                    statusScan = "LOKASI_SALAH"
                    message = "Buku '${book.title}' (${book.itemCode}) ditemukan di '$actualLocationDuringScan', seharusnya di '${book.expectedLocation ?: "N/A"}."
                }
                // Update status di BookMaster
                bookRepository.updateBookScanStatusByRfid(rfidTagHex, statusScan, currentTimestamp, actualLocationDuringScan)
                // TODO: Anda mungkin juga ingin mencatat ini di StockOpnameItem jika menggunakan tabel terpisah untuk item sesi

                _lastScannedBookInfo.value = message
                _uiState.update { it.copy(isLoading = false, userMessage = message) }
                // LiveData/Flow yang diobservasi untuk daftar buku akan otomatis terupdate
            } else {
                val message = "RFID Tag '$rfidTagHex' tidak terdaftar di master buku."
                _lastScannedBookInfo.value = message
                _uiState.update { it.copy(isLoading = false, userMessage = message) }
            }
        }
    }


    /**
     * Mengatur ulang status scan untuk semua buku (memulai sesi opname baru).
     */
    fun startNewStockOpnameSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            bookRepository.resetAllBookScanStatusForNewSession()
            _lastScannedBookInfo.value = null // Bersihkan info scan terakhir
            _uiState.update { it.copy(isLoading = false, userMessage = "Sesi stock opname baru telah dimulai. Semua status scan direset.") }
        }
    }

    /**
     * Menghapus semua buku dari database.
     */
    fun clearAllBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            bookRepository.clearAllBookMasters()
            _uiState.update { it.copy(isLoading = false, userMessage = "Semua data buku telah dihapus.") }
        }
    }

    /**
     * Menghapus satu buku tertentu.
     */
    fun deleteBook(book: BookMaster) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            bookRepository.deleteBookMaster(book)
            _uiState.update { it.copy(isLoading = false, userMessage = "Buku '${book.title}' telah dihapus.") }
        }
    }

    /**
     * Fungsi untuk UI yang ingin mengambil semua buku sekali tanpa observasi Flow/LiveData.
     * Hati-hati penggunaan ini di UI thread jika datanya besar.
     */
    suspend fun getAllBooksListOnce(): List<BookMaster> {
        _uiState.update { it.copy(isLoading = true) }
        return try {
            bookRepository.getAllBookMastersList().also {
                _uiState.update { state -> state.copy(isLoading = false) }
            }
        } catch (e: Exception) {
            _uiState.update { state -> state.copy(isLoading = false, userMessage = "Error mengambil data: ${e.message}") }
            emptyList()
        }
    }

    /**
     * Membersihkan pesan pengguna setelah ditampilkan.
     */
    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
        // _lastScannedBookInfo.value = null // Jika ini juga ingin dibersihkan setelah ditampilkan
    }

    fun pairingResultConsumed() {
        _uiState.update { it.copy(pairingResult = null) }
    }
}
