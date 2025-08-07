package com.example.aplikasistockopnameperpus.util

object Constants {
    const val DATABASE_NAME = "stock_opname_database"

    // Request codes
    const val REQUEST_CODE_PICK_CSV_FILE = 1001
    const val REQUEST_CODE_PICK_XLS_FILE = 1002
    const val REQUEST_CODE_PICK_XLSX_FILE = 1003
    const val REQUEST_CODE_PICK_TXT_FILE = 1004

    // --- Konstanta untuk Parsing CSV dari SLiMS ---
    // Nama-nama header yang diharapkan ada di file CSV SLiMS.
    // Sesuaikan nama ini agar PERSIS sama (case-insensitive) dengan header di file CSV Anda.
    object SlimsCsvHeaders {
        const val ITEM_CODE = "item_code" // atau "ITEMCODE" jika itu yang ada di file
        const val TITLE = "title"
        const val CALL_NUMBER = "call_number"
        const val COLLECTION_TYPE_NAME = "coll_type_name"
        const val INVENTORY_CODE = "inventory_code"
        const val RECEIVED_DATE = "received_date"
        const val LOCATION_NAME = "location_name"
        const val ORDER_DATE = "order_date"
        const val ITEM_STATUS_NAME = "item_status_name"
        const val SITE = "site"
        const val SOURCE = "source"
        const val PRICE = "price"
        const val PRICE_CURRENCY = "price_currency"
        const val INVOICE_DATE = "invoice_date"
        const val INPUT_DATE = "input_date"
        const val LAST_UPDATE = "last_update"
        // Tambahkan header lain dari SLiMS yang mungkin ingin Anda parse
    }

    // Header CSV untuk EXPORT dari aplikasi Anda (BISA BERBEDA dari impor SLiMS)
    // Ini adalah contoh jika Anda ingin mengekspor data dari aplikasi Anda sendiri
    // dalam format tertentu, termasuk rfidTagHex dan tid.
    val APP_EXPORT_CSV_BOOK_HEADERS = listOf(
        "ItemCode_Aplikasi", // Mungkin itemCode asli dari SLiMS
        "Judul_Aplikasi",    // Mungkin title asli dari SLiMS
        "RFID_Tag_Hex_EPC",
        "Lokasi_Harusnya_SLiMS",
        "TID_Tag",
        "Status_Pairing_Aplikasi",
        "Status_Opname_Aplikasi"
        // Tambahkan kolom lain yang relevan untuk ekspor dari aplikasi
    )

    const val DISPLAY_DATE_TIME_FORMAT = "dd/MM/yyyy HH:mm:ss"
    // Batasan untuk parsing (ini masih relevan)
    const val MAX_CELL_LENGTH = 255 // Contoh batasan panjang sel
    const val MAX_ROWS_TO_PARSE = 20000 // Naikkan jika perlu untuk file besar

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
