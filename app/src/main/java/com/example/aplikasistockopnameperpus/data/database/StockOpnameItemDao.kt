package com.example.aplikasistockopnameperpus.data.database

import androidx.room.*
import com.example.aplikasistockopnameperpus.data.model.ReportItemData // PASTIKAN IMPORT DTO ANDA
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
                    (isNewOrUnexpectedItem = :isNewOrUnexpectedFilter) -- Perbaikan untuk boolean
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


    // Di dalam interface StockOpnameItemDao
    @Transaction
    @Query("SELECT " +
            "soi.reportId AS soi_reportId, " +
            "soi.rfidTagHexScanned AS soi_rfidTagHexScanned, " +
            "soi.tidScanned AS soi_tidScanned, " +
            "soi.itemCodeMaster AS soi_itemCodeMaster, " +
            "soi.titleMaster AS soi_titleMaster, " +
            "soi.scanTimestamp AS soi_scanTimestamp, " +
            "soi.status AS soi_status, " +
            "soi.actualLocationIfDifferent AS soi_actualLocationIfDifferent, " +
            "soi.expectedLocationMaster AS soi_expectedLocationMaster, " +
            "soi.isNewOrUnexpectedItem AS soi_isNewOrUnexpectedItem, " +
            "bm.id AS bm_id, " +
            "bm.itemCode AS bm_itemCode, " +
            "bm.title AS bm_title, " +
            "bm.callNumber AS bm_callNumber, " +
            "bm.collectionType AS bm_collectionType, " +
            "bm.inventoryCode AS bm_inventoryCode, " +
            "bm.receivedDate AS bm_receivedDate, " +
            "bm.locationName AS bm_locationName, " +
            "bm.orderDate AS bm_orderDate, " +
            "bm.slimsItemStatus AS bm_slimsItemStatus, " +
            "bm.siteName AS bm_siteName, " +
            "bm.source AS bm_source, " +
            "bm.price AS bm_price, " +
            "bm.priceCurrency AS bm_priceCurrency, " +
            "bm.invoiceDate AS bm_invoiceDate, " +
            "bm.inputDate AS bm_inputDate, " +
            "bm.lastUpdate AS bm_lastUpdate, " +
            "bm.rfidTagHex AS bm_rfidTagHex, " +
            "bm.tid AS bm_tid, " +
            "bm.importTimestamp AS bm_importTimestamp, " +
            "bm.pairingTimestamp AS bm_pairingTimestamp, " +
            "bm.pairingStatus AS bm_pairingStatus, " +
            "bm.lastSeenTimestamp AS bm_lastSeenTimestamp, " +
            "bm.actualScannedLocation AS bm_actualScannedLocation, " +
            "bm.opnameStatus AS bm_opnameStatus, " +
            "bm.isNewOrUnexpected AS bm_isNewOrUnexpected " +
            "FROM stock_opname_items soi " +
            "LEFT JOIN book_master bm ON soi.itemCodeMaster = bm.itemCode " +
            "WHERE soi.reportId = :reportId " + // Filter utama berdasarkan reportId
            // --- AWAL PENAMBAHAN FILTER ---
            "AND (:statusQuery IS NULL OR soi.status LIKE '%' || :statusQuery || '%') " +                 // Filter status dari StockOpnameItem
            "AND (:titleQuery IS NULL OR bm.title LIKE '%' || :titleQuery || '%') " +                     // Filter judul dari BookMaster
            "AND (:itemCodeQuery IS NULL OR bm.itemCode LIKE '%' || :itemCodeQuery || '%') " +             // Filter kode item dari BookMaster
            "AND (:locationQuery IS NULL OR (bm.locationName LIKE '%' || :locationQuery || '%' OR soi.actualLocationIfDifferent LIKE '%' || :locationQuery || '%')) " + // Filter lokasi dari BookMaster atau lokasi aktual StockOpnameItem
            "AND (:epcQuery IS NULL OR soi.rfidTagHexScanned LIKE '%' || :epcQuery || '%') " +             // Filter EPC dari StockOpnameItem
            "AND (:isNewOrUnexpectedFilter IS NULL OR soi.isNewOrUnexpectedItem = :isNewOrUnexpectedFilter) " + // Filter item baru/tak terduga dari StockOpnameItem
            "AND (:scanStartDateMillis IS NULL OR soi.scanTimestamp >= :scanStartDateMillis) " +
            "AND (:scanEndDateMillis IS NULL OR soi.scanTimestamp <= :scanEndDateMillis) " +
            // --- AKHIR PENAMBAHAN FILTER ---
            "ORDER BY soi_scanTimestamp DESC")
    fun getFilteredReportDataWithDetailsFlow(
        reportId: Long,
        statusQuery: String?,
        titleQuery: String?,
        itemCodeQuery: String?,
        locationQuery: String?,
        epcQuery: String?,
        isNewOrUnexpectedFilter: Boolean?,
        scanStartDateMillis: Long?,
        scanEndDateMillis: Long?
    ): Flow<List<ReportItemData>>
}

