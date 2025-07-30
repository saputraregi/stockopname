package com.example.aplikasistockopnameperpus.viewmodel // Sesuaikan package jika perlu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.example.aplikasistockopnameperpus.data.database.AppDatabase // Pastikan ini adalah kelas Database Anda
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import com.example.aplikasistockopnameperpus.data.repository.BookRepository

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val bookRepository: BookRepository

    // Menggunakan Flow yang dikonversi ke LiveData untuk observasi di Activity
    val allStockOpnameReports: LiveData<List<StockOpnameReport>>

    init {
        // Mendapatkan instance DAO dari AppDatabase
        val bookMasterDao = AppDatabase.getDatabase(application).bookMasterDao()
        val stockOpnameReportDao = AppDatabase.getDatabase(application).stockOpnameReportDao() // DAO yang kita butuhkan
        val stockOpnameItemDao = AppDatabase.getDatabase(application).stockOpnameItemDao()

        // Inisialisasi BookRepository dengan semua DAO yang diperlukan
        bookRepository = BookRepository(bookMasterDao, stockOpnameReportDao, stockOpnameItemDao)

        // Mengambil Flow dari repository
        allStockOpnameReports = bookRepository.getAllStockOpnameReportsFlow().asLiveData() // Menggunakan nama fungsi yang diperbarui
    }

    // Jika Anda perlu mendapatkan detail satu report (misalnya untuk halaman detail)
    // Fungsi ini bisa mengembalikan LiveData<StockOpnameReport?> atau Anda bisa memanggilnya
    // dari coroutine di Activity/Fragment saat dibutuhkan.
    // Untuk kesederhanaan, kita bisa buat fungsi suspend di ViewModel yang dipanggil dari coroutine UI.
    // Atau, jika Anda ingin observasi langsung, DAO harus menyediakan Flow.

    // Contoh mendapatkan report by ID (jika dibutuhkan nanti untuk detail)
    // suspend fun getReportDetails(reportId: Long): StockOpnameReport? {
    //     return bookRepository.getStockOpnameReportById(reportId)
    // }

    // TODO: Tambahkan fungsi untuk filter, urut, atau ekspor ulang jika diperlukan
    // Misalnya:
    // fun applyFilter(filterCriteria: ...) {
    //     allStockOpnameReports = bookRepository.getFilteredReportsFlow(filterCriteria).asLiveData()
    // }
}

