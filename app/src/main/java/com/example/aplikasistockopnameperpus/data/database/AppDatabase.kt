package com.example.aplikasistockopnameperpus.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookMaster::class,
        StockOpnameReport::class,
        StockOpnameItem::class
    ],
    version = 1, // Naikkan versi jika skema berubah, dan tambahkan migrasi
    exportSchema = false // Set true jika ingin mengekspor skema ke file JSON
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookMasterDao(): BookMasterDao
    abstract fun stockOpnameReportDao(): StockOpnameReportDao
    abstract fun stockOpnameItemDao(): StockOpnameItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stock_opname_perpus_db" // Nama file database Anda
                )
                    // .addMigrations(MIGRATION_1_2) // Tambahkan jika ada migrasi
                    .fallbackToDestructiveMigrationFrom(true) // HATI-HATI: Hapus data jika skema berubah & tidak ada migrasi
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
