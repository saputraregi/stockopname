package com.example.aplikasistockopnameperpus.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_opname_reports")
data class StockOpnameReport(
    @PrimaryKey(autoGenerate = true)
    val reportId: Long = 0,
    val reportName: String, // Misal: "Opname Bulanan Gudang A"
    val startTimeMillis: Long = System.currentTimeMillis(),
    var endTimeMillis: Long? = null,
    var totalItemsExpected: Int = 0,
    var totalItemsFound: Int = 0,
    var totalItemsMissing: Int = 0,
    var totalItemsNewOrUnexpected: Int = 0 // Untuk tag yang tidak ada di master
)