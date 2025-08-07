package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
// import android.content.ContentResolver // Tidak digunakan secara langsung di sini lagi
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aplikasistockopnameperpus.data.database.AppDatabase
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.util.parser.CsvFileParser
import com.example.aplikasistockopnameperpus.util.parser.ExcelFileParser
import com.example.aplikasistockopnameperpus.util.parser.TxtFileParser
import com.example.aplikasistockopnameperpus.util.parser.FileParser // Penting: Impor interface FileParser
import com.example.aplikasistockopnameperpus.util.parser.ParseResult // Penting: Impor ParseResult
import com.example.aplikasistockopnameperpus.util.exporter.CsvFileExporter
import com.example.aplikasistockopnameperpus.util.exporter.ExcelFileExporter
import com.example.aplikasistockopnameperpus.util.exporter.TxtFileExporter
// Impor FileExporter interface jika ada
// import com.example.aplikasistockopnameperpus.util.exporter.FileExporter

import com.example.aplikasistockopnameperpus.util.FileType
import com.example.aplikasistockopnameperpus.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

// Kelas ImportExportStatus tetap sama
sealed class ImportExportStatus {
    object Loading : ImportExportStatus()
    data class Progress(val percentage: Int, val processedItems: Int, val totalItems: Int) : ImportExportStatus() // totalItems bisa jadi jumlah baris
    data class Success(val message: String, val warnings: List<String>? = null) : ImportExportStatus() // Tambah warnings
    data class Error(val errorMessage: String) : ImportExportStatus()
    // Anda bisa menambahkan InvalidFormat di sini juga jika ingin status spesifik untuk UI
    // data class InvalidFormat(val message: String) : ImportExportStatus()
}

class ImportExportViewModel(application: Application) : AndroidViewModel(application) {

    private val bookRepository: BookRepository
    // Parser diinisialisasi sebagai implementasi spesifik dari FileParser
    private val csvFileParser: FileParser = CsvFileParser()
    private val excelFileParser: FileParser = ExcelFileParser()
    // Untuk TxtFileParser, jika Anda ingin delimiter berbeda, Anda bisa mengaturnya di sini
    // atau memiliki beberapa instance jika perlu. Untuk sekarang, default delimiter.
    private val txtFileParser: FileParser = TxtFileParser()

    // Exporter
    private val csvExporter = CsvFileExporter() // Asumsi implementasi FileExporter
    private val excelExporter = ExcelFileExporter()
    private val txtExporter = TxtFileExporter()

    init {
        val database = AppDatabase.getDatabase(application)
        bookRepository = BookRepository(
            database.bookMasterDao(),
            database.stockOpnameReportDao(),
            database.stockOpnameItemDao()
        )
    }

    private var _selectedImportFileUri = MutableLiveData<Uri?>()
    val selectedImportFileUri: LiveData<Uri?> get() = _selectedImportFileUri // Dibuat public jika UI perlu observe

    private val _importStatus = MutableLiveData<ImportExportStatus?>()
    val importStatus: LiveData<ImportExportStatus?> get() = _importStatus

    private val _exportStatus = MutableLiveData<ImportExportStatus?>()
    val exportStatus: LiveData<ImportExportStatus?> get() = _exportStatus

    fun setSelectedFileForImport(uri: Uri?) {
        _selectedImportFileUri.value = uri
        if (uri == null) {
            _importStatus.value = null // Reset status jika file di-clear
        }
    }

    fun clearSelectedFileForImport() {
        setSelectedFileForImport(null)
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }

