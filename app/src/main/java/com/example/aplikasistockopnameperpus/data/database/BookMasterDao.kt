package com.example.aplikasistockopnameperpus.data.database

import androidx.room.*
import com.example.aplikasistockopnameperpus.data.model.ReportItemData
import com.example.aplikasistockopnameperpus.model.FilterCriteria
import kotlinx.coroutines.flow.Flow

@Dao
interface BookMasterDao {

    // Fungsi untuk filter lanjutan
    @Query("""
        SELECT * FROM book_master
        WHERE
            (:statusFilter IS NULL OR opnameStatus = :statusFilter) AND
            (:titleQuery IS NULL OR title LIKE '%' || :titleQuery || '%') AND
            (:itemCodeQuery IS NULL OR itemCode LIKE '%' || :itemCodeQuery || '%') AND
            (:locationQuery IS NULL OR locationName LIKE '%' || :locationQuery || '%') AND
            (:epcQuery IS NULL OR rfidTagHex LIKE '%' || :epcQuery || '%') AND
            (:isNewOrUnexpectedFilter IS NULL OR isNewOrUnexpected = :isNewOrUnexpectedFilter)
        ORDER BY itemCode ASC
    """)
    fun getFilteredBooks(
        statusFilter: OpnameStatus?,
        titleQuery: String?,
        itemCodeQuery: String?,
        locationQuery: String?,
        epcQuery: String?,
        isNewOrUnexpectedFilter: Boolean? // Tambahkan parameter ini
    ): Flow<List<BookMaster>>

    // Fungsi untuk mendapatkan total buku di master (tidak termasuk yang baru/tak terduga secara default)
    @Query("SELECT COUNT(*) FROM book_master WHERE isNewOrUnexpected = 0") // Asumsi item 'normal' punya isNewOrUnexpected = 0 atau false
    suspend fun getTotalBookCount(): Int

    @Query("SELECT * FROM book_master WHERE id IN (:ids)")
    suspend fun getBooksByIdsList(ids: List<Long>): List<BookMaster>

    // Fungsi untuk mencari item baru/tak terduga berdasarkan EPC
    @Query("SELECT * FROM book_master WHERE rfidTagHex = :epc AND isNewOrUnexpected = 1 LIMIT 1")
    suspend fun findNewOrUnexpectedByEpc(epc: String): BookMaster?

    // Fungsi untuk mencari item baru/tak terduga berdasarkan Item Code
    @Query("SELECT * FROM book_master WHERE itemCode = :itemCode AND isNewOrUnexpected = 1 LIMIT 1")
    suspend fun findNewOrUnexpectedByItemCode(itemCode: String): BookMaster?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookMaster>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBook(book: BookMaster): Long

    @Query("SELECT * FROM book_master ORDER BY title ASC")
    fun getAllBooksFlow(): Flow<List<BookMaster>>

    @Query("SELECT * FROM book_master")
    suspend fun getAllBooksList(): List<BookMaster>

    @Query("SELECT * FROM book_master WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Long): BookMaster?

    @Query("SELECT * FROM book_master WHERE rfidTagHex = :rfidTagHex AND rfidTagHex IS NOT NULL LIMIT 1")
    suspend fun getBookByRfidTag(rfidTagHex: String): BookMaster?

    @Query("SELECT * FROM book_master WHERE itemCode = :itemCode LIMIT 1")
    suspend fun getBookByItemCode(itemCode: String): BookMaster?

    @Query("UPDATE book_master SET tid = :newTid, pairingStatus = :newPairingStatus, pairingTimestamp = :pairingTimestamp WHERE itemCode = :itemCode")
    suspend fun updatePairingDetailsByItemCode(
        itemCode: String,
        newTid: String?,
        newPairingStatus: PairingStatus,
        pairingTimestamp: Long?
    )

    @Query("UPDATE book_master SET rfidTagHex = :newRfidTagHex, tid = :newTid, pairingStatus = :newPairingStatus, pairingTimestamp = :pairingTimestamp WHERE itemCode = :itemCode")
    suspend fun updateFullRfidDetailsByItemCode(
        itemCode: String,
        newRfidTagHex: String?,
        newTid: String?,
        newPairingStatus: PairingStatus,
        pairingTimestamp: Long?
    )

    @Query("UPDATE book_master SET opnameStatus = :status, lastSeenTimestamp = :timestamp, actualScannedLocation = :actualLocation WHERE rfidTagHex = :rfidTagHex AND rfidTagHex IS NOT NULL")
    suspend fun updateBookOpnameStatusByRfid(
        rfidTagHex: String,
        status: OpnameStatus?,
        timestamp: Long?,
        actualLocation: String?
    )

    @Query("UPDATE book_master SET opnameStatus = :status, lastSeenTimestamp = :timestamp, actualScannedLocation = :actualLocation WHERE itemCode = :itemCode")
    suspend fun updateBookOpnameStatusByItemCode(
        itemCode: String,
        status: OpnameStatus?,
        timestamp: Long?,
        actualLocation: String?
    )

    @Query("UPDATE book_master SET opnameStatus = :newStatus, lastSeenTimestamp = :timestamp, actualScannedLocation = :actualLocation WHERE id IN (:bookIds)")
    suspend fun updateOpnameStatusForBookIds(bookIds: List<Long>, newStatus: OpnameStatus, timestamp: Long?, actualLocation: String?)

    @Query("SELECT * FROM book_master WHERE pairingStatus = :status ORDER BY title ASC")
    fun getBooksByPairingStatusFlow(status: PairingStatus): Flow<List<BookMaster>>

    @Query("SELECT * FROM book_master WHERE pairingStatus = :status ORDER BY title ASC")
    suspend fun getBooksByPairingStatusList(status: PairingStatus): List<BookMaster>

    @Query("SELECT * FROM book_master WHERE pairingStatus = :notPairedStatus OR pairingStatus IS NULL ORDER BY title ASC")
    fun getUntaggedBooksFlow(notPairedStatus: PairingStatus = PairingStatus.NOT_PAIRED): Flow<List<BookMaster>>

    // Fungsi resetAllBookOpnameStatusForNewSession sudah baik dan menerima parameter defaultIsNew.
    // Pastikan ini yang dipanggil oleh repository Anda.
    @Query("UPDATE book_master SET opnameStatus = :defaultOpnameStatus, lastSeenTimestamp = NULL, actualScannedLocation = NULL, isNewOrUnexpected = :defaultIsNew")
    suspend fun resetAllBookOpnameStatusForNewSession(
        defaultOpnameStatus: OpnameStatus = OpnameStatus.NOT_SCANNED,
        defaultIsNew: Boolean = false // Parameter ini penting untuk mereset flag isNewOrUnexpected
    )

    @Query("DELETE FROM book_master")
    suspend fun clearAllBooks()

    @Update
    suspend fun updateBook(book: BookMaster): Int

    @Delete
    suspend fun deleteBook(book: BookMaster): Int

}
