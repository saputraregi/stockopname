package com.example.aplikasistockopnameperpus.util.parser

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.data.database.toEPC128Hex
import com.example.aplikasistockopnameperpus.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import java.io.InputStream
import java.io.PushbackInputStream
import java.io.IOException

class ExcelFileParser : FileParser {

    companion object {
        private const val TAG = "ExcelFileParser"
    }

    // Helper function to get cell value as String, handling different cell types
    private fun getCellValueAsString(cell: Cell?): String? {
        if (cell == null) {
            return null
        }
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue?.trim()
            CellType.NUMERIC -> {
                // Check if it's a date
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Anda mungkin ingin memformat tanggal secara spesifik di sini
                    // Untuk saat ini, kita akan mengambilnya sebagai string default dari POI
                    cell.localDateTimeCellValue?.toString() // Atau SimpleDateFormat jika Anda punya format target
                } else {
                    // Handle numeric value, format as string without .0 for integers
                    val numValue = cell.numericCellValue
                    if (numValue == numValue.toLong().toDouble()) {
                        numValue.toLong().toString()
                    } else {
                        numValue.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                // Coba evaluasi formula, jika gagal, ambil string formula
                try {
                    cell.numericCellValue.toString() // Atau evaluasi ke tipe yang sesuai
                } catch (e: Exception) {
                    cell.cellFormula?.trim()
                }
            }
            CellType.BLANK -> null
            CellType.ERROR -> cell.errorCellValue.toString()
            else -> null
        }
    }


    private fun getColumnIndex(headerRow: Row?, columnName: String): Int {
        headerRow ?: return -1
        for (cell in headerRow) {
            if (getCellValueAsString(cell)?.trim().equals(columnName, ignoreCase = true)) {
                return cell.columnIndex
            }
        }
        return -1
    }

