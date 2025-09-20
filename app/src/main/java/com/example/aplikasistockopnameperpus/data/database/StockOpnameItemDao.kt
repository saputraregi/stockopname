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

    @Query("""
            SELECT * FROM stock_opname_items
            WHERE reportId = :reportId AND
                (:statusQuery IS NULL OR status LIKE '%' || :statusQuery || '%') AND 
                (:titleQuery IS NULL OR titleMaster LIKE '%' || :titleQuery || '%') AND
                (:itemCodeQuery IS NULL OR itemCodeMaster LIKE '%' || :itemCodeQuery || '%') AND
                (:locationQuery IS NULL OR (expectedLocationMaster LIKE '%' || :locationQuery || '%' OR actualLocationIfDifferent LIKE '%' || :locationQuery || '%')) AND
                (:epcQuery IS NULL OR rfidTagHexScanned LIKE '%' || :epcQuery || '%') AND
                (:isNewOrUnexpectedFilter IS NULL OR 
                    ( (:isNewOrUnexpectedFilter = 1 AND (status = 'NEW_ITEM' OR status = 'UNKNOWN_ITEM_IN_REPORT_STATUS')) OR
                      (:isNewOrUnexpectedFilter = 0 AND (status != 'NEW_ITEM' AND status != 'UNKNOWN_ITEM_IN_REPORT_STATUS')) )
                )
            ORDER BY titleMaster ASC
        """)
    fun getFilteredReportItemsFlow(
        reportId: Long,
        statusQuery: String?,
        titleQuery: String?,
        itemCodeQuery: String?,
        locationQuery: String?,
        epcQuery: String?,
        isNewOrUnexpectedFilter: Boolean?
    ): Flow<List<StockOpnameItem>>
}
