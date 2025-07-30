package com.example.aplikasistockopnameperpus.util.parser

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook // Untuk .xlsx
// import org.apache.poi.hssf.usermodel.HSSFWorkbook // Untuk .xls jika perlu dukungan terpisah

import java.io.InputStream

class ExcelFileParser(private val isXlsx: Boolean = true) : FileParser { // Default ke .xlsx

    companion object {
        private const val TAG = "ExcelFileParser"
    }

    override suspend fun parse(inputStream: InputStream, onProgress: ((Int) -> Unit)?): ParseResult {
        return withContext(Dispatchers.IO) {
            val books = mutableListOf<BookMaster>()
            val warnings = mutableListOf<String>()
            var currentProcessingRow = 0 // Untuk onProgress

            try {
                val workbook: Workbook = if (isXlsx) {
                    XSSFWorkbook(inputStream)
                } else {
                    // HSSFWorkbook(inputStream) // Aktifkan jika ingin membedakan .xls
                    return@withContext ParseResult.Error("Format file .xls tidak didukung saat ini (gunakan .xlsx).", 0)
                }

                if (workbook.numberOfSheets == 0) {
                    return@withContext ParseResult.Error("File Excel tidak memiliki sheet.", 0)
                }

                val sheet: Sheet = workbook.getSheetAt(0) // Ambil sheet pertama
                val headerRow: Row? = sheet.getRow(0)

                if (headerRow == null) {
                    return@withContext ParseResult.Error("Sheet pertama dalam file Excel tidak memiliki baris header.", 0)
                }

                val columnIndices = mapExcelHeadersToIndices(headerRow)
                if (!columnIndices.containsKey("ITEMCODE") ||
                    !columnIndices.containsKey("TITLE") ||
                    !columnIndices.containsKey("RFIDTAGHEX")) {
                    return@withContext ParseResult.Error("Header Excel tidak valid. Pastikan kolom ITEMCODE, TITLE, RFIDTAGHEX ada.", 0)
                }


                val rowIterator = sheet.iterator()
                if (rowIterator.hasNext()) { // Lewati baris header
                    rowIterator.next()
                }

                while (rowIterator.hasNext()) {
                    val row = rowIterator.next()
                    currentProcessingRow = row.rowNum + 1 // Row num is 0-based
                    onProgress?.invoke(currentProcessingRow)

                    val itemCode = getCellStringValue(row.getCell(columnIndices["ITEMCODE"] ?: -1))
                    val title = getCellStringValue(row.getCell(columnIndices["TITLE"] ?: -1))
                    val rfidTagHex = getCellStringValue(row.getCell(columnIndices["RFIDTAGHEX"] ?: -1))
                    val expectedLocation = getCellStringValue(row.getCell(columnIndices["EXPECTEDLOCATION"] ?: -1))
                    val tid = getCellStringValue(row.getCell(columnIndices["TID"] ?: -1))


                    if (itemCode.isNullOrBlank() || title.isNullOrBlank() || rfidTagHex.isNullOrBlank()) {
                        warnings.add("Peringatan di baris $currentProcessingRow: ITEMCODE, TITLE, atau RFIDTAGHEX kosong. Baris dilewati.")
                        continue
                    }
                    if (rfidTagHex.length < 8 || !rfidTagHex.matches(Regex("^[0-9a-fA-F]+$"))) {
                        warnings.add("Peringatan di baris $currentProcessingRow: RFIDTAGHEX '$rfidTagHex' tidak valid. Baris dilewati.")
                        continue
                    }


                    books.add(
                        BookMaster(
                            itemCode = itemCode,
                            title = title,
                            rfidTagHex = rfidTagHex.uppercase(),
                            expectedLocation = expectedLocation,
                            tid = tid
                        )
                    )
                    if (books.size >= Constants.MAX_ROWS_TO_PARSE) {
                        warnings.add("Mencapai batas maksimum ${Constants.MAX_ROWS_TO_PARSE} baris untuk diproses.")
                        break
                    }
                }
                workbook.close()
                inputStream.close()

                if (books.isEmpty() && currentProcessingRow > 1) {
                    ParseResult.Error("Tidak ada data buku yang valid ditemukan di file Excel.", currentProcessingRow)
                } else if (books.isEmpty() && currentProcessingRow <=1 && columnIndices.isNotEmpty()) {
                    ParseResult.Error("File Excel hanya berisi header atau kosong.", currentProcessingRow)
                }
                else {
                    ParseResult.Success(books, warnings)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Excel file: ${e.message}", e)
                ParseResult.Error("Terjadi kesalahan saat memproses file Excel: ${e.message}", currentProcessingRow)
            }
        }
    }

    private fun mapExcelHeadersToIndices(headerRow: Row): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        headerRow.forEach { cell ->
            val headerText = getCellStringValue(cell)?.uppercase()?.trim()
            if (headerText != null && Constants.DEFAULT_CSV_BOOK_HEADERS.contains(headerText)) {
                map[headerText] = cell.columnIndex
            }
        }
        return map
    }

    private fun getCellStringValue(cell: Cell?): String? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue?.trim()?.take(Constants.MAX_CELL_LENGTH)
            CellType.NUMERIC -> {
                // Cek apakah itu tanggal atau angka biasa
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue?.toString() // Atau format sesuai kebutuhan
                } else {
                    // Hindari .0 untuk angka integer
                    val number = cell.numericCellValue
                    if (number == number.toLong().toDouble()) {
                        number.toLong().toString()
                    } else {
                        number.toString()
                    }
                }?.take(Constants.MAX_CELL_LENGTH)
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString().take(Constants.MAX_CELL_LENGTH)
            CellType.FORMULA -> { // Coba evaluasi formula, jika gagal ambil cached value
                try {
                    val evaluator = cell.sheet.workbook.creationHelper.createFormulaEvaluator()
                    val cellValue = evaluator.evaluate(cell)
                    when (cellValue.cellType) {
                        CellType.STRING -> cellValue.stringValue?.trim()?.take(Constants.MAX_CELL_LENGTH)
                        CellType.NUMERIC -> {
                            val number = cellValue.numberValue
                            if (number == number.toLong().toDouble()) {
                                number.toLong().toString()
                            } else {
                                number.toString()
                            }?.take(Constants.MAX_CELL_LENGTH)
                        }
                        CellType.BOOLEAN -> cellValue.booleanValue.toString().take(Constants.MAX_CELL_LENGTH)
                        else -> cell.toString().trim().take(Constants.MAX_CELL_LENGTH) // Fallback
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not evaluate formula in cell $cell: ${e.message}")
                    // Fallback to cached formula result string if evaluation fails
                    try { cell.stringCellValue?.trim()?.take(Constants.MAX_CELL_LENGTH) } catch (ex: Exception) { null }
                }
            }
            else -> cell.toString().trim().take(Constants.MAX_CELL_LENGTH) // Atau null jika ingin lebih ketat
        }
    }
}
