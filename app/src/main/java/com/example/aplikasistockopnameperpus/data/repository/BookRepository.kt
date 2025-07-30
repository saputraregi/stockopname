package com.example.aplikasistockopnameperpus.data.repository

import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.BookMasterDao
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItemDao
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReportDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookRepository(
    private val bookMasterDao: BookMasterDao,
    private val stockOpnameReportDao: StockOpnameReportDao,
    private val stockOpnameItemDao: StockOpnameItemDao
) {

    // --- BookMaster Operations ---

    fun getAllBookMastersFlow(): Flow<List<BookMaster>> = bookMasterDao.getAllBooksFlow()

    suspend fun getAllBookMastersList(): List<BookMaster> {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getAllBooksList()
        }
    }

    suspend fun getBookByRfidTag(rfidTag: String): BookMaster? {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getBookByRfidTag(rfidTag)
        }
    }

    suspend fun getBookByItemCode(itemCode: String): BookMaster? {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getBookByItemCode(itemCode)
        }
    }

    suspend fun insertOrUpdateBookMaster(bookMaster: BookMaster): Long {
        return withContext(Dispatchers.IO) {
            bookMasterDao.insertOrUpdateBook(bookMaster)
        }
    }

    suspend fun insertAllBookMasters(books: List<BookMaster>) {
        withContext(Dispatchers.IO) {
            bookMasterDao.insertAll(books)
        }
    }

    suspend fun updateBookMasterScanStatus(rfidTag: String, status: String, timestamp: Long) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateBookScanStatus(rfidTag, status, timestamp)
        }
    }

    suspend fun resetAllBookMasterScanStatus() {
        withContext(Dispatchers.IO) {
            bookMasterDao.resetAllBookScanStatus()
        }
    }

    suspend fun clearAllBookMasters() {
        withContext(Dispatchers.IO) {
            bookMasterDao.clearAllBooks()
        }
    }

    suspend fun updateBookMaster(bookMaster: BookMaster) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateBook(bookMaster)
        }
    }

    suspend fun deleteBookMaster(bookMaster: BookMaster) {
        withContext(Dispatchers.IO) {
            bookMasterDao.deleteBook(bookMaster)
        }
    }

    // --- StockOpnameReport Operations ---

    fun getAllStockOpnameReportsFlow(): Flow<List<StockOpnameReport>> = stockOpnameReportDao.getAllReportsFlow()

    suspend fun getReportById(reportId: Long): StockOpnameReport? {
        return withContext(Dispatchers.IO) {
            stockOpnameReportDao.getReportById(reportId)
        }
    }

    suspend fun getLatestReport(): StockOpnameReport? {
        return withContext(Dispatchers.IO) {
            stockOpnameReportDao.getLatestReport()
        }
    }

    suspend fun insertReport(report: StockOpnameReport): Long {
        return withContext(Dispatchers.IO) {
            stockOpnameReportDao.insertReport(report)
        }
    }

    suspend fun updateReport(report: StockOpnameReport) {
        withContext(Dispatchers.IO) {
            stockOpnameReportDao.updateReport(report)
        }
    }


    // --- StockOpnameItem Operations ---

    fun getItemsForReportFlow(reportId: Long): Flow<List<StockOpnameItem>> =
        stockOpnameItemDao.getItemsForReportFlow(reportId)

    suspend fun getItemsForReportList(reportId: Long): List<StockOpnameItem> {
        return withContext(Dispatchers.IO) {
            stockOpnameItemDao.getItemsForReportList(reportId)
        }
    }

    suspend fun insertAllStockOpnameItems(items: List<StockOpnameItem>) {
        withContext(Dispatchers.IO) {
            stockOpnameItemDao.insertAllItems(items)
        }
    }

    suspend fun insertOrUpdateStockOpnameItem(item: StockOpnameItem) {
        withContext(Dispatchers.IO) {
            stockOpnameItemDao.insertOrUpdateItem(item)
        }
    }

    // --- Combined Operations (Contoh) ---

    /**
     * Menyimpan sesi stock opname secara keseluruhan.
     * Melibatkan pembuatan report header dan penyimpanan semua item terkait.
     * Fungsi ini juga bisa mengembalikan ID report yang baru dibuat.
     */
    suspend fun saveFullStockOpnameSession(
        reportDetails: StockOpnameReport,
        itemsInSession: List<StockOpnameItem>
    ): Long {
        return withContext(Dispatchers.IO) {
            // Biasanya, ID report dihasilkan saat insertReport
            val newReportId = stockOpnameReportDao.insertReport(reportDetails)

            // Update reportId untuk semua item jika belum diset atau jika perlu
            val itemsWithReportId = itemsInSession.map {
                if (it.reportId == 0L && newReportId > 0) it.copy(reportId = newReportId) else it
            }
            stockOpnameItemDao.insertAllItems(itemsWithReportId)
            newReportId // Kembalikan ID report baru
        }
    }
}
