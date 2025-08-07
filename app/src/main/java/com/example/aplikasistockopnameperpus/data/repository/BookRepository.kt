// Di file BookRepository.kt
package com.example.aplikasistockopnameperpus.data.repository

import androidx.room.Transaction
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.BookMasterDao
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus // Import Enum
import com.example.aplikasistockopnameperpus.data.database.PairingStatus // Import Enum
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

    // Tambahan: getBookById dari DAO
    suspend fun getBookById(id: Long): BookMaster? {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getBookById(id)
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

    /**
     * Updates pairing details (TID, status, timestamp) for a book by its item code.
     * rfidTagHex is NOT updated here, assuming it's derived or set during initial import.
     */
    suspend fun updatePairingDetailsForBook(
        itemCode: String,
        newTid: String?,
        newPairingStatus: PairingStatus, // Menggunakan Enum
        pairingTimestamp: Long?
    ) {
        withContext(Dispatchers.IO) {
            // Menggunakan fungsi DAO yang baru: updatePairingDetailsByItemCode
            bookMasterDao.updatePairingDetailsByItemCode(
                itemCode = itemCode,
                newTid = newTid,
                newPairingStatus = newPairingStatus,
                pairingTimestamp = pairingTimestamp
            )
        }
    }

    /**
     * Updates full RFID details (rfidTagHex, TID, status, timestamp) for a book by its item code.
     * Use this if rfidTagHex also needs to be updated during/after pairing.
     */
    suspend fun updateFullRfidDetailsForBook(
        itemCode: String,
        newRfidTagHex: String?,
        newTid: String?,
        newPairingStatus: PairingStatus, // Menggunakan Enum
        pairingTimestamp: Long?
    ) {
        withContext(Dispatchers.IO) {
            // Menggunakan fungsi DAO yang baru: updateFullRfidDetailsByItemCode
            bookMasterDao.updateFullRfidDetailsByItemCode(
                itemCode = itemCode,
                newRfidTagHex = newRfidTagHex,
                newTid = newTid,
                newPairingStatus = newPairingStatus,
                pairingTimestamp = pairingTimestamp
            )
        }
    }

    suspend fun updateOpnameStatusForBookIds(
        bookIds: List<Long>,
        newStatus: OpnameStatus,
        timestamp: Long?,
        actualLocation: String? // Tambahkan ini jika DAO membutuhkannya
    ) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateOpnameStatusForBookIds(bookIds, newStatus, timestamp, actualLocation)
        }
    }

    suspend fun updateBookOpnameStatusByRfid( // Nama disesuaikan
        rfidTag: String,
        status: OpnameStatus?, // Menggunakan Enum
        timestamp: Long?,
        actualLocation: String?
    ) {
        withContext(Dispatchers.IO) {
            // Menggunakan fungsi DAO yang baru
            bookMasterDao.updateBookOpnameStatusByRfid(rfidTag, status, timestamp, actualLocation)
        }
    }

    suspend fun updateBookOpnameStatusByItemCode( // Nama disesuaikan
        itemCode: String,
        status: OpnameStatus?, // Menggunakan Enum
        timestamp: Long?,
        actualLocation: String?
    ) {
        withContext(Dispatchers.IO) {
            // Menggunakan fungsi DAO yang baru
            bookMasterDao.updateBookOpnameStatusByItemCode(itemCode, status, timestamp, actualLocation)
        }
    }

    fun getUntaggedBooksFlow(): Flow<List<BookMaster>> {
        // Memanggil fungsi DAO yang sudah diupdate untuk menggunakan Enum secara internal
        return bookMasterDao.getUntaggedBooksFlow() // Tidak perlu parameter lagi di sini jika DAO memiliki default
        // Atau jika Anda ingin tetap eksplisit dari Repository:
        // return bookMasterDao.getUntaggedBooksFlow(PairingStatus.NOT_PAIRED)
    }

    fun getBooksByPairingStatusFlow(status: PairingStatus): Flow<List<BookMaster>> { // Menggunakan Enum
        // Menggunakan fungsi DAO yang baru
        return bookMasterDao.getBooksByPairingStatusFlow(status)
    }

    suspend fun getBooksByPairingStatusList(status: PairingStatus): List<BookMaster> { // Menggunakan Enum
        return withContext(Dispatchers.IO) {
            // Menggunakan fungsi DAO yang baru
            bookMasterDao.getBooksByPairingStatusList(status)
        }
    }

    suspend fun resetAllBookOpnameStatusForNewSession() { // Nama disesuaikan
        withContext(Dispatchers.IO) {
            // Menggunakan fungsi DAO yang baru, bisa tanpa parameter jika default sudah di DAO
            bookMasterDao.resetAllBookOpnameStatusForNewSession()
            // Atau jika Anda ingin lebih eksplisit dari Repository:
            // bookMasterDao.resetAllBookOpnameStatusForNewSession(OpnameStatus.NOT_SCANNED, false)
        }
    }

    suspend fun clearAllBookMasters() {
        withContext(Dispatchers.IO) {
            bookMasterDao.clearAllBooks()
        }
    }

    suspend fun updateBookMaster(bookMaster: BookMaster): Int { // Sesuaikan return type jika DAO mengembalikan Int
        return withContext(Dispatchers.IO) {
            bookMasterDao.updateBook(bookMaster)
        }
    }

    suspend fun deleteBookMaster(bookMaster: BookMaster): Int { // Sesuaikan return type jika DAO mengembalikan Int
        return withContext(Dispatchers.IO) {
            bookMasterDao.deleteBook(bookMaster)
        }
    }

    // --- StockOpnameReport Operations ---
    // Tidak ada perubahan di sini kecuali jika DAO terkait juga berubah
    fun getAllStockOpnameReportsFlow(): Flow<List<StockOpnameReport>> = stockOpnameReportDao.getAllReportsFlow()

    suspend fun getReportById(reportId: Long): StockOpnameReport? {
        return withContext(Dispatchers.IO) {
            stockOpnameReportDao.getReportById(reportId)
        }
    }

    suspend fun insertStockOpnameReportWithItems(report: StockOpnameReport, items: List<StockOpnameItem>): Long {
        // Meneruskan panggilan ke Dao
        return bookMasterDao.insertStockOpnameReportWithItems(report, items)
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

    suspend fun updateReport(report: StockOpnameReport) { // Pertimbangkan return Int jika DAO diubah
        withContext(Dispatchers.IO) {
            stockOpnameReportDao.updateReport(report)
        }
    }


    // --- StockOpnameItem Operations ---
    // Tidak ada perubahan di sini kecuali jika DAO terkait juga berubah
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

    suspend fun insertOrUpdateStockOpnameItem(item: StockOpnameItem) { // Pertimbangkan return Long jika DAO diubah
        withContext(Dispatchers.IO) {
            stockOpnameItemDao.insertOrUpdateItem(item)
        }
    }

    // --- Combined Operations ---
    @Transaction // Penting untuk operasi yang melibatkan beberapa DAO call
    suspend fun saveFullStockOpnameSession(
        reportDetails: StockOpnameReport,
        itemsInSession: List<StockOpnameItem>
    ): Long {
        return withContext(Dispatchers.IO) {
            // Pastikan operasi ini berjalan dalam satu transaksi jika DAO tidak menanganinya
            val newReportId = stockOpnameReportDao.insertReport(reportDetails)
            if (newReportId > 0) { // Hanya insert item jika report berhasil dibuat
                val itemsWithReportId = itemsInSession.map { item ->
                    // Hanya set reportId jika belum ada dan newReportId valid
                    if (item.reportId == 0L) item.copy(reportId = newReportId) else item
                }
                stockOpnameItemDao.insertAllItems(itemsWithReportId)
            }
            newReportId // Kembalikan ID report baru (bisa 0 atau -1 jika gagal)
        }
    }
}
