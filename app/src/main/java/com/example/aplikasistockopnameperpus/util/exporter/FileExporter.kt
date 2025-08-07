package com.example.aplikasistockopnameperpus.util.exporter

import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem // Jika perlu export detail opname
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import java.io.OutputStream

/**
 * Hasil dari operasi export file.
 */
sealed class ExportResult {
    data class Success(val filePath: String, val itemsExported: Int) : ExportResult()
    data class Error(val errorMessage: String) : ExportResult()
    object NoDataToExport : ExportResult()
}

/**
 * Interface untuk semua jenis file exporter.
 */
interface FileExporter {
    /**
     * Mengekspor daftar BookMaster ke output stream.
     * @param books Daftar buku yang akan diekspor.
     * @param outputStream Stream tujuan untuk menulis file.
     * @param headers Daftar header kolom (opsional, bisa default).
     * @return ExportResult yang berisi path file atau pesan error.
     */
    suspend fun exportBooks(
        books: List<BookMaster>,
        outputStream: OutputStream,
        headers: List<String> = listOf("ITEMCODE", "TITLE", "RFIDTAGHEX", "EXPECTEDLOCATION", "TID", "SCANSTATUS", "LASTSEENTIMESTAMP")
    ): ExportResult


    suspend fun exportOpnameItems(
        opnameItems: List<StockOpnameItem>,
        outputStream: OutputStream,
        headers: List<String> = listOf( // Perhatikan default headers ini
            "REPORT_ID", "RFID_SCANNED", "TID_SCANNED",
            "ITEMCODE_MASTER", "TITLE_MASTER",
            "SCAN_TIMESTAMP", "STATUS"
        )
    ): ExportResult

    /**
     * Mengekspor daftar item hasil opname.
     * Anda bisa membuat fungsi serupa untuk StockOpnameReport jika diperlukan.
     */
    suspend fun exportOpnameResults(
        opnameItems: List<StockOpnameItem>,
        report: StockOpnameReport?, // Jadikan report opsional jika bisa null
        outputStream: OutputStream,
        headers: List<String> = listOf( // Contoh header default
            "REPORT_ID", "REPORT_START_TIME", "REPORT_END_TIME", "USER_NAME", "LOCATION",
            "ITEMCODE_MASTER", "TITLE_MASTER", /* "RFID_MASTER", */
            "RFID_SCANNED", "TID_SCANNED", "SCAN_TIMESTAMP", "STATUS"
        )
    ): ExportResult
}
