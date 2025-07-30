package com.example.aplikasistockopnameperpus.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.data.database.StockOpnameItem
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
// import com.example.aplikasistockopnameperpus.di.ServiceLocator
import com.example.aplikasistockopnameperpus.util.Constants
import com.example.aplikasistockopnameperpus.util.StorageHelper
import com.example.aplikasistockopnameperpus.util.exporter.CsvFileExporter
import com.example.aplikasistockopnameperpus.util.exporter.ExcelFileExporter
import com.example.aplikasistockopnameperpus.util.exporter.ExportResult
import com.example.aplikasistockopnameperpus.util.exporter.FileExporter
import com.example.aplikasistockopnameperpus.util.exporter.TxtFileExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class ExportWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
    private val bookRepository: BookRepository // Hanya bookRepository yang diinject
    // private val opnameRepository: StockOpnameRepository // HAPUS PARAMETER INI
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ExportWorker"

        // Input Data Keys
        const val KEY_EXPORT_TYPE = "export_type"
        const val KEY_FILE_FORMAT = "file_format"
        const val KEY_DESTINATION_URI = "destination_uri"
        const val KEY_OPNAME_REPORT_ID = "opname_report_id"

        // Output Data Keys
        const val KEY_RESULT_MESSAGE = "result_message"
        const val KEY_RESULT_FILE_PATH = "result_file_path"
        const val KEY_RESULT_ITEMS_EXPORTED = "result_items_exported"
        const val KEY_RESULT_ERROR_MESSAGE = "result_error_message"
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Jika tidak menggunakan DI framework dan BookRepository menghandle semuanya:
    // private val bookRepository: BookRepository by lazy { ServiceLocator.provideBookRepository(appContext) }

    override suspend fun doWork(): Result {
        val exportType = inputData.getString(KEY_EXPORT_TYPE)
            ?: return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to "Tipe ekspor tidak ditemukan"))
        val fileFormat = inputData.getString(KEY_FILE_FORMAT)
            ?: return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to "Format file tidak ditemukan"))
        val destinationUriString = inputData.getString(KEY_DESTINATION_URI)
        val opnameReportId = inputData.getLong(KEY_OPNAME_REPORT_ID, -1L)

        val notificationId = Constants.NOTIFICATION_ID_EXPORT_PROGRESS

        createNotificationChannel()
        val initialNotification = createNotification(
            appContext.getString(R.string.export_notification_title_starting), // Menggunakan string resource
            appContext.getString(R.string.export_notification_message_starting), // Menggunakan string resource
            isOngoing = true,
            isIndeterminate = true
        ).build() // build notifikasinya

        try {
            setForeground(ForegroundInfo(notificationId, initialNotification))
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menjalankan foreground service untuk ekspor: ${e.message}", e)
            // Anda mungkin ingin mengembalikan Result.failure() di sini jika foreground service penting
        }

        var outputStream: OutputStream? = null
        var finalExportedFilePath: String? = null

        try {
            val exporter: FileExporter = when (fileFormat.lowercase()) {
                "csv" -> CsvFileExporter()
                "xlsx" -> ExcelFileExporter()
                "txt" -> TxtFileExporter()
                else -> {
                    val errorMsg = appContext.getString(R.string.export_error_unsupported_format, fileFormat)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                    return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
                }
            }

            if (destinationUriString != null) {
                val destinationUri = Uri.parse(destinationUriString)
                outputStream = appContext.contentResolver.openOutputStream(destinationUri)
                finalExportedFilePath = StorageHelper.getFileName(appContext, destinationUri)
                    ?: "exported_data_${System.currentTimeMillis()}.$fileFormat"
            } else {
                val directoryType = Environment.DIRECTORY_DOCUMENTS
                val fileNamePrefix = when(exportType) {
                    Constants.EXPORT_TYPE_MASTER_BOOK -> Constants.EXPORT_FILE_PREFIX_MASTER
                    Constants.EXPORT_TYPE_OPNAME_DETAIL -> Constants.EXPORT_FILE_PREFIX_OPNAME
                    else -> "unknown_export"
                }
                val file = StorageHelper.createPublicExportFile(appContext, directoryType, fileNamePrefix, fileFormat)

                if (file == null) {
                    val errorMsg = appContext.getString(R.string.export_error_creating_public_file)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                    return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
                }
                finalExportedFilePath = file.absolutePath
                outputStream = FileOutputStream(file)
            }

            if (outputStream == null) {
                val errorMsg = appContext.getString(R.string.export_error_opening_stream)
                updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
            }

            updateNotification(notificationId, appContext.getString(R.string.export_notification_title_progress), appContext.getString(R.string.export_notification_message_collecting_data), isOngoing = true, isIndeterminate = true)

            val exportResult: ExportResult = when (exportType) {
                Constants.EXPORT_TYPE_MASTER_BOOK -> {
                    val books = withContext(Dispatchers.IO) { bookRepository.getAllStockOpnameReportsFlow()
                    }
                    exporter.exportBooks(books as List<BookMaster>, outputStream)
                }
                Constants.EXPORT_TYPE_OPNAME_DETAIL -> {
                    if (opnameReportId == -1L) {
                        val errorMsg = appContext.getString(R.string.export_error_invalid_opname_id)
                        updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                        return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
                    }
                    // GUNAKAN bookRepository DI SINI
                    val opnameItems = withContext(Dispatchers.IO) { bookRepository.getAllStockOpnameReportsFlow() }
                    exporter.exportOpnameItems(opnameItems as List<StockOpnameItem>, outputStream)
                }
                else -> {
                    val errorMsg = appContext.getString(R.string.export_error_unknown_type, exportType)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), errorMsg, isOngoing = false, isError = true)
                    return Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
                }
            }

            withContext(Dispatchers.IO) {
                outputStream.flush()
            }

            when (exportResult) {
                is ExportResult.Success -> {
                    val successMsg = appContext.getString(R.string.export_success_message, exportResult.itemsExported)
                    val displayPath = if(destinationUriString != null) appContext.getString(R.string.export_success_path_saf) else appContext.getString(R.string.export_success_path_public, finalExportedFilePath)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_success), "$successMsg\n$displayPath", isOngoing = false, isError = false)
                    Result.success(
                        workDataOf(
                            KEY_RESULT_MESSAGE to successMsg,
                            KEY_RESULT_FILE_PATH to finalExportedFilePath,
                            KEY_RESULT_ITEMS_EXPORTED to exportResult.itemsExported
                        )
                    )
                }
                is ExportResult.Error -> {
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_failed), exportResult.errorMessage, isOngoing = false, isError = true)
                    Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to exportResult.errorMessage))
                }
                is ExportResult.NoDataToExport -> {
                    val noDataMsg = appContext.getString(R.string.export_no_data)
                    updateNotification(notificationId, appContext.getString(R.string.export_notification_title_complete), noDataMsg, isOngoing = false, isError = false)
                    Result.success(
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
            Result.failure(workDataOf(KEY_RESULT_ERROR_MESSAGE to errorMsg))
        } finally {
            try {
                withContext(Dispatchers.IO){ // Pastikan close juga di IO thread jika stream adalah FileOutputStream
                    outputStream?.close()
                }
            } catch (ioe: IOException) {
                Log.w(TAG, "Gagal menutup output stream: ${ioe.message}")
            }
        }
        return TODO("Provide the return value")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID_EXPORT,
                Constants.NOTIFICATION_CHANNEL_NAME_EXPORT,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = Constants.NOTIFICATION_CHANNEL_DESCRIPTION_EXPORT // Menggunakan konstanta dari Constants
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
    ): NotificationCompat.Builder { // Hanya mengembalikan Builder
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
        isError: Boolean = false
    ) {
        val notification = createNotification(title, contentText, isOngoing, isIndeterminate, isError).build() // build di sini
        notificationManager.notify(notificationId, notification)
    }
}
