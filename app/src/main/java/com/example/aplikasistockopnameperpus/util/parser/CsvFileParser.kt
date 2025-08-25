package com.example.aplikasistockopnameperpus.util.parser

import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.data.database.toEPC96Hex
import com.example.aplikasistockopnameperpus.util.Constants
import android.util.Log
// androidx.compose.ui.semantics.getOrNull // Kemungkinan tidak diperlukan di sini, hapus jika tidak ada error
import com.opencsv.CSVReader // Import OpenCSV
import com.opencsv.exceptions.CsvValidationException // Import untuk exception OpenCSV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader // Diperlukan untuk CSVReader
import java.nio.charset.StandardCharsets

// Pastikan ParseResult dan FileParser sudah didefinisikan di file terpisah atau di sini
// Misal dari FileParser.kt Anda:
// import com.example.aplikasistockopnameperpus.util.parser.FileParser
// import com.example.aplikasistockopnameperpus.util.parser.ParseResult

class CsvFileParser : FileParser {

    companion object {
        private const val TAG = "CsvFileParser"
    }

    private fun getColumnIndex(headerTokens: Array<String>?, columnName: String): Int {
        if (headerTokens == null) return -1
        return headerTokens.indexOfFirst { it.trim().equals(columnName, ignoreCase = true) }
    }

