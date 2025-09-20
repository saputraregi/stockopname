package com.example.aplikasistockopnameperpus.util.parser

import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.data.database.toEPC96Hex
import com.example.aplikasistockopnameperpus.util.Constants
import android.util.Log
import com.opencsv.CSVParserBuilder // Import CSVParserBuilder
import com.opencsv.CSVReaderBuilder // Import CSVReaderBuilder
import com.opencsv.exceptions.CsvValidationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class CsvFileParser : FileParser {

    companion object {
        private const val TAG = "CsvFileParser"
        private const val DEFAULT_DELIMITER = ',' // Koma sebagai default
        private const val SEMICOLON_DELIMITER = ';' // Semikolon
    }

    private fun getColumnIndex(headerTokens: Array<String>?, columnName: String): Int {
        if (headerTokens == null) return -1
        return headerTokens.indexOfFirst { it.trim().equals(columnName, ignoreCase = true) }
    }

    // Anda bisa menambahkan parameter delimiter di sini jika ingin lebih fleksibel
    override suspend fun parse(inputStream: InputStream, onProgress: ((Int) -> Unit)?): ParseResult {
        return withContext(Dispatchers.IO) {
            val books = mutableListOf<BookMaster>()
            val warnings = mutableListOf<String>()
            var processedLineCount = 0

            try {
                InputStreamReader(inputStream, StandardCharsets.UTF_8).use { streamReader ->
                    // --- PERUBAHAN DI SINI ---
                    // Tentukan delimiter yang akan digunakan. Untuk contoh ini, kita asumsikan semikolon.
                    // Jika Anda ingin mendeteksi atau menerima parameter, logikanya akan lebih kompleks.
                    val currentDelimiter = SEMICOLON_DELIMITER // Atau DEFAULT_DELIMITER jika itu yang utama

                    val parser = CSVParserBuilder()
                        .withSeparator(currentDelimiter) // Tentukan delimiter di sini
                        // .withQuoteChar('"') // Anda bisa mengatur karakter kutipan jika perlu
                        .build()

                    CSVReaderBuilder(streamReader)
                        .withCSVParser(parser)
                        .build()
                        .use { csvReader ->
                            // --- AKHIR PERUBAHAN ---

                            val headerTokens: Array<String>? = try {
                                csvReader.readNext()
                            } catch (e: CsvValidationException) {
                                Log.e(TAG, "Error validasi CSV saat membaca header (baris ~1) dengan delimiter '$currentDelimiter': ${e.message}", e)
                                return@withContext ParseResult.Error("Format CSV tidak valid pada header: ${e.message}")
                            }

                            // ... sisa kode Anda tetap sama ...
                            // (pengecekan headerTokens, loop while, dll.)

                            if (headerTokens == null) {
                                warnings.add("File CSV kosong atau tidak ada baris header.")
                                return@withContext ParseResult.Success(books, warnings)
                            }
                            processedLineCount++
                            onProgress?.invoke(processedLineCount)
                            Log.d(TAG, "Header (delimiter '$currentDelimiter'): ${headerTokens.joinToString(", ")}")

                            val itemCodeIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.ITEM_CODE)
                            val titleIdx = getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.TITLE)
                            // ... (columnIndex lainnya)

                            if (itemCodeIdx == -1 || titleIdx == -1) {
                                val missing = mutableListOf<String>()
                                if (itemCodeIdx == -1) missing.add("'${Constants.SlimsCsvHeaders.ITEM_CODE}'")
                                if (titleIdx == -1) missing.add("'${Constants.SlimsCsvHeaders.TITLE}'")
                                val errorMessage = "Header ${missing.joinToString(" dan ")} tidak ditemukan. Pastikan file CSV memiliki kolom tersebut dan delimiter yang benar ('$currentDelimiter')."
                                Log.e(TAG, "$errorMessage Header yang ditemukan: ${headerTokens.joinToString(", ")}")
                                return@withContext ParseResult.InvalidFormat(errorMessage)
                            }

                            var currentDataLineNumber = 1
                            var dataTokens: Array<String>?

                            while (true) {
                                dataTokens = try {
                                    csvReader.readNext()
                                } catch (e: CsvValidationException) {
                                    val approxFileLineNumberForLog = processedLineCount + 1
                                    warnings.add("Baris data ${currentDataLineNumber} (sekitar baris file ${approxFileLineNumberForLog}): Error validasi CSV, baris dilewati. Pesan: ${e.message}")
                                    Log.w(TAG, "Baris data ${currentDataLineNumber} (sekitar baris file ${approxFileLineNumberForLog}): CsvValidationException - ${e.message}. Detail: Line ${e.lineNumber}", e)
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
                                        currentDataLineNumber++
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
                                            callNumber = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.CALL_NUMBER))?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                            collectionType = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.COLLECTION_TYPE_NAME))?.trim()?.takeIf { it.isNotEmpty() },
                                            inventoryCode = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.INVENTORY_CODE))?.trim()?.takeIf { it.isNotEmpty() },
                                            receivedDate = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.RECEIVED_DATE))?.trim()?.takeIf { it.isNotEmpty() },
                                            locationName = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.LOCATION_NAME))?.trim()?.takeIf { it.isNotEmpty() },
                                            orderDate = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.ORDER_DATE))?.trim()?.takeIf { it.isNotEmpty() },
                                            slimsItemStatus = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.ITEM_STATUS_NAME))?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                            siteName = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.SITE))?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                            source = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.SOURCE))?.trim()?.takeIf { it.isNotEmpty() },
                                            price = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.PRICE))?.trim()?.takeIf { it.isNotEmpty() },
                                            priceCurrency = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.PRICE_CURRENCY))?.trim()?.takeIf { it.isNotEmpty() },
                                            invoiceDate = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.INVOICE_DATE))?.trim()?.takeIf { it.isNotEmpty() },
                                            inputDate = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.INPUT_DATE))?.trim()?.takeIf { it.isNotEmpty() },
                                            lastUpdate = dataTokens.getOrNull(getColumnIndex(headerTokens, Constants.SlimsCsvHeaders.LAST_UPDATE))?.trim()?.takeIf { it.isNotEmpty() },
                                            pairingStatus = PairingStatus.NOT_PAIRED
                                        )
                                    )
                                } catch (e: IndexOutOfBoundsException) {
                                    warnings.add("Baris ${currentDataLineNumber}: Dilewati karena format kolom tidak sesuai (kurang kolom). Isi baris: ${dataTokens.joinToString(currentDelimiter.toString())}") // Gunakan delimiter yang benar untuk join
                                    Log.w(TAG, "Baris ${currentDataLineNumber}: IndexOutOfBounds - ${dataTokens.joinToString(currentDelimiter.toString())}", e)
                                } catch (e: Exception) {
                                    warnings.add("Baris ${currentDataLineNumber}: Error saat parsing baris '${dataTokens.joinToString(currentDelimiter.toString())}'. Pesan: ${e.message}")
                                    Log.e(TAG, "Baris ${currentDataLineNumber}: Generic error - ${dataTokens.joinToString(currentDelimiter.toString())}", e)
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
