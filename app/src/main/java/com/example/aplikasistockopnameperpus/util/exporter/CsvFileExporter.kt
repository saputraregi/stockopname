package com.example.aplikasistockopnameperpus.util.exporter

import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvFileExporter : FileExporter {

    companion object {
        private const val TAG = "CsvFileExporter"
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
                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    // Tulis header
                    writer.append(headers.joinToString(","))
                    writer.newLine()

                    // Tulis data buku
                    books.forEach { book ->
                        val line = listOfNotNull(
                            book.itemCode.csvEscape(),
                            book.title.csvEscape(),
                            book.rfidTagHex.csvEscape(),
                            book.expectedLocation.csvEscape(),
                            book.tid.csvEscape(),
                            book.scanStatus.csvEscape(),
                            book.lastSeenTimestamp?.let {
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
                            } ?: ""
                        ).joinToString(",")
                        writer.append(line)
                        writer.newLine()
                    }
                    writer.flush()
                }
                // Karena kita menerima OutputStream, kita tidak tahu path file nya di sini.
                // ViewModel yang memanggil ini seharusnya yang bertanggung jawab atas path.
                // Untuk konsistensi, kita bisa mengembalikan path dummy atau meminta path sebagai argumen.
                // Atau, ubah signature untuk tidak mengembalikan filePath jika hanya stream.
                // Untuk saat ini, kita akan mengasumsikan ViewModel yang mengelola nama file dan path.
                ExportResult.Success("Exported to provided stream", books.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting books to CSV: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor data buku ke CSV: ${e.message}")
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
                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.append(headers.joinToString(","))
                    writer.newLine()

                    opnameItems.forEach { item ->
                        val scanTimestampStr = if (item.scanTimestamp > 0)
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.scanTimestamp))
                        else ""

                        val line = listOfNotNull(
                            item.reportId.toString(),
                            item.rfidTagHexScanned.csvEscape(),
                            item.tidScanned.csvEscape(),
                            item.itemCodeMaster.csvEscape(),
                            item.titleMaster.csvEscape(),
                            scanTimestampStr,
                            item.status.csvEscape()
                        ).joinToString(",")
                        writer.append(line)
                        writer.newLine()
                    }
                    writer.flush()
                }
                ExportResult.Success("Exported to provided stream", opnameItems.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting opname items to CSV: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor item opname ke CSV: ${e.message}")
            }
        }
    }

    // Helper untuk escape karakter khusus CSV
    private fun String?.csvEscape(): String {
        if (this == null) return ""
        // Jika mengandung koma, newline, atau double quote, kelilingi dengan double quote
        // dan escape double quote internal dengan menggandakannya.
        return if (this.contains(",") || this.contains("\n") || this.contains("\"")) {
            "\"${this.replace("\"", "\"\"")}\""
        } else {
            this
        }
    }
}
