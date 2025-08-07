package com.example.aplikasistockopnameperpus.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookMasterDao {

    @Transaction // Penting untuk operasi yang melibatkan beberapa tabel
    suspend fun insertStockOpnameReportWithItems(report: StockOpnameReport, items: List<StockOpnameItem>): Long {
        val reportId = insertStockOpnameReport(report) // 1. Insert report, dapatkan ID-nya
        items.forEach { stockOpnameItem ->
            stockOpnameItem.reportId = reportId // 2. Set reportId untuk setiap item
        }
        insertStockOpnameItems(items)
        return reportId
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockOpnameReport(report: StockOpnameReport): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockOpnameItems(items: List<StockOpnameItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookMaster>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBook(book: BookMaster): Long

    @Query("SELECT * FROM book_master ORDER BY title ASC")
    fun getAllBooksFlow(): Flow<List<BookMaster>>

    @Query("SELECT * FROM book_master")
    suspend fun getAllBooksList(): List<BookMaster>

    // Tambahan: Dapatkan buku berdasarkan ID primary key
    @Query("SELECT * FROM book_master WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Long): BookMaster?

    @Query("SELECT * FROM book_master WHERE rfidTagHex = :rfidTagHex AND rfidTagHex IS NOT NULL LIMIT 1")
    suspend fun getBookByRfidTag(rfidTagHex: String): BookMaster?

    @Query("SELECT * FROM book_master WHERE itemCode = :itemCode LIMIT 1")
    suspend fun getBookByItemCode(itemCode: String): BookMaster?

    // Fungsi untuk update detail RFID setelah pairing berhasil/gagal
    // Menggunakan Enum PairingStatus sebagai tipe parameter
    @Query("UPDATE book_master SET tid = :newTid, pairingStatus = :newPairingStatus, pairingTimestamp = :pairingTimestamp WHERE itemCode = :itemCode")
    suspend fun updatePairingDetailsByItemCode(
        itemCode: String,
        newTid: String?,
        newPairingStatus: PairingStatus, // Menggunakan Enum
        pairingTimestamp: Long?
    )

    // Overload jika Anda juga perlu update rfidTagHex saat pairing (meski skenario awal tidak)
    @Query("UPDATE book_master SET rfidTagHex = :newRfidTagHex, tid = :newTid, pairingStatus = :newPairingStatus, pairingTimestamp = :pairingTimestamp WHERE itemCode = :itemCode")
    suspend fun updateFullRfidDetailsByItemCode(
        itemCode: String,
        newRfidTagHex: String?,
        newTid: String?,
        newPairingStatus: PairingStatus, // Menggunakan Enum
        pairingTimestamp: Long?
    )


    // Fungsi untuk update status scan saat stock opname (bisa via rfidTagHex atau itemCode)
    // Menggunakan Enum OpnameStatus sebagai tipe parameter
    @Query("UPDATE book_master SET opnameStatus = :status, lastSeenTimestamp = :timestamp, actualScannedLocation = :actualLocation WHERE rfidTagHex = :rfidTagHex AND rfidTagHex IS NOT NULL")
    suspend fun updateBookOpnameStatusByRfid(
        rfidTagHex: String,
        status: OpnameStatus?, // Menggunakan Enum
        timestamp: Long?,
        actualLocation: String?
    )

    @Query("UPDATE book_master SET opnameStatus = :status, lastSeenTimestamp = :timestamp, actualScannedLocation = :actualLocation WHERE itemCode = :itemCode")
    suspend fun updateBookOpnameStatusByItemCode(
        itemCode: String,
        status: OpnameStatus?, // Menggunakan Enum
        timestamp: Long?,
        actualLocation: String?
    )

    // Di BookMasterDao.kt
    @Query("UPDATE book_master SET opnameStatus = :newStatus, lastSeenTimestamp = :timestamp, actualScannedLocation = :actualLocation WHERE id IN (:bookIds)")
    suspend fun updateOpnameStatusForBookIds(bookIds: List<Long>, newStatus: OpnameStatus, timestamp: Long?, actualLocation: String?) // Tambahkan actualLocation jika perlu

    // Fungsi untuk mendapatkan buku berdasarkan status pairing RFID
    // Menggunakan Enum PairingStatus sebagai tipe parameter
    @Query("SELECT * FROM book_master WHERE pairingStatus = :status ORDER BY title ASC")
    fun getBooksByPairingStatusFlow(status: PairingStatus): Flow<List<BookMaster>> // Menggunakan Enum

    @Query("SELECT * FROM book_master WHERE pairingStatus = :status ORDER BY title ASC")
    suspend fun getBooksByPairingStatusList(status: PairingStatus): List<BookMaster> // Menggunakan Enum

    // Menggunakan Enum PairingStatus untuk NOT_PAIRED
    @Query("SELECT * FROM book_master WHERE pairingStatus = :notPairedStatus OR pairingStatus IS NULL ORDER BY title ASC")
    fun getUntaggedBooksFlow(notPairedStatus: PairingStatus = PairingStatus.NOT_PAIRED): Flow<List<BookMaster>>

    // Reset status scan untuk sesi opname baru
    // Menggunakan Enum OpnameStatus untuk NOT_SCANNED
    @Query("UPDATE book_master SET opnameStatus = :defaultOpnameStatus, lastSeenTimestamp = NULL, actualScannedLocation = NULL, isNewOrUnexpected = :defaultIsNew")
    suspend fun resetAllBookOpnameStatusForNewSession(
        defaultOpnameStatus: OpnameStatus = OpnameStatus.NOT_SCANNED, // Menggunakan Enum
        defaultIsNew: Boolean = false
    )

    @Query("DELETE FROM book_master")
    suspend fun clearAllBooks()

    @Update
    suspend fun updateBook(book: BookMaster): Int // Kembalikan Int (jumlah baris diupdate) agar lebih informatif

    @Delete
    suspend fun deleteBook(book: BookMaster): Int // Kembalikan Int (jumlah baris dihapus)
}
