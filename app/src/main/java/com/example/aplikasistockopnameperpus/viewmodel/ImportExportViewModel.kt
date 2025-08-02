package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.data.database.AppDatabase
import com.example.aplikasistockopnameperpus.data.database.BookMaster
// Import repository, parser, exporter jika sudah dibuat
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.util.parser.CsvFileParser
import com.example.aplikasistockopnameperpus.util.parser.ExcelFileParser
import com.example.aplikasistockopnameperpus.util.parser.TxtFileParser
import com.example.aplikasistockopnameperpus.util.exporter.CsvFileExporter
import com.example.aplikasistockopnameperpus.util.FileType // Pastikan enum ini ada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

// Kelas untuk merepresentasikan status operasi
sealed class ImportExportStatus {
    object Loading : ImportExportStatus()
    data class Progress(val percentage: Int, val processedItems: Int, val totalItems: Int) : ImportExportStatus()
    data class Success(val message: String) : ImportExportStatus()
    data class Error(val errorMessage: String) : ImportExportStatus()
}

class ImportExportViewModel(application: Application) : AndroidViewModel(application) {

    private val bookMasterDao = AppDatabase.getDatabase(application).bookMasterDao()
    // private val repository: BookRepository = BookRepository(bookMasterDao) // Jika menggunakan Repository

    // Untuk menyimpan URI file yang dipilih untuk import
    private var _selectedImportFileUri = MutableLiveData<Uri?>()
    // val selectedImportFileUri: LiveData<Uri?> get() = _selectedImportFileUri // Jika perlu diobservasi

    private val _importStatus = MutableLiveData<ImportExportStatus?>()
    val importStatus: LiveData<ImportExportStatus?> get() = _importStatus

    private val _exportStatus = MutableLiveData<ImportExportStatus?>()
    val exportStatus: LiveData<ImportExportStatus?> get() = _exportStatus

    fun setSelectedFileForImport(uri: Uri?) {
        _selectedImportFileUri.value = uri
    }

    fun clearSelectedFileForImport() {
        _selectedImportFileUri.value = null
    }

    fun startImportMasterData() {
        val uri = _selectedImportFileUri.value ?: run {
            _importStatus.value = ImportExportStatus.Error("Tidak ada file dipilih untuk import.")
            return
        }

        _importStatus.value = ImportExportStatus.Loading
        viewModelScope.launch {
            try {
                val contentResolver = getApplication<Application>().contentResolver
                val inputStream: InputStream? = contentResolver.openInputStream(uri)

                if (inputStream == null) {
                    _importStatus.postValue(ImportExportStatus.Error("Gagal membuka file."))
                    return@launch
                }

                // Tentukan tipe file berdasarkan ekstensi atau MIME type dari URI
                val fileType = determineFileTypeFromUri(uri) // Anda perlu implementasi fungsi ini

                val booksToImport: List<BookMaster> = when (fileType) {
                    FileType.CSV -> {
                        // val csvParser = CsvFileParser() // Instance parser Anda
                        // withContext(Dispatchers.IO) { csvParser.parseFile(inputStream) }
                        // Placeholder:
                        withContext(Dispatchers.IO) { parseCsvPlaceholder(inputStream) }
                    }
                    FileType.EXCEL_XLSX, FileType.EXCEL_XLS -> {
                        // val excelParser = ExcelFileParser() // Instance parser Anda
                        // withContext(Dispatchers.IO) { excelParser.parseFile(inputStream) }
                        // Placeholder:
                        withContext(Dispatchers.IO) { parseExcelPlaceholder(inputStream) }
                    }
                    FileType.TXT -> {
                        // val txtParser = TxtFileParser() // Instance parser Anda
                        // withContext(Dispatchers.IO) { txtParser.parseFile(inputStream) }
                        // Placeholder:
                        withContext(Dispatchers.IO) { parseTxtPlaceholder(inputStream) }
                    }
                    null -> {
                        _importStatus.postValue(ImportExportStatus.Error("Tipe file tidak didukung atau tidak dikenali."))
                        return@launch
                    }
                }
                inputStream.close()


                if (booksToImport.isNotEmpty()) {
                    // Opsi: Hapus data lama sebelum import baru? Atau lakukan merge/update?
                    // Untuk contoh ini, kita replace semua (hati-hati dengan ID jika autoGenerate)
                    // bookMasterDao.clearAllBooks() // Hati-hati dengan ini
                    var importedCount = 0
                    booksToImport.forEachIndexed { index, book ->
                        try {
                            // Validasi data buku sebelum insert
                            if (book.itemCode.isBlank() || book.title.isBlank()){
                                // Log atau skip item yang tidak valid
                                System.err.println("Skipping invalid book: ${book.itemCode}")
                            } else {
                                bookMasterDao.insertOrUpdateBook(book) // Atau insertAll jika parser mengembalikan list
                                importedCount++
                            }
                        } catch (e: Exception) {
                            // Tangani error duplikasi atau lainnya per item jika perlu
                            System.err.println("Error inserting book ${book.itemCode}: ${e.message}")
                        }
                        // Update progress
                        val progressPercentage = ((index + 1).toFloat() / booksToImport.size * 100).toInt()
                        _importStatus.postValue(ImportExportStatus.Progress(progressPercentage, index + 1, booksToImport.size))
                    }

                    _importStatus.postValue(ImportExportStatus.Success("$importedCount dari ${booksToImport.size} buku berhasil diimpor."))
                } else {
                    _importStatus.postValue(ImportExportStatus.Error("Tidak ada data buku yang valid ditemukan dalam file."))
                }

            } catch (e: Exception) {
                _importStatus.postValue(ImportExportStatus.Error("Import gagal: ${e.message}"))
                e.printStackTrace()
            } finally {
                // Reset URI setelah selesai
                // clearSelectedFileForImport() // Pindah ke Activity setelah sukses/error
            }
        }
    }

