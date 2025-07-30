package com.example.aplikasistockopnameperpus

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.text
import androidx.lifecycle.Observer
import androidx.lifecycle.observe
import com.example.aplikasistockopnameperpus.databinding.ActivityRadarBinding // Gunakan ViewBinding
import com.example.aplikasistockopnameperpus.viewmodel.RadarViewModel
// Import kelas RadarLocationEntity dari SDK Anda
// import com.rscja.deviceapi.entity.RadarLocationEntity // Contoh, sesuaikan

class RadarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRadarBinding
    private val radarViewModel: RadarViewModel by viewModels()

    // Ganti dengan tipe data yang benar dari SDK Anda jika bukan RadarLocationEntity
    // private var currentTagList: List<RadarLocationEntity> = emptyList()
    private var targetEpc: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRadarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        observeViewModel()

        // Ambil EPC target dari intent jika ada (misalnya jika activity ini dipanggil dengan target tertentu)
        // targetEpc = intent.getStringExtra("TARGET_EPC")
        // binding.editTextEpcTarget.setText(targetEpc)

        // Inisialisasi SDK Reader di ViewModel atau di sini jika lebih sesuai
        // radarViewModel.initUhfReader(this) // Contoh
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarRadar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_radar) // Pastikan string ada
    }

    private fun setupListeners() {
        binding.buttonStartRadar.setOnClickListener {
            targetEpc = binding.editTextEpcTarget.text.toString().trim().uppercase()
            if (targetEpc.isNullOrEmpty()) {
                // Mode scan semua, atau berikan peringatan jika EPC wajib
                // Toast.makeText(this, "Memulai pencarian semua tag", Toast.LENGTH_SHORT).show()
            }
            // Kirim target EPC yang valid (bisa null/kosong jika scan semua)
            radarViewModel.startTracking(this, targetEpc)
        }

        binding.buttonStopRadar.setOnClickListener {
            radarViewModel.stopTracking()
        }

        binding.seekBarSearchRange.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Logika Chainway: int p = 35 - progress;
                    // Pastikan `setSearchParameter` di ViewModel menangani nilai ini dengan benar
                    radarViewModel.setSearchParameter(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun observeViewModel() {
        radarViewModel.isTracking.observe(this, Observer { isTracking ->
            binding.buttonStartRadar.isEnabled = !isTracking
            binding.buttonStopRadar.isEnabled = isTracking
            binding.editTextEpcTarget.isEnabled = !isTracking
            binding.seekBarSearchRange.isEnabled = isTracking // Atau logika lain sesuai kebutuhan

            if (isTracking) {
                binding.radarView.startRadar()
                binding.textViewRadarStatus.text = if (targetEpc.isNullOrEmpty()) "Mencari semua tag..." else "Mencari: $targetEpc"
            } else {
                binding.radarView.stopRadar()
                binding.textViewRadarStatus.text = getString(R.string.status_radar_idle)
            }
        })

        radarViewModel.detectedTags.observe(this, Observer { tags ->
            // currentTagList = tags
            // Update RadarView dengan data tag baru
            // binding.radarView.bindingData(tags, targetEpc)
            Log.d("RadarActivity", "Tags detected: ${tags.size}")
            // Jika Anda punya implementasi bindingData di RadarView.kt:
            binding.radarView.bindingData(tags, targetEpc ?: "")


            // Contoh sederhana: tampilkan jumlah tag terdeteksi
            // binding.textViewRadarStatus.text = "Tag terdeteksi: ${tags.size}"
        })

        radarViewModel.readerAngle.observe(this, Observer { angle ->
            // Jika RadarView Anda mendukung rotasi berdasarkan sudut antena
            // binding.radarView.setRotation(-angle.toFloat()) // Sesuai logika Chainway
            Log.d("RadarActivity", "Reader angle: $angle")
        })

        radarViewModel.toastMessage.observe(this, Observer { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        })

        // Observe progress dari ViewModel jika ingin seekBar dikontrol dari sana
        radarViewModel.searchRangeProgress.observe(this) { progress ->
            binding.seekBarSearchRange.progress = progress
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        // Penting: Hentikan tracking jika activity dijeda untuk menghemat baterai & resource
        if (radarViewModel.isTracking.value == true) {
            radarViewModel.stopTracking()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // radarViewModel.releaseUhfReader() // Contoh, jika ViewModel menangani lifecycle reader
    }
}