    override suspend fun parse(inputStream: InputStream, onProgress: ((Int) -> Unit)?): ParseResult {
        return withContext(Dispatchers.IO) {
            val books = mutableListOf<BookMaster>()
            val warnings = mutableListOf<String>()
            var processedRowCount = 0 // Sekarang ini adalah row count

            // Gunakan PushbackInputStream untuk memungkinkan POI mendeteksi format file (xls vs xlsx)
            // tanpa mengonsumsi stream jika kita perlu reset
            val pushbackInputStream = if (inputStream.markSupported()) inputStream else PushbackInputStream(inputStream, 8)

            try {
                WorkbookFactory.create(pushbackInputStream).use { workbook ->
                    if (workbook.numberOfSheets == 0) {
                        warnings.add("File Excel tidak memiliki sheet.")
                        return@withContext ParseResult.Success(emptyList(), warnings)
                    }

                    val sheet: Sheet = workbook.getSheetAt(0) // Ambil sheet pertama
                    val rowIterator = sheet.iterator()

                    if (!rowIterator.hasNext()) {
                        warnings.add("Sheet pertama dalam file Excel kosong.")
                        return@withContext ParseResult.Success(emptyList(), warnings)
                    }

                    val headerRow = rowIterator.next()
                    processedRowCount++
                    onProgress?.invoke(processedRowCount)

                    // Dapatkan indeks kolom berdasarkan nama header dari Constants
                    val itemCodeIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.ITEM_CODE)
                    val titleIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.TITLE)
                    val callNumberIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.CALL_NUMBER)
                    val collTypeNameIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.COLLECTION_TYPE_NAME)
                    val inventoryCodeIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.INVENTORY_CODE)
                    val receivedDateIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.RECEIVED_DATE)
                    val locationNameIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.LOCATION_NAME)
                    val orderDateIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.ORDER_DATE)
                    val itemStatusNameIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.ITEM_STATUS_NAME)
                    val siteIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.SITE)
                    val sourceIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.SOURCE)
                    val priceIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.PRICE)
                    val priceCurrencyIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.PRICE_CURRENCY)
                    val invoiceDateIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.INVOICE_DATE)
                    val inputDateIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.INPUT_DATE)
                    val lastUpdateIdx = getColumnIndex(headerRow, Constants.SlimsCsvHeaders.LAST_UPDATE)

                    if (itemCodeIdx == -1 || titleIdx == -1) {
                        Log.e(TAG, "Header '${Constants.SlimsCsvHeaders.ITEM_CODE}' atau '${Constants.SlimsCsvHeaders.TITLE}' tidak ditemukan di sheet Excel.")
                        return@withContext ParseResult.InvalidFormat("Header wajib tidak ditemukan.")
                    }

                    var currentDataRowNumber = 1 // Nomor baris data setelah header
                    rowIterator.forEach { row ->
                        processedRowCount++
                        currentDataRowNumber++
                        onProgress?.invoke(processedRowCount)

                        try {
                            val itemCode = getCellValueAsString(row.getCell(itemCodeIdx))
                            val title = getCellValueAsString(row.getCell(titleIdx))

                            if (itemCode.isNullOrBlank()) {
                                warnings.add("Baris Excel ${currentDataRowNumber}: Dilewati karena '${Constants.SlimsCsvHeaders.ITEM_CODE}' kosong atau hanya spasi.")
                                return@forEach // Lanjut ke baris berikutnya
                            }
                            if (title.isNullOrBlank()) {
                                warnings.add("Baris Excel ${currentDataRowNumber} (Item Code: $itemCode): '${Constants.SlimsCsvHeaders.TITLE}' kosong atau hanya spasi, tetap diimpor dengan judul kosong.")
                            }

                            val rfidHex = itemCode.toEPC128Hex()

                            books.add(
                                BookMaster(
                                    itemCode = itemCode,
                                    title = title ?: "",
                                    rfidTagHex = rfidHex,
                                    tid = null, // Anda mungkin ingin membaca ini dari kolom lain jika ada
                                    callNumber = getCellValueAsString(row.getCell(callNumberIdx))?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                    collectionType = getCellValueAsString(row.getCell(collTypeNameIdx))?.takeIf { it.isNotEmpty() },
                                    inventoryCode = getCellValueAsString(row.getCell(inventoryCodeIdx))?.takeIf { it.isNotEmpty() },
                                    receivedDate = getCellValueAsString(row.getCell(receivedDateIdx))?.takeIf { it.isNotEmpty() },
                                    locationName = getCellValueAsString(row.getCell(locationNameIdx))?.takeIf { it.isNotEmpty() },
                                    orderDate = getCellValueAsString(row.getCell(orderDateIdx))?.takeIf { it.isNotEmpty() },
                                    slimsItemStatus = getCellValueAsString(row.getCell(itemStatusNameIdx))?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                    siteName = getCellValueAsString(row.getCell(siteIdx))?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                    source = getCellValueAsString(row.getCell(sourceIdx))?.takeIf { it.isNotEmpty() },
                                    price = getCellValueAsString(row.getCell(priceIdx))?.takeIf { it.isNotEmpty() },
                                    priceCurrency = getCellValueAsString(row.getCell(priceCurrencyIdx))?.takeIf { it.isNotEmpty() },
                                    invoiceDate = getCellValueAsString(row.getCell(invoiceDateIdx))?.takeIf { it.isNotEmpty() },
                                    inputDate = getCellValueAsString(row.getCell(inputDateIdx))?.takeIf { it.isNotEmpty() },
                                    lastUpdate = getCellValueAsString(row.getCell(lastUpdateIdx))?.takeIf { it.isNotEmpty() },
                                    pairingStatus = PairingStatus.NOT_PAIRED // Default status
                                )
                            )
                        } catch (e: IllegalStateException) { // Dapat terjadi jika tipe sel tidak seperti yang diharapkan saat getCell
                            warnings.add("Baris Excel ${currentDataRowNumber}: Dilewati karena tipe data sel tidak valid atau formula error. Error: ${e.message}")
                            Log.w(TAG, "Baris Excel ${currentDataRowNumber}: Masalah saat membaca sel", e)
                        }
                        catch (e: Exception) {
                            warnings.add("Baris Excel ${currentDataRowNumber}: Error saat parsing baris. Pesan: ${e.message}")
                            Log.e(TAG, "Baris Excel ${currentDataRowNumber}: Generic error - Row content might be: ${row.joinToString("|") { getCellValueAsString(it) ?: "" }}", e)
                        }
                    }
                } // Akhir dari Workbook.use
                ParseResult.Success(books, warnings)
            } catch (e: org.apache.poi.poifs.filesystem.OfficeXmlFileException) {
                Log.e(TAG, "Error parsing Excel: File kemungkinan adalah format XML lama (misalnya, Excel 2003 XML) yang tidak didukung langsung oleh XSSFWorkbook/HSSFWorkbook via WorkbookFactory.create. Coba simpan sebagai .xlsx atau .xls standar. Pesan: ${e.message}", e)
                ParseResult.Error("Gagal memproses file Excel. Format mungkin tidak didukung atau file rusak: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error fatal saat parsing Excel: ${e.message}", e)
                ParseResult.Error("Gagal memproses file Excel: ${e.message}")
            } finally {
                // Tutup input stream jika tidak ditutup oleh WorkbookFactory.create
                // Namun, Workbook.use{} harusnya sudah menanganinya.
                // Jika Anda menggunakan PushbackInputStream dan tidak yakin, Anda bisa menutupnya di sini.
                try {
                    pushbackInputStream.close()
                } catch (ioe: IOException) {
                    Log.w(TAG, "Gagal menutup pushbackInputStream", ioe)
                }
            }
        }
    }
}
