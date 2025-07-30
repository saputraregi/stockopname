package com.example.aplikasistockopnameperpus.util

enum class FileType(val mimeTypes: Array<String>, val extension: String, val displayName: String) {
    CSV(arrayOf("text/csv", "text/comma-separated-values"), "csv", "CSV"),
    EXCEL_XLSX(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "xlsx", "Excel (XLSX)"),
    EXCEL_XLS(arrayOf("application/vnd.ms-excel"), "xls", "Excel (XLS)"), // Jika Anda akan mendukungnya
    TXT(arrayOf("text/plain"), "txt", "TXT")
    // Tambahkan tipe lain jika perlu
}
