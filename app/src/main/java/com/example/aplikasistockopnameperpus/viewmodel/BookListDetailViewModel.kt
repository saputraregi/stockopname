package com.example.aplikasistockopnameperpus.viewmodel // Sesuaikan package

import android.app.Application
import androidx.lifecycle.*
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.model.FilterCriteria
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import android.content.Context
import androidx.core.content.ContextCompat
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.util.Constants


class BookListDetailViewModel(private val bookRepository: BookRepository, application: Application) : AndroidViewModel(application) {

    private val myApplicationInstance: MyApplication = getApplication() // getApplication() adalah dari AndroidViewModel

    sealed class UiState {
        object Loading : UiState()
        data class Success(val books: List<BookMasterDisplayWrapper>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    fun loadBooks(filterCriteria: FilterCriteria) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                bookRepository.getBooksByFilterCriteria(filterCriteria)
                    .map { bookList ->
                        // Gunakan application context yang sudah ada dari AndroidViewModel
                        bookList.map { book -> mapBookMasterToDisplayWrapper(book, getApplication<Application>().applicationContext) }
                    }
                    .collect { displayWrappers ->
                        _uiState.postValue(UiState.Success(displayWrappers))
                    }
            } catch (e: Exception) {
                _uiState.postValue(UiState.Error("Error loading books: ${e.message}"))
            }
        }
    }

    // Fungsi mapper, bisa dipindahkan ke kelas utilitas jika digunakan di banyak tempat
    private fun mapBookMasterToDisplayWrapper(book: BookMaster, context: Context): BookMasterDisplayWrapper {
        val displayPairingStatusText: String
        val displayPairingStatusColor: Int

        when (book.pairingStatus) {
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
        }

        val displayOpnameStatusText: String
        val displayOpnameStatusColor: Int
        var displayLastSeenInfo: String? = null
        val dateFormat = SimpleDateFormat(Constants.DISPLAY_DATE_TIME_FORMAT, Locale.getDefault())

        when (book.opnameStatus) {
            OpnameStatus.NOT_SCANNED -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_not_scanned_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_not_scanned_color)
            }
            OpnameStatus.FOUND -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_found_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_found_correct_color)
                if (book.lastSeenTimestamp != null) {
                    var seenInfo = dateFormat.format(Date(book.lastSeenTimestamp!!))
                    if (!book.actualScannedLocation.isNullOrBlank()) {
                        seenInfo += " ${context.getString(R.string.opname_last_seen_at_location_prefix_short, book.actualScannedLocation)}"
                    }
                    displayLastSeenInfo = seenInfo.trim()
                }
            }
            OpnameStatus.MISSING -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_missing_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_missing_color)
            }
            OpnameStatus.NEW_ITEM -> {
                displayOpnameStatusText = context.getString(R.string.opname_status_new_item_simplified)
                displayOpnameStatusColor = ContextCompat.getColor(context, R.color.opname_status_new_item_color)
                if (book.lastSeenTimestamp != null) {
                    var seenInfo = context.getString(R.string.opname_detected_at_prefix, dateFormat.format(Date(book.lastSeenTimestamp!!)))
                    if (!book.actualScannedLocation.isNullOrBlank()) {
                        seenInfo += " ${context.getString(R.string.opname_last_seen_at_location_prefix_short, book.actualScannedLocation)}"
                    }
                    displayLastSeenInfo = seenInfo.trim()
                }
            }
        }
        val displayExpectedLocation = book.locationName ?: context.getString(R.string.location_not_available)
        val isSelectableForMissing = book.opnameStatus == OpnameStatus.NOT_SCANNED && !book.isNewOrUnexpected

        return BookMasterDisplayWrapper(
            bookMaster = book,
            displayPairingStatusText = displayPairingStatusText,
            displayPairingStatusColor = displayPairingStatusColor,
            displayOpnameStatusText = displayOpnameStatusText,
            displayOpnameStatusColor = displayOpnameStatusColor,
            displayLastSeenInfo = displayLastSeenInfo,
            displayExpectedLocation = displayExpectedLocation,
            isSelectableForMissing = isSelectableForMissing
        )
    }
}

class BookListDetailViewModelFactory(
    private val repository: BookRepository,
    private val application: Application // Terima Application di sini
) : ViewModelProvider.Factory { // Atau ViewModelProvider.AndroidViewModelFactory jika hanya Application
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookListDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Sekarang kirim 'application' yang diterima factory
            return BookListDetailViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
