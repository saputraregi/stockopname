package com.example.aplikasistockopnameperpus.util.exporter

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook // Untuk .xlsx
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExcelFileExporter : FileExporter {

    companion object {
        private const val TAG = "ExcelFileExporter"
    }

    override suspend fun exportBooks(
        books: List<BookMaster>,
        outputStream: OutputStream,
        headers: List<String>
    ): ExportResult {
        return withContext(Dispatchers.IO) {
            if (books.isEmpty()) {
                return@withContext ExportResult.NoDataToExport
            }
            try {
                XSSFWorkbook().use { workbook -> // Gunakan XSSFWorkbook untuk .xlsx
                    val sheet: Sheet = workbook.createSheet("Master Buku")

                    // Buat style untuk header
                    val headerFont: Font = workbook.createFont().apply {
                        bold = true
                        fontHeightInPoints = 12.toShort()
                    }
                    val headerCellStyle: CellStyle = workbook.createCellStyle().apply {
                        setFont(headerFont)
                        alignment = HorizontalAlignment.CENTER
                        verticalAlignment = VerticalAlignment.CENTER
                    }

                    // Tulis header
                    val headerRow: Row = sheet.createRow(0)
                    headers.forEachIndexed { index, headerText ->
                        val cell: Cell = headerRow.createCell(index)
                        cell.setCellValue(headerText)
                        cell.cellStyle = headerCellStyle
                    }

                    // Tulis data buku
                    var rowIndex = 1
                    books.forEach { book ->
                        val row: Row = sheet.createRow(rowIndex++)
                        row.createCell(0).setCellValue(book.itemCode ?: "")
                        row.createCell(1).setCellValue(book.title ?: "")
                        row.createCell(2).setCellValue(book.rfidTagHex ?: "")
                        row.createCell(3).setCellValue(book.expectedLocation ?: "")
                        row.createCell(4).setCellValue(book.tid ?: "")
                        row.createCell(5).setCellValue(book.scanStatus ?: "")
                        row.createCell(6).setCellValue(
                            book.lastSeenTimestamp?.let {
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
                            } ?: ""
                        )
                    }

                    // Auto-size columns (bisa memakan waktu untuk sheet besar)
                    headers.indices.forEach { sheet.autoSizeColumn(it) }

                    workbook.write(outputStream)
                }
                ExportResult.Success("Exported to provided stream", books.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting books to Excel: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor data buku ke Excel: ${e.message}")
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
                return@withContext ExportResult.NoDataToExport
            }
            try {
                XSSFWorkbook().use { workbook ->
                    val sheet: Sheet = workbook.createSheet("Detail Opname")

                    val headerFont: Font = workbook.createFont().apply { bold = true }
                    val headerCellStyle: CellStyle = workbook.createCellStyle().apply { setFont(headerFont) }

                    val headerRow: Row = sheet.createRow(0)
                    headers.forEachIndexed { index, headerText ->
                        val cell: Cell = headerRow.createCell(index)
                        cell.setCellValue(headerText)
                        cell.cellStyle = headerCellStyle
                    }

                    var rowIndex = 1
                    opnameItems.forEach { item ->
                        val row: Row = sheet.createRow(rowIndex++)
                        row.createCell(0).setCellValue(item.reportId.toDouble()) // Report ID
                        row.createCell(1).setCellValue(item.rfidTagHexScanned ?: "")
                        row.createCell(2).setCellValue(item.tidScanned ?: "")
                        row.createCell(3).setCellValue(item.itemCodeMaster ?: "")
                        row.createCell(4).setCellValue(item.titleMaster ?: "")
                        row.createCell(5).setCellValue(
                            if (item.scanTimestamp > 0)
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.scanTimestamp))
                            else ""
                        )
                        row.createCell(6).setCellValue(item.status ?: "")
                    }

                    headers.indices.forEach { sheet.autoSizeColumn(it) }
                    workbook.write(outputStream)
                }
                ExportResult.Success("Exported to provided stream", opnameItems.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting opname items to Excel: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor item opname ke Excel: ${e.message}")
            }
        }
    }
}