    fun startExportMasterData(uri: Uri, fileType: FileType) {
        _exportStatus.value = ImportExportStatus.Loading
        viewModelScope.launch {
            try {
                val books = bookMasterDao.getAllBooksList() // Ambil semua buku
                if (books.isEmpty()) {
                    _exportStatus.postValue(ImportExportStatus.Error("Tidak ada data master untuk diekspor."))
                    return@launch
                }

                val contentResolver = getApplication<Application>().contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    when (fileType) {
                        FileType.CSV -> {
                            // val csvExporter = CsvFileExporter()
                            // withContext(Dispatchers.IO) { csvExporter.exportBooks(outputStream, books) }
                            withContext(Dispatchers.IO) { exportCsvPlaceholder(outputStream, books, "master") }
                        }
                        FileType.EXCEL_XLSX -> {
                            // val excelExporter = ExcelFileExporter() // Untuk XLSX
                            // withContext(Dispatchers.IO) { excelExporter.exportBooks(outputStream, books) }
                            withContext(Dispatchers.IO) { exportExcelPlaceholder(outputStream, books, "master") }
                        }
                        // Tambahkan case untuk EXCEL_XLS jika perlu
                        FileType.TXT -> {
                            // val txtExporter = TxtFileExporter()
                            // withContext(Dispatchers.IO) { txtExporter.exportBooks(outputStream, books) }
                            withContext(Dispatchers.IO) { exportTxtPlaceholder(outputStream, books, "master") }
                        }
                        else -> {
                            _exportStatus.postValue(ImportExportStatus.Error("Format export tidak didukung."))
                            return@launch
                        }
                    }
                    _exportStatus.postValue(ImportExportStatus.Success("Data master berhasil diekspor ke ${uri.lastPathSegment}."))
                } ?: _exportStatus.postValue(ImportExportStatus.Error("Gagal membuat file untuk ekspor."))

            } catch (e: Exception) {
                _exportStatus.postValue(ImportExportStatus.Error("Ekspor gagal: ${e.message}"))
                e.printStackTrace()
            }
        }
    }

    fun startExportOpnameResult(uri: Uri, fileType: FileType) {
        _exportStatus.value = ImportExportStatus.Loading
        viewModelScope.launch {
            // TODO: Ambil data hasil stock opname terakhir dari database
            // Misalnya, Anda memiliki DAO untuk StockOpnameReport dan StockOpnameItem
            // val lastReport = stockOpnameReportDao.getLatestReport()
            // val itemsForReport = stockOpnameItemDao.getItemsForReport(lastReport.id)

            // Untuk sekarang, kita buat data dummy atau error jika belum ada
            val dummyOpnameData = listOf(
                mapOf("itemCode" to "OP001", "status" to "Ditemukan", "epc" to "EPC001", "waktu" to System.currentTimeMillis()),
                mapOf("itemCode" to "OP002", "status" to "Tidak Ditemukan", "epc" to "EPC002", "waktu" to 0L)
            )

            if (dummyOpnameData.isEmpty()) { // Ganti dengan pengecekan data opname yang sebenarnya
                _exportStatus.postValue(ImportExportStatus.Error("Tidak ada data hasil opname untuk diekspor."))
                return@launch
            }

            try {
                val contentResolver = getApplication<Application>().contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    when (fileType) {
                        FileType.CSV -> {
                            withContext(Dispatchers.IO) { exportCsvPlaceholder(outputStream, dummyOpnameData, "opname") }
                        }
                        FileType.EXCEL_XLSX -> {
                            withContext(Dispatchers.IO) { exportExcelPlaceholder(outputStream, dummyOpnameData, "opname") }
                        }
                        FileType.TXT -> {
                            withContext(Dispatchers.IO) { exportTxtPlaceholder(outputStream, dummyOpnameData, "opname") }
                        }
                        else -> {
                            _exportStatus.postValue(ImportExportStatus.Error("Format export tidak didukung."))
                            return@launch
                        }
                    }
                    _exportStatus.postValue(ImportExportStatus.Success("Hasil stock opname berhasil diekspor ke ${uri.lastPathSegment}."))
                } ?: _exportStatus.postValue(ImportExportStatus.Error("Gagal membuat file untuk ekspor."))

            } catch (e: Exception) {
                _exportStatus.postValue(ImportExportStatus.Error("Ekspor hasil opname gagal: ${e.message}"))
                e.printStackTrace()
            }
        }
    }

    // --- Helper & Placeholder Functions ---
    private fun determineFileTypeFromUri(uri: Uri): FileType? {
        val fileName = uri.lastPathSegment?.lowercase()
        return when {
            fileName?.endsWith(".csv") == true -> FileType.CSV
            fileName?.endsWith(".xlsx") == true -> FileType.EXCEL_XLSX
            fileName?.endsWith(".xls") == true -> FileType.EXCEL_XLS // Anda perlu menangani ini jika didukung parser
            fileName?.endsWith(".txt") == true -> FileType.TXT
            else -> {
                // Coba dari MIME type jika nama file tidak jelas
                //val mimeType = getApplication<Application>().contentResolver.getType(uri)
                //when (mimeType) {
                //    "text/csv", "text/comma-separated-values" -> FileType.CSV
                //    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> FileType.EXCEL_XLSX
                //    "application/vnd.ms-excel" -> FileType.EXCEL_XLS
                //    "text/plain" -> FileType.TXT
                //    else -> null
                //}
                null
            }
        }
    }

    // Placeholder untuk parser - Ganti dengan implementasi nyata menggunakan library
    private suspend fun parseCsvPlaceholder(inputStream: InputStream): List<BookMaster> {
        // Implementasi parsing CSV sederhana
        val books = mutableListOf<BookMaster>()
        inputStream.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line -> // Skip header
                val tokens = line.split(',') // Asumsi dipisahkan koma
                if (tokens.size >= 3) { // Asumsi minimal ada itemCode, title, rfidTagHex
                    try {
                        books.add(BookMaster(
                            itemCode = tokens[0].trim(),
                            title = tokens[1].trim(),
                            rfidTagHex = tokens[2].trim(),
                            author = if (tokens.size > 3) tokens[3].trim() else null,
                            // ... tambahkan kolom lain sesuai format CSV Anda
                        ))
                    } catch (e: Exception) {
                        System.err.println("Error parsing line CSV: $line - ${e.message}")
                    }
                }
            }
        }
        return books
    }

    private suspend fun parseExcelPlaceholder(inputStream: InputStream): List<BookMaster> {
        // Placeholder - Implementasi Excel parsing akan lebih kompleks (Apache POI)
        System.out.println("DEBUG: Excel parsing placeholder called. InputStream available: ${inputStream.available()}")
        // Contoh data dummy karena parsing Excel butuh library
        return listOf(
            BookMaster(itemCode = "EX001", title = "Buku Excel 1", rfidTagHex = "EXCELTAG001", author = "Penulis Excel"),
            BookMaster(itemCode = "EX002", title = "Buku Excel 2", rfidTagHex = "EXCELTAG002", author = "Penulis Excel Lain")
        )
    }

    private suspend fun parseTxtPlaceholder(inputStream: InputStream): List<BookMaster> {
        // Implementasi parsing TXT sederhana (misal, dipisahkan tab)
        val books = mutableListOf<BookMaster>()
        inputStream.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line -> // Skip header jika ada
                val tokens = line.split('\t') // Asumsi dipisahkan tab
                if (tokens.size >= 3) {
                    try {
                        books.add(BookMaster(
                            itemCode = tokens[0].trim(),
                            title = tokens[1].trim(),
                            rfidTagHex = tokens[2].trim(),
                            // ... tambahkan kolom lain
                        ))
                    } catch (e: Exception) {
                        System.err.println("Error parsing line TXT: $line - ${e.message}")
                    }
                }
            }
        }
        return books
    }

    // Placeholder untuk exporter - Ganti dengan implementasi nyata
    private suspend fun exportCsvPlaceholder(outputStream: OutputStream, data: List<Any>, type: String) {
        outputStream.bufferedWriter().use { writer ->
            if (type == "master") {
                // Saring list untuk hanya menyertakan instance BookMaster
                val bookMasters = data.filterIsInstance<BookMaster>()
                if (bookMasters.isNotEmpty()) { // Lanjutkan hanya jika ada objek BookMaster yang sebenarnya
                    writer.appendLine("ItemCode,Title,RFIDTagHex,Author,Publisher,Year,Category,ExpectedLocation") // Header
                    bookMasters.forEach { book ->
                        writer.appendLine("${book.itemCode},${book.title},${book.rfidTagHex},${book.author ?: ""},${book.publisher ?: ""},${book.yearPublished ?: ""},${book.category ?: ""},${book.expectedLocation ?: ""}")
                    }
                } else if (data.isNotEmpty()) {
                    // Opsional: Tangani kasus di mana list tidak kosong tetapi tidak berisi objek BookMaster
                    System.err.println("Peringatan: List data tipe 'master' tidak berisi objek BookMaster.")
                }
            } else if (type == "opname") {
                // Serupa untuk Map
                val opnameData = data.filterIsInstance<Map<String, Any>>()
                if (opnameData.isNotEmpty()) {
                    writer.appendLine("ItemCode,Status,EPC,WaktuScan") // Header
                    opnameData.forEach { item ->
                        writer.appendLine("${item["itemCode"]},${item["status"]},${item["epc"]},${item["waktu"]}")
                    }
                } else if (data.isNotEmpty()) {
                    System.err.println("Peringatan: List data tipe 'opname' tidak berisi objek Map<String, Any>.")
                }
            }
        }
    }
    private suspend fun exportExcelPlaceholder(outputStream: OutputStream, data: List<Any>, type: String) {
        // Placeholder - Implementasi Excel export akan lebih kompleks
        outputStream.bufferedWriter().use { it.write("Placeholder untuk data Excel ($type)\nData count: ${data.size}") }

    }
    private suspend fun exportTxtPlaceholder(outputStream: OutputStream, data: List<Any>, type: String) {
        outputStream.bufferedWriter().use { writer ->
            if (type == "master" && data.firstOrNull() is BookMaster) {
                writer.appendLine("ItemCode\tTitle\tRFIDTagHex\tAuthor") // Header
                (data as List<BookMaster>).forEach { book ->
                    writer.appendLine("${book.itemCode}\t${book.title}\t${book.rfidTagHex}\t${book.author ?: ""}")
                }
            } else if (type == "opname" && data.firstOrNull() is Map<*, *>) {
                writer.appendLine("ItemCode\tStatus\tEPC\tWaktuScan") // Header
                (data as List<Map<String, Any>>).forEach { item ->
                    writer.appendLine("${item["itemCode"]}\t${item["status"]}\t${item["epc"]}\t${item["waktu"]}")
                }
            }
        }
    }
}
