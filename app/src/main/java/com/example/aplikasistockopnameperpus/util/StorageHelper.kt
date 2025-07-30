package com.example.aplikasistockopnameperpus.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageHelper {

    fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        fileName = it.getString(displayNameIndex)
                    }
                }
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            fileName = uri.lastPathSegment
        }
        return fileName
    }

    /**
     * Menyalin file dari Uri ke cache direktori internal aplikasi.
     * Berguna untuk bekerja dengan file yang dipilih melalui Storage Access Framework.
     */
    fun copyFileToInternalCache(context: Context, uri: Uri, desiredName: String? = null): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val actualFileName = desiredName ?: getFileName(context, uri) ?: "temp_file_${System.currentTimeMillis()}"
            val outputFile = File(context.cacheDir, actualFileName)

            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            outputFile
        } catch (e: IOException) {
            Log.e("StorageHelper", "Error copying file to cache: ${e.message}", e)
            null
        }
    }

    /**
     * Membuat file di direktori eksternal (misalnya Documents atau Download).
     * Perhatikan izin dan Scoped Storage. Untuk Android 10+, pertimbangkan MediaStore.
     * Fungsi ini lebih cocok untuk file yang akan langsung diakses pengguna.
     * Untuk Android 10+, ini akan menyimpan di direktori spesifik aplikasi di ExternalStorage.
     * Untuk akses lebih luas, gunakan MediaStore atau SAF untuk user-picked location.
     */
    fun createPublicExportFile(context: Context, directoryType: String, fileNamePrefix: String, extension: String): File? {
        val timestamp = SimpleDateFormat(Constants.EXPORT_DATE_FORMAT, Locale.getDefault()).format(Date())
        val fileName = "${fileNamePrefix}_$timestamp.$extension"

        // Untuk Android Q (API 29) ke atas, getExternalFilesDir(directoryType) adalah pilihan yang baik
        // karena tidak memerlukan izin WRITE_EXTERNAL_STORAGE untuk direktori khusus aplikasi.
        // Jika directoryType adalah null, itu akan berada di root direktori eksternal aplikasi.
        // Untuk direktori publik standar seperti Documents atau Download, Anda mungkin memerlukan MediaStore.
        val storageDir = context.getExternalFilesDir(directoryType)

        if (storageDir == null) {
            Log.e("StorageHelper", "External storage directory not available for type: $directoryType")
            return null
        }

        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Log.e("StorageHelper", "Failed to create directory: ${storageDir.absolutePath}")
                return null
            }
        }
        return File(storageDir, fileName)
    }

    fun getInputStreamFromUri(context: Context, uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: IOException) {
            Log.e("StorageHelper", "Error opening input stream from URI: ${e.message}", e)
            null
        }
    }
}
