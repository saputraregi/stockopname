package com.example.aplikasistockopnameperpus.util.exporter

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
// Import Enum Anda jika ada (misal: PairingStatus, ScanStatus)
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExcelFileExporter : FileExporter {

    companion object {
        private const val TAG = "ExcelFileExporter"
        // Menggunakan java.util.Date untuk SimpleDateFormat
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    private fun createHeaderCellStyle(workbook: Workbook): CellStyle {
        val headerFont: Font = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 12.toShort()
        }
        return workbook.createCellStyle().apply {
            setFont(headerFont)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            // fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
            // fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
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
                XSSFWorkbook().use { workbook ->
                    val sheet: Sheet = workbook.createSheet("Master Buku")
                    val headerCellStyle = createHeaderCellStyle(workbook)

                    // Tulis header
                    val headerRowExcel: Row = sheet.createRow(0)
                    headers.forEachIndexed { index, headerText ->
                        val cell: Cell = headerRowExcel.createCell(index)
                        cell.setCellValue(headerText)
                        cell.cellStyle = headerCellStyle
                    }

                    // Tulis data buku
                    var rowIndex = 1
                    books.forEach { book ->
                        val currentRow = sheet.createRow(rowIndex++)
                        var cellIndex = 0

                        val lastSeenStr = book.lastSeenTimestamp?.let { DATE_FORMATTER.format(Date(it)) } ?: ""
                        val pairingTimestampStr = book.pairingTimestamp?.let { DATE_FORMATTER.format(Date(it)) } ?: ""

                        headers.forEach { header ->
                            val cellValue: String = when (header.uppercase()) {
                                "ITEMCODE" -> book.itemCode ?: ""
                                "TITLE" -> book.title ?: ""
                                "CALLNUMBER" -> book.callNumber ?: ""
                                "RFIDTAGHEX" -> book.rfidTagHex ?: ""
                                "TID" -> book.tid ?: ""
                                "PAIRINGSTATUS" -> book.pairingStatus.name // Asumsi Enum
                                "PAIRINGTIMESTAMP" -> pairingTimestampStr
                                "ACTUALLOCATION" -> book.locationName ?: "" // Sesuai CsvExporter Anda
                                "LASTSEENTIMESTAMP" -> lastSeenStr
                                // Tambahkan case lain dari BookMaster jika diperlukan
                                // "AUTHOR" -> book.author ?: ""
                                // "PUBLISHER" -> book.publisher ?: ""
                                // "YEARPUBLISHED" -> book.yearPublished?.toString() ?: ""
                                // "CATEGORY" -> book.category ?: ""
                                // "EXPECTEDLOCATION" -> book.expectedLocation ?: ""
                                // "SCANSTATUS" -> book.scanStatus.name // Asumsi Enum
                                // "NOTES" -> book.notes ?: ""
                                else -> ""
                            }
                            currentRow.createCell(cellIndex++).setCellValue(cellValue)
                        }
                    }

                    if (headers.isNotEmpty()) {
                        headers.indices.forEach { sheet.autoSizeColumn(it) }
                    }
                    workbook.write(outputStream)
                }
                ExportResult.Success("Books exported to Excel stream", books.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting books to Excel: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor data buku ke Excel: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting books to Excel: ${e.message}", e)
                ExportResult.Error("Terjadi kesalahan tak terduga saat mengekspor buku (Excel): ${e.message}")
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
                XSSFWorkbook().use { workbook ->
                    val sheet: Sheet = workbook.createSheet("Item Opname")
                    val headerCellStyle = createHeaderCellStyle(workbook)

                    val headerRowExcel: Row = sheet.createRow(0)
                    headers.forEachIndexed { index, headerText ->
                        val cell: Cell = headerRowExcel.createCell(index)
                        cell.setCellValue(headerText)
                        cell.cellStyle = headerCellStyle
                    }

                    var rowIndex = 1
                    opnameItems.forEach { item ->
                        val currentRow = sheet.createRow(rowIndex++)
                        var cellIndex = 0

                        val scanTimestampStr = item.scanTimestamp.takeIf { it > 0L }
                            ?.let { DATE_FORMATTER.format(Date(it)) } ?: ""

                        headers.forEach { header ->
                            val cellValue: String = when (header.uppercase()) {
                                "REPORT_ID" -> item.reportId.toString()
                                "RFID_SCANNED" -> item.rfidTagHexScanned
                                "TID_SCANNED" -> item.tidScanned ?: ""
                                "ITEMCODE_MASTER" -> item.itemCodeMaster ?: ""
                                "TITLE_MASTER" -> item.titleMaster ?: ""
                                "SCAN_TIMESTAMP" -> scanTimestampStr
                                "STATUS" -> item.status // 'status' adalah String
                                "ACTUAL_LOCATION_IF_DIFFERENT" -> item.actualLocationIfDifferent ?: ""
                                else -> ""
                            }
                            currentRow.createCell(cellIndex++).setCellValue(cellValue)
                        }
                    }

                    if (headers.isNotEmpty()) {
                        headers.indices.forEach { sheet.autoSizeColumn(it) }
                    }
                    workbook.write(outputStream)
                }
                ExportResult.Success("Opname items exported to Excel stream", opnameItems.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting opname items to Excel: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor item opname ke Excel: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting opname items to Excel: ${e.message}", e)
                ExportResult.Error("Terjadi kesalahan tak terduga saat mengekspor item opname (Excel): ${e.message}")
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
                Log.w(TAG, "Headers are empty for exporting opname results. Excel sheet might be malformed.")
            }

            try {
                XSSFWorkbook().use { workbook ->
                    val sheet: Sheet = workbook.createSheet("Hasil Opname Keseluruhan")
                    val headerCellStyle = createHeaderCellStyle(workbook)

                    val headerRowExcel: Row = sheet.createRow(0)
                    headers.forEachIndexed { index, headerText ->
                        val cell: Cell = headerRowExcel.createCell(index)
                        cell.setCellValue(headerText)
                        cell.cellStyle = headerCellStyle
                    }

                    var rowIndex = 1

                    if (opnameItems.isNotEmpty()) {
                        opnameItems.forEach { item ->
                            val currentRow = sheet.createRow(rowIndex++)
                            var cellIndex = 0

                            val scanTimestampStr = item.scanTimestamp.takeIf { it > 0L }
                                ?.let { DATE_FORMATTER.format(Date(it)) } ?: ""
                            val reportStartTimeStr = report?.startTimeMillis
                                ?.let { DATE_FORMATTER.format(Date(it)) } ?: ""
                            val reportEndTimeStr = report?.endTimeMillis
                                ?.let { DATE_FORMATTER.format(Date(it)) } ?: ""

                            headers.forEach { header ->
                                val cellValue: String = when (header.uppercase()) {
                                    // Kolom dari StockOpnameReport
                                    "REPORT_ID" -> report?.reportId?.toString() ?: ""
                                    "REPORT_NAME" -> report?.reportName ?: ""
                                    "REPORT_START_TIME" -> reportStartTimeStr
                                    "REPORT_END_TIME" -> reportEndTimeStr
                                    "TOTAL_ITEMS_EXPECTED" -> report?.totalItemsExpected?.toString() ?: ""
                                    "TOTAL_ITEMS_FOUND" -> report?.totalItemsFound?.toString() ?: ""
                                    "TOTAL_ITEMS_MISSING" -> report?.totalItemsMissing?.toString() ?: ""
                                    "TOTAL_ITEMS_NEW_OR_UNEXPECTED" -> report?.totalItemsNewOrUnexpected?.toString() ?: ""

                                    // Kolom dari StockOpnameItem
                                    "RFID_SCANNED" -> item.rfidTagHexScanned
                                    "TID_SCANNED" -> item.tidScanned ?: ""
                                    "ITEMCODE_MASTER" -> item.itemCodeMaster ?: ""
                                    "TITLE_MASTER" -> item.titleMaster ?: ""
                                    "SCAN_TIMESTAMP" -> scanTimestampStr
                                    "STATUS" -> item.status // 'status' adalah String
                                    "ACTUAL_LOCATION_IF_DIFFERENT" -> item.actualLocationIfDifferent ?: ""
                                    else -> ""
                                }
                                currentRow.createCell(cellIndex++).setCellValue(cellValue)
                            }
                        }
                    } else if (report != null) {
                        // Hanya ada info report
                        val currentRow = sheet.createRow(rowIndex++)
                        var cellIndex = 0

                        val reportStartTimeStr = report.startTimeMillis.let { DATE_FORMATTER.format(Date(it)) }
                        val reportEndTimeStr = report.endTimeMillis?.let { DATE_FORMATTER.format(Date(it)) } ?: ""

                        headers.forEach { header ->
                            val cellValue: String = when (header.uppercase()) {
                                "REPORT_ID" -> report.reportId.toString()
                                "REPORT_NAME" -> report.reportName
                                "REPORT_START_TIME" -> reportStartTimeStr
                                "REPORT_END_TIME" -> reportEndTimeStr
                                "TOTAL_ITEMS_EXPECTED" -> report.totalItemsExpected.toString()
                                "TOTAL_ITEMS_FOUND" -> report.totalItemsFound.toString()
                                "TOTAL_ITEMS_MISSING" -> report.totalItemsMissing.toString()
                                "TOTAL_ITEMS_NEW_OR_UNEXPECTED" -> report.totalItemsNewOrUnexpected.toString()
                                // Kolom item akan kosong
                                "RFID_SCANNED", "TID_SCANNED", "ITEMCODE_MASTER", "TITLE_MASTER",
                                "SCAN_TIMESTAMP", "STATUS", "ACTUAL_LOCATION_IF_DIFFERENT" -> ""
                                else -> ""
                            }
                            currentRow.createCell(cellIndex++).setCellValue(cellValue)
                        }
                    }

                    if (headers.isNotEmpty()) {
                        headers.indices.forEach { sheet.autoSizeColumn(it) }
                    }
                    workbook.write(outputStream)
                }
                val itemsActuallyExported = if (opnameItems.isNotEmpty()) opnameItems.size else if (report != null) 1 else 0
                ExportResult.Success("Opname results exported to Excel stream", itemsActuallyExported)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting opname results to Excel: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor hasil opname ke Excel: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error exporting opname results to Excel: ${e.message}", e)
                ExportResult.Error("Terjadi kesalahan tak terduga saat mengekspor hasil opname (Excel): ${e.message}")
            }
        }
    }
}
