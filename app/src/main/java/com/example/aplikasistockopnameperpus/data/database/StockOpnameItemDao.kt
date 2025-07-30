package com.example.aplikasistockopnameperpus.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StockOpnameItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(items: List<StockOpnameItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateItem(item: StockOpnameItem)

    @Query("SELECT * FROM stock_opname_items WHERE reportId = :reportId")
    fun getItemsForReportFlow(reportId: Long): Flow<List<StockOpnameItem>>

    @Query("SELECT * FROM stock_opname_items WHERE reportId = :reportId")
    suspend fun getItemsForReportList(reportId: Long): List<StockOpnameItem>
}
