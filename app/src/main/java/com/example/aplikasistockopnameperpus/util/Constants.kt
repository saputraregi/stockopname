package com.example.aplikasistockopnameperpus.util

object Constants {
    const val DATABASE_NAME = "stock_opname_database"

    // Request codes
    const val REQUEST_CODE_PICK_CSV_FILE = 1001
    const val REQUEST_CODE_PICK_XLS_FILE = 1002
    const val REQUEST_CODE_PICK_XLSX_FILE = 1003
    const val REQUEST_CODE_PICK_TXT_FILE = 1004

    // Header CSV default untuk impor buku (sesuaikan dengan format Anda)
    val DEFAULT_CSV_BOOK_HEADERS = listOf("ITEMCODE", "TITLE", "RFIDTAGHEX", "EXPECTEDLOCATION", "TID")
    // Indeks kolom berdasarkan header di atas
    const val CSV_COL_ITEM_CODE = 0
    const val CSV_COL_TITLE = 1
    const val CSV_COL_RFID_TAG_HEX = 2
    const val CSV_COL_EXPECTED_LOCATION = 3
    const val CSV_COL_TID = 4 // Opsional

    // Batasan untuk parsing
    const val MAX_CELL_LENGTH = 255 // Contoh batasan panjang sel
    const val MAX_ROWS_TO_PARSE = 10000 // Contoh batasan jumlah baris

    // Notifikasi untuk Import
    const val NOTIFICATION_CHANNEL_ID_IMPORT = "import_channel"
    const val NOTIFICATION_CHANNEL_NAME_IMPORT = "Proses Impor"
    const val NOTIFICATION_ID_IMPORT_PROGRESS = 101

    // Notifikasi untuk Export
    const val NOTIFICATION_CHANNEL_ID_EXPORT = "export_channel"
    const val NOTIFICATION_CHANNEL_NAME_EXPORT = "Proses Ekspor"
    const val NOTIFICATION_ID_EXPORT_PROGRESS = 102
    const val NOTIFICATION_CHANNEL_DESCRIPTION_EXPORT = "Deskripsi Notifikasi Ekspor"

    // Export
    const val EXPORT_DATE_FORMAT = "yyyyMMdd_HHmmss"
    const val EXPORT_TYPE_MASTER_BOOK = "master_book"
    const val EXPORT_TYPE_OPNAME_DETAIL = "opname_detail"
    const val EXPORT_FILE_PREFIX_OPNAME = "StockOpname"
    const val EXPORT_FILE_PREFIX_MASTER = "MasterBuku"

}
