/*package com.example.aplikasistockopnameperpus.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.aplikasistockopnameperpus.R // Pastikan R diimpor dengan benar
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.repository.BookRepository // Ganti dengan path repository Anda
// import com.example.aplikasistockopnameperpus.di.ServiceLocator // Jika menggunakan ServiceLocator sederhana
import com.example.aplikasistockopnameperpus.util.Constants
import com.example.aplikasistockopnameperpus.util.StorageHelper
import com.example.aplikasistockopnameperpus.util.parser.CsvFileParser
import com.example.aplikasistockopnameperpus.util.parser.ExcelFileParser
import com.example.aplikasistockopnameperpus.util.parser.FileParser
import com.example.aplikasistockopnameperpus.util.parser.ParseResult
import com.example.aplikasistockopnameperpus.util.parser.TxtFileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

class ImportWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
    // Cara mendapatkan repository:
    // 1. Lewat konstruktor jika menggunakan Hilt atau Koin
    // 2. Menggunakan ServiceLocator sederhana (seperti contoh di bawah)
    // 3. Menginisialisasi secara langsung (kurang ideal untuk testing)
    private val bookRepository: BookRepository // Idealnya di-inject
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ImportWorker"
        const val KEY_FILE_URI = "file_uri"
        const val KEY_FILE_TYPE = "file_type" // "csv", "xlsx", "txt"

        // Output keys for result
        const val KEY_RESULT_MESSAGE = "result_message"
        const val KEY_RESULT_SUCCESS_COUNT = "result_success_count"
        const val KEY_RESULT_WARNING_COUNT = "result_warning_count"
        const val KEY_RESULT_ERROR_MESSAGE = "result_error_message"

        // Output key for progress (optional, if UI observes this directly)
        const val KEY_PROGRESS_ROWS_PROCESSED = "progress_rows_processed"
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Jika tidak menggunakan DI framework, Anda bisa mendapatkan repository dari ServiceLocator
    // private val bookRepository: BookRepository by lazy {
    //     ServiceLocator.provideBookRepository(appContext)
    // }

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString(KEY_FILE_URI) ?: return Result.failure(
            workDataOf(KEY_RESULT_ERROR_MESSAGE to "URI file tidak ditemukan")
        )
        val fileType = inputData.getString(KEY_FILE_TYPE) ?: return Result.failure(
            workDataOf(KEY_RESULT_ERROR_MESSAGE to "Tipe file tidak ditemukan")
        )

        val fileUri = Uri.parse(fileUriString)
        val notificationId = Constants.NOTIFICATION_ID_IMPORT_PROGRESS

        createNotificationChannel()
        val initialNotification = createNotification(
            "Impor Data Buku",
            "Memulai proses impor...",
            isOngoing = true,
            isIndeterminate = true
        )

        try {
            setForeground(ForegroundInfo(notificationId, initialNotification))
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menjalankan foreground service untuk impor: ${e.message}", e)
            // Lanjutkan tanpa foreground jika gagal, namun ini berisiko proses dihentikan OS
            // Pastikan izin FOREGROUND_SERVICE dan FOREGROUND_SERVICE_DATA_SYNC ada di manifest
        }

        var tempFile: File? = null
        var inputStream: InputStream? = null

        try {
            val fileName = StorageHelper.getFileName(appContext, fileUri) ?: "import_data_${System.currentTimeMillis()}"
            tempFile = StorageHelper.copyFileToInternalCache(appContext, fileUri, fileName)

            if (tempFile == null || !tempFile.exists()) {
                val errorMsg = "Gagal menyalin file untuk diproses."
                updateNotification(notificationId,"Impor Gagal", errorMsg, isOngoing = false, isError = true)
                return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
            }
            inputStream = tempFile.inputStream()

            val parser: FileParser = when (fileType.lowercase()) {
                "csv" -> CsvFileParser()
                "xlsx" -> ExcelFileParser(isXlsx = true)
                // "xls" -> ExcelFileParser(isXlsx = false) // Jika ingin mendukung .xls
                "txt" -> TxtFileParser() // Anda bisa menambahkan konfigurasi delimiter jika perlu
                else -> {
                    val errorMsg = "Tipe file tidak didukung: $fileType"
                    updateNotification(notificationId, "Impor Gagal", errorMsg, isOngoing = false, isError = true)
                    return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
                }
            }

            updateNotification(notificationId, "Impor Data Buku", "Memproses file...", isOngoing = true, isIndeterminate = true)

            // Callback untuk progress dari parser
            val progressCallback: (Int) -> Unit = { rowsProcessed ->
                // Update notifikasi setiap N baris untuk menghindari update berlebihan
                if (rowsProcessed % 20 == 0) {
                    updateNotification(
                        notificationId,
                        "Impor Data Buku",
                        "Memproses baris $rowsProcessed...",
                        isOngoing = true,
                        isIndeterminate = true // Tetap indeterminate jika total baris tidak diketahui
                    )
                }
                // Jika ingin mengirim progress ke UI (Activity/Fragment)
                // setProgressAsync(workDataOf(KEY_PROGRESS_ROWS_PROCESSED to rowsProcessed))
            }

            val parseResult = parser.parse(inputStream, progressCallback)

            return withContext(Dispatchers.IO) { // Pastikan operasi database di IO dispatcher
                when (parseResult) {
                    is ParseResult.Success -> {
                        if (parseResult.books.isNotEmpty()) {
                            // Pertimbangkan strategi: Hapus semua data lama atau lakukan upsert
                            // bookRepository.clearAllBooks() // Contoh jika ingin menghapus data lama
                            bookRepository.insertMultipleBooks(parseResult.books) // Pastikan ini suspend function atau panggil dalam withContext(Dispatchers.IO)

                            val successMsg = "Impor berhasil: ${parseResult.books.size} buku ditambahkan." +
                                    if (parseResult.warnings.isNotEmpty()) " (${parseResult.warnings.size} peringatan)" else ""
                            updateNotification(notificationId, "Impor Selesai", successMsg, isOngoing = false, isError = false)
                            Result.success(
                                workDataOf(
                                    KEY_RESULT_MESSAGE to successMsg,
                                    KEY_RESULT_SUCCESS_COUNT to parseResult.books.size,
                                    KEY_RESULT_WARNING_COUNT to parseResult.warnings.size
                                )
                            )
                        } else {
                            val emptyMsg = "Tidak ada data buku yang valid untuk diimpor." +
                                    if (parseResult.warnings.isNotEmpty()) " (${parseResult.warnings.size} peringatan)" else ""
                            updateNotification(notificationId, "Impor Selesai", emptyMsg, isOngoing = false, isError = false) // Bukan error, tapi tidak ada data
                            Result.success( // Atau failure jika ini dianggap gagal
                                workDataOf(
                                    KEY_RESULT_MESSAGE to emptyMsg,
                                    KEY_RESULT_SUCCESS_COUNT to 0,
                                    KEY_RESULT_WARNING_COUNT to parseResult.warnings.size
                                )
                            )
                        }
                    }
                    is ParseResult.Error -> {
                        val errorMsg = "Gagal impor: ${parseResult.errorMessage}" +
                                if (parseResult.lineNumber != null) " di baris ${parseResult.lineNumber}" else ""
                        updateNotification(notificationId, "Impor Gagal", errorMsg, isOngoing = false, isError = true)
                        Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
                    }
                    is ParseResult.InvalidFormat -> {
                        val formatErrorMsg = "Format file tidak valid atau tidak dapat dibaca."
                        updateNotification(notificationId, "Impor Gagal", formatErrorMsg, isOngoing = false, isError = true)
                        Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to formatErrorMsg))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan selama proses impor: ${e.message}", e)
            val errorMsg = "Kesalahan tak terduga: ${e.message}"
            updateNotification(notificationId, "Impor Gagal", errorMsg, isOngoing = false, isError = true)
            return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
        } finally {
            try {
                inputStream?.close()
                tempFile?.delete()
            } catch (ioe: IOException) {
                Log.w(TAG, "Gagal menutup stream atau menghapus file sementara: ${ioe.message}")
            }
            // Notifikasi akan di-cancel otomatis jika autoCancel=true dan ongoing=false
        }
    }

    private fun BookRepository.insertMultipleBooks(
        masters: List<BookMaster>
    ) {
    }

    private fun ForegroundInfo(
        i: Int,
        builder: NotificationCompat.Builder
    ): ForegroundInfo {
        return TODO("Provide the return value")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID_IMPORT,
                Constants.NOTIFICATION_CHANNEL_NAME_IMPORT,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi untuk proses impor data buku"
                // setSound(null, null) // Nonaktifkan suara jika terlalu mengganggu
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        title: String,
        contentText: String,
        isOngoing: Boolean,
        isIndeterminate: Boolean = false,
        progress: Int = 0,
        maxProgress: Int = 100,
        isError: Boolean = false
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, Constants.NOTIFICATION_CHANNEL_ID_IMPORT)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(if (isError) R.drawable.ic_error else R.drawable.ic_upload_file) // Sediakan ic_error.xml dan ic_upload_file.xml
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing) // Hapus notifikasi jika sudah tidak ongoing
            .setOnlyAlertOnce(true) // Hanya getarkan/bunyi sekali saat notifikasi pertama muncul/diupdate
            .apply {
                if (isIndeterminate) {
                    setProgress(0, 0, true)
                } else if (!isError && isOngoing) { // Hanya tampilkan progress bar jika sedang berjalan dan bukan error
                    setProgress(maxProgress, progress, false)
                } else {
                    // Hapus progress bar jika selesai atau error
                    setProgress(0,0,false)
                }
            }
    }

    private fun updateNotification(
        notificationId: Int,
        title: String,
        contentText: String,
        isOngoing: Boolean,
        isIndeterminate: Boolean = false,
        progress: Int = 0,
        maxProgress: Int = 100,
        isError: Boolean = false
    ) {
        val notification = createNotification(title, contentText, isOngoing, isIndeterminate, progress, maxProgress, isError).build()
        notificationManager.notify(notificationId, notification)
    }
}
*/