    override suspend fun parse(inputStream: InputStream, onProgress: ((Int) -> Unit)?): ParseResult {
        return withContext(Dispatchers.IO) {
            val books = mutableListOf<BookMaster>()
            val warnings = mutableListOf<String>()
            var processedLineCount = 0

            try {
                InputStreamReader(inputStream, StandardCharsets.UTF_8).use { streamReader ->
                    CSVReader(streamReader).use { csvReader ->

                        val headerTokens: Array<String>? = try {
                            csvReader.readNext()
                        } catch (e: CsvValidationException) {
                            Log.e(TAG, "Error validasi CSV saat membaca header (baris ~1): ${e.message}", e)
                            return@withContext ParseResult.Error("Format CSV tidak valid pada header: ${e.message}")
                        }

                        if (headerTokens == null) {
                            warnings.add("File CSV kosong atau tidak ada baris header.")
                            return@withContext ParseResult.Success(books, warnings)
                        }
                        processedLineCount++
                        onProgress?.invoke(processedLineCount)
                        Log.d(TAG, "Header dari OpenCSV: ${headerTokens.joinToString(", ")}")

                        val itemCodeIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.ITEM_CODE)
                        val titleIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.TITLE)
                        val callNumberIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.CALL_NUMBER)
                        val collTypeNameIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.COLLECTION_TYPE_NAME)
                        val inventoryCodeIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.INVENTORY_CODE)
                        val receivedDateIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.RECEIVED_DATE)
                        val locationNameIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.LOCATION_NAME)
                        val orderDateIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.ORDER_DATE)
                        val itemStatusNameIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.ITEM_STATUS_NAME)
                        val siteIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.SITE)
                        val sourceIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.SOURCE)
                        val priceIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.PRICE)
                        val priceCurrencyIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.PRICE_CURRENCY)
                        val invoiceDateIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.INVOICE_DATE)
                        val inputDateIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.INPUT_DATE)
                        val lastUpdateIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.LAST_UPDATE)

                        if (itemCodeIdx == -1 || titleIdx == -1) {
                            val missing = mutableListOf<String>()
                            if (itemCodeIdx == -1) missing.add("'${Constants.SlimsCsvHeaders.ITEM_CODE}'")
                            if (titleIdx == -1) missing.add("'${Constants.SlimsCsvHeaders.TITLE}'")
                            val errorMessage = "Header ${missing.joinToString(" dan ")} tidak ditemukan. Pastikan file CSV memiliki kolom tersebut."
                            Log.e(TAG, "$errorMessage Header yang ditemukan: ${headerTokens.joinToString(", ")}")
                            return@withContext ParseResult.InvalidFormat(errorMessage)
                        }

                        var currentDataLineNumber = 1
                        var dataTokens: Array<String>?

                        while (true) {
                            dataTokens = try {
                                csvReader.readNext()
                            } catch (e: CsvValidationException) {
                                // Menghilangkan referensi ke csvReader.linesSkipped
                                val approxFileLineNumberForLog = processedLineCount + 1
                                warnings.add("Baris data ${currentDataLineNumber} (sekitar baris file ${approxFileLineNumberForLog}): Error validasi CSV, baris dilewati. Pesan: ${e.message}")
                                Log.w(TAG, "Baris data ${currentDataLineNumber} (sekitar baris file ${approxFileLineNumberForLog}): CsvValidationException - ${e.message}. Detail dari Exception (jika ada): Line ${e.lineNumber}", e)
                                currentDataLineNumber++
                                processedLineCount++
                                onProgress?.invoke(processedLineCount)
                                continue
                            }

                            if (dataTokens == null) break

                            processedLineCount++
                            onProgress?.invoke(processedLineCount)

                            try {
                                val itemCode = dataTokens.getOrNull(itemCodeIdx)?.trim()
                                val title = dataTokens.getOrNull(titleIdx)?.trim()

                                if (itemCode.isNullOrBlank()) {
                                    warnings.add("Baris ${currentDataLineNumber}: Dilewati karena '${Constants.SlimsCsvHeaders.ITEM_CODE}' kosong atau hanya spasi.")
                                    currentDataLineNumber++ // Jangan lupa increment jika continue
                                    continue
                                }
                                if (title.isNullOrBlank()) {
                                    warnings.add("Baris ${currentDataLineNumber} (Item Code: $itemCode): '${Constants.SlimsCsvHeaders.TITLE}' kosong atau hanya spasi, tetap diimpor dengan judul kosong.")
                                }

                                val rfidHex = itemCode.toEPC96Hex()

                                books.add(
                                    BookMaster(
                                        itemCode = itemCode,
                                        title = title ?: "",
                                        rfidTagHex = rfidHex,
                                        tid = null,
                                        callNumber = dataTokens.getOrNull(callNumberIdx)?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                        collectionType = dataTokens.getOrNull(collTypeNameIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        inventoryCode = dataTokens.getOrNull(inventoryCodeIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        receivedDate = dataTokens.getOrNull(receivedDateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        locationName = dataTokens.getOrNull(locationNameIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        orderDate = dataTokens.getOrNull(orderDateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        slimsItemStatus = dataTokens.getOrNull(itemStatusNameIdx)?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                        siteName = dataTokens.getOrNull(siteIdx)?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                        source = dataTokens.getOrNull(sourceIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        price = dataTokens.getOrNull(priceIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        priceCurrency = dataTokens.getOrNull(priceCurrencyIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        invoiceDate = dataTokens.getOrNull(invoiceDateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        inputDate = dataTokens.getOrNull(inputDateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        lastUpdate = dataTokens.getOrNull(lastUpdateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                        pairingStatus = PairingStatus.NOT_PAIRED
                                    )
                                )
                            } catch (e: IndexOutOfBoundsException) {
                                warnings.add("Baris ${currentDataLineNumber}: Dilewati karena format kolom tidak sesuai (kurang kolom). Isi baris: ${dataTokens.joinToString(",")}")
                                Log.w(TAG, "Baris ${currentDataLineNumber}: IndexOutOfBounds - ${dataTokens.joinToString(",")}", e)
                            } catch (e: Exception) {
                                warnings.add("Baris ${currentDataLineNumber}: Error saat parsing baris '${dataTokens.joinToString(",")}'. Pesan: ${e.message}")
                                Log.e(TAG, "Baris ${currentDataLineNumber}: Generic error - ${dataTokens.joinToString(",")}", e)
                            }
                            currentDataLineNumber++
                        }
                    }
                }
                ParseResult.Success(books, warnings)
            } catch (e: CsvValidationException) {
                Log.e(TAG, "Error validasi CSV fatal (mungkin di awal atau format umum): ${e.message}", e)
                ParseResult.Error("Format CSV tidak valid: ${e.message}. Pastikan file adalah CSV yang benar.")
            } catch (e: Exception) {
                Log.e(TAG, "Error fatal saat parsing CSV: ${e.message}", e)
                ParseResult.Error("Gagal memproses file CSV: ${e.message}")
            }
        }
    }
}
