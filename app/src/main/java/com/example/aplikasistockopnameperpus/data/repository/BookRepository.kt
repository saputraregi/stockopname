// Di file BookRepository.kt
package com.example.aplikasistockopnameperpus.data.repository

// Ganti import ini jika BookMasterDao ada di package lain
import com.example.aplikasistockopnameperpus.data.database.BookMasterDao
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItemDao
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReportDao
// ... import Flow, BookMaster, StockOpnameItem, StockOpnameReport, dll. ...
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookRepository(
    private val bookMasterDao: BookMasterDao, // Sebelumnya bookDao, pastikan namanya konsisten
    private val stockOpnameReportDao: StockOpnameReportDao,
    private val stockOpnameItemDao: StockOpnameItemDao
) {

    // --- BookMaster Operations ---
    // Pastikan nama DAO yang digunakan di sini adalah bookMasterDao
    fun getAllBookMastersFlow(): Flow<List<com.example.aplikasistockopnameperpus.data.database.BookMaster>> = bookMasterDao.getAllBooksFlow()

    suspend fun getAllBookMastersList(): List<com.example.aplikasistockopnameperpus.data.database.BookMaster> {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getAllBooksList()
        }
    }

    suspend fun getBookByRfidTag(rfidTag: String): com.example.aplikasistockopnameperpus.data.database.BookMaster? {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getBookByRfidTag(rfidTag)
        }
    }

    suspend fun getBookByItemCode(itemCode: String): com.example.aplikasistockopnameperpus.data.database.BookMaster? {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getBookByItemCode(itemCode)
        }
    }

    suspend fun insertOrUpdateBookMaster(bookMaster: com.example.aplikasistockopnameperpus.data.database.BookMaster): Long {
        return withContext(Dispatchers.IO) {
            bookMasterDao.insertOrUpdateBook(bookMaster)
        }
    }

    suspend fun insertAllBookMasters(books: List<com.example.aplikasistockopnameperpus.data.database.BookMaster>) {
        withContext(Dispatchers.IO) {
            bookMasterDao.insertAll(books)
        }
    }

    suspend fun updateRfidDetailsForBook(
        itemCode: String,
        newRfidTagHex: String?,
        newTid: String?,
        newPairingStatus: String,
        pairingTimestamp: Long?
    ) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateRfidDetailsByItemCode(
                itemCode = itemCode,
                newRfidTagHex = newRfidTagHex,
                newTid = newTid,
                newPairingStatus = newPairingStatus,
                pairingTimestamp = pairingTimestamp
            )
        }
    }

    suspend fun updateBookScanStatusByRfid(
        rfidTag: String,
        status: String?,
        timestamp: Long?,
        actualLocation: String?
    ) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateBookScanStatusByRfid(rfidTag, status, timestamp, actualLocation)
        }
    }

    suspend fun updateBookScanStatusByItemCode(
        itemCode: String,
        status: String?,
        timestamp: Long?,
        actualLocation: String?
    ) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateBookScanStatusByItemCode(itemCode, status, timestamp, actualLocation)
        }
    }


    fun getUntaggedBooksFlow(): Flow<List<com.example.aplikasistockopnameperpus.data.database.BookMaster>> {
        return bookMasterDao.getUntaggedBooksFlow()
    }

    fun getBooksByRfidPairingStatusFlow(status: String): Flow<List<com.example.aplikasistockopnameperpus.data.database.BookMaster>> {
        return bookMasterDao.getBooksByRfidPairingStatusFlow(status)
    }

    suspend fun getBooksByRfidPairingStatusList(status: String): List<com.example.aplikasistockopnameperpus.data.database.BookMaster> {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getBooksByRfidPairingStatusList(status)
        }
    }

    suspend fun resetAllBookScanStatusForNewSession() {
        withContext(Dispatchers.IO) {
            bookMasterDao.resetAllBookScanStatusForNewSession()
        }
    }

    suspend fun clearAllBookMasters() {
        withContext(Dispatchers.IO) {
            bookMasterDao.clearAllBooks()
        }
    }

    suspend fun updateBookMaster(bookMaster: com.example.aplikasistockopnameperpus.data.database.BookMaster) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateBook(bookMaster)
        }
    }

    suspend fun deleteBookMaster(bookMaster: com.example.aplikasistockopnameperpus.data.database.BookMaster) {
        withContext(Dispatchers.IO) {
            bookMasterDao.deleteBook(bookMaster)
        }
    }

    // --- StockOpnameReport Operations ---
    fun getAllStockOpnameReportsFlow(): Flow<List<com.example.aplikasistockopnameperpus.data.database.StockOpnameReport>> = stockOpnameReportDao.getAllReportsFlow()

    suspend fun getReportById(reportId: Long): com.example.aplikasistockopnameperpus.data.database.StockOpnameReport? {
        return withContext(Dispatchers.IO) {
            stockOpnameReportDao.getReportById(reportId)
        }
    }

    suspend fun getLatestReport(): com.example.aplikasistockopnameperpus.data.database.StockOpnameReport? {
        return withContext(Dispatchers.IO) {
            stockOpnameReportDao.getLatestReport()
        }
    }

    suspend fun insertReport(report: com.example.aplikasistockopnameperpus.data.database.StockOpnameReport): Long {
        return withContext(Dispatchers.IO) {
            stockOpnameReportDao.insertReport(report)
        }
    }

    suspend fun updateReport(report: com.example.aplikasistockopnameperpus.data.database.StockOpnameReport) {
        withContext(Dispatchers.IO) {
            stockOpnameReportDao.updateReport(report)
        }
    }


    // --- StockOpnameItem Operations ---
    fun getItemsForReportFlow(reportId: Long): Flow<List<com.example.aplikasistockopnameperpus.data.database.StockOpnameItem>> =
        stockOpnameItemDao.getItemsForReportFlow(reportId)

    suspend fun getItemsForReportList(reportId: Long): List<com.example.aplikasistockopnameperpus.data.database.StockOpnameItem> {
        return withContext(Dispatchers.IO) {
            stockOpnameItemDao.getItemsForReportList(reportId)
        }
    }

    suspend fun insertAllStockOpnameItems(items: List<com.example.aplikasistockopnameperpus.data.database.StockOpnameItem>) {
        withContext(Dispatchers.IO) {
            stockOpnameItemDao.insertAllItems(items)
        }
    }

    suspend fun insertOrUpdateStockOpnameItem(item: com.example.aplikasistockopnameperpus.data.database.StockOpnameItem) {
        withContext(Dispatchers.IO) {
            stockOpnameItemDao.insertOrUpdateItem(item)
        }
    }

    // --- Combined Operations ---
    // (Tidak ada perubahan di sini, tetap sama)
    suspend fun saveFullStockOpnameSession(
        reportDetails: com.example.aplikasistockopnameperpus.data.database.StockOpnameReport,
        itemsInSession: List<com.example.aplikasistockopnameperpus.data.database.StockOpnameItem>
    ): Long {
        return withContext(Dispatchers.IO) {
            val newReportId = stockOpnameReportDao.insertReport(reportDetails)
            val itemsWithReportId = itemsInSession.map {
                if (it.reportId == 0L && newReportId > 0) it.copy(reportId = newReportId) else it
            }
            stockOpnameItemDao.insertAllItems(itemsWithReportId)
            newReportId
        }
    }
}
