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

    @Query("""
        SELECT * FROM stock_opname_reports
        WHERE
        (:startDateMillis IS NULL OR startTimeMillis >= :startDateMillis) AND
        (:endDateMillis IS NULL OR startTimeMillis <= :endDateMillis) AND
        (:nameQuery IS NULL OR reportName LIKE '%' || :nameQuery || '%')
        ORDER BY
            CASE :sortOrderName WHEN 'DATE_DESC' THEN reportDate END DESC,
            CASE :sortOrderName WHEN 'DATE_ASC' THEN reportDate END ASC,
            CASE :sortOrderName WHEN 'NAME_ASC' THEN reportName END ASC,
            CASE :sortOrderName WHEN 'NAME_DESC' THEN reportName END DESC
     """)
    fun getFilteredAndSortedReports(
        startDateMillis: Long?,
        endDateMillis: Long?,
        nameQuery: String?,
        sortOrderName: String // Nama dari enum ReportSortOrder, misal "DATE_DESC"
    ): Flow<List<StockOpnameReport>>
}