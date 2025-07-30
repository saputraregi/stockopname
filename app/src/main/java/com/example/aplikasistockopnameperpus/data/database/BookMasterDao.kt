package com.example.aplikasistockopnameperpus.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookMasterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) // Ganti jika ada duplikat berdasarkan PrimaryKey atau Unique Index
    suspend fun insertAll(books: List<BookMaster>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBook(book: BookMaster): Long // Mengembalikan ID atau jumlah baris yang terpengaruh

    @Query("SELECT * FROM book_master ORDER BY title ASC")
    fun getAllBooksFlow(): Flow<List<BookMaster>> // Untuk observasi data secara reaktif

    @Query("SELECT * FROM book_master")
    suspend fun getAllBooksList(): List<BookMaster> // Untuk operasi sekali ambil

    @Query("SELECT * FROM book_master WHERE rfidTagHex = :rfidTagHex LIMIT 1")
    suspend fun getBookByRfidTag(rfidTagHex: String): BookMaster?

    @Query("SELECT * FROM book_master WHERE itemCode = :itemCode LIMIT 1")
    suspend fun getBookByItemCode(itemCode: String): BookMaster?

    @Query("UPDATE book_master SET scanStatus = :status, lastSeenTimestamp = :timestamp WHERE rfidTagHex = :rfidTagHex")
    suspend fun updateBookScanStatus(rfidTagHex: String, status: String, timestamp: Long)

    @Query("UPDATE book_master SET scanStatus = NULL, lastSeenTimestamp = NULL") // Reset status untuk opname baru
    suspend fun resetAllBookScanStatus()

    @Query("DELETE FROM book_master")
    suspend fun clearAllBooks()

    @Update
    suspend fun updateBook(book: BookMaster)

    @Delete
    suspend fun deleteBook(book: BookMaster)
}
