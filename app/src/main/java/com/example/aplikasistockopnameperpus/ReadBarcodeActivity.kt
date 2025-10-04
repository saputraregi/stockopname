package com.example.aplikasistockopnameperpus

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.adapter.SimpleTextAdapter
import com.example.aplikasistockopnameperpus.databinding.ActivityReadBarcodeBinding
import com.example.aplikasistockopnameperpus.viewmodel.ReadBarcodeViewModel

class ReadBarcodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadBarcodeBinding
    private val viewModel: ReadBarcodeViewModel by viewModels()
    private lateinit var barcodeAdapter: SimpleTextAdapter

    private val TRIGGER_KEY_MAIN = 293
    private val TRIGGER_KEY_BACKUP = 139

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadBarcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarReadBarcode)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarReadBarcode.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        viewModel.setupSdkListeners()

        setupRecyclerView()
        setupClickListeners() // <-- Panggil fungsi baru ini
        observeViewModel()
    }

    // --- FUNGSI BARU UNTUK CLICK LISTENER ---
    private fun setupClickListeners() {
        binding.fabScanBarcode.setOnClickListener {
            // Panggil fungsi start di ViewModel
            // Logikanya: tombol ditekan -> start scan. Hasil akan otomatis stop dari callback onBarcodeScanned.
            viewModel.startBarcodeScan()
        }
    }
    // ----------------------------------------

    private fun setupRecyclerView() {
        barcodeAdapter = SimpleTextAdapter(emptyList())
        binding.recyclerViewScannedBarcodes.apply {
            adapter = barcodeAdapter
            layoutManager = LinearLayoutManager(this@ReadBarcodeActivity).apply {
                reverseLayout = true
                stackFromEnd = true
            }
        }
    }

    private fun observeViewModel() {
        viewModel.lastScannedBarcode.observe(this) { barcode ->
            binding.textViewLastScannedBarcodeValue.text = barcode ?: ""
        }

        viewModel.scannedBarcodesList.observe(this) { barcodes ->
            barcodeAdapter.updateData(barcodes)
            if (barcodes.isNotEmpty()) {
                binding.recyclerViewScannedBarcodes.scrollToPosition(0)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarScan.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Nonaktifkan tombol saat sedang loading untuk mencegah klik ganda
            binding.fabScanBarcode.isEnabled = !isLoading
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_clear_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                viewModel.clearScanHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Fungsi onKeyDown dan onKeyUp tetap sama
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)
        if (keyCode == TRIGGER_KEY_MAIN || keyCode == TRIGGER_KEY_BACKUP) {
            if (event.repeatCount == 0) {
                viewModel.startBarcodeScan()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyUp(keyCode, event)
        if (keyCode == TRIGGER_KEY_MAIN || keyCode == TRIGGER_KEY_BACKUP) {
            viewModel.stopBarcodeScan()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopBarcodeScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Saat activity benar-benar dihancurkan, bersihkan listener
        // untuk mencegah memory leak dan perilaku tak terduga.
        (application as MyApplication).sdkManager.onBarcodeScanned = null
        Log.d("ReadBarcodeActivity", "Activity destroyed, onBarcodeScanned listener cleared.")
    }
}
