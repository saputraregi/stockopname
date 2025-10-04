package com.example.aplikasistockopnameperpus

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope // Tambahkan ini
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.adapter.MenuAdapter
import com.example.aplikasistockopnameperpus.model.MenuAction
import com.example.aplikasistockopnameperpus.model.MenuItem
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import kotlinx.coroutines.Dispatchers // Tambahkan ini
import kotlinx.coroutines.launch // Tambahkan ini
import kotlinx.coroutines.withContext // Tambahkan ini

class UhfInitActivity : AppCompatActivity() {

    private lateinit var recyclerViewMenu: RecyclerView
    private lateinit var menuAdapter: MenuAdapter
    private lateinit var textViewReaderStatus: TextView
    private lateinit var buttonReaderAction: Button
    private lateinit var sdkManager: ChainwaySDKManager // Ganti myApplication dengan sdkManager langsung

    // Variabel untuk melacak status koneksi dari sudut pandang Activity
    // Ini akan disinkronkan melalui callback dari sdkManager
    private var isUhfReaderActuallyConnected: Boolean = false
        set(value) {
            field = value
            updateUIBasedOnConnectionState() // Panggil update UI setiap kali status ini berubah
            // Update juga status di RecyclerView jika diperlukan
            menuAdapter.setReaderConnected(value)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uhf_init)
        sdkManager = (application as MyApplication).sdkManager

        textViewReaderStatus = findViewById(R.id.textViewReaderStatus)
        buttonReaderAction = findViewById(R.id.buttonReaderAction)
        recyclerViewMenu = findViewById(R.id.recyclerViewMenu)