    fun startImportMasterData() {
        val uri = _selectedImportFileUri.value ?: run {
            _importStatus.value = ImportExportStatus.Error("Tidak ada file dipilih untuk import.")
            return
        }

        _importStatus.value = ImportExportStatus.Loading
        viewModelScope.launch {
            try {
                val fileType = determineFileTypeFromUri(uri)
                if (fileType == null) {
                    _importStatus.postValue(ImportExportStatus.Error("Tipe file tidak didukung atau tidak dikenali."))
                    return@launch
                }

                // Pilih parser yang sesuai berdasarkan fileType
                val parser: FileParser = when (fileType) {
                    FileType.CSV -> csvFileParser
                    FileType.EXCEL_XLSX, FileType.EXCEL_XLS -> excelFileParser
                    FileType.TXT -> txtFileParser
                    // Tidak perlu else karena fileType sudah dipastikan tidak null dan merupakan salah satu dari ini
                }

                StorageHelper.getInputStreamFromUri(getApplication(), uri)?.use { inputStream ->
                    // Definisikan callback onProgress
                    // Total items untuk progress parsing biasanya adalah jumlah baris.
                    // Ini sulit diketahui di awal tanpa membaca seluruh file dulu.
                    // Untuk sementara, kita akan update progress berdasarkan item yang berhasil diproses,
                    // atau jika parser bisa memberikan total item (misalnya jumlah baris).
                    // Untuk sekarang, kita update progress saat parsing dan saat import ke DB.

                    // Tahap 1: Parsing file
                    // Kita tidak bisa tahu totalItems untuk progress parsing secara akurat di sini
                    // kecuali parser mengembalikan total baris yang akan dibaca.
                    // Untuk sekarang, kita hanya akan menampilkan progress saat import ke DB.
                    // Jika parser Anda bisa memberikan perkiraan jumlah baris, Anda bisa
                    // mem-post progress dari callback onProgress parser.
                    // Misalnya, _importStatus.postValue(ImportExportStatus.Progress(it, it, UNKNOWN_TOTAL_ITEMS))

                    val parseResult: ParseResult = parser.parse(inputStream) { processedLines ->
                        // Callback onProgress dari parser.
                        // Anda bisa mem-post status progress di sini jika parser Anda
                        // dapat memberikan informasi yang cukup (misalnya, % selesai parsing).
                        // Karena kita belum tahu total baris, kita mungkin tidak bisa memberikan persentase akurat.
                        // Untuk saat ini, kita akan fokus pada progress saat import ke database.
                        // Log.d("ImportVM", "Parser progress: $processedLines lines processed")
                    }

                    when (parseResult) {
                        is ParseResult.Success -> {
                            val booksToImport = parseResult.books
                            val parsingWarnings = parseResult.warnings

                            if (booksToImport.isNotEmpty()) {
                                // Opsi: Hapus data lama (jika perlu, lakukan via Repository)
                                // withContext(Dispatchers.IO) { bookRepository.clearAllBookMasters() }

                                var importedCount = 0
                                val importErrors = mutableListOf<String>()

                                _importStatus.postValue(ImportExportStatus.Progress(0, 0, booksToImport.size)) // Progress awal untuk import DB

                                booksToImport.forEachIndexed { index, book ->
                                    if (book.itemCode.isBlank() /*|| book.title.isBlank()*/) { // Title bisa kosong sesuai parser
                                        val errorMsg = "Data dilewati (baris file sekitar ${index + 2}): Item Code kosong. (Judul: ${book.title.ifBlank { "[kosong]" }})"
                                        Log.w("ImportVM", errorMsg)
                                        importErrors.add(errorMsg) // Ini adalah error validasi sebelum ke DB
                                    } else {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                bookRepository.insertOrUpdateBookMaster(book)
                                            }
                                            importedCount++
                                        } catch (e: Exception) {
                                            val errorMsg = "Gagal import buku ${book.itemCode}: ${e.message}"
                                            Log.e("ImportVM", errorMsg, e)
                                            importErrors.add(errorMsg)
                                        }
                                    }
                                    val progressPercentage = (((index + 1).toFloat() / booksToImport.size) * 100).toInt()
                                    _importStatus.postValue(ImportExportStatus.Progress(progressPercentage, index + 1, booksToImport.size))
                                }

                                val finalWarnings = parsingWarnings + importErrors // Gabungkan semua peringatan/error
                                val successMessage = "$importedCount dari ${booksToImport.size} buku berhasil diimpor."

                                if (finalWarnings.isNotEmpty()) {
                                    _importStatus.postValue(ImportExportStatus.Success(successMessage, finalWarnings))
                                } else {
                                    _importStatus.postValue(ImportExportStatus.Success(successMessage))
                                }

                            } else {
                                // Jika booksToImport kosong tapi parsing berhasil (mungkin file kosong atau hanya header)
                                if (parsingWarnings.isNotEmpty()) {
                                    _importStatus.postValue(ImportExportStatus.Error("Tidak ada data buku yang valid ditemukan.\nPeringatan parsing:\n- ${parsingWarnings.joinToString("\n- ")}"))
                                } else {
                                    _importStatus.postValue(ImportExportStatus.Error("Tidak ada data buku yang valid ditemukan dalam file."))
                                }
                            }
                        }
                        is ParseResult.Error -> {
                            _importStatus.postValue(ImportExportStatus.Error("Import gagal saat parsing: ${parseResult.errorMessage}"))
                        }
                        is ParseResult.InvalidFormat -> {
                            _importStatus.postValue(ImportExportStatus.Error("Import gagal: Format file tidak sesuai. ${parseResult.message}"))
                            // Atau gunakan status spesifik jika Anda menambahkannya:
                            // _importStatus.postValue(ImportExportStatus.InvalidFormat("Format file tidak sesuai. ${parseResult.message}"))
                        }
                    }
                } ?: _importStatus.postValue(ImportExportStatus.Error("Gagal membuka file untuk import."))

            } catch (e: Exception) {
                _importStatus.postValue(ImportExportStatus.Error("Import gagal: ${e.message ?: "Terjadi kesalahan tidak diketahui"}"))
                Log.e("ImportVM", "Import Exception", e)
            } finally {
                // Pertimbangkan untuk tidak clear URI di sini agar pengguna bisa mencoba lagi
                // atau UI membersihkannya setelah status ditampilkan.
                // clearSelectedFileForImport() // Opsional: hapus jika ingin URI tetap ada
            }
        }
    }

    fun startExportMasterData(outputUri: Uri, fileType: FileType) {
        _exportStatus.value = ImportExportStatus.Loading
        viewModelScope.launch {
            try {
                val books = withContext(Dispatchers.IO) { bookRepository.getAllBookMastersList() }
                if (books.isEmpty()) {
                    _exportStatus.postValue(ImportExportStatus.Error("Tidak ada data master untuk diekspor."))
                    return@launch
                }

                getApplication<Application>().contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    withContext(Dispatchers.IO) {
                        when (fileType) {
                            FileType.CSV -> csvExporter.exportBooks(books, outputStream) // Asumsi exportBooks ada di CsvFileExporter
                            FileType.EXCEL_XLSX -> excelExporter.exportBooks(books, outputStream) // Asumsi exportBooks ada di ExcelFileExporter
                            FileType.EXCEL_XLS -> {
                                _exportStatus.postValue(ImportExportStatus.Error("Format Excel (XLS) belum didukung untuk ekspor ini."))
                                return@withContext
                            }
                            FileType.TXT -> txtExporter.exportBooks(books, outputStream) // Asumsi exportBooks ada di TxtFileExporter
                        }
                    }
                    val fileName = StorageHelper.getFileName(getApplication(), outputUri) ?: outputUri.lastPathSegment ?: "file"
                    _exportStatus.postValue(ImportExportStatus.Success("Data master berhasil diekspor ke $fileName."))
                } ?: _exportStatus.postValue(ImportExportStatus.Error("Gagal membuat file untuk ekspor."))

            } catch (e: Exception) {
                _exportStatus.postValue(ImportExportStatus.Error("Ekspor gagal: ${e.message ?: "Terjadi kesalahan tidak diketahui"}"))
                Log.e("ImportVM", "Export Master Exception", e)
            }
        }
    }

    fun startExportOpnameResult(outputUri: Uri, fileType: FileType) {
        _exportStatus.value = ImportExportStatus.Loading
        viewModelScope.launch {
            try {
                val latestReport = withContext(Dispatchers.IO) { bookRepository.getLatestReport() }
                if (latestReport == null) {
                    _exportStatus.postValue(ImportExportStatus.Error("Tidak ada data laporan stock opname ditemukan."))
                    return@launch
                }

                val itemsForReport = withContext(Dispatchers.IO) { bookRepository.getItemsForReportList(latestReport.reportId) }
                if (itemsForReport.isEmpty()) {
                    _exportStatus.postValue(ImportExportStatus.Error("Tidak ada item ditemukan untuk laporan stock opname terakhir (ID: ${latestReport.reportId})."))
                    return@launch
                }

                // Anda mungkin perlu membuat data class gabungan jika exporter membutuhkan lebih banyak detail
                // data class OpnameExportItemDetails(val item: StockOpnameItem, val book: BookMaster?)
                // val detailedItemsForReport = itemsForReport.map { item ->
                //     val book = bookRepository.getBookMasterByItemCode(item.itemCode) // Operasi IO, lakukan di context IO
                //     OpnameExportItemDetails(item, book)
                // }

                getApplication<Application>().contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    withContext(Dispatchers.IO) {
                        when (fileType) {
                            // Pastikan exporter Anda memiliki metode exportOpnameResults
                            FileType.CSV -> csvExporter.exportOpnameResults(itemsForReport, latestReport, outputStream)
                            FileType.EXCEL_XLSX -> excelExporter.exportOpnameResults(itemsForReport, latestReport, outputStream)
                            FileType.EXCEL_XLS -> {
                                _exportStatus.postValue(ImportExportStatus.Error("Format Excel (XLS) belum didukung untuk ekspor hasil opname ini."))
                                return@withContext
                            }
                            FileType.TXT -> txtExporter.exportOpnameResults(itemsForReport, latestReport, outputStream)
                        }
                    }
                    val fileName = StorageHelper.getFileName(getApplication(), outputUri) ?: outputUri.lastPathSegment ?: "file"
                    _exportStatus.postValue(ImportExportStatus.Success("Hasil stock opname (Laporan ID: ${latestReport.reportId}) berhasil diekspor ke $fileName."))
                } ?: _exportStatus.postValue(ImportExportStatus.Error("Gagal membuat file untuk ekspor."))

            } catch (e: Exception) {
                _exportStatus.postValue(ImportExportStatus.Error("Ekspor hasil opname gagal: ${e.message ?: "Terjadi kesalahan tidak diketahui"}"))
                Log.e("ImportVM", "Export Opname Exception", e)
            }
        }
    }

    private suspend fun determineFileTypeFromUri(uri: Uri): FileType? {
        val context = getApplication<Application>()
        var determinedType: FileType? = null
        val fileName = try {
            withContext(Dispatchers.IO) { StorageHelper.getFileName(context, uri) }
        } catch (e: Exception) {
            Log.e("ImportVM", "Gagal mendapatkan nama file dari URI: $uri", e)
            null
        }

        Log.d("ImportVM", "Menentukan tipe file untuk: $fileName (dari URI: $uri)")

        if (fileName != null) {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension.isNotEmpty()) {
                determinedType = FileType.fromExtension(extension)
                Log.d("ImportVM", "Tipe dari ekstensi '$extension': $determinedType")
            }
        }

        if (determinedType == null) {
            try {
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType != null) {
                    Log.d("ImportVM", "MIME type dari ContentResolver: $mimeType")
                    determinedType = FileType.fromMimeType(mimeType)
                    Log.d("ImportVM", "Tipe dari MIME '$mimeType': $determinedType")
                } else {
                    Log.w("ImportVM", "MIME type null untuk URI: $uri")
                }
            } catch (e: SecurityException) {
                Log.e("ImportVM", "SecurityException saat mendapatkan MIME type untuk URI: $uri. Pesan: ${e.message}. Pastikan permission URI diberikan dengan benar.", e)
                // Jika tidak bisa mendapatkan MIME type karena permission, ini bisa jadi masalah.
                // Untuk sekarang, kita biarkan determinedType null.
            } catch (e: Exception) {
                Log.e("ImportVM", "Error mendapatkan MIME type untuk URI: $uri. Pesan: ${e.message}", e)
            }
        }

        if (determinedType == null && fileName != null) {
            // Fallback jika MIME type gagal tapi ekstensi ada dan tidak dikenali oleh FileType.fromExtension
            // Anda bisa tambahkan logika di sini jika perlu, misalnya berdasarkan ekstensi yang lebih umum
            // jika FileType.fromExtension sangat ketat.
            Log.w("ImportVM", "Tidak dapat menentukan tipe file secara pasti untuk '$fileName'. Mengandalkan ekstensi jika ada, atau akan gagal.")
        } else if (determinedType == null && fileName == null){
            Log.e("ImportVM", "Tidak dapat menentukan tipe file karena nama file dan MIME type tidak tersedia atau gagal diakses.")
        }


        Log.i("ImportVM", "Tipe file yang ditentukan akhir: $determinedType untuk URI: $uri")
        return determinedType
    }
}
