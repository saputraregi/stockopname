package com.example.aplikasistockopnameperpus.util.parser

import com.example.aplikasistockopnameperpus.data.database.BookMaster
import java.io.InputStream

/**
 * Hasil dari operasi parsing file.
 */
sealed class ParseResult {
    data class Success(val books: List<BookMaster>, val warnings: List<String> = emptyList()) : ParseResult()
    data class Error(val errorMessage: String, val lineNumber: Int? = null) : ParseResult()
    object InvalidFormat : ParseResult() // Jika format file tidak sesuai harapan
}

/**
 * Interface untuk semua jenis file parser.
 */
interface FileParser {
    /**
     * Mem-parsing stream input menjadi daftar BookMaster.
     * @param inputStream Stream dari file yang akan diparsing.
     * @param onProgress Callback untuk melaporkan kemajuan (misalnya, baris yang diproses).
     * @return ParseResult yang berisi daftar buku atau pesan error.
     */
    suspend fun parse(inputStream: InputStream, onProgress: ((Int) -> Unit)? = null): ParseResult
}