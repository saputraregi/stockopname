package com.example.aplikasistockopnameperpus.util

enum class FileType(val mimeTypes: Array<String>, val extension: String, val displayName: String, val canImport: Boolean = true, val canExport: Boolean = true) {
    CSV(arrayOf("text/csv", "text/comma-separated-values"), "csv", "CSV"),
    EXCEL_XLSX(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "xlsx", "Excel (XLSX)"),
    EXCEL_XLS(arrayOf("application/vnd.ms-excel"), "xls", "Excel (XLS)"), // Jika Anda akan mendukungnya
    TXT(arrayOf("text/plain"), "txt", "TXT");

    // Tambahkan tipe lain jika perlu
    companion object {
        fun fromExtension(extension: String?): FileType? {
            if (extension == null) return null
            return entries.find { it.extension.equals(extension, ignoreCase = true) }
        }

        fun fromMimeType(mimeType: String?): FileType? {
            if (mimeType == null) return null
            return entries.find { it.mimeTypes.any { type -> type.equals(mimeType, ignoreCase = true) } }
        }
    }
}
