package com.example.aplikasistockopnameperpus // Ganti dengan package Anda

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.glance.visibility
import com.example.aplikasistockopnameperpus.R // Pastikan R diimport dengan benar
import com.example.aplikasistockopnameperpus.databinding.ActivitySetupBinding // Ganti dengan nama binding Anda
import com.example.aplikasistockopnameperpus.viewmodel.SetupViewModel
import com.example.aplikasistockopnameperpus.viewmodel.SetupViewModelFactory

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val viewModel: SetupViewModel by viewModels {
        // Ganti null dengan instance reader Anda jika sudah ada, atau mekanisme dependency injection lain
        SetupViewModelFactory(null /* readerInstance */)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSetup)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Reader Setup" // Atau dari string resource

        setupUIListeners()
        observeViewModel()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupUIListeners() {
        // --- 1. Output Power ---
        binding.buttonGetPower.setOnClickListener { viewModel.getPower() }
        binding.buttonSetPower.setOnClickListener {
            val powerStr = (binding.layoutPowerSpinner.editText as? AutoCompleteTextView)?.text.toString()
            powerStr.toIntOrNull()?.let {
                viewModel.setPower(it)
            } ?: showToast("Pilih daya yang valid.")
        }

        // --- 2. Frequency/Region ---
        binding.buttonGetFrequencyRegion.setOnClickListener { viewModel.getFrequencyRegion() }
        binding.buttonSetFrequencyRegion.setOnClickListener {
            val region = (binding.layoutFrequencyRegionSpinner.editText as? AutoCompleteTextView)?.text.toString()
            if (region.isNotBlank()) {
                viewModel.setFrequencyRegion(region)
            } else {
                showToast("Pilih region.")
            }
        }

        // --- 3. Frequency Hopping ---
        binding.buttonSetFreqHopping.setOnClickListener {
            val type = when (binding.radioGroupFreqHoppingType.checkedRadioButtonId) {
                R.id.radioFreqHoppingUS -> "US"
                R.id.radioFreqHoppingOthers -> "Others"
                else -> ""
            }
            val table = (binding.layoutFreqHoppingSpinner.editText as? AutoCompleteTextView)?.text.toString()
            if (type.isNotBlank() && table.isNotBlank()) {
                viewModel.setFreqHopping(type, table)
            } else {
                showToast("Pilih tipe dan tabel Frequency Hopping.")
            }
        }
        // Listener untuk get Freq Hopping (jika ada tombol Get terpisah untuk ini)
        // binding.buttonGetFreqHopping.setOnClickListener { viewModel.getFreqHoppingSettings() }


        // --- 4. Protocol ---
        binding.buttonSetProtocol.setOnClickListener {
            val protocol = (binding.layoutProtocolSpinner.editText as? AutoCompleteTextView)?.text.toString()
            if (protocol.isNotBlank()) {
                viewModel.setProtocol(protocol)
            } else {
                showToast("Pilih protokol.")
            }
        }
        binding.buttonGetProtocol.setOnClickListener { // Asumsi ada tombol Get untuk Protocol
            viewModel.getProtocol()
        }


        // --- 5. RFLink Profile ---
        binding.buttonGetRFLink.setOnClickListener { viewModel.getRFLinkProfile() }
        binding.buttonSetRFLink.setOnClickListener {
            val profile = (binding.layoutRFLinkSpinner.editText as? AutoCompleteTextView)?.text.toString()
            if (profile.isNotBlank()) {
                viewModel.setRFLinkProfile(profile)
            } else {
                showToast("Pilih profil RFLink.")
            }
        }

        // --- 6. Memory Bank (Inventory) ---
        (binding.layoutMemoryBankSpinner.editText as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
            val selectedBank = viewModel.memoryBankOptions.value?.get(position)
            viewModel.onMemoryBankSelected(selectedBank)
        }
        binding.buttonGetMemoryBank.setOnClickListener {
            val bank = (binding.layoutMemoryBankSpinner.editText as? AutoCompleteTextView)?.text.toString()
            val offset = binding.editTextMemoryBankOffset.text.toString().toIntOrNull()
            val length = binding.editTextMemoryBankLength.text.toString().toIntOrNull()
            if (bank.isNotBlank()) {
                viewModel.getMemoryBankData(bank, offset, length)
            } else {
                showToast("Pilih Memory Bank.")
            }
        }
        binding.buttonSetMemoryBank.setOnClickListener {
            val bank = (binding.layoutMemoryBankSpinner.editText as? AutoCompleteTextView)?.text.toString()
            val offsetStr = binding.editTextMemoryBankOffset.text.toString()
            val lengthStr = binding.editTextMemoryBankLength.text.toString()
            // Untuk set, Anda mungkin memerlukan input data. Untuk sekarang, kita skip data input.
            if (bank.isNotBlank()) {
                // Anda perlu UI untuk memasukkan data yang akan ditulis jika operasi set memerlukan data
                val dataToWrite = "SIMULATED_DATA" // Contoh, harusnya dari input user
                viewModel.setMemoryBankData(bank, offsetStr.toIntOrNull(), lengthStr.toIntOrNull(), dataToWrite)
            } else {
                showToast("Pilih Memory Bank.")
            }
        }

        // --- 7. Session Gen2 ---
        binding.buttonGetSessionGen2.setOnClickListener { viewModel.getSessionGen2() }
        binding.buttonSetSessionGen2.setOnClickListener {
            val sessionId = (binding.layoutSessionIdSpinner.editText as? AutoCompleteTextView)?.text.toString()
            val target = (binding.layoutInventoriedFlagSpinner.editText as? AutoCompleteTextView)?.text.toString()
            if (sessionId.isNotBlank() && target.isNotBlank()) {
                viewModel.setSessionGen2(sessionId, target)
            } else {
                showToast("Pilih Session ID dan Target.")
            }
        }

        // --- 8. Fast Inventory ---
        binding.buttonGetFastInventory.setOnClickListener { viewModel.getFastInventoryStatus() }
        binding.radioGroupFastInventory.setOnCheckedChangeListener { _, checkedId ->
            checkedId == R.id.radioFastInventoryOpen
            // Jika Anda ingin set langsung saat radio berubah:
            // viewModel.setFastInventoryStatus(isOpen)
        }
        binding.buttonSetFastInventory.setOnClickListener {
            val isOpen = binding.radioGroupFastInventory.checkedRadioButtonId == R.id.radioFastInventoryOpen
            viewModel.setFastInventoryStatus(isOpen)
        }
        // Untuk SwitchMaterial:
        // binding.switchFastInventory.setOnCheckedChangeListener { _, isChecked ->
        //     viewModel.setFastInventoryStatus(isChecked)
        // }


        // --- 9. TagFocus & FastID Switches ---
        binding.switchTagFocus.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTagFocusEnabled(isChecked)
            // Tambahkan tombol Get jika SDK mendukung pembacaan status
        }
        binding.switchFastID.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setFastIDEnabled(isChecked)
            // Tambahkan tombol Get jika SDK mendukung pembacaan status
        }

        // --- 10. Factory Reset ---
        binding.buttonFactoryReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Factory Reset")
                .setMessage("Apakah Anda yakin ingin melakukan factory reset? Semua pengaturan akan kembali ke default pabrik.")
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.performFactoryReset()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarSetup?.visibility = if (isLoading) View.VISIBLE else View.GONE // Tambahkan ProgressBar ke XML jika perlu
            // Disable UI elements when loading if needed
        }

        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                showToast(it)
                viewModel.clearToastMessage() // Clear message after showing
            }
        }

        // Observers for populating spinners/AutoCompleteTextViews
        viewModel.powerOptions.observe(this) { setSpinnerAdapter(binding.layoutPowerSpinner.editText, it?.map { p -> p.toString() }) }
        viewModel.frequencyRegionOptions.observe(this) { setSpinnerAdapter(binding.layoutFrequencyRegionSpinner.editText, it) }
        viewModel.freqHoppingTableOptions.observe(this) { setSpinnerAdapter(binding.layoutFreqHoppingSpinner.editText, it) }
        viewModel.protocolOptions.observe(this) { setSpinnerAdapter(binding.layoutProtocolSpinner.editText, it) }
        viewModel.rfLinkProfileOptions.observe(this) { setSpinnerAdapter(binding.layoutRFLinkSpinner.editText, it) }
        viewModel.memoryBankOptions.observe(this) { setSpinnerAdapter(binding.layoutMemoryBankSpinner.editText, it) }
        viewModel.sessionIdOptions.observe(this) { setSpinnerAdapter(binding.layoutSessionIdSpinner.editText, it) }
        viewModel.inventoriedFlagOptions.observe(this) { setSpinnerAdapter(binding.layoutInventoriedFlagSpinner.editText, it) }


        // Observers for updating UI with current values from ViewModel
        viewModel.currentPower.observe(this) { (binding.layoutPowerSpinner.editText as? AutoCompleteTextView)?.setText(it?.toString(), false) }
        viewModel.currentFrequencyRegion.observe(this) { (binding.layoutFrequencyRegionSpinner.editText as? AutoCompleteTextView)?.setText(it, false) }

        viewModel.currentFreqHoppingType.observe(this) { type ->
            when (type) {
                "US" -> binding.radioGroupFreqHoppingType.check(R.id.radioFreqHoppingUS)
                "Others" -> binding.radioGroupFreqHoppingType.check(R.id.radioFreqHoppingOthers)
            }
        }
        viewModel.currentFreqHoppingTable.observe(this) { (binding.layoutFreqHoppingSpinner.editText as? AutoCompleteTextView)?.setText(it, false) }
        viewModel.isFreqHoppingContainerVisible.observe(this) { binding.containerFreqHopping.visibility = if (it) View.VISIBLE else View.GONE }


        viewModel.currentProtocol.observe(this) { (binding.layoutProtocolSpinner.editText as? AutoCompleteTextView)?.setText(it, false) }
        viewModel.currentRFLinkProfile.observe(this) { (binding.layoutRFLinkSpinner.editText as? AutoCompleteTextView)?.setText(it, false) }

        viewModel.currentMemoryBank.observe(this) { (binding.layoutMemoryBankSpinner.editText as? AutoCompleteTextView)?.setText(it, false) }
        viewModel.isMemoryBankDetailsVisible.observe(this) { isVisible ->
            binding.containerMemoryBankDetails.visibility = if (isVisible) View.VISIBLE else View.GONE
        }
        viewModel.currentMemoryBankOffset.observe(this) { binding.editTextMemoryBankOffset.setText(it?.toString() ?: "") }
        viewModel.currentMemoryBankLength.observe(this) { binding.editTextMemoryBankLength.setText(it?.toString() ?: "") }

        viewModel.currentSessionId.observe(this) { (binding.layoutSessionIdSpinner.editText as? AutoCompleteTextView)?.setText(it, false) }
        viewModel.currentInventoriedFlag.observe(this) { (binding.layoutInventoriedFlagSpinner.editText as? AutoCompleteTextView)?.setText(it, false) }

        viewModel.isFastInventoryOpen.observe(this) { isOpen ->
            isOpen?.let {
                if (it) binding.radioGroupFastInventory.check(R.id.radioFastInventoryOpen)
                else binding.radioGroupFastInventory.check(R.id.radioFastInventoryClose)
                // For SwitchMaterial:
                // binding.switchFastInventory.isChecked = it
            }
        }

        viewModel.isTagFocusEnabled.observe(this) { binding.switchTagFocus.isChecked = it }
        viewModel.isFastIDEnabled.observe(this) { binding.switchFastID.isChecked = it }
    }

    private fun <T> setSpinnerAdapter(autoCompleteTextView: View?, options: List<T>?) {
        (autoCompleteTextView as? AutoCompleteTextView)?.let { actv ->
            options?.let {
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, it)
                actv.setAdapter(adapter)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
