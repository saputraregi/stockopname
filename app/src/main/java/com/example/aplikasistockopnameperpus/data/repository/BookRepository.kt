package com.example.aplikasistockopnameperpus.data.repository

import androidx.room.Transaction
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.BookMasterDao
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItemDao
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReportDao
import com.example.aplikasistockopnameperpus.model.FilterCriteria // PASTIKAN IMPORT INI ADA
import com.example.aplikasistockopnameperpus.model.ScanMethod // Jika Anda memiliki fungsi findNewOrUnexpectedItemByIdentifier
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
        newPairingStatus: PairingStatus,
        pairingTimestamp: Long?
    ) {
        withContext(Dispatchers.IO) {
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
        newPairingStatus: PairingStatus,
        pairingTimestamp: Long?
    ) {
        withContext(Dispatchers.IO) {
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
        actualLocation: String?
    ) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateOpnameStatusForBookIds(bookIds, newStatus, timestamp, actualLocation)
        }
    }

    suspend fun updateBookOpnameStatusByRfid(
        rfidTag: String,
        status: OpnameStatus?,
        timestamp: Long?,
        actualLocation: String?
    ) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateBookOpnameStatusByRfid(rfidTag, status, timestamp, actualLocation)
        }
    }

    suspend fun updateBookOpnameStatusByItemCode(
        itemCode: String,
        status: OpnameStatus?,
        timestamp: Long?,
        actualLocation: String?
    ) {
        withContext(Dispatchers.IO) {
            bookMasterDao.updateBookOpnameStatusByItemCode(itemCode, status, timestamp, actualLocation)
        }
    }

    // Fungsi baru untuk mendapatkan buku berdasarkan FilterCriteria
    fun getBooksByFilterCriteria(criteria: FilterCriteria): Flow<List<BookMaster>> {
        // Meneruskan panggilan ke DAO dengan parameter yang sudah disiapkan
        // Parameter string query akan menggunakan wildcard (%) dari DAO jika query LIKE digunakan
        return bookMasterDao.getFilteredBooks(
            statusFilter = criteria.opnameStatus,
            titleQuery = criteria.titleQuery,
            itemCodeQuery = criteria.itemCodeQuery,
            locationQuery = criteria.locationQuery,
            epcQuery = criteria.epcQuery,
            isNewOrUnexpectedFilter = criteria.isNewOrUnexpected
        )
        // Tidak perlu withContext(Dispatchers.IO) di sini karena DAO mengembalikan Flow,
        // dan Flow biasanya sudah berjalan di background thread yang ditentukan oleh Room.
    }

    fun getFilteredReportItemsFlow(reportId: Long, criteria: FilterCriteria): Flow<List<StockOpnameItem>> {
        // Konversi OpnameStatus Enum ke String jika FilterCriteria.opnameStatus digunakan
        // untuk memfilter StockOpnameItem.status (yang bertipe String).
        val statusQueryString: String? = criteria.opnameStatus?.name

        // Anda mungkin juga perlu menangani criteria.isNewOrUnexpected di sini jika
        // DAO Anda menerimanya sebagai Boolean tetapi Anda ingin logika yang lebih kompleks
        // berdasarkan status string "NEW_ITEM" atau "UNKNOWN_ITEM_IN_REPORT_STATUS".
        // Untuk saat ini, kita akan meneruskannya langsung jika DAO Anda sudah menanganinya.

        return stockOpnameItemDao.getFilteredReportItemsFlow(
            reportId = reportId,
            statusQuery = statusQueryString, // Menggunakan hasil konversi di atas
            titleQuery = criteria.titleQuery,
            itemCodeQuery = criteria.itemCodeQuery,
            locationQuery = criteria.locationQuery,
            epcQuery = criteria.epcQuery,
            isNewOrUnexpectedFilter = criteria.isNewOrUnexpected // Teruskan langsung
        )
    }

    // Fungsi baru untuk mendapatkan total buku di master (digunakan di report saving)
    suspend fun getTotalBookCountInMaster(): Int {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getTotalBookCount() // Anda perlu menambahkan fungsi ini di BookMasterDao
        }
    }


    // Fungsi baru untuk mencari item baru/tak terduga berdasarkan identifier (digunakan di ViewModel saat scan)
    suspend fun findNewOrUnexpectedItemByIdentifier(identifier: String, method: ScanMethod): BookMaster? {
        return withContext(Dispatchers.IO) {
            when (method) {
                ScanMethod.UHF -> bookMasterDao.findNewOrUnexpectedByEpc(identifier)
                ScanMethod.BARCODE -> bookMasterDao.findNewOrUnexpectedByItemCode(identifier)
                // Anda perlu menambahkan fungsi findNewOrUnexpectedByEpc dan findNewOrUnexpectedByItemCode di BookMasterDao
            }
        }
    }


    // Fungsi baru yang lebih komprehensif untuk mereset status buku dan data sesi
    // (dipanggil dari ViewModel saat startNewOpnameSession)
    @Transaction
    suspend fun resetAllBookOpnameStatusesAndClearSessionData() {
        withContext(Dispatchers.IO) {
            // 1. Reset status semua buku di BookMaster ke NOT_SCANNED dan isNewOrUnexpected ke false
            bookMasterDao.resetAllBookOpnameStatusForNewSession(OpnameStatus.NOT_SCANNED, false)
            // 2. Hapus temporary data atau data sesi lama jika ada (misalnya, jika Anda punya tabel sesi terpisah)
            // Untuk saat ini, kita hanya fokus pada reset status BookMaster.
            // Jika Anda memiliki tabel lain untuk item sesi yang tidak permanen, hapus di sini.
        }
    }


    fun getUntaggedBooksFlow(): Flow<List<BookMaster>> {
        return bookMasterDao.getUntaggedBooksFlow()
    }

    fun getBooksByPairingStatusFlow(status: PairingStatus): Flow<List<BookMaster>> {
        return bookMasterDao.getBooksByPairingStatusFlow(status)
    }

    suspend fun getBooksByPairingStatusList(status: PairingStatus): List<BookMaster> {
        return withContext(Dispatchers.IO) {
            bookMasterDao.getBooksByPairingStatusList(status)
        }
    }

    // Fungsi resetAllBookOpnameStatusForNewSession LAMA bisa dihapus atau diganti dengan yang baru di atas
    /*
    suspend fun resetAllBookOpnameStatusForNewSession() {
        withContext(Dispatchers.IO) {
            bookMasterDao.resetAllBookOpnameStatusForNewSession()
        }
    }
    */

    suspend fun clearAllBookMasters() {
        withContext(Dispatchers.IO) {
            bookMasterDao.clearAllBooks()
        }
    }

    suspend fun updateBookMaster(bookMaster: BookMaster): Int {
        return withContext(Dispatchers.IO) {
            bookMasterDao.updateBook(bookMaster)
        }
    }

    suspend fun deleteBookMaster(bookMaster: BookMaster): Int {
        return withContext(Dispatchers.IO) {
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

    @Transaction
    suspend fun insertStockOpnameReportWithItems(report: StockOpnameReport, items: List<StockOpnameItem>): Long {
        return withContext(Dispatchers.IO) {
            val newReportId = stockOpnameReportDao.insertReport(report) // <-- dipastikan return Long
            if (newReportId > 0) {
                val itemsWithReportId = items.map { item ->
                    item.copy(reportId = newReportId)
                }
                stockOpnameItemDao.insertAllItems(itemsWithReportId) // <-- return Unit, ini OK
            }
            newReportId // <-- Ini yang di-return dari blok withContext, tipenya Long
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

    suspend fun insertOrUpdateStockOpnameItem(item: StockOpnameItem) { // Tipe kembalian diubah menjadi Unit (implisit)
        withContext(Dispatchers.IO) {
            stockOpnameItemDao.insertOrUpdateItem(item) // DAO mengembalikan Unit
        }
    }

    // --- Combined Operations ---
    // Fungsi saveFullStockOpnameSession LAMA bisa dihapus jika insertStockOpnameReportWithItems
    // sudah mencakup fungsionalitasnya atau jika Anda mengandalkan logika di ViewModel untuk save.
    /*
    @Transaction
    suspend fun saveFullStockOpnameSession(
        reportDetails: StockOpnameReport,
        itemsInSession: List<StockOpnameItem>
    ): Long {
        return withContext(Dispatchers.IO) {
            val newReportId = stockOpnameReportDao.insertReport(reportDetails)
            if (newReportId > 0) {
                val itemsWithReportId = itemsInSession.map { item ->
                    if (item.reportId == 0L) item.copy(reportId = newReportId) else item
                }
                stockOpnameItemDao.insertAllItems(itemsWithReportId)
            }
            newReportId
        }
    }
    */
}

