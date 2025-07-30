package com.example.aplikasistockopnameperpus.util.parser

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class CsvFileParser : FileParser {

    companion object {
        private const val TAG = "CsvFileParser"
    }

    override suspend fun parse(inputStream: InputStream, onProgress: ((Int) -> Unit)?): ParseResult {
        return withContext(Dispatchers.IO) {
            val books = mutableListOf<BookMaster>()
            val warnings = mutableListOf<String>()
            var lineNumber = 0
            var isHeaderChecked = false

            try {
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    var columnIndices: Map<String, Int>? = null

                    while (reader.readLine().also { line = it } != null) {
                        lineNumber++
                        onProgress?.invoke(lineNumber)

                        val parts = line!!.split(",").map { it.trim().replace("\"", "") }

                        if (!isHeaderChecked) {
                            // Validasi header (opsional tapi direkomendasikan)
                            // Ini mengasumsikan header mirip dengan Constants.DEFAULT_CSV_BOOK_HEADERS
                            val headers = parts.map { it.uppercase() }
                            if (headers.containsAll(Constants.DEFAULT_CSV_BOOK_HEADERS.take(3))) { // Cek minimal itemcode, title, rfid
                                columnIndices = mapHeadersToIndices(headers)
                                isHeaderChecked = true
                                if (!headers.containsAll(Constants.DEFAULT_CSV_BOOK_HEADERS)) {
                                    warnings.add("Peringatan di baris $lineNumber: Header CSV tidak sepenuhnya cocok dengan format standar. Beberapa data mungkin tidak terbaca.")
                                }
                                continue // Lewati baris header
                            } else {
                                Log.w(TAG, "Header CSV tidak valid: $headers")
                                return@withContext ParseResult.Error("Format header CSV tidak valid. Pastikan kolom ITEMCODE, TITLE, RFIDTAGHEX ada.", lineNumber)
                            }
                        }

                        if (columnIndices == null) {
                            return@withContext ParseResult.Error("Kesalahan internal: Indeks kolom tidak terinisialisasi.", lineNumber)
                        }

                        if (parts.size < Constants.DEFAULT_CSV_BOOK_HEADERS.size - 2) { // Minimal itemcode, title, rfid
                            warnings.add("Peringatan di baris $lineNumber: Jumlah kolom kurang dari yang diharapkan (${parts.size}). Baris dilewati.")
                            continue
                        }

                        val itemCode = getColumnValue(parts, columnIndices, "ITEMCODE")
                        val title = getColumnValue(parts, columnIndices, "TITLE")
                        val rfidTagHex = getColumnValue(parts, columnIndices, "RFIDTAGHEX")
                        val expectedLocation = getColumnValue(parts, columnIndices, "EXPECTEDLOCATION")
                        val tid = getColumnValue(parts, columnIndices, "TID") // Opsional

                        if (itemCode.isNullOrBlank() || title.isNullOrBlank() || rfidTagHex.isNullOrBlank()) {
                            warnings.add("Peringatan di baris $lineNumber: ITEMCODE, TITLE, atau RFIDTAGHEX kosong. Baris dilewati.")
                            continue
                        }
                        if (rfidTagHex.length < 8 || !rfidTagHex.matches(Regex("^[0-9a-fA-F]+$"))) { // Validasi sederhana EPC
                            warnings.add("Peringatan di baris $lineNumber: RFIDTAGHEX '$rfidTagHex' tidak valid. Baris dilewati.")
                            continue
                        }


                        books.add(
                            BookMaster(
                                itemCode = itemCode,
                                title = title,
                                rfidTagHex = rfidTagHex.uppercase(), // Standarisasi ke uppercase
                                expectedLocation = expectedLocation,
                                tid = tid,
                                // scanStatus dan lastSeenTimestamp akan null secara default
                            )
                        )
                        if (books.size >= Constants.MAX_ROWS_TO_PARSE) {
                            warnings.add("Mencapai batas maksimum ${Constants.MAX_ROWS_TO_PARSE} baris untuk diproses.")
                            break
                        }
                    }
                }
                if (books.isEmpty() && lineNumber > 1) { // Ada baris data tapi tidak ada yang valid
                    ParseResult.Error("Tidak ada data buku yang valid ditemukan di file CSV.", lineNumber)
                } else if (books.isEmpty() && lineNumber <=1 && isHeaderChecked) { // Hanya header valid
                    ParseResult.Error("File CSV hanya berisi header atau kosong.", lineNumber)
                }
                else {
                    ParseResult.Success(books, warnings)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing CSV file: ${e.message}", e)
                ParseResult.Error("Terjadi kesalahan saat memproses file CSV: ${e.message}", lineNumber)
            }
        }
    }

    private fun mapHeadersToIndices(headers: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        Constants.DEFAULT_CSV_BOOK_HEADERS.forEach { expectedHeader ->
            val index = headers.indexOf(expectedHeader.uppercase())
            if (index != -1) {
                map[expectedHeader] = index
            }
        }
        return map
    }

    private fun getColumnValue(parts: List<String>, indices: Map<String, Int>, columnName: String): String? {
        val index = indices[columnName]
        return if (index != null && index < parts.size) parts[index].take(Constants.MAX_CELL_LENGTH) else null
    }
}
