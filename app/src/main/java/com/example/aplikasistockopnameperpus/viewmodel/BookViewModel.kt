package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.data.database.AppDatabase
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.BookMasterDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val bookDao: BookMasterDao
    // Menggunakan Flow dari DAO dan mengonversinya menjadi LiveData untuk UI
    val allBooks: LiveData<List<BookMaster>>

    // Jika Anda membutuhkan LiveData untuk jumlah buku yang ditemukan/tidak ditemukan,
    // Anda perlu query spesifik di DAO yang mengembalikan Flow<Int> atau LiveData<Int>.
    // Untuk saat ini, kita akan fokus pada fungsi DAO yang ada.

    init {
        val database = AppDatabase.getDatabase(application)
        bookDao = database.bookMasterDao()
        allBooks = bookDao.getAllBooksFlow().asLiveData() // Konversi Flow ke LiveData
    }

    /**
     * Menyisipkan atau memperbarui satu buku. Menggunakan insertOrUpdateBook dari DAO.
     * Mengembalikan ID baris yang terpengaruh atau -1 jika gagal.
     */
    suspend fun insertOrUpdateBook(book: BookMaster): Long {
        return withContext(Dispatchers.IO) {
            bookDao.insertOrUpdateBook(book)
        }
    }

    /**
     * Menyisipkan daftar buku. Menggunakan insertAll dari DAO.
     */
    fun insertAllBooks(books: List<BookMaster>) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.insertAll(books) // Menggunakan insertAll dari DAO
        }
    }

    /**
     * Memperbarui buku yang sudah ada. Menggunakan updateBook dari DAO.
     */
    fun updateBook(book: BookMaster) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateBook(book)
        }
    }

    /**
     * Mendapatkan semua buku sebagai List sekali ambil.
     */
    suspend fun getAllBooksListOnce(): List<BookMaster> {
        return withContext(Dispatchers.IO) {
            bookDao.getAllBooksList()
        }
    }

    /**
     * Mendapatkan buku berdasarkan RFID Tag (sekali ambil).
     */
    suspend fun getBookByRfidTagOnce(rfidTagHex: String): BookMaster? {
        return withContext(Dispatchers.IO) {
            bookDao.getBookByRfidTag(rfidTagHex)
        }
    }

    /**
     * Mendapatkan buku berdasarkan Item Code (sekali ambil).
     */
    suspend fun getBookByItemCodeOnce(itemCode: String): BookMaster? {
        return withContext(Dispatchers.IO) {
            bookDao.getBookByItemCode(itemCode)
        }
    }

    /**
     * Memperbarui status scan buku berdasarkan RFID.
     */
    fun updateBookScanStatus(rfidTagHex: String, status: String, timestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateBookScanStatus(rfidTagHex, status, timestamp)
        }
    }

    /**
     * Mengatur ulang status scan untuk semua buku.
     */
    fun resetAllBookScanStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.resetAllBookScanStatus()
        }
    }

    /**
     * Menghapus semua buku dari database.
     */
    fun clearAllBooks() { // Mengganti nama deleteAllBooks agar cocok dengan DAO
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.clearAllBooks()
        }
    }

    /**
     * Menghapus satu buku tertentu.
     */
    fun deleteBook(book: BookMaster) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.deleteBook(book)
        }
    }
}
