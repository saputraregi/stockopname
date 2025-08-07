package com.example.aplikasistockopnameperpus.util.parser

import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.data.database.toEPC128Hex
import com.example.aplikasistockopnameperpus.util.Constants
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.StandardCharsets

// Impor ParseResult dari file yang sama atau package yang benar
// import com.example.aplikasistockopnameperpus.util.parser.ParseResult

class CsvFileParser : FileParser { // Implementasikan interface FileParser

    private fun getColumnIndex(headerLine: String, columnName: String, delimiter: Char = ','): Int {
        return headerLine.split(delimiter).indexOfFirst { it.trim().equals(columnName, ignoreCase = true) }
    }

    override suspend fun parse(inputStream: InputStream, onProgress: ((Int) -> Unit)?): ParseResult {
        // Jalankan parsing di Dispatchers.IO karena ini operasi I/O
        return withContext(Dispatchers.IO) {
            val books = mutableListOf<BookMaster>()
            val warnings = mutableListOf<String>()
            var processedLineCount = 0

            try {
                inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    val lineIterator = lines.iterator()
                    if (!lineIterator.hasNext()) {
                        warnings.add("File CSV kosong.")
                        // Jika file kosong dianggap sukses dengan 0 buku, bukan error
                        return@useLines // Keluar dari useLines, akan lanjut ke return Success di bawah
                    }

                    val headerLine = lineIterator.next()
                    processedLineCount++
                    onProgress?.invoke(processedLineCount)

                    val itemCodeIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.ITEM_CODE)
                    val titleIdx = getColumnIndex(headerLine, Constants.SlimsCsvHeaders.TITLE)
                    // ... (dapatkan semua indeks kolom lainnya seperti sebelumnya) ...
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
                        Log.e("CsvFileParser", "Header 'item_code' atau 'title' tidak ditemukan.")
                        return@withContext ParseResult.InvalidFormat("Header 'item_code' atau 'title' tidak ditemukan.") // Format file tidak sesuai harapan
                    }

                    var currentDataLineNumber = 1 // Nomor baris data setelah header
                    lineIterator.forEach { line ->
                        processedLineCount++
                        currentDataLineNumber++
                        onProgress?.invoke(processedLineCount) // Laporkan progress per baris yang diproses

                        val tokens = line.split(',') // Atau delimiter lain yang sesuai
                        try {
                            val itemCode = tokens.getOrNull(itemCodeIdx)?.trim()
                            val title = tokens.getOrNull(titleIdx)?.trim()

                            if (itemCode.isNullOrBlank()) { // itemCode adalah kunci utama
                                warnings.add("Baris ${currentDataLineNumber}: Dilewati karena 'item_code' kosong atau hanya spasi.")
                                return@forEach // Lanjut ke baris berikutnya
                            }
                            if (title.isNullOrBlank()) {
                                warnings.add("Baris ${currentDataLineNumber} (Item Code: $itemCode): 'title' kosong atau hanya spasi, tetap diimpor dengan judul kosong.")
                                // Bisa juga dilewati jika title wajib
                            }

                            val rfidHex = itemCode.toEPC128Hex()

                            books.add(
                                BookMaster(
                                    itemCode = itemCode, // Sudah pasti non-null di sini
                                    title = title ?: "", // Beri nilai default jika null setelah trim
                                    rfidTagHex = rfidHex,
                                    tid = null,
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
                                    pairingStatus = PairingStatus.NOT_PAIRED
                                )
                            )
                        } catch (e: IndexOutOfBoundsException) {
                            warnings.add("Baris ${currentDataLineNumber}: Dilewati karena format kolom tidak sesuai (kurang kolom). Isi baris: $line")
                            Log.w("CsvFileParser", "Baris ${currentDataLineNumber}: IndexOutOfBounds - $line", e)
                        } catch (e: Exception) {
                            // Untuk error tak terduga lainnya pada satu baris, catat sebagai peringatan dan lanjutkan jika memungkinkan
                            warnings.add("Baris ${currentDataLineNumber}: Error saat parsing baris '$line'. Pesan: ${e.message}")
                            Log.e("CsvFileParser", "Baris ${currentDataLineNumber}: Generic error - $line", e)
                        }
                    }
                } // Akhir dari useLines
                ParseResult.Success(books, warnings)
            } catch (e: Exception) {
                // Error saat membuka file atau error besar lainnya
                Log.e("CsvFileParser", "Error fatal saat parsing CSV: ${e.message}", e)
                ParseResult.Error("Gagal memproses file CSV: ${e.message}")
            }
        }
    }
}
