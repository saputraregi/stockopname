package com.example.aplikasistockopnameperpus.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object StorageHelper {

    /**
     * Mendapatkan nama file dari URI.
     * Menggunakan Dispatchers.IO karena query ke ContentResolver bisa menjadi operasi I/O.
     */
    suspend fun getFileName(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            return@withContext cursor.getString(displayNameIndex)
                        }
                    }
                }
                null // Jika cursor null atau tidak bisa mendapatkan nama
            }
            ContentResolver.SCHEME_FILE -> uri.lastPathSegment
            else -> null
        }
    }

    /**
     * Menyalin file dari Uri ke cache direktori internal aplikasi.
     * Berguna untuk bekerja dengan file yang dipilih melalui Storage Access Framework.
     * Fungsi ini adalah suspending function dan menjalankan operasi I/O di Dispatchers.IO.
     * @return File yang disalin di cache, atau null jika terjadi error.
     */
    suspend fun copyFileToInternalCache(context: Context, uri: Uri, desiredName: String? = null): File? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val actualFileName = desiredName
                    ?: getFileName(context, uri) // getFileName sekarang suspend fun
                    ?: "temp_${UUID.randomUUID()}" // Nama sementara yang lebih unik

                val outputFile = File(context.cacheDir, actualFileName.takeLast(128).replace("[^a-zA-Z0-9._-]".toRegex(), "_")) // Sanitasi nama file

                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                return@withContext outputFile
            }
            Log.w("StorageHelper", "Gagal membuka InputStream dari URI: $uri")
            null
        } catch (e: IOException) {
            Log.e("StorageHelper", "Error copying file to cache: ${e.message}", e)
            null
        } catch (e: SecurityException) {
            Log.e("StorageHelper", "Security exception copying file to cache: ${e.message}", e)
            null
        }
    }

    /**
     * Membuat file di direktori eksternal publik (misalnya Documents atau Download) menggunakan MediaStore untuk Android 10+.
     * Untuk versi lebih lama, akan mencoba menggunakan getExternalFilesDir (spesifik aplikasi).
     * Fungsi ini adalah suspending function dan menjalankan operasi I/O di Dispatchers.IO.
     *
     * @param context Context aplikasi.
     * @param directoryType Konstanta dari Environment seperti Environment.DIRECTORY_DOCUMENTS, Environment.DIRECTORY_DOWNLOADS.
     * @param fileNamePrefix Awalan nama file.
     * @param extension Ekstensi file (misalnya, "csv", "xlsx").
     * @param mimeType MIME type file (misalnya, "text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").
     * @return Uri ke file yang dibuat, atau null jika gagal.
     */
    suspend fun createPublicExportFile(
        context: Context,
        directoryType: String, // Misal: Environment.DIRECTORY_DOWNLOADS
        fileNamePrefix: String,
        extension: String,
        mimeType: String
    ): Uri? = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat(Constants.EXPORT_DATE_FORMAT, Locale.getDefault()).format(Date())
        val fileName = "${fileNamePrefix}_$timestamp.$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                // Menentukan lokasi relatif di dalam direktori publik yang dipilih
                // Misalnya untuk Environment.DIRECTORY_DOWNLOADS, path bisa "MySubFolder/"
                put(MediaStore.MediaColumns.RELATIVE_PATH, directoryType + File.separator)
            }

            // Tentukan koleksi berdasarkan direktori (ini adalah contoh sederhana, mungkin perlu disesuaikan)
            val collection = when (directoryType) {
                Environment.DIRECTORY_DOWNLOADS -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                Environment.DIRECTORY_DOCUMENTS -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // Lebih umum untuk dokumen
                // Tambahkan case lain jika perlu (Pictures, Music, dll.)
                else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // Fallback
            }

            try {
                return@withContext resolver.insert(collection, contentValues)
                // Untuk menulis ke Uri ini:
                // resolver.openOutputStream(uri)?.use { outputStream -> /* tulis data */ }
            } catch (e: Exception) {
                Log.e("StorageHelper", "Error creating public export file with MediaStore: ${e.message}", e)
                return@withContext null
            }
        } else {
            // Fallback untuk Android < Q (membutuhkan izin WRITE_EXTERNAL_STORAGE di Manifest dan runtime)
            @Suppress("DEPRECATION") // getExternalStoragePublicDirectory is deprecated
            val publicDir = Environment.getExternalStoragePublicDirectory(directoryType)
            val storageDir = File(publicDir, "YourAppName") // Opsional: subdirektori untuk aplikasi Anda

            if (!storageDir.exists() && !storageDir.mkdirs()) {
                Log.e("StorageHelper", "Failed to create directory for <Q: ${storageDir.absolutePath}")
                return@withContext null
            }
            val file = File(storageDir, fileName)
            try {
                // Untuk versi < Q, fungsi ini hanya membuat objek File.
                // Penulisan sebenarnya akan dilakukan oleh pemanggil.
                // Mengembalikan Uri dari file.
                if (file.createNewFile() || file.exists()) { // Pastikan file dibuat atau sudah ada
                    return@withContext Uri.fromFile(file)
                } else {
                    Log.e("StorageHelper", "Failed to create file for <Q: ${file.absolutePath}")
                    return@withContext null
                }
            } catch (e: IOException) {
                Log.e("StorageHelper", "Error creating file for <Q: ${e.message}", e)
                return@withContext null
            }
        }
    }


    /**
     * Mendapatkan InputStream dari Uri.
     * Fungsi ini adalah suspending function dan menjalankan operasi I/O di Dispatchers.IO.
     */
    suspend fun getInputStreamFromUri(context: Context, uri: Uri): InputStream? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)
        } catch (e: IOException) {
            Log.e("StorageHelper", "Error opening input stream from URI: ${e.message}", e)
            null
        } catch (e: SecurityException) {
            Log.e("StorageHelper", "Security exception opening input stream from URI: ${e.message}", e)
            null
        }
    }
}

