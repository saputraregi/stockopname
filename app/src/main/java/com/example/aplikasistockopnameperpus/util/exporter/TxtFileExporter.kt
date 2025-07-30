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

class TxtFileExporter(private val delimiter: String = ",") : FileExporter { // Default delimiter koma

    companion object {
        private const val TAG = "TxtFileExporter"
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
                    writer.append(headers.joinToString(delimiter))
                    writer.newLine()

                    // Tulis data buku
                    books.forEach { book ->
                        val line = listOfNotNull(
                            book.itemCode,
                            book.title,
                            book.rfidTagHex,
                            book.expectedLocation,
                            book.tid,
                            book.scanStatus,
                            book.lastSeenTimestamp?.let {
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
                            } ?: ""
                        ).joinToString(delimiter) { it ?: "" } // Pastikan null menjadi string kosong
                        writer.append(line)
                        writer.newLine()
                    }
                    writer.flush()
                }
                ExportResult.Success("Exported to provided stream", books.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting books to TXT: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor data buku ke TXT: ${e.message}")
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
                    writer.append(headers.joinToString(delimiter))
                    writer.newLine()

                    opnameItems.forEach { item ->
                        val scanTimestampStr = if (item.scanTimestamp > 0)
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.scanTimestamp))
                        else ""

                        val line = listOfNotNull(
                            item.reportId.toString(),
                            item.rfidTagHexScanned,
                            item.tidScanned,
                            item.itemCodeMaster,
                            item.titleMaster,
                            scanTimestampStr,
                            item.status
                        ).joinToString(delimiter) { it ?: "" }
                        writer.append(line)
                        writer.newLine()
                    }
                    writer.flush()
                }
                ExportResult.Success("Exported to provided stream", opnameItems.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error exporting opname items to TXT: ${e.message}", e)
                ExportResult.Error("Gagal mengekspor item opname ke TXT: ${e.message}")
            }
        }
    }
}
