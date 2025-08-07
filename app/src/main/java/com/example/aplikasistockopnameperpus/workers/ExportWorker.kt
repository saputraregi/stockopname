/*package com.example.aplikasistockopnameperpus.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap // Import MimeTypeMap
import androidx.compose.ui.geometry.isEmpty
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.util.Constants
import com.example.aplikasistockopnameperpus.util.StorageHelper
import com.example.aplikasistockopnameperpus.util.exporter.CsvFileExporter
import com.example.aplikasistockopnameperpus.util.exporter.ExcelFileExporter
import com.example.aplikasistockopnameperpus.util.exporter.ExportResult
import com.example.aplikasistockopnameperpus.util.exporter.FileExporter
import com.example.aplikasistockopnameperpus.util.exporter.TxtFileExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull // Untuk mengambil item pertama dari Flow
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException // Import FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class ExportWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
    private val bookRepository: BookRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ExportWorker"
        const val KEY_EXPORT_TYPE = "export_type"
        const val KEY_FILE_FORMAT = "file_format"
        const val KEY_DESTINATION_URI = "destination_uri"
        const val KEY_OPNAME_REPORT_ID = "opname_report_id"
        const val KEY_RESULT_MESSAGE = "result_message"
        const val KEY_RESULT_FILE_PATH = "result_file_path"
        const val KEY_RESULT_ITEMS_EXPORTED = "result_items_exported"
        const val KEY_RESULT_ERROR_MESSAGE = "result_error_message"
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Fungsi helper untuk mendapatkan MIME type
    private fun getMimeTypeFromExtension(extension: String): String? {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }

    override suspend fun doWork(): Result {
        val exportType = inputData.getString(KEY_EXPORT_TYPE)
            ?: return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to appContext.getString(R.string.export_error_missing_export_type))) // Gunakan string resource
        val fileFormat = inputData.getString(KEY_FILE_FORMAT)
            ?: return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to appContext.getString(R.string.export_error_missing_file_format))) // Gunakan string resource
        val destinationUriString = inputData.getString(KEY_DESTINATION_URI)
        val opnameReportId = inputData.getLong(KEY_OPNAME_REPORT_ID, -1L)

        val notificationId = Constants.NOTIFICATION_ID_EXPORT_PROGRESS

        createNotificationChannel()
        val initialNotification = createNotification(
            appContext.getString(R.string.export_notification_title_starting),
            appContext.getString(R.string.export_notification_message_starting),
            isOngoing = true,
            isIndeterminate = true
        ).build()

        try {
            setForeground(ForegroundInfo(notificationId, initialNotification))
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menjalankan foreground service untuk ekspor: ${e.message}", e)
            // Pertimbangkan untuk gagal di sini jika foreground penting
            // return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to "Gagal memulai layanan ekspor."))
        }

        var outputStream: OutputStream? = null
        var finalExportedFilePath: String? = null
        val exportProcessResult: Result // Variabel untuk menyimpan hasil akhir

        try {
            val exporter: FileExporter = when (fileFormat.lowercase()) {
                "csv" -> CsvFileExporter()
                "xlsx" -> ExcelFileExporter()
                "txt" -> TxtFileExporter()
                else -> {
                    val errorMsg = appContext.getString(R.string.export_error_unsupported_format, fileFormat)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                    return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg)) // Langsung return
                }
            }

            if (destinationUriString != null) {
                val destinationUri = Uri.parse(destinationUriString)
                try {
                    outputStream = appContext.contentResolver.openOutputStream(destinationUri)
                    if (outputStream == null) throw IOException("Content resolver returned null output stream.")
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal membuka output stream dari URI: ${e.message}", e)
                    val errorMsg = appContext.getString(R.string.export_error_opening_uri_stream, e.message) // Buat string resource
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                    return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg)) // Langsung return
                }
                finalExportedFilePath = StorageHelper.getFileName(appContext, destinationUri)
                    ?: "exported_data_${System.currentTimeMillis()}.$fileFormat"
            } else {
                // Membuat file di direktori publik
                val directoryType = Environment.DIRECTORY_DOCUMENTS
                val fileNamePrefix = when(exportType) {
                    Constants.EXPORT_TYPE_MASTER_BOOK -> Constants.EXPORT_FILE_PREFIX_MASTER
                    Constants.EXPORT_TYPE_OPNAME_DETAIL -> Constants.EXPORT_FILE_PREFIX_OPNAME
                    else -> "unknown_export"
                }

                val mimeType = getMimeTypeFromExtension(fileFormat)
                if (mimeType == null) {
                    val errorMsg = appContext.getString(R.string.export_error_unknown_mime_type, fileFormat)
                    Log.e(TAG, errorMsg)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                    return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg)) // Langsung return
                }

                // Modifikasi StorageHelper.createPublicExportFile untuk menerima mimeType
                val file = StorageHelper.createPublicExportFile(
                    appContext,
                    directoryType,
                    fileNamePrefix,
                    fileFormat, // ekstensi
                    mimeType    // mimeType
                )

                if (file == null) {
                    val errorMsg = appContext.getString(R.string.export_error_creating_public_file)
                    Log.e(TAG, errorMsg)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                    return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg)) // Langsung return
                }
                finalExportedFilePath = file.absolutePath
                try {
                    outputStream = FileOutputStream(file)
                } catch (e: FileNotFoundException) {
                    Log.e(TAG, "Error creating FileOutputStream: ${e.message}", e)
                    val errorMsg = appContext.getString(R.string.export_error_opening_file_stream, e.message)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                    return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg)) // Langsung return
                }
            }

            // Pada titik ini, outputStream seharusnya tidak null jika tidak ada return failure sebelumnya
            if (outputStream == null) { // Double check, meskipun logika di atas seharusnya sudah menangani
                val errorMsg = appContext.getString(R.string.export_error_opening_stream)
                updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg)) // Langsung return
            }

            updateNotification(notificationId, appContext.getString(R.string.export_notification_title_progress), appContext.getString(R.string.export_notification_message_collecting_data), isOngoing = true, isIndeterminate = true)

            val exportResultData: ExportResult = when (exportType) {
                Constants.EXPORT_TYPE_MASTER_BOOK -> {
                    // AMBIL SEMUA BOOK MASTER - PASTIKAN FUNGSI REPOSITORY BENAR
                    // Menggunakan .firstOrNull() jika Flow hanya emit satu list, atau .toList() jika multiple
                    val books = withContext(Dispatchers.IO) {
                        bookRepository.getAllBookMastersFlow().firstOrNull() // Atau fungsi lain yang mengembalikan List<BookMaster>
                    }
                    if (books == null || books.isEmpty()) {
                        ExportResult.NoDataToExport // Kembalikan NoDataToExport jika tidak ada buku
                    } else {
                        exporter.exportBooks(books, outputStream)
                    }
                }
                Constants.EXPORT_TYPE_OPNAME_DETAIL -> {
                    if (opnameReportId == -1L) {
                        val errorMsg = appContext.getString(R.string.export_error_invalid_opname_id)
                        updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                        // Tidak bisa return Result.failure dari dalam when block dengan cara ini,
                        // Jadi kita set ExportResult.Error
                        ExportResult.Error(errorMsg)
                    } else {
                        // AMBIL ITEM OPNAME BERDASARKAN ID - PASTIKAN FUNGSI REPOSITORY BENAR
                        val opnameItems = withContext(Dispatchers.IO) {
                            // Anda perlu fungsi seperti: bookRepository.getStockOpnameItemsByReportIdFlow(opnameReportId).firstOrNull()
                            // Untuk sementara, jika getAllStockOpnameReportsFlow() benar-benar mengembalikan List<StockOpnameItem> (yang meragukan)
                            bookRepository.getStockOpnameItemsByReportIdFlow(opnameReportId).firstOrNull() // GANTI DENGAN FUNGSI YANG BENAR
                        }
                        if (opnameItems == null || opnameItems.isEmpty()) {
                            ExportResult.NoDataToExport
                        } else {
                            exporter.exportOpnameItems(opnameItems, outputStream)
                        }
                    }
                }
                else -> {
                    val errorMsg = appContext.getString(R.string.export_error_unknown_type, exportType)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                    ExportResult.Error(errorMsg) // Set ExportResult.Error
                }
            }

            // Hanya flush jika exportResultData bukan Error, karena stream mungkin tidak valid
            if (exportResultData !is ExportResult.Error) {
                withContext(Dispatchers.IO) {
                    outputStream.flush()
                }
            }

            exportProcessResult = when (exportResultData) {
                is ExportResult.Success -> {
                    val successMsg = appContext.getString(R.string.export_success_message, exportResultData.itemsExported)
                    val displayPath = if(destinationUriString != null) appContext.getString(R.string.export_success_path_saf) else appContext.getString(R.string.export_success_path_public, finalExportedFilePath ?: "N/A")
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_success), "$successMsg\n$displayPath", isOngoing = false, isError = false)
                    Result.success(
                        workDataOf(
                            KEY_RESULT_MESSAGE to successMsg,
                            KEY_RESULT_FILE_PATH to finalExportedFilePath,
                            KEY_RESULT_ITEMS_EXPORTED to exportResultData.itemsExported
                        )
                    )
                }
                is ExportResult.Error -> {
                    // Pesan error sudah di-handle di dalam when(exportType) atau sebelumnya
                    // updateNotification sudah dipanggil jika error terdeteksi lebih awal
                    // Jika error dari exporter.exportXXX(), maka notifikasi diupdate di sini
                    if (!exportResultData.errorMessage.contains("ID laporan opname tidak valid") && !exportResultData.errorMessage.contains("Tipe ekspor tidak dikenal")) {
                        updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), exportResultData.errorMessage, isOngoing = false, isError = true)
                    }
                    Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to exportResultData.errorMessage))
                }
                is ExportResult.NoDataToExport -> {
                    val noDataMsg = appContext.getString(R.string.export_no_data)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_complete), noDataMsg, isOngoing = false, isError = false) // Judulnya bisa "Selesai" atau "Informasi"
                    Result.success( // Tetap success, tapi dengan pesan bahwa tidak ada data
                        workDataOf(
                            KEY_RESULT_MESSAGE to noDataMsg,
                            KEY_RESULT_ITEMS_EXPORTED to 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan selama proses ekspor: ${e.message}", e)
            val errorMsg = appContext.getString(R.string.export_error_unexpected, e.message ?: "Unknown error")
            updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
            exportProcessResult = Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
        } finally {
            try {
                withContext(Dispatchers.IO){
                    outputStream?.close()
                }
            } catch (ioe: IOException) {
                Log.w(TAG, "Gagal menutup output stream: ${ioe.message}")
            }
        }
        return exportProcessResult // Mengembalikan hasil yang sudah disimpan
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID_EXPORT,
                Constants.NOTIFICATION_CHANNEL_NAME_EXPORT,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = Constants.NOTIFICATION_CHANNEL_DESCRIPTION_EXPORT
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        title: String,
        contentText: String,
        isOngoing: Boolean,
        isIndeterminate: Boolean = false,
        isError: Boolean = false
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, Constants.NOTIFICATION_CHANNEL_ID_EXPORT)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(if (isError) R.drawable.ic_error else if (!isOngoing && !isError) R.drawable.ic_check_circle else R.drawable.ic_download_file)
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)
            .setOnlyAlertOnce(true)
            .apply {
                if (isIndeterminate && isOngoing) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(0,0,false) // Hapus progress jika tidak indeterminate atau tidak ongoing
                }
            }
    }

    private fun updateNotification(
        notificationId: Int,
        title: String,
        contentText: String,
        isOngoing: Boolean,
        isIndeterminate: Boolean = false,
        isError: Boolean = false
    ) {
        val notification = createNotification(title, contentText, isOngoing, isIndeterminate, isError).build()
        notificationManager.notify(notificationId, notification)
    }
}
*/