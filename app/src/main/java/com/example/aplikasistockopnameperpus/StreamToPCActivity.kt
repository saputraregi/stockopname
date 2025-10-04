package com.example.aplikasistockopnameperpus

import android.content.Context
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.aplikasistockopnameperpus.databinding.ActivityStreamToPcBinding
import com.example.aplikasistockopnameperpus.viewmodel.StreamToPcViewModel
import kotlinx.coroutines.launch

class StreamToPcActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStreamToPcBinding
    private val viewModel: StreamToPcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStreamToPcBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarStreamToPc)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarStreamToPc.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.editTextPcIpAddress.setText(getSavedIpAddress())

        observeConnectionStatus()
        setupButton()
    }

    private fun observeConnectionStatus() {
        lifecycleScope.launch {
            viewModel.connectionStatus.collect { status ->
                binding.textViewStreamStatus.text = status
                updateUiBasedOnStatus(status)
            }
        }
    }

    private fun setupButton() {
        binding.buttonConnectToggle.setOnClickListener {
            val status = viewModel.connectionStatus.value
            if (status.startsWith("Terhubung")) {
                viewModel.disconnect()
            } else {
                val ipAddress = binding.editTextPcIpAddress.text.toString()
                if (ipAddress.count { it == '.' } == 3 && ipAddress.split(".").all { it.isNotEmpty() }) {
                    saveIpAddress(ipAddress)
                    viewModel.connect(ipAddress)
                } else {
                    binding.editTextPcIpAddress.error = "Format alamat IP tidak valid"
                }
            }
        }
    }

    private fun updateUiBasedOnStatus(status: String) {
        val isConnected = status.startsWith("Terhubung")
        if (isConnected) {
            binding.buttonConnectToggle.text = "Putuskan"
            binding.buttonConnectToggle.backgroundTintList = ContextCompat.getColorStateList(this, R.color.merah_warning)
            binding.editTextPcIpAddress.isEnabled = false
        } else {
            binding.buttonConnectToggle.text = "Hubungkan"
            binding.buttonConnectToggle.backgroundTintList = ContextCompat.getColorStateList(this, R.color.design_default_color_primary)
            binding.editTextPcIpAddress.isEnabled = true
        }
    }

    // Simpan dan ambil IP terakhir untuk kemudahan pengguna
    private fun saveIpAddress(ip: String) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_pc_ip", ip).apply()
    }

    private fun getSavedIpAddress(): String {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_pc_ip", "192.168.1.") ?: "192.168.1."
    }
}
