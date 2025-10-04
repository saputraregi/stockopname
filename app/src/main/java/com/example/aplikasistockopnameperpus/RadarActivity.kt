package com.example.aplikasistockopnameperpus // Sesuaikan package Anda

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent // <<<---- TAMBAHKAN IMPORT INI
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.text
import androidx.glance.visibility
import androidx.lifecycle.Observer // Pastikan Observer di-import dengan benar
import androidx.lifecycle.observe
import com.example.aplikasistockopnameperpus.databinding.ActivityRadarBinding
import com.example.aplikasistockopnameperpus.fragment.SelectTagDialogFragment // Pastikan import ini benar
import com.example.aplikasistockopnameperpus.viewmodel.RadarViewModel

// IMPLEMENTASIKAN INTERFACE LISTENER DI SINI
class RadarActivity : AppCompatActivity(), SelectTagDialogFragment.OnTagSelectedListener {

    private lateinit var binding: ActivityRadarBinding
    private val radarViewModel: RadarViewModel by viewModels()

    companion object {
        private const val TAG = "RadarActivity" // Untuk logging
        // --- TAMBAHKAN KEYCODE UNTUK TOMBOL FISIK ---
        private const val TRIGGER_KEY_MAIN = 293
        private const val TRIGGER_KEY_BACKUP = 139
        // ------------------------------------------
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRadarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarRadar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupListeners() {
        binding.buttonSelectTagFromListRadar.setOnClickListener {
            showSelectTagDialog()
        }

        binding.buttonStartRadar.setOnClickListener {
            val targetEpcFromInput = binding.editTextEpcTargetRadar.text.toString().trim().uppercase()
            radarViewModel.setTargetEpc(targetEpcFromInput.ifEmpty { null })
            radarViewModel.startTracking(this)
        }

        binding.buttonStopRadar.setOnClickListener {
            radarViewModel.stopTracking()
        }

        binding.seekBarSearchRangeRadar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // if (fromUser) { Log.d(TAG, "SeekBar onProgressChanged (fromUser), progress: $progress") }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    Log.d(TAG, "SeekBar onStopTrackingTouch, progress: ${it.progress}")
                    radarViewModel.setSearchParameter(it.progress)
                }
            }
        })
    }

    private fun observeViewModel() {
        radarViewModel.targetEpcForRadar.observe(this) { epc ->
            if (binding.editTextEpcTargetRadar.text.toString() != (epc ?: "")) {
                binding.editTextEpcTargetRadar.setText(epc ?: "")
            }
        }

        radarViewModel.isTracking.observe(this) { isTracking ->
            binding.buttonStartRadar.isEnabled = !isTracking
            binding.buttonStopRadar.isEnabled = isTracking
            binding.buttonSelectTagFromListRadar.isEnabled = !isTracking
            binding.editTextEpcTargetRadar.isEnabled = !isTracking
            binding.seekBarSearchRangeRadar.isEnabled = !isTracking

            if (isTracking) {
                binding.radarView.startRadarAnimation()
                val currentTarget = radarViewModel.targetEpcForRadar.value
                val currentProgress = radarViewModel.searchRangeProgress.value ?: binding.seekBarSearchRangeRadar.progress
                binding.textViewRadarStatus.text = if (currentTarget.isNullOrBlank()) {
                    if (currentProgress == 0) getString(R.string.status_radar_searching_all)
                    else getString(R.string.status_radar_idle_no_target_set_but_specific_range)
                } else {
                    getString(R.string.status_radar_searching_epc, currentTarget)
                }
            } else {
                binding.radarView.stopRadarAnimation()
                binding.radarView.clearPanel()
                binding.textViewRadarStatus.text = getString(R.string.status_radar_idle)
            }
        }

        radarViewModel.detectedTags.observe(this) { tags ->
            // Logging untuk melihat data apa yang diterima dari ViewModel
            Log.d(TAG, "Observer 'detectedTags' dipicu. Menerima ${tags.size} tag.")

            val targetEpc = radarViewModel.targetEpcForRadar.value
            binding.radarView.bindingData(tags, targetEpc)
            if (targetEpc.isNullOrEmpty()) {
                // Logika ini sudah benar: jika tidak ada target, sembunyikan panah.
                // Pastikan Anda telah menambahkan imageViewArrow dan textViewAngle ke XML Anda.
                binding.imageViewArrow.visibility = View.INVISIBLE
                binding.textViewAngle.text = ""
                return@observe
            }

            // Cari tag target di dalam daftar
            val targetTag = tags.find { it.epc.equals(targetEpc, ignoreCase = true) }

            // Logging untuk melihat apakah target ditemukan
            if (targetTag != null) {
                Log.i(TAG, "Target EPC '$targetEpc' DITEMUKAN dalam daftar scan. Sudut UI: ${targetTag.uiAngle}")
                // Jika tag target ditemukan, update UI
                binding.imageViewArrow.visibility = View.VISIBLE
                binding.imageViewArrow.rotation = targetTag.uiAngle.toFloat()
                binding.textViewAngle.text = getString(R.string.angle_display_format, targetTag.uiAngle)

                val scale = 1.0f + (targetTag.distanceValue / 100.0f)
                binding.imageViewArrow.scaleX = scale
                binding.imageViewArrow.scaleY = scale
            } else {
                // Logging jika target TIDAK ditemukan
                if (tags.isNotEmpty()) {
                    Log.w(TAG, "Target EPC '$targetEpc' TIDAK DITEMUKAN. Tag yang ada: ${tags.joinToString { it.epc }}")
                }
                // Jika tag target TIDAK ditemukan di pemindaian terakhir, sembunyikan panah
                binding.imageViewArrow.visibility = View.INVISIBLE
            }
        }


        radarViewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                radarViewModel.clearToastMessage()
            }
        }

        radarViewModel.searchRangeProgress.observe(this) { progress ->
            if (binding.seekBarSearchRangeRadar.progress != progress) {
                binding.seekBarSearchRangeRadar.progress = progress
            }
        }
    }

    // --- TAMBAHKAN METODE onKeyDown dan handlePhysicalTriggerPress ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        Log.d(TAG, "onKeyDown - KeyCode: $keyCode, Event Repeat Count: ${event.repeatCount}")

        if (keyCode == TRIGGER_KEY_MAIN || keyCode == TRIGGER_KEY_BACKUP) {
            if (event.repeatCount == 0) { // Hanya proses saat pertama kali ditekan
                Log.i(TAG, "Physical trigger pressed (KeyCode: $keyCode)")
                handlePhysicalTriggerPress()
            }
            return true // Event telah ditangani
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handlePhysicalTriggerPress() {
        if (radarViewModel.isTracking.value == true) {
            Log.d(TAG, "Physical trigger: Stopping radar tracking.")
            radarViewModel.stopTracking()
        } else {
            val targetEpcFromInput = binding.editTextEpcTargetRadar.text.toString().trim().uppercase()
            radarViewModel.setTargetEpc(targetEpcFromInput.ifEmpty { null })

            Log.d(TAG, "Physical trigger: Starting radar tracking for EPC: ${targetEpcFromInput.ifEmpty{"ANY"}}")
            radarViewModel.startTracking(this.applicationContext) // Gunakan applicationContext jika aman
        }
    }
    // -------------------------------------------------------------

    private fun showSelectTagDialog() {
        val dialogFragment = SelectTagDialogFragment.newInstance()
        dialogFragment.listener = this
        dialogFragment.show(supportFragmentManager, SelectTagDialogFragment.TAG)
    }

    override fun onTagSelected(selectedEpc: String) {
        radarViewModel.setTargetEpc(selectedEpc)
        Toast.makeText(this, getString(R.string.toast_epc_selected, selectedEpc), Toast.LENGTH_SHORT).show()
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
        if (radarViewModel.isTracking.value == true) {
            Log.d(TAG, "onPause: Stopping radar tracking.")
            radarViewModel.stopTracking()
        }
    }
}