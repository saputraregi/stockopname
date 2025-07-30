package com.example.aplikasistockopnameperpus.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StockOpnameReportDao {
    @Insert
    suspend fun insertReport(report: StockOpnameReport): Long // Mengembalikan ID report baru

    @Update
    suspend fun updateReport(report: StockOpnameReport)

    @Query("SELECT * FROM stock_opname_reports ORDER BY startTimeMillis DESC")
    fun getAllReportsFlow(): Flow<List<StockOpnameReport>>

    @Query("SELECT * FROM stock_opname_reports WHERE reportId = :reportId LIMIT 1")
    suspend fun getReportById(reportId: Long): StockOpnameReport?

    @Query("SELECT * FROM stock_opname_reports ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getLatestReport(): StockOpnameReport?
}