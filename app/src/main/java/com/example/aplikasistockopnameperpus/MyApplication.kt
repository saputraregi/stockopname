package com.example.aplikasistockopnameperpus

import android.app.Application
import android.util.Log
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
// import com.rscja.deviceapi.RFIDWithUHFUART

class MyApplication : Application() {

    // Komentari atau hapus ini jika Anda hanya menggunakan ChainwaySDKManager
    // private var uhfManagerInstance: Any? = null
    // private var isReaderInitialized = false

    // Ini yang akan digunakan oleh ViewModel Anda
    lateinit var chainwaySDKManager: ChainwaySDKManager
        private set // private set agar hanya bisa diubah dari dalam MyApplication

    override fun onCreate() {
        super.onCreate()
        Log.d("MyApplication", "Application Created")

        // Inisialisasi ChainwaySDKManager di sini!
        try {
            // Asumsikan ChainwaySDKManager memiliki konstruktor yang menerima konteks aplikasi
            chainwaySDKManager = ChainwaySDKManager(this)
            Log.i("MyApplication", "ChainwaySDKManager initialized successfully.")

            // Jika ChainwaySDKManager Anda perlu diinisialisasi lebih lanjut (seperti memanggil metode init() nya)
            // lakukan di sini atau di dalam konstruktor ChainwaySDKManager itu sendiri.
            // Contoh:
            // if (chainwaySDKManager.initializeReader()) {
            //     Log.i("MyApplication", "Chainway Reader initialized via SDKManager.")
            // } else {
            //     Log.e("MyApplication", "Failed to initialize Chainway Reader via SDKManager.")
            // }

        } catch (e: Exception) {
            Log.e("MyApplication", "Error initializing ChainwaySDKManager", e)
            // Handle error, mungkin dengan memberikan instance dummy atau menandai bahwa SDK tidak tersedia
            // Untuk sekarang, jika gagal, ViewModel akan mendapatkan error saat mengakses.
            // Anda bisa melempar RuntimeException di sini agar aplikasi crash lebih awal
            // dan jelas menunjukkan masalah inisialisasi SDK.
            // throw RuntimeException("Failed to initialize ChainwaySDKManager", e)
        }

        // Kode initUhfManager() dan yang terkait dengannya tampaknya untuk SDK yang berbeda
        // atau versi manajemen SDK yang lebih lama. Jika ChainwaySDKManager adalah yang utama,
        // Anda mungkin tidak memerlukan initUhfManager(), getUhfManagerInstance(), dll.,
        // kecuali jika ChainwaySDKManager Anda adalah wrapper di sekitar mereka.
        // Untuk saat ini, kita fokus pada inisialisasi chainwaySDKManager yang digunakan ViewModel.
    }

    // ... (sisa kode seperti isReaderOpened, freeUhfManager, dll., mungkin perlu disesuaikan
    //      atau dihapus jika fungsinya sekarang ditangani oleh ChainwaySDKManager) ...

    // Jika Anda masih memerlukan initUhfManager untuk SDK lain atau logika lama:
    @Synchronized
    fun initUhfManager(): Boolean {
        // ... (kode Anda yang sudah ada) ...
        // Pastikan ini tidak konflik dengan logika ChainwaySDKManager
        // Jika uhfManagerInstance adalah bagian dari ChainwaySDKManager,
        // maka inisialisasinya harus melalui ChainwaySDKManager.
        return false // Contoh, sesuaikan
    }

    fun isReaderOpened(): Boolean {
        // Logika sebenarnya akan memanggil metode dari SDK
        // return uhfReader != null && uhfReader.isOpened() // Contoh dari SDK RSCJA
        // atau
        // return chainwayUhfSdk.getConnectionStatus() == Status.CONNECTED // Contoh konseptual

        // Untuk simulasi atau jika SDK belum terintegrasi penuh:
        return true // Variabel boolean yang Anda kelola
    }

    fun connectReader(): Boolean { // Tambahkan : Boolean
        try {
            // Logika untuk menghubungkan reader
            // Jika berhasil:
            // isReaderInitialized = true // (Contoh, jika Anda punya variabel ini)
            // Log.i("MyApplication", "Reader connected successfully.")
            return true // Kembalikan true jika berhasil
        } catch (e: Exception) {
            // Log.e("MyApplication", "Failed to connect reader", e)
            return false // Kembalikan false jika gagal
        }
    }

    fun disconnectReader() {
        // Your actual disconnection logic here
        Log.d("MyApplication", "Reader disconnected")
    }


    fun getUhfManagerInstance(): Any? {
        // ... (kode Anda yang sudah ada) ...
        return null // Contoh, sesuaikan
    }


    override fun onTerminate() {
        super.onTerminate()
        Log.d("MyApplication", "Application Terminated")
        // Jika ChainwaySDKManager Anda memiliki metode untuk melepaskan resource, panggil di sini
        if (::chainwaySDKManager.isInitialized) { // Cek apakah sudah diinisialisasi sebelum digunakan
            chainwaySDKManager.releaseResources() // Asumsi ada metode ini di ChainwaySDKManager
            Log.d("MyApplication", "ChainwaySDKManager resources released.")
        }
        // freeUhfManager() // Panggil jika masih relevan
    }
}
