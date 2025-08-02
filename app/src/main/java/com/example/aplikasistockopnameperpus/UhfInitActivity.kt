package com.example.aplikasistockopnameperpus

import android.content.Intent
import android.os.Bundle
import android.widget.Button // Import Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // Untuk mengambil warna
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.adapter.MenuAdapter
import com.example.aplikasistockopnameperpus.model.MenuAction
import com.example.aplikasistockopnameperpus.model.MenuItem
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.MyApplication



class UhfInitActivity : AppCompatActivity() {

    private lateinit var recyclerViewMenu: RecyclerView
    private lateinit var menuAdapter: MenuAdapter
    private lateinit var textViewReaderStatus: TextView
    private lateinit var buttonReaderAction: Button // Hanya satu tombol
    private lateinit var sdkManager: ChainwaySDKManager

    // Variabel untuk melacak status koneksi
    private var isReaderConnected: Boolean = false
    // Untuk mendapatkan instance dari MyApplication (jika Anda meletakkan logika UHF di sana)
    private lateinit var myApplication: MyApplication


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uhf_init)

        myApplication = application as MyApplication

        // ======== TAMBAHKAN INISIALISASI sdkManager DI SINI ========
        sdkManager = myApplication.sdkManager // Asumsikan sdkManager adalah properti di MyApplication
        // atau jika sdkManager adalah instance baru yang dibuat di sini:
        // sdkManager = ChainwaySDKManager(this) // Tergantung desain Anda

        textViewReaderStatus = findViewById(R.id.textViewReaderStatus)
        buttonReaderAction = findViewById(R.id.buttonReaderAction)
        recyclerViewMenu = findViewById(R.id.recyclerViewMenu)

        setupRecyclerView()
        setupReaderButton()

        updateUIBasedOnConnectionState()
    }

    private fun setupRecyclerView() {
        val menuItems = listOf(
            MenuItem("Import/Export", R.drawable.ic_launcher_background, MenuAction.IMPORT_EXPORT),
            MenuItem("Stock Opname", R.drawable.ic_launcher_background, MenuAction.STOCK_OPNAME),
            MenuItem("Report", R.drawable.ic_launcher_background, MenuAction.REPORT),
            MenuItem("Read/Write Tag", R.drawable.ic_launcher_background, MenuAction.READ_WRITE_TAG),
            MenuItem("Radar", R.drawable.ic_launcher_background, MenuAction.RADAR),
            MenuItem("Setup", R.drawable.ic_launcher_background, MenuAction.SETUP),
            MenuItem("Pair & Write Tag", R.drawable.ic_launcher_background, MenuAction.PAIRING_WRITE)
        )
        menuAdapter = MenuAdapter(menuItems) { menuAction ->
            handleMenuAction(menuAction)
        }
        recyclerViewMenu.adapter = menuAdapter
        // recyclerViewMenu.layoutManager = GridLayoutManager(this, 3) // Jika tidak di XML
    }

    private fun handleMenuAction(menuAction: MenuAction) {
        // Cek apakah reader terhubung sebelum melakukan aksi yang memerlukan reader
        if (!isReaderConnected && (menuAction == MenuAction.STOCK_OPNAME ||
                    menuAction == MenuAction.READ_WRITE_TAG ||
                    menuAction == MenuAction.RADAR)) {
            showToast("Harap hubungkan reader terlebih dahulu.")
            return
        }

        when (menuAction) {
            MenuAction.IMPORT_EXPORT -> {
                try {
                    val intent = Intent(this, ImportExportActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    showToast("Error: Tidak bisa membuka Import/Export.")
                    android.util.Log.e("UhfInitActivity", "Error starting ImportExportActivity", e)
                }
            }
            MenuAction.STOCK_OPNAME -> {
                try {
                    val intent = Intent(this, StockOpnameActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    showToast("Error: Tidak bisa membuka Stock Opname.")
                    android.util.Log.e("UhfInitActivity", "Error starting StockOpnameActivity", e)
                }
            }
            MenuAction.REPORT -> {
                try {
                    val intent = Intent(this, ReportActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    showToast("Error: Tidak bisa membuka Report.")
                    android.util.Log.e("UhfInitActivity", "Error starting ReportActivity", e)
                }
            }
            MenuAction.READ_WRITE_TAG -> {
                try {
                    val intent = Intent(this, ReadWriteTagActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    showToast("Error: Tidak bisa membuka Read Write Tag.")
                    android.util.Log.e("UhfInitActivity", "Error starting ReadWriteTagActivity", e)
                }
            }
            MenuAction.RADAR -> {
                try {
                    val intent = Intent(this, RadarActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    showToast("Error: Tidak bisa membuka Radar.")
                    android.util.Log.e("UhfInitActivity", "Error starting RadarActivity", e)
                }
            }
            MenuAction.SETUP -> {
                try {
                    val intent = Intent(this, SetupActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    showToast("Error: Tidak bisa membuka Setup.")
                    android.util.Log.e("UhfInitActivity", "Error starting SetupActivity", e)
                }
            }
            MenuAction.PAIRING_WRITE -> {
                try {
                    val intent = Intent(this, BookTaggingActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    showToast("Error: Tidak bisa membuka Pair and Write.")
                    android.util.Log.e("UhfInitActivity", "Error starting BookTaggingActivity", e)
                }
            }
        }
    }

    private fun setupReaderButton() {
        buttonReaderAction.setOnClickListener {
            if (isReaderConnected) {
                // Aksi untuk Disconnect
                disconnectReader()
            } else {
                // Aksi untuk Connect
                connectReader()
            }
        }
    }

    private fun connectReader() {
        textViewReaderStatus.text = "Status Reader: Mencoba menghubungkan..."
        buttonReaderAction.isEnabled = false // Nonaktifkan tombol selama proses

        // TODO: Implementasikan logika koneksi reader UHF yang sebenarnya menggunakan myApplication.connectReader()
        // Contoh simulasi dengan delay (GANTI DENGAN LOGIKA ASLI)
        //val success = myApplication.connectReader() // Panggil dari MyApplication

        // Simulasi berhasil terhubung:
        // Ganti ini dengan hasil sebenarnya dari operasi koneksi
        // Misalnya, jika menggunakan Coroutines atau Handler untuk operasi background:
        // lifecycleScope.launch {
        //     val success = withContext(Dispatchers.IO) { myApplication.connectReader() }
        //     if (success) {
        //         isReaderConnected = true
        //         showToast("Reader berhasil terhubung.")
        //     } else {
        //         isReaderConnected = false
        //         showToast("Gagal menghubungkan reader.")
        //     }
        //     updateUIBasedOnConnectionState()
        // }

        // --- Simulasi Sederhana Tanpa Threading (HANYA UNTUK TESTING UI CEPAT) ---
        //val success = true; // Ganti dengan myApplication.connectReader()
        val success = sdkManager.connectDevices() // Gunakan metode dari MyApplication
        if (success) {
            isReaderConnected = true
            showToast("Reader berhasil terhubung.")
        } else {
            isReaderConnected = false // Sebenarnya tidak perlu karena sudah false
            showToast("Gagal menghubungkan reader.")
        }
        updateUIBasedOnConnectionState()
        // --- Akhir Simulasi Sederhana ---
    }

    private fun disconnectReader() {
        textViewReaderStatus.text = "Status Reader: Memutuskan koneksi..."
        buttonReaderAction.isEnabled = false // Nonaktifkan tombol selama proses

        // TODO: Implementasikan logika diskoneksi reader UHF yang sebenarnya menggunakan myApplication.disconnectReader()
        // myApplication.disconnectReader() // Panggil dari MyApplication

        // Simulasi berhasil terputus:
        // Ganti ini dengan hasil sebenarnya dari operasi diskoneksi
        // lifecycleScope.launch {
        //     withContext(Dispatchers.IO) { myApplication.disconnectReader() }
        //     isReaderConnected = false
        //     showToast("Reader berhasil diputuskan.")
        //     updateUIBasedOnConnectionState()
        // }

        // --- Simulasi Sederhana Tanpa Threading (HANYA UNTUK TESTING UI CEPAT) ---
        sdkManager.disconnectDevices() // Gunakan metode dari MyApplication
        isReaderConnected = false
        showToast("Reader berhasil diputuskan.")
        updateUIBasedOnConnectionState()
        // --- Akhir Simulasi Sederhana ---
    }

    private fun updateUIBasedOnConnectionState() {
        if (isReaderConnected) {
            textViewReaderStatus.text = "Status Reader: Terhubung"
            buttonReaderAction.text = "Putuskan Koneksi"
            // Opsional: Ubah warna tombol untuk menandakan status terhubung
            buttonReaderAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.merah_warning) // Buat warna ini di colors.xml
            buttonReaderAction.isEnabled = true
        } else {
            textViewReaderStatus.text = "Status Reader: Tidak Terhubung"
            buttonReaderAction.text = "Hubungkan Reader"
            // Opsional: Kembalikan warna tombol ke default atau warna "connect"
            buttonReaderAction.backgroundTintList = ContextCompat.getColorStateList(this, com.google.android.material.R.color.design_default_color_primary) // atau warna kustom Anda
            buttonReaderAction.isEnabled = true
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Panggil ini saat activity dihancurkan untuk memastikan reader terputus jika masih terhubung
    override fun onDestroy() {
        super.onDestroy()
        if (isReaderConnected) {
            // Sebaiknya diskoneksi dilakukan di Application class jika itu adalah singleton manager
            // Jika tidak, Anda bisa memanggilnya dari sini
            // myApplication.disconnectReader()
        }
    }
}
