package com.example.aplikasistockopnameperpus.util.parser

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class TxtFileParser : FileParser {

    companion object {
        private const val TAG = "TxtFileParser"
    }

    override suspend fun parse(inputStream: InputStream, onProgress: ((Int) -> Unit)?): ParseResult {
        // Implementasi TXT parser ini mengasumsikan format yang mirip dengan CSV.
        // Anda mungkin perlu menyesuaikannya jika format TXT Anda berbeda (misalnya, fixed-width, tab-separated).
        return withContext(Dispatchers.IO) {
            val books = mutableListOf<BookMaster>()
            val warnings = mutableListOf<String>()
            var lineNumber = 0
            var isHeaderChecked = false

            try {
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String
                    var columnIndices: Map<String, Int>? = null
                    var assumedDelimiter = ',' // Default ke koma

                    // Coba deteksi header dan delimiter di beberapa baris pertama
                    val firstLines = mutableListOf<String>()
                    for (i in 0..2) { // Baca beberapa baris awal untuk coba deteksi
                        reader.readLine()?.also { firstLines.add(it) } ?: break
                    }

                    // Reset reader atau gunakan stream baru jika Anda ingin membaca ulang dari awal
                    // Untuk kesederhanaan, kita akan proses `firstLines` lalu lanjut dari `reader`

                    for (firstLine in firstLines) {
                        lineNumber++
                        onProgress?.invoke(lineNumber)
                        line = firstLine

                        // Coba tebak delimiter
                        if (!isHeaderChecked) {
                            if (line.contains("\t")) assumedDelimiter = '\t'
                            else if (line.contains(";")) assumedDelimiter = ';'
                        }

                        val parts = line.split(assumedDelimiter).map { it.trim().replace("\"", "") }

                        if (!isHeaderChecked) {
                            val headers = parts.map { it.uppercase() }
                            if (headers.containsAll(Constants.DEFAULT_CSV_BOOK_HEADERS.take(3))) {
                                columnIndices = mapHeadersToIndices(headers, Constants.DEFAULT_CSV_BOOK_HEADERS)
                                isHeaderChecked = true
                                if (!headers.containsAll(Constants.DEFAULT_CSV_BOOK_HEADERS)) {
                                    warnings.add("Peringatan di baris $lineNumber: Header TXT (delimiter: '$assumedDelimiter') tidak sepenuhnya cocok. Beberapa data mungkin tidak terbaca.")
                                }
                                continue // Lewati baris header
                            } else if (lineNumber == firstLines.size) { // Gagal deteksi header di semua baris sampel
                                Log.w(TAG, "Header TXT tidak valid: $headers")
                                return@withContext ParseResult.Error("Format header TXT tidak valid (delimiter: '$assumedDelimiter'). Pastikan ITEMCODE, TITLE, RFIDTAGHEX ada.", lineNumber)
                            }
                            // Jika belum di baris terakhir sampel dan belum ketemu header, coba baris berikutnya
                        } else {
                            // Proses baris data pertama yang mungkin sudah terbaca dari `firstLines`
                            processDataLine(line, columnIndices, books, warnings, lineNumber, assumedDelimiter)
                        }
                    }


                    // Lanjutkan membaca sisa file jika header sudah terdeteksi
                    if (!isHeaderChecked && books.isEmpty()) { // Jika setelah sampel tidak ada header dan tidak ada buku
                        return@withContext ParseResult.Error("Tidak dapat menemukan header yang valid atau data buku di file TXT.", lineNumber)
                    } else if (!isHeaderChecked && books.isNotEmpty()){
                        // Misal, tidak ada header tapi data mirip, ini case yang rumit, mungkin perlu asumsi urutan kolom.
                        // Untuk saat ini, anggap header wajib.
                        return@withContext ParseResult.Error("Header tidak ditemukan di file TXT, tidak dapat melanjutkan.", lineNumber)
                    }


                    while (reader.readLine().also { line = it } != null) {
                        lineNumber++
                        onProgress?.invoke(lineNumber)
                        processDataLine(line!!, columnIndices, books, warnings, lineNumber, assumedDelimiter)
                        if (books.size >= Constants.MAX_ROWS_TO_PARSE) {
                            warnings.add("Mencapai batas maksimum ${Constants.MAX_ROWS_TO_PARSE} baris untuk diproses.")
                            break
                        }
                    }
                }

                if (books.isEmpty() && lineNumber > 1 && isHeaderChecked) { // Ada header tapi tidak ada data valid
                    ParseResult.Error("Tidak ada data buku yang valid ditemukan di file TXT.", lineNumber)
                } else if (books.isEmpty() && lineNumber <=1 && isHeaderChecked) {
                    ParseResult.Error("File TXT hanya berisi header atau kosong.", lineNumber)
                }
                else {
                    ParseResult.Success(books, warnings)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing TXT file: ${e.message}", e)
                ParseResult.Error("Terjadi kesalahan saat memproses file TXT: ${e.message}", lineNumber)
            }
        }
    }

    private fun processDataLine(
        line: String,
        columnIndices: Map<String, Int>?,
        books: MutableList<BookMaster>,
        warnings: MutableList<String>,
        lineNumber: Int,
        delimiter: Char
    ) {
        if (columnIndices == null) {
            warnings.add("Peringatan di baris $lineNumber: Kesalahan internal, indeks kolom tidak siap. Baris dilewati.")
            return
        }

        val parts = line.split(delimiter).map { it.trim().replace("\"", "") }

        if (parts.size < Constants.DEFAULT_CSV_BOOK_HEADERS.size - 2) { // Minimal itemcode, title, rfid
            warnings.add("Peringatan di baris $lineNumber: Jumlah kolom kurang dari yang diharapkan (${parts.size}). Baris dilewati.")
            return
        }

        val itemCode = getColumnValue(parts, columnIndices, "ITEMCODE")
        val title = getColumnValue(parts, columnIndices, "TITLE")
        val rfidTagHex = getColumnValue(parts, columnIndices, "RFIDTAGHEX")
        val expectedLocation = getColumnValue(parts, columnIndices, "EXPECTEDLOCATION")
        val tid = getColumnValue(parts, columnIndices, "TID")

        if (itemCode.isNullOrBlank() || title.isNullOrBlank() || rfidTagHex.isNullOrBlank()) {
            warnings.add("Peringatan di baris $lineNumber: ITEMCODE, TITLE, atau RFIDTAGHEX kosong. Baris dilewati.")
            return
        }
        if (rfidTagHex.length < 8 || !rfidTagHex.matches(Regex("^[0-9a-fA-F]+$"))) {
            warnings.add("Peringatan di baris $lineNumber: RFIDTAGHEX '$rfidTagHex' tidak valid. Baris dilewati.")
            return
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
    }


    private fun mapHeadersToIndices(headers: List<String>, expectedHeaders: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        expectedHeaders.forEach { expectedHeader ->
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
