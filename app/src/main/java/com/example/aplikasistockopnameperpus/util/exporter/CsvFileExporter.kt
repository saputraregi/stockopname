package com.example.aplikasistockopnameperpus.util.exporter

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
// Import Enum Anda jika ada untuk status di BookMaster (misal: PairingStatus, ScanStatus)
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date // Pastikan import ini ada dan benar
import java.util.Locale

class CsvFileExporter : FileExporter {

    companion object {
        private const val TAG = "CsvFileExporter"
        // Menggunakan java.util.Date untuk SimpleDateFormat
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    override suspend fun exportBooks(
        books: List<BookMaster>,
        outputStream: OutputStream,
        headers: List<String>
    ): ExportResult {
        return withContext(Dispatchers.IO) {
            if (books.isEmpty()) {
                Log.i(TAG, "No books data to export.")
                return@withContext ExportResult.NoDataToExport
            }
            try {
                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.append(headers.joinToString(","))
                    writer.newLine()
                    books.forEach { book ->
                        // Konversi Long timestamp ke Date untuk formatter
                        val lastSeenStr = book.lastSeenTimestamp?.let { DATE_FORMATTER.format(Date(it)) } ?: ""
                        val pairingTimestampStr = book.pairingTimestamp?.let { DATE_FORMATTER.format(Date(it)) } ?: ""

                        val lineData = mutableListOf<String>()
                        headers.forEach { header ->
                            when (header.uppercase()) {
                                "ITEMCODE" -> lineData.add(book.itemCode.csvEscape())
                                "TITLE" -> lineData.add(book.title.csvEscape())
                                "CALLNUMBER" -> lineData.add(book.callNumber.csvEscape())
                                "RFIDTAGHEX" -> lineData.add(book.rfidTagHex.csvEscape())
                                "TID" -> lineData.add(book.tid.csvEscape())
                                // Asumsi PairingStatus dan ScanStatus adalah Enum, jadi pakai .name
                                "PAIRINGSTATUS" -> lineData.add(book.pairingStatus.name.csvEscape())
                                "PAIRINGTIMESTAMP" -> lineData.add(pairingTimestampStr.csvEscape())
                                "ACTUALLOCATION" -> lineData.add(book.locationName.csvEscape())
                                "LASTSEENTIMESTAMP" -> lineData.add(lastSeenStr.csvEscape())
                                else -> lineData.add("".csvEscape())
                            }
                        }
                        writer.append(lineData.joinToString(","))
                        writer.newLine()
                    }
                    writer.flush()
                }
                ExportResult.Success("Books exported (path not available here)", books.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting books to CSV: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor data buku ke CSV: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting books: ${e.message}", e)
                ExportResult.Error("Terjadi kesalahan tak terduga saat mengekspor buku: ${e.message}")
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
                Log.i(TAG, "No opname items data to export.")
                return@withContext ExportResult.NoDataToExport
            }
            try {
                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.append(headers.joinToString(","))
                    writer.newLine()
                    opnameItems.forEach { item ->
                        // Konversi Long timestamp ke Date untuk formatter
                        val scanTimestampStr = item.scanTimestamp.takeIf { it > 0L } // Pastikan perbandingan dengan Long
                            ?.let { DATE_FORMATTER.format(Date(it)) } ?: ""

                        val lineData = mutableListOf<String>()
                        headers.forEach { header ->
                            when (header.uppercase()) {
                                "REPORT_ID" -> lineData.add(item.reportId.toString().csvEscape())
                                "RFID_SCANNED" -> lineData.add(item.rfidTagHexScanned.csvEscape())
                                "TID_SCANNED" -> lineData.add(item.tidScanned.csvEscape())
                                "ITEMCODE_MASTER" -> lineData.add(item.itemCodeMaster.csvEscape())
                                "TITLE_MASTER" -> lineData.add(item.titleMaster.csvEscape())
                                "SCAN_TIMESTAMP" -> lineData.add(scanTimestampStr.csvEscape())
                                "STATUS" -> lineData.add(item.status.csvEscape()) // 'status' adalah String
                                "ACTUAL_LOCATION_IF_DIFFERENT" -> lineData.add(item.actualLocationIfDifferent.csvEscape())
                                else -> lineData.add("".csvEscape())
                            }
                        }
                        writer.append(lineData.joinToString(","))
                        writer.newLine()
                    }
                    writer.flush()
                }
                ExportResult.Success("Opname items exported (path not available here)", opnameItems.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting opname items to CSV: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor item opname ke CSV: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting opname items: ${e.message}", e)
                ExportResult.Error("Terjadi kesalahan tak terduga saat mengekspor item opname: ${e.message}")
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
                Log.i(TAG, "No data (items or report) to export for opname results.")
                return@withContext ExportResult.NoDataToExport
            }
            if (headers.isEmpty()) {
                Log.w(TAG, "Headers are empty for exporting opname results. CSV might be malformed.")
            }

            try {
                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.append(headers.joinToString(","))
                    writer.newLine()

                    if (opnameItems.isNotEmpty()) {
                        opnameItems.forEach { item ->
                            // Konversi Long timestamp ke Date untuk formatter
                            val scanTimestampStr = item.scanTimestamp.takeIf { it > 0L }
                                ?.let { DATE_FORMATTER.format(Date(it)) } ?: ""
                            val reportStartTimeStr = report?.startTimeMillis
                                ?.let { DATE_FORMATTER.format(Date(it)) } ?: ""
                            val reportEndTimeStr = report?.endTimeMillis
                                ?.let { DATE_FORMATTER.format(Date(it)) } ?: ""

                            val lineData = mutableListOf<String>()
                            headers.forEach { header ->
                                when (header.uppercase()) {
                                    // Kolom dari StockOpnameReport
                                    "REPORT_ID" -> lineData.add(report?.reportId?.toString().csvEscape()) // Menggunakan reportId dari report
                                    "REPORT_NAME" -> lineData.add(report?.reportName.csvEscape())
                                    "REPORT_START_TIME" -> lineData.add(reportStartTimeStr.csvEscape())
                                    "REPORT_END_TIME" -> lineData.add(reportEndTimeStr.csvEscape())
                                    "TOTAL_ITEMS_EXPECTED" -> lineData.add(report?.totalItemsExpected?.toString().csvEscape())
                                    "TOTAL_ITEMS_FOUND" -> lineData.add(report?.totalItemsFound?.toString().csvEscape())
                                    "TOTAL_ITEMS_MISSING" -> lineData.add(report?.totalItemsMissing?.toString().csvEscape())
                                    "TOTAL_ITEMS_NEW_OR_UNEXPECTED" -> lineData.add(report?.totalItemsNewOrUnexpected?.toString().csvEscape())

                                    // Kolom dari StockOpnameItem
                                    // "ITEM_REPORT_ID" -> lineData.add(item.reportId.toString().csvEscape()) // Jika Anda ingin reportId dari item juga
                                    "RFID_SCANNED" -> lineData.add(item.rfidTagHexScanned.csvEscape())
                                    "TID_SCANNED" -> lineData.add(item.tidScanned.csvEscape())
                                    "ITEMCODE_MASTER" -> lineData.add(item.itemCodeMaster.csvEscape())
                                    "TITLE_MASTER" -> lineData.add(item.titleMaster.csvEscape())
                                    "SCAN_TIMESTAMP" -> lineData.add(scanTimestampStr.csvEscape())
                                    "STATUS" -> lineData.add(item.status.csvEscape()) // 'status' adalah String
                                    "ACTUAL_LOCATION_IF_DIFFERENT" -> lineData.add(item.actualLocationIfDifferent.csvEscape())
                                    else -> lineData.add("".csvEscape())
                                }
                            }
                            writer.append(lineData.joinToString(","))
                            writer.newLine()
                        }
                    } else if (report != null) {
                        // Hanya ada info report
                        // Konversi Long timestamp ke Date untuk formatter
                        val reportStartTimeStr = report.startTimeMillis.let { DATE_FORMATTER.format(Date(it)) }
                        val reportEndTimeStr = report.endTimeMillis?.let { DATE_FORMATTER.format(Date(it)) } ?: ""

                        val lineData = mutableListOf<String>()
                        headers.forEach { header ->
                            when (header.uppercase()) {
                                "REPORT_ID" -> lineData.add(report.reportId.toString().csvEscape())
                                "REPORT_NAME" -> lineData.add(report.reportName.csvEscape())
                                "REPORT_START_TIME" -> lineData.add(reportStartTimeStr.csvEscape())
                                "REPORT_END_TIME" -> lineData.add(reportEndTimeStr.csvEscape())
                                "TOTAL_ITEMS_EXPECTED" -> lineData.add(report.totalItemsExpected.toString().csvEscape())
                                "TOTAL_ITEMS_FOUND" -> lineData.add(report.totalItemsFound.toString().csvEscape())
                                "TOTAL_ITEMS_MISSING" -> lineData.add(report.totalItemsMissing.toString().csvEscape())
                                "TOTAL_ITEMS_NEW_OR_UNEXPECTED" -> lineData.add(report.totalItemsNewOrUnexpected.toString().csvEscape())

                                // Kolom item akan kosong
                                "RFID_SCANNED", "TID_SCANNED", "ITEMCODE_MASTER", "TITLE_MASTER",
                                "SCAN_TIMESTAMP", "STATUS", "ACTUAL_LOCATION_IF_DIFFERENT" -> lineData.add("".csvEscape())
                                else -> lineData.add("".csvEscape())
                            }
                        }
                        if (lineData.any { it.isNotEmpty() }) {
                            writer.append(lineData.joinToString(","))
                            writer.newLine()
                        }
                    }
                    writer.flush()
                }
                val itemsActuallyExported = if (opnameItems.isNotEmpty()) opnameItems.size else if (report != null) 1 else 0
                ExportResult.Success("Opname results exported (path not available here)", itemsActuallyExported)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting opname results to CSV: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor hasil opname ke CSV: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting opname results: ${e.message}", e)
                ExportResult.Error("Terjadi kesalahan tak terduga saat mengekspor: ${e.message}")
            }
        }
    }

    private fun String?.csvEscape(): String {
        if (this == null) return ""
        return if (this.contains(",") || this.contains("\n") || this.contains("\"")) {
            "\"${this.replace("\"", "\"\"")}\""
        } else {
            this
        }
    }
}
