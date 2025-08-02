package com.example.aplikasistockopnameperpus.data.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase



@Database(
    entities = [
        BookMaster::class,
        StockOpnameReport::class,
        StockOpnameItem::class
    ],
    version = 1, // Target versi database
    exportSchema = true
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
                val currentInstance = INSTANCE
                if (currentInstance != null) {
                    return currentInstance
                }
                Log.d("AppDatabase_SETUP", "Creating new database instance with fallback. Target Version: 1")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stock_opname_perpus_db"
                )
                    // HAPUS .addMigrations(...) UNTUK SEMENTARA
                    .fallbackToDestructiveMigration() // INI AKAN MENGHAPUS DATABASE LAMA
                    .setJournalMode(JournalMode.TRUNCATE) // Opsional
                    .build()
                INSTANCE = instance
                Log.d("AppDatabase_SETUP", "Database instance created using fallback.")
                instance
            }
        }
    }
}
