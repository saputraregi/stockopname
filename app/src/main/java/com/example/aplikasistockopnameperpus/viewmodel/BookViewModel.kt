/*package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.PairingStatus // Pastikan ini ada dan benar
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel // Contoh jika menggunakan Hilt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject // Contoh jika menggunakan Hilt

data class BookUiState(
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val selectedBookForPairing: BookMaster? = null,
    val pairingResult: PairingResult? = null
)

data class PairingResult(
    val success: Boolean,
    val message: String,
    val bookMaster: BookMaster? = null
)

@HiltViewModel // Misal menggunakan Hilt
class BookViewModel @Inject constructor( // Misal menggunakan Hilt
    application: Application,
    private val bookRepository: BookRepository
) : AndroidViewModel(application) {

    val allBooks: LiveData<List<BookMaster>> = bookRepository.getAllBookMastersFlow().asLiveData()

    private val _untaggedBooks = MutableStateFlow<List<BookMaster>>(emptyList())
    val untaggedBooks: StateFlow<List<BookMaster>> = _untaggedBooks.asStateFlow()

    private val _uiState = MutableStateFlow(BookUiState())
    val uiState: StateFlow<BookUiState> = _uiState.asStateFlow()

    private val _selectedBook = MutableLiveData<BookMaster?>()
    val selectedBook: LiveData<BookMaster?> = _selectedBook

    private val _lastScannedBookInfo = MutableLiveData<String?>()
    val lastScannedBookInfo: LiveData<String?> = _lastScannedBookInfo

    init {
        loadUntaggedBooks()
    }

    private fun loadUntaggedBooks() {
        viewModelScope.launch {
            bookRepository.getUntaggedBooksFlow()
                .catch { exception ->
                    _uiState.update { it.copy(isLoading = false, userMessage = "Error memuat buku belum ditag: ${exception.message}") }
                }
                .collect { books ->
                    _untaggedBooks.value = books
                }
        }
    }

    fun setSelectedBook(book: BookMaster?) {
        _selectedBook.value = book
        _uiState.update { it.copy(selectedBookForPairing = book) }
    }

    fun insertOrUpdateBook(book: BookMaster) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val resultId = bookRepository.insertOrUpdateBookMaster(book) // Asumsi suspend fun
                if (resultId > 0) {
                    _uiState.update { it.copy(isLoading = false, userMessage = "Buku berhasil disimpan/diperbarui.") }
                } else {
                    _uiState.update { it.copy(isLoading = false, userMessage = "Gagal menyimpan buku (ID tidak valid).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Error menyimpan buku: ${e.message}") }
            }
        }
    }

    fun insertAllBooks(books: List<BookMaster>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                bookRepository.insertAllBookMasters(books) // Asumsi suspend fun
                _uiState.update { it.copy(isLoading = false, userMessage = "${books.size} buku berhasil diimpor.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Error mengimpor buku: ${e.message}") }
            }
        }
    }

    fun updateRfidDetailsAfterPairing(
        itemCode: String,
        newRfidTagHex: String?,
        newTid: String?,
        newPairingStatus: PairingStatus,
        pairingTimestamp: Long?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                bookRepository.updateFullRfidDetailsForBook(
                    itemCode, newRfidTagHex, newTid, newPairingStatus.name, pairingTimestamp
                )
                val updatedBook = bookRepository.getBookByItemCode(itemCode) // Asumsi suspend fun
                val success = newPairingStatus == PairingStatus.PAIRED_WRITE_SUCCESS && newRfidTagHex != null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = if (success) "Pairing RFID berhasil untuk $itemCode." else "Proses pairing selesai untuk $itemCode.",
                        pairingResult = PairingResult(success, if (success) "Pairing berhasil" else "Pairing selesai dengan status: ${newPairingStatus.name}", updatedBook)
                    )
                }
                if (updatedBook != null) { // Muat ulang hanya jika buku memang ada dan mungkin statusnya berubah
                    loadUntaggedBooks()
                }
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

    fun processScannedRfidTag(rfidTagHex: String, actualLocationDuringScan: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val book = bookRepository.getBookByRfidTag(rfidTagHex) // Asumsi suspend fun
                if (book != null) {
                    val currentTimestamp = System.currentTimeMillis()
                    val statusScan: String
                    val message: String

                    // Lebih aman membandingkan dengan actualLocationDuringScan di sisi kiri jika book.isNewOrUnexpected bisa null
                    if (actualLocationDuringScan.equals(book.isNewOrUnexpected, ignoreCase = true)) {
                        statusScan = "DITEMUKAN" // Pertimbangkan Enum ScanStatus.FOUND.name
                        message = "Buku '${book.title}' (${book.itemCode}) DITEMUKAN di lokasi yang benar."
                    } else {
                        statusScan = "LOKASI_SALAH" // Pertimbangkan Enum ScanStatus.WRONG_LOCATION.name
                        message = "Buku '${book.title}' (${book.itemCode}) ditemukan di '$actualLocationDuringScan', seharusnya di '${book.expectedLocation ?: "N/A"}'."
                    }
                    bookRepository.updateBookOpnameStatusByItemCode(rfidTagHex, statusScan, currentTimestamp, actualLocationDuringScan) // Asumsi suspend fun

                    _lastScannedBookInfo.value = message
                    _uiState.update { it.copy(isLoading = false, userMessage = message) }
                } else {
                    val message = "RFID Tag '$rfidTagHex' tidak terdaftar di master buku."
                    _lastScannedBookInfo.value = message
                    _uiState.update { it.copy(isLoading = false, userMessage = message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Error memproses scan: ${e.message}") }
            }
        }
    }

    fun startNewStockOpnameSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                bookRepository.resetAllBookOpnameStatusForNewSession() // Asumsi suspend fun
                _lastScannedBookInfo.value = null
                _uiState.update { it.copy(isLoading = false, userMessage = "Sesi stock opname baru telah dimulai. Semua status scan direset.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Error memulai sesi baru: ${e.message}") }
            }
        }
    }

    fun clearAllBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                bookRepository.clearAllBookMasters() // Asumsi suspend fun
                _uiState.update { it.copy(isLoading = false, userMessage = "Semua data buku telah dihapus.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Error menghapus semua buku: ${e.message}") }
            }
        }
    }

    fun deleteBook(book: BookMaster) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                bookRepository.deleteBookMaster(book) // Asumsi suspend fun
                _uiState.update { it.copy(isLoading = false, userMessage = "Buku '${book.title}' telah dihapus.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Error menghapus buku '${book.title}': ${e.message}") }
            }
        }
    }

    suspend fun getAllBooksListOnce(): List<BookMaster> {
        _uiState.update { it.copy(isLoading = true) }
        return try {
            // Asumsi bookRepository.getAllBookMastersList adalah suspend fun dari Room DAO
            bookRepository.getAllBookMastersList().also {
                _uiState.update { state -> state.copy(isLoading = false) }
            }
        } catch (e: Exception) {
            _uiState.update { state -> state.copy(isLoading = false, userMessage = "Error mengambil data: ${e.message}") }
            emptyList()
        }
    }

    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun pairingResultConsumed() {
        _uiState.update { it.copy(pairingResult = null) }
    }
}
*/