        setupRecyclerView()
        setupReaderButton()
        // Pindahkan setupSdkManagerCallbacks() ke onResume()
        // setupSdkManagerCallbacks() // Hapus dari sini atau biarkan jika Anda juga ingin setup awal
    }

    override fun onResume() {
        super.onResume()
        Log.d("UhfInitActivity", "onResume: Setting up SDK manager callbacks and updating UI.")
        setupSdkManagerCallbacks() // PENTING: Set callback setiap kali Activity resume
        // Update UI berdasarkan status SDK saat ini setiap kali resume
        isUhfReaderActuallyConnected = sdkManager.isDeviceReady("uhf")
    }

    override fun onPause() {
        super.onPause()
        Log.d("UhfInitActivity", "onPause: Clearing SDK manager onDeviceStatusChanged callback.")
        // Penting untuk membersihkan callback untuk menghindari pemanggilan ke Activity yang tidak aktif
        // dan potensi memory leak.
        sdkManager.onDeviceStatusChanged = null
    }

    private fun setupSdkManagerCallbacks() {
        // Hapus pemanggilan berulang jika sudah ada, atau pastikan aman untuk dipanggil berkali-kali
        // sdkManager.onDeviceStatusChanged = null // Hapus listener lama sebelum set yang baru (jika perlu)

        sdkManager.onDeviceStatusChanged = { isConnected, deviceType ->
            lifecycleScope.launch(Dispatchers.Main) {
                if (deviceType == "UHF") {
                    Log.d("UhfInitActivity", "Callback onDeviceStatusChanged: UHF Status Changed to ${if (isConnected) "Connected" else "Disconnected"}")
                    isUhfReaderActuallyConnected = isConnected // Ini akan memicu updateUIBasedOnConnectionState()
                    // Tidak perlu Toast di sini jika updateUI sudah cukup
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val menuItems = listOf(
            MenuItem("Import/Export", R.drawable.ic_import, MenuAction.IMPORT_EXPORT),
            MenuItem("Pair & Write Tag", R.drawable.ic_booktagging, MenuAction.PAIRING_WRITE),
            MenuItem("Stock Opname", R.drawable.ic_stock, MenuAction.STOCK_OPNAME),
            MenuItem("Report", R.drawable.ic_report, MenuAction.REPORT),
            MenuItem("Cek Detail Buku", R.drawable.ic_book_check, MenuAction.BOOK_CHECK),
            MenuItem("Stream ke PC", R.drawable.ic_pc, MenuAction.STREAM_TO_PC),
            MenuItem("Read/Write Tag", R.drawable.ic_scan_uhf, MenuAction.READ_WRITE_TAG),
            MenuItem("Read Barcode", R.drawable.ic_barcode_menu, MenuAction.READ_BARCODE),
            MenuItem("Radar", R.drawable.ic_radar, MenuAction.RADAR),
            MenuItem("Setup", R.drawable.ic_settings, MenuAction.SETUP)
        )
        menuAdapter = MenuAdapter(menuItems, sdkManager.isDeviceReady("uhf")) { menuAction ->
            handleMenuAction(menuAction)
        }
        recyclerViewMenu.adapter = menuAdapter
    }

    private fun handleMenuAction(menuAction: MenuAction) {
        if (!isUhfReaderActuallyConnected && (menuAction == MenuAction.STOCK_OPNAME ||
                    menuAction == MenuAction.READ_WRITE_TAG ||
                    menuAction == MenuAction.RADAR ||
                    menuAction == MenuAction.PAIRING_WRITE ||
                    menuAction == MenuAction.SETUP ||
                    menuAction == MenuAction.BOOK_CHECK ||
                    menuAction == MenuAction.STREAM_TO_PC ||
                    menuAction == MenuAction.READ_BARCODE)) {
            showToast("Harap hubungkan reader terlebih dahulu.")
            return
        }

        when (menuAction) {
            MenuAction.IMPORT_EXPORT -> startActivity(Intent(this, ImportExportActivity::class.java))
            MenuAction.STOCK_OPNAME -> startActivity(Intent(this, StockOpnameActivity::class.java))
            MenuAction.REPORT -> startActivity(Intent(this, ReportActivity::class.java))
            MenuAction.BOOK_CHECK -> startActivity(Intent(this, BookCheckActivity::class.java))
            MenuAction.READ_WRITE_TAG -> startActivity(Intent(this, ReadWriteTagActivity::class.java))
            MenuAction.READ_BARCODE -> startActivity(Intent(this, ReadBarcodeActivity::class.java))
            MenuAction.STREAM_TO_PC -> startActivity(Intent(this, StreamToPcActivity::class.java))
            MenuAction.RADAR -> startActivity(Intent(this, RadarActivity::class.java))
            MenuAction.SETUP -> startActivity(Intent(this, SetupActivity::class.java))
            MenuAction.PAIRING_WRITE -> startActivity(Intent(this, BookTaggingActivity::class.java))
        }.runCatching {
            // Tidak perlu blok try-catch individu jika hanya logging dan toast
        }.onFailure { e ->
            showToast("Error membuka menu: ${menuAction.name}")
            android.util.Log.e("UhfInitActivity", "Error starting activity for ${menuAction.name}", e)
        }
    }

    private fun setupReaderButton() {
        buttonReaderAction.setOnClickListener {
            if (isUhfReaderActuallyConnected) {
                disconnectReader()
            } else {
                connectReader()
            }
        }
    }

    private fun connectReader() {
        textViewReaderStatus.text = "Status Reader: Mencoba menghubungkan..."
        buttonReaderAction.isEnabled = false // Nonaktifkan tombol selama proses

        lifecycleScope.launch {
            val success = try {
                // Panggil fungsi yang benar-benar melakukan inisialisasi/koneksi
                // `initializeModules()` akan mencoba menghubungkan UHF dan Barcode
                // dan akan memicu onDeviceStatusChanged
                // Kita bisa langsung menggunakan status dari sdkManager setelah ini jika perlu,
                // tapi callback adalah yang utama untuk update UI.
                withContext(Dispatchers.IO) {
                    sdkManager.initializeModules() // Ini akan mencoba menginisialisasi
                    sdkManager.isDeviceReady("uhf") // Cek status setelah inisialisasi
                }
            } catch (e: Exception) {
                Log.e("UhfInitActivity", "Exception during connectReader (initializeModules)", e)
                false
            }

            // Callback onDeviceStatusChanged akan menangani update isUhfReaderActuallyConnected dan UI
            // Namun, kita bisa memberikan feedback langsung di sini jika diperlukan.
            if (!success && !isUhfReaderActuallyConnected) { // Jika inisialisasi gagal dan status belum jadi connected via callback
                showToast("Gagal menghubungkan reader UHF.")
            }
            // `updateUIBasedOnConnectionState()` akan dipanggil oleh setter `isUhfReaderActuallyConnected`
            // Jadi, kita hanya perlu memastikan tombol diaktifkan kembali
            buttonReaderAction.isEnabled = true
        }
    }

    private fun disconnectReader() {
        textViewReaderStatus.text = "Status Reader: Memutuskan koneksi..."
        buttonReaderAction.isEnabled = false

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    sdkManager.releaseResources() // Panggil fungsi yang melepaskan resource
                }
                // Callback onDeviceStatusChanged akan menangani update isUhfReaderActuallyConnected
                // dan UI menjadi "Tidak Terhubung"
                showToast("Reader berhasil diputuskan.") // Berikan feedback langsung
            } catch (e: Exception) {
                Log.e("UhfInitActivity", "Exception during disconnectReader (releaseResources)", e)
                showToast("Gagal memutuskan koneksi reader.")
            }
            // `updateUIBasedOnConnectionState()` akan dipanggil oleh setter `isUhfReaderActuallyConnected`
            buttonReaderAction.isEnabled = true
        }
    }

    private fun updateUIBasedOnConnectionState() {
        if (isUhfReaderActuallyConnected) {
            textViewReaderStatus.text = "Status Reader: Terhubung"
            buttonReaderAction.text = "Putuskan Koneksi"
            buttonReaderAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.merah_warning)
        } else {
            textViewReaderStatus.text = "Status Reader: Tidak Terhubung"
            buttonReaderAction.text = "Hubungkan Reader"
            buttonReaderAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.design_default_color_primary)
        }
        buttonReaderAction.isEnabled = true // Pastikan tombol selalu bisa diklik setelah update
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Tidak perlu secara eksplisit memanggil sdkManager.releaseResources() di sini
        // jika sdkManager adalah singleton yang dikelola oleh MyApplication dan
        // MyApplication menangani lifecycle-nya (misalnya di onTerminate).
        // Jika sdkManager dibuat per-Activity (bukan praktik terbaik untuk SDK semacam ini),
        // maka Anda perlu memanggilnya di sini.
        // Asumsi sdkManager dikelola oleh MyApplication.
        // Hapus listener untuk menghindari memory leak
        sdkManager.onDeviceStatusChanged = null
    }
}

