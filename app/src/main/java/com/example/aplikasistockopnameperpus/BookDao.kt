package com.example.aplikasistockopnameperpus // Ganti dengan package Anda

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aplikasistockopnameperpus.model.Book

@Dao // Menandakan bahwa ini adalah Data Access Object untuk Room
interface BookDao {

    // Metode untuk menyisipkan satu buku
    // OnConflictStrategy.REPLACE: Jika buku dengan itemCode yang sama sudah ada,
    // data lama akan diganti dengan data baru.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book) // 'suspend' agar bisa dipanggil dari coroutine

    // Metode untuk menyisipkan daftar buku (misalnya, saat impor data)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBooks(books: List<Book>)

    // Metode untuk memperbarui data buku yang sudah ada
    @Update
    suspend fun updateBook(book: Book)

    // Metode untuk menghapus satu buku
    @Delete
    suspend fun deleteBook(book: Book)

    // Metode untuk menghapus semua buku dari tabel (misalnya, sebelum impor baru)
    @Query("DELETE FROM tabel_buku")
    suspend fun deleteAllBooks()

    // Metode untuk mendapatkan satu buku berdasarkan itemCode-nya
    // Mengembalikan objek Book yang bisa null jika tidak ditemukan
    @Query("SELECT * FROM tabel_buku WHERE itemCode = :itemCode LIMIT 1")
    fun getBookByItemCode(itemCode: String): Book? // Tidak perlu suspend jika tidak mengembalikan Flow/LiveData dan cepat

    // Metode untuk mendapatkan semua buku, diurutkan berdasarkan itemCode
    // Mengembalikan LiveData<List<Book>> agar UI bisa observe perubahan secara otomatis
    @Query("SELECT * FROM tabel_buku ORDER BY itemCode ASC")
    fun getAllBooks(): LiveData<List<Book>>

    // Metode untuk memperbarui status 'foundStatus' untuk buku tertentu
    @Query("UPDATE tabel_buku SET foundStatus = :status WHERE itemCode = :itemCode")
    suspend fun updateFoundStatus(itemCode: String, status: Boolean)

    // Metode untuk menghitung jumlah buku yang ditemukan
    @Query("SELECT COUNT(*) FROM tabel_buku WHERE foundStatus = 1")
    fun getFoundBooksCount(): LiveData<Int>

    // Metode untuk menghitung jumlah buku yang belum ditemukan
    @Query("SELECT COUNT(*) FROM tabel_buku WHERE foundStatus = 0")
    fun getNotFoundBooksCount(): LiveData<Int>

    // Metode untuk mendapatkan semua buku yang statusnya ditemukan (misalnya untuk ekspor)
    // Ini mungkin tidak perlu LiveData jika hanya dipanggil sekali saat ekspor di background thread
    @Query("SELECT * FROM tabel_buku WHERE foundStatus = 1")
    suspend fun getAllFoundBooksList(): List<Book>
}
