package com.example.aplikasistockopnameperpus.util.exporter

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport // Pastikan ini diimpor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TxtFileExporter(private val delimiter: String = ",") : FileExporter {

    companion object {
        private const val TAG = "TxtFileExporter"
        // Menggunakan java.util.Date untuk SimpleDateFormat
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // Helper untuk null-safe toString dan formatting
        private fun formatField(data: Any?): String {
            return when (data) {
                null -> ""
                is Long -> if (data > 0L) DATE_FORMATTER.format(Date(data)) else "" // Asumsi Long adalah timestamp
                is Date -> DATE_FORMATTER.format(data)
                is Enum<*> -> data.name // Untuk Enum seperti PairingStatus, ScanStatus
                else -> data.toString()
            }
        }
    }

    override suspend fun exportBooks(
        books: List<BookMaster>,
        outputStream: OutputStream,
        headers: List<String>
    ): ExportResult {
        return withContext(Dispatchers.IO) {
            if (books.isEmpty()) {
                Log.i(TAG, "No books data to export to TXT.")
                return@withContext ExportResult.NoDataToExport
            }
            try {
                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.append(headers.joinToString(delimiter))
                    writer.newLine()

                    books.forEach { book ->
                        val lineValues = headers.map { header ->
                            when (header.uppercase()) {
                                "ITEMCODE" -> formatField(book.itemCode)
                                "TITLE" -> formatField(book.title)
                                "CALLNUMBER" -> formatField(book.callNumber)
                                "RFIDTAGHEX" -> formatField(book.rfidTagHex)
                                "TID" -> formatField(book.tid)
                                "PAIRINGSTATUS" -> formatField(book.pairingStatus) // Asumsi Enum
                                "PAIRINGTIMESTAMP" -> formatField(book.pairingTimestamp)
                                "ACTUALLOCATION" -> formatField(book.locationName) // Sesuai CsvExporter Anda
                                "LASTSEENTIMESTAMP" -> formatField(book.lastSeenTimestamp)
                                // Tambahkan case lain dari BookMaster jika diperlukan dan ada di headers
                                // "AUTHOR" -> formatField(book.author)
                                // "PUBLISHER" -> formatField(book.publisher)
                                // "YEARPUBLISHED" -> formatField(book.yearPublished)
                                // "CATEGORY" -> formatField(book.category)
                                // "EXPECTEDLOCATION" -> formatField(book.expectedLocation)
                                // "SCANSTATUS" -> formatField(book.scanStatus) // Asumsi Enum
                                // "NOTES" -> formatField(book.notes)
                                else -> "" // Kolom kosong jika header tidak dikenal
                            }
                        }
                        writer.append(lineValues.joinToString(delimiter))
                        writer.newLine()
                    }
                    writer.flush()
                }
                ExportResult.Success("Books exported to TXT stream", books.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting books to TXT: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor data buku ke TXT: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting books to TXT: ${e.message}", e)
                ExportResult.Error("Terjadi kesalahan tak terduga saat mengekspor buku (TXT): ${e.message}")
            }
        }
    }

    override suspend fun exportOpnameItems(
        opnameItems: List<StockOpnameItem>,
        outputStream: OutputStream,
        headers: List<String>
    ): ExportResult {
        return withContext(Dispatchers.IO) {
            if (opnameItems.isEmpty()) {
                Log.i(TAG, "No opname items data to export to TXT.")
                return@withContext ExportResult.NoDataToExport
            }
            try {
                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.append(headers.joinToString(delimiter))
                    writer.newLine()

                    opnameItems.forEach { item ->
                        val lineValues = headers.map { header ->
                            when (header.uppercase()) {
                                "REPORT_ID" -> formatField(item.reportId)
                                "RFID_SCANNED" -> formatField(item.rfidTagHexScanned)
                                "TID_SCANNED" -> formatField(item.tidScanned)
                                "ITEMCODE_MASTER" -> formatField(item.itemCodeMaster)
                                "TITLE_MASTER" -> formatField(item.titleMaster)
                                "SCAN_TIMESTAMP" -> formatField(item.scanTimestamp)
                                "STATUS" -> formatField(item.status) // 'status' adalah String
                                "ACTUAL_LOCATION_IF_DIFFERENT" -> formatField(item.actualLocationIfDifferent)
                                else -> ""
                            }
                        }
                        writer.append(lineValues.joinToString(delimiter))
                        writer.newLine()
                    }
                    writer.flush()
                }
                ExportResult.Success("Opname items exported to TXT stream", opnameItems.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting opname items to TXT: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor item opname ke TXT: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting opname items to TXT: ${e.message}", e)
                ExportResult.Error("Terjadi kesalahan tak terduga saat mengekspor item opname (TXT): ${e.message}")
            }
        }
    }

    override suspend fun exportOpnameResults(
        opnameItems: List<StockOpnameItem>,
        report: StockOpnameReport?,
        outputStream: OutputStream,
        headers: List<String>
    ): ExportResult {
        return withContext(Dispatchers.IO) {
            if (opnameItems.isEmpty() && report == null) {
                Log.i(TAG, "No data (items or report) to export for opname results to TXT.")
                return@withContext ExportResult.NoDataToExport
            }
            if (headers.isEmpty()) {
                Log.w(TAG, "Headers are empty for exporting opname results to TXT. File might be malformed.")
            }

            try {
                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.append(headers.joinToString(delimiter))
                    writer.newLine()

                    if (opnameItems.isNotEmpty()) {
                        opnameItems.forEach { item ->
                            val lineValues = headers.map { header ->
                                when (header.uppercase()) {
                                    // Kolom dari StockOpnameReport
                                    "REPORT_ID" -> formatField(report?.reportId)
                                    "REPORT_NAME" -> formatField(report?.reportName)
                                    "REPORT_START_TIME" -> formatField(report?.startTimeMillis)
                                    "REPORT_END_TIME" -> formatField(report?.endTimeMillis)
                                    "TOTAL_ITEMS_EXPECTED" -> formatField(report?.totalItemsExpected)
                                    "TOTAL_ITEMS_FOUND" -> formatField(report?.totalItemsFound)
                                    "TOTAL_ITEMS_MISSING" -> formatField(report?.totalItemsMissing)
                                    "TOTAL_ITEMS_NEW_OR_UNEXPECTED" -> formatField(report?.totalItemsNewOrUnexpected)

                                    // Kolom dari StockOpnameItem
                                    "RFID_SCANNED" -> formatField(item.rfidTagHexScanned)
                                    "TID_SCANNED" -> formatField(item.tidScanned)
                                    "ITEMCODE_MASTER" -> formatField(item.itemCodeMaster)
                                    "TITLE_MASTER" -> formatField(item.titleMaster)
                                    "SCAN_TIMESTAMP" -> formatField(item.scanTimestamp)
                                    "STATUS" -> formatField(item.status) // 'status' adalah String
                                    "ACTUAL_LOCATION_IF_DIFFERENT" -> formatField(item.actualLocationIfDifferent)
                                    else -> ""
                                }
                            }
                            writer.append(lineValues.joinToString(delimiter))
                            writer.newLine()
                        }
                    } else if (report != null) {
                        // Hanya ada info report
                        val lineValues = headers.map { header ->
                            when (header.uppercase()) {
                                "REPORT_ID" -> formatField(report.reportId)
                                "REPORT_NAME" -> formatField(report.reportName)
                                "REPORT_START_TIME" -> formatField(report.startTimeMillis)
                                "REPORT_END_TIME" -> formatField(report.endTimeMillis)
                                "TOTAL_ITEMS_EXPECTED" -> formatField(report.totalItemsExpected)
                                "TOTAL_ITEMS_FOUND" -> formatField(report.totalItemsFound)
                                "TOTAL_ITEMS_MISSING" -> formatField(report.totalItemsMissing)
                                "TOTAL_ITEMS_NEW_OR_UNEXPECTED" -> formatField(report.totalItemsNewOrUnexpected)
                                // Kolom item akan kosong
                                "RFID_SCANNED", "TID_SCANNED", "ITEMCODE_MASTER", "TITLE_MASTER",
                                "SCAN_TIMESTAMP", "STATUS", "ACTUAL_LOCATION_IF_DIFFERENT" -> ""
                                else -> ""
                            }
                        }
                        if (lineValues.any { it.isNotEmpty() }) {
                            writer.append(lineValues.joinToString(delimiter))
                            writer.newLine()
                        }
                    }
                    writer.flush()
                }
                val itemsActuallyExported = if (opnameItems.isNotEmpty()) opnameItems.size else if (report != null) 1 else 0
                ExportResult.Success("Opname results exported to TXT stream", itemsActuallyExported)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting opname results to TXT: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor hasil opname ke TXT: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting opname results to TXT: ${e.message}", e)
                ExportResult.Error("Terjadi kesalahan tak terduga saat mengekspor hasil opname (TXT): ${e.message}")
            }
        }
    }
}
