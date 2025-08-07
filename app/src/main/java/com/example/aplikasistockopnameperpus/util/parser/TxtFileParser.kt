package com.example.aplikasistockopnameperpus.util.parser

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.data.database.toEPC128Hex
import com.example.aplikasistockopnameperpus.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.IOException // Pastikan ini diimpor jika ada operasi I/O yang bisa melemparnya
import java.nio.charset.StandardCharsets

class TxtFileParser(private val delimiter: Char = ',') : FileParser { // Default delimiter koma

    companion object {
        private const val TAG = "TxtFileParser"
    }

    private fun getColumnIndex(headerLine: String, columnName: String): Int {
        // Menggunakan delimiter yang dikonfigurasi
        return headerLine.split(delimiter).indexOfFirst { it.trim().equals(columnName, ignoreCase = true) }
    }

    override suspend fun parse(inputStream: InputStream, onProgress: ((Int) -> Unit)?): ParseResult {
        return withContext(Dispatchers.IO) {
            val books = mutableListOf<BookMaster>()
            val warnings = mutableListOf<String>()
            var processedLineCount = 0

            try {
                // Menggunakan bufferedReader dengan charset yang sesuai, UTF-8 adalah pilihan yang baik
                inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    val lineIterator = lines.iterator()

                    if (!lineIterator.hasNext()) {
                        warnings.add("File TXT/Plain Text kosong.")
                        return@useLines // Keluar dari useLines, akan lanjut ke return Success di bawah
                    }

                    val headerLine = lineIterator.next()
                    processedLineCount++
                    onProgress?.invoke(processedLineCount)

                    // Dapatkan indeks kolom berdasarkan nama header dari Constants
                    val itemCodeIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.ITEM_CODE)
                    val titleIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.TITLE)
                    val callNumberIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.CALL_NUMBER)
                    val collTypeNameIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.COLLECTION_TYPE_NAME)
                    val inventoryCodeIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.INVENTORY_CODE)
                    val receivedDateIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.RECEIVED_DATE)
                    val locationNameIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.LOCATION_NAME)
                    val orderDateIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.ORDER_DATE)
                    val itemStatusNameIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.ITEM_STATUS_NAME)
                    val siteIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.SITE)
                    val sourceIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.SOURCE)
                    val priceIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.PRICE)
                    val priceCurrencyIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.PRICE_CURRENCY)
                    val invoiceDateIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.INVOICE_DATE)
                    val inputDateIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.INPUT_DATE)
                    val lastUpdateIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.LAST_UPDATE)

                    if (itemCodeIdx == -1 || titleIdx == -1) {
                        Log.e(TAG, "Header '${Constants.SlimsCsvHeaders.ITEM_CODE}' atau '${Constants.SlimsCsvHeaders.TITLE}' tidak ditemukan di file TXT.")
                        // Menggunakan pesan yang lebih spesifik jika ada
                        return@withContext ParseResult.InvalidFormat("Header wajib '${Constants.SlimsCsvHeaders.ITEM_CODE}' atau '${Constants.SlimsCsvHeaders.TITLE}' tidak ditemukan.")
                    }

                    var currentDataLineNumber = 1 // Nomor baris data setelah header
                    lineIterator.forEach { line ->
                        processedLineCount++
                        currentDataLineNumber++
                        onProgress?.invoke(processedLineCount)

                        // Menggunakan delimiter yang dikonfigurasi
                        val tokens = line.split(delimiter)
                        try {
                            val itemCode = tokens.getOrNull(itemCodeIdx)?.trim()
                            val title = tokens.getOrNull(titleIdx)?.trim()

                            if (itemCode.isNullOrBlank()) {
                                warnings.add("Baris ${currentDataLineNumber}: Dilewati karena '${Constants.SlimsCsvHeaders.ITEM_CODE}' kosong atau hanya spasi.")
                                return@forEach // Lanjut ke baris berikutnya
                            }
                            if (title.isNullOrBlank()) {
                                warnings.add("Baris ${currentDataLineNumber} (Item Code: $itemCode): '${Constants.SlimsCsvHeaders.TITLE}' kosong atau hanya spasi, tetap diimpor dengan judul kosong.")
                            }

                            val rfidHex = itemCode.toEPC128Hex()

                            books.add(
                                BookMaster(
                                    itemCode = itemCode,
                                    title = title ?: "",
                                    rfidTagHex = rfidHex,
                                    tid = null, // Sesuaikan jika ada kolom TID di file TXT
                                    callNumber = tokens.getOrNull(callNumberIdx)?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                    collectionType = tokens.getOrNull(collTypeNameIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    inventoryCode = tokens.getOrNull(inventoryCodeIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    receivedDate = tokens.getOrNull(receivedDateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    locationName = tokens.getOrNull(locationNameIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    orderDate = tokens.getOrNull(orderDateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    slimsItemStatus = tokens.getOrNull(itemStatusNameIdx)?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                    siteName = tokens.getOrNull(siteIdx)?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "nan" },
                                    source = tokens.getOrNull(sourceIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    price = tokens.getOrNull(priceIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    priceCurrency = tokens.getOrNull(priceCurrencyIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    invoiceDate = tokens.getOrNull(invoiceDateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    inputDate = tokens.getOrNull(inputDateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    lastUpdate = tokens.getOrNull(lastUpdateIdx)?.trim()?.takeIf { it.isNotEmpty() },
                                    pairingStatus = PairingStatus.NOT_PAIRED // Default status
                                )
                            )
                        } catch (e: IndexOutOfBoundsException) {
                            warnings.add("Baris ${currentDataLineNumber}: Dilewati karena format kolom tidak sesuai (kurang kolom dari yang diharapkan berdasarkan delimiter '${delimiter}'). Isi baris: $line")
                            Log.w(TAG, "Baris ${currentDataLineNumber}: IndexOutOfBounds - $line", e)
                        } catch (e: Exception) {
                            warnings.add("Baris ${currentDataLineNumber}: Error saat parsing baris '$line'. Pesan: ${e.message}")
                            Log.e(TAG, "Baris ${currentDataLineNumber}: Generic error - $line", e)
                        }
                    }
                } // Akhir dari useLines
                ParseResult.Success(books, warnings)
            } catch (e: IOException) {
                // Ini bisa terjadi jika ada masalah saat membaca stream
                Log.e(TAG, "Error I/O saat parsing file TXT: ${e.message}", e)
                ParseResult.Error("Gagal memproses file TXT karena masalah I/O: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error fatal saat parsing file TXT: ${e.message}", e)
                ParseResult.Error("Gagal memproses file TXT: ${e.message}")
            }
        }
    }
}
