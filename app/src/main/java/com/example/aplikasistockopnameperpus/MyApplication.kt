package com.example.aplikasistockopnameperpus

import android.app.Application
import android.util.Log
import com.example.aplikasistockopnameperpus.data.database.AppDatabase
import com.example.aplikasistockopnameperpus.data.repository.BookRepository
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.util.RealtimeStreamManager

class MyApplication : Application() {

    private val database by lazy { AppDatabase.getDatabase(this) }

    val bookRepository by lazy {
        BookRepository(
            bookMasterDao = database.bookMasterDao(),
            stockOpnameReportDao = database.stockOpnameReportDao(),
            stockOpnameItemDao = database.stockOpnameItemDao()
        )
    }

    val realtimeStreamManager: RealtimeStreamManager by lazy {
        RealtimeStreamManager()
    }

    // Hanya satu instance dari ChainwaySDKManager yang dikelola di sini
    lateinit var sdkManager: ChainwaySDKManager
        private set

    override fun onCreate() {
        super.onCreate()
        Log.d("MyApplication", "Application Created")

        // Inisialisasi ChainwaySDKManager
        // Semua logika inisialisasi dan koneksi awal SDK ada di dalam konstruktor/init ChainwaySDKManager
        try {
            sdkManager = ChainwaySDKManager(this)
            Log.i("MyApplication", "ChainwaySDKManager initialized via MyApplication.")
        } catch (e: Exception) {
            Log.e("MyApplication", "Error initializing ChainwaySDKManager from MyApplication", e)
            // Anda mungkin ingin menangani ini dengan cara aplikasi tidak bisa melanjutkan
            // atau menyediakan implementasi SDK "dummy" / "null" jika terjadi kegagalan kritis.
            // throw RuntimeException("Critical failure: ChainwaySDKManager could not be initialized.", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("MyApplication", "Application Terminated")
        if (::sdkManager.isInitialized) {
            sdkManager.releaseResources()
            Log.d("MyApplication", "ChainwaySDKManager resources released via MyApplication.")
        }
        // AppDatabase.destroyInstance() // Jika perlu
    }
}
