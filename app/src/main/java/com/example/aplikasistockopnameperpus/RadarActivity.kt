package com.example.aplikasistockopnameperpus // Sesuaikan package Anda

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.aplikasistockopnameperpus.databinding.ActivityRadarBinding
import com.example.aplikasistockopnameperpus.fragment.SelectTagDialogFragment // Pastikan import ini benar
import com.example.aplikasistockopnameperpus.viewmodel.RadarViewModel

// IMPLEMENTASIKAN INTERFACE LISTENER DI SINI
class RadarActivity : AppCompatActivity(), SelectTagDialogFragment.OnTagSelectedListener {

    private lateinit var binding: ActivityRadarBinding
    private val radarViewModel: RadarViewModel by viewModels()

    companion object {
        private const val TAG = "RadarActivity" // Untuk logging
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
            showSelectTagDialog() // Memanggil fungsi untuk menampilkan dialog
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
                if (fromUser) {
                    Log.d(TAG, "SeekBar onProgressChanged (fromUser), progress: $progress")
                }
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
        radarViewModel.targetEpcForRadar.observe(this, Observer { epc ->
            if (binding.editTextEpcTargetRadar.text.toString() != (epc ?: "")) {
                binding.editTextEpcTargetRadar.setText(epc ?: "")
            }
        })

        radarViewModel.isTracking.observe(this, Observer { isTracking ->
            binding.buttonStartRadar.isEnabled = !isTracking
            binding.buttonStopRadar.isEnabled = isTracking
            binding.buttonSelectTagFromListRadar.isEnabled = !isTracking
            binding.editTextEpcTargetRadar.isEnabled = !isTracking
            binding.seekBarSearchRangeRadar.isEnabled = !isTracking

            if (isTracking) {
                binding.radarView.startRadar()
                val currentTarget = radarViewModel.targetEpcForRadar.value
                val currentProgress = radarViewModel.searchRangeProgress.value ?: binding.seekBarSearchRangeRadar.progress
                binding.textViewRadarStatus.text = if (currentTarget.isNullOrEmpty()) {
                    if (currentProgress == 0) getString(R.string.status_radar_searching_all)
                    else getString(R.string.status_radar_idle_no_target_set_but_specific_range)
                } else {
                    getString(R.string.status_radar_searching_epc, currentTarget)
                }
            } else {
                binding.radarView.stopRadar()
                binding.textViewRadarStatus.text = getString(R.string.status_radar_idle)
            }
        })

        radarViewModel.detectedTags.observe(this, Observer { tags ->
            val currentTarget = radarViewModel.targetEpcForRadar.value
            binding.radarView.bindingData(tags, currentTarget)
            Log.d(TAG, "Detected tags updated in UI: ${tags.size}")
        })

        radarViewModel.readerAngle.observe(this, Observer { angle ->
            // Logika untuk readerAngle (jika diperlukan untuk merotasi RadarView atau bagiannya)
            Log.d(TAG, "Reader angle updated in UI: $angle")
        })

        radarViewModel.toastMessage.observe(this, Observer { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                radarViewModel.clearToastMessage()
            }
        })

        radarViewModel.searchRangeProgress.observe(this, Observer { progress ->
            if (binding.seekBarSearchRangeRadar.progress != progress) {
                binding.seekBarSearchRangeRadar.progress = progress
            }
        })
    }

    private fun showSelectTagDialog() {
        val dialogFragment = SelectTagDialogFragment.newInstance()
        // SET LISTENER DI SINI
        dialogFragment.listener = this
        dialogFragment.show(supportFragmentManager, SelectTagDialogFragment.TAG)
    }

    // IMPLEMENTASI METODE DARI OnTagSelectedListener
    override fun onTagSelected(selectedEpc: String) {
        radarViewModel.setTargetEpc(selectedEpc)
        Toast.makeText(this, getString(R.string.toast_epc_selected, selectedEpc), Toast.LENGTH_SHORT).show()
        // EditText akan otomatis terupdate karena meng-observe targetEpcForRadar
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
