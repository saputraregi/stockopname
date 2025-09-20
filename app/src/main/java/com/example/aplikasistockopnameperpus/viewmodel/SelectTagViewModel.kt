package com.example.aplikasistockopnameperpus.viewmodel // atau package yang sesuai

import androidx.lifecycle.*
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class SelectTagUiState {
    object Loading : SelectTagUiState()
    data class Success(val books: List<BookMaster>) : SelectTagUiState()
    data class Error(val message: String) : SelectTagUiState()
    object Empty : SelectTagUiState() // Jika tidak ada hasil atau query kosong
}

@OptIn(FlowPreview::class)
class SelectTagViewModel(private val bookRepository: BookRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<SelectTagUiState>(SelectTagUiState.Empty)
    val uiState: StateFlow<SelectTagUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300) // Tunggu 300ms setelah user berhenti mengetik
                .distinctUntilChanged()
                .collectLatest { query ->
                    loadBooks(query)
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun loadBooks(query: String) {
        viewModelScope.launch {
            _uiState.value = SelectTagUiState.Loading
            try {
                // Ambil semua buku dan filter di sini, atau buat query DAO yang lebih spesifik
                // Hanya ambil buku yang sudah memiliki rfidTagHex
                val allTaggedBooks = bookRepository.getAllBookMastersList().filter { !it.rfidTagHex.isNullOrBlank() }

                if (query.isBlank()) {
                    if (allTaggedBooks.isNotEmpty()) {
                        _uiState.value = SelectTagUiState.Success(allTaggedBooks)
                    } else {
                        _uiState.value = SelectTagUiState.Empty
                    }
                } else {
                    val filteredBooks = allTaggedBooks.filter { book ->
                        book.title.contains(query, ignoreCase = true) ||
                                book.itemCode.contains(query, ignoreCase = true) ||
                                (book.rfidTagHex?.contains(query, ignoreCase = true) == true)
                    }
                    if (filteredBooks.isNotEmpty()) {
                        _uiState.value = SelectTagUiState.Success(filteredBooks)
                    } else {
                        _uiState.value = SelectTagUiState.Empty
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SelectTagUiState.Error("Gagal memuat daftar buku: ${e.message}")
            }
        }
    }

    // Panggil ini saat dialog pertama kali dibuka untuk memuat semua buku yang sudah ditag
    fun initialLoad() {
        loadBooks("")
    }
}

// ViewModelFactory untuk SelectTagViewModel
class SelectTagViewModelFactory(private val bookRepository: BookRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SelectTagViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SelectTagViewModel(bookRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
