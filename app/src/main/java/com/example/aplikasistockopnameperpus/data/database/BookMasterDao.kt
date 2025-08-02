package com.example.aplikasistockopnameperpus.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookMasterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookMaster>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBook(book: BookMaster): Long

    @Query("SELECT * FROM book_master ORDER BY title ASC")
    fun getAllBooksFlow(): Flow<List<BookMaster>>

    @Query("SELECT * FROM book_master")
    suspend fun getAllBooksList(): List<BookMaster>

    @Query("SELECT * FROM book_master WHERE rfidTagHex = :rfidTagHex AND rfidTagHex IS NOT NULL LIMIT 1") // Pastikan tidak null
    suspend fun getBookByRfidTag(rfidTagHex: String): BookMaster?

    @Query("SELECT * FROM book_master WHERE itemCode = :itemCode LIMIT 1")
    suspend fun getBookByItemCode(itemCode: String): BookMaster?

    // Fungsi untuk update detail RFID setelah pairing berhasil
    @Query("UPDATE book_master SET rfidTagHex = :newRfidTagHex, tid = :newTid, rfidPairingStatus = :newPairingStatus, rfidPairingTimestamp = :pairingTimestamp WHERE itemCode = :itemCode")
    suspend fun updateRfidDetailsByItemCode(
        itemCode: String,
        newRfidTagHex: String?, // Sesuaikan nullability berdasarkan apakah ini wajib setelah pairing
        newTid: String?,
        newPairingStatus: String,
        pairingTimestamp: Long? // Jadikan nullable jika bisa tidak diset
    )

    // Fungsi untuk update status scan saat stock opname (bisa via rfidTagHex atau itemCode)
    @Query("UPDATE book_master SET scanStatus = :status, lastSeenTimestamp = :timestamp, actualScannedLocation = :actualLocation WHERE rfidTagHex = :rfidTagHex AND rfidTagHex IS NOT NULL")
    suspend fun updateBookScanStatusByRfid(rfidTagHex: String, status: String?, timestamp: Long?, actualLocation: String?)

    @Query("UPDATE book_master SET scanStatus = :status, lastSeenTimestamp = :timestamp, actualScannedLocation = :actualLocation WHERE itemCode = :itemCode")
    suspend fun updateBookScanStatusByItemCode(itemCode: String, status: String?, timestamp: Long?, actualLocation: String?)


    // Fungsi untuk mendapatkan buku berdasarkan status pairing RFID
    @Query("SELECT * FROM book_master WHERE rfidPairingStatus = :status ORDER BY title ASC")
    fun getBooksByRfidPairingStatusFlow(status: String): Flow<List<BookMaster>>

    @Query("SELECT * FROM book_master WHERE rfidPairingStatus = :status ORDER BY title ASC")
    suspend fun getBooksByRfidPairingStatusList(status: String): List<BookMaster> // Perbaikan di sini

    @Query("SELECT * FROM book_master WHERE rfidPairingStatus = 'BELUM_DITAG' OR rfidPairingStatus IS NULL ORDER BY title ASC")
    fun getUntaggedBooksFlow(): Flow<List<BookMaster>>

    // Reset status scan untuk sesi opname baru
    @Query("UPDATE book_master SET scanStatus = NULL, lastSeenTimestamp = NULL, actualScannedLocation = NULL")
    suspend fun resetAllBookScanStatusForNewSession() // Nama lebih deskriptif

    @Query("DELETE FROM book_master")
    suspend fun clearAllBooks()

    @Update
    suspend fun updateBook(book: BookMaster) // Berguna untuk update fleksibel

    @Delete
    suspend fun deleteBook(book: BookMaster)
}
