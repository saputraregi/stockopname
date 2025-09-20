package com.example.aplikasistockopnameperpus

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
// import androidx.lifecycle.map // Tidak digunakan
// import androidx.lifecycle.observe // Tidak digunakan
import com.example.aplikasistockopnameperpus.databinding.ActivitySetupBinding
import com.example.aplikasistockopnameperpus.viewmodel.SetupViewModel

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val viewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSetup)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_setup)

        setupUIListeners()
        observeViewModel()

        // Panggil setelah UI siap dan observer terpasang
        // Pastikan reader sudah terhubung sebelum memanggil ini,
        // ViewModel akan menangani pengecekan isDeviceReady.
        viewModel.loadAllInitialSettings()
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
            } ?: showToast(getString(R.string.toast_select_valid_power))
        }
        // Listener untuk AutoCompleteTextView Power (opsional, jika ingin update langsung saat dipilih)
        (binding.layoutPowerSpinner.editText as? AutoCompleteTextView)?.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val adapter = (binding.layoutPowerSpinner.editText as? AutoCompleteTextView)?.adapter
                val selectedPowerStr = adapter?.getItem(position) as? String
                selectedPowerStr?.toIntOrNull()?.let {
                    // Anda bisa memilih untuk langsung set, atau hanya update LiveData di ViewModel,
                    // dan biarkan tombol "Set" yang melakukan aksi set ke reader.
                    // Untuk konsistensi dengan tombol Set, mungkin lebih baik tidak set di sini.
                    // viewModel.setPower(it)
                }
            }


        // --- 2. Frequency/Region ---
        binding.buttonGetFrequencyRegion.setOnClickListener { viewModel.getFrequencyRegion() }
        binding.buttonSetFrequencyRegion.setOnClickListener {
            val region = (binding.layoutFrequencyRegionSpinner.editText as? AutoCompleteTextView)?.text.toString()
            if (region.isNotBlank()) {
                viewModel.setFrequencyRegion(region)
            } else {
                showToast(getString(R.string.toast_select_region))
            }
        }

        // --- 3. Frequency Hopping ---
        // Karena ViewModel menyederhanakan FreHop hanya menjadi nilai float,
        // RadioGroup type mungkin tidak lagi relevan.
        binding.buttonSetFreqHopping.setOnClickListener {
            val tableOrValue = (binding.layoutFreqHoppingSpinner.editText as? AutoCompleteTextView)?.text.toString()
            if (tableOrValue.isNotBlank()) {
                viewModel.setFreqHopping(tableOrValue) // Hanya mengirim nilai
            } else {
                showToast(getString(R.string.toast_select_hopping_type_table)) // Pesan disesuaikan
            }
        }
        binding.buttonGetFreqHopping?.setOnClickListener {
            viewModel.getFreqHoppingSettings()
        }
        // Listener untuk radio group FreHop type mungkin tidak lagi diperlukan
        // jika ViewModel tidak lagi menggunakan 'type'.
        // binding.radioGroupFreqHoppingType.setOnCheckedChangeListener { group, checkedId ->
        //    val type = when (checkedId) { ... }
        //    viewModel.onFreqHoppingTypeChanged(type) // Contoh jika ViewModel perlu tahu
        // }


        // --- 4. Protocol ---
        binding.buttonGetProtocol.setOnClickListener { viewModel.getProtocol() }
        binding.buttonSetProtocol.setOnClickListener {
            val protocol = (binding.layoutProtocolSpinner.editText as? AutoCompleteTextView)?.text.toString()
            if (protocol.isNotBlank()) {
                viewModel.setProtocol(protocol)
            } else {
                showToast(getString(R.string.toast_select_protocol))
            }
        }

        // --- 5. RFLink Profile ---
        binding.buttonGetRFLink.setOnClickListener { viewModel.getRFLinkProfile() }
        binding.buttonSetRFLink.setOnClickListener {
            val profile = (binding.layoutRFLinkSpinner.editText as? AutoCompleteTextView)?.text.toString()
            if (profile.isNotBlank()) {
                viewModel.setRFLinkProfile(profile)
            } else {
                showToast(getString(R.string.toast_select_rflink_profile))
            }
        }

        // --- 6. Memory Bank (Inventory Mode) ---
        (binding.layoutMemoryBankSpinner.editText as? AutoCompleteTextView)?.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val adapter = (binding.layoutMemoryBankSpinner.editText as? AutoCompleteTextView)?.adapter
                val selectedBank = adapter?.getItem(position) as? String
                selectedBank?.let {
                    viewModel.onMemoryBankSelected(it)
                }
            }
        binding.buttonGetMemoryBank.setOnClickListener { viewModel.getMemoryBankData() }
        binding.buttonSetMemoryBank.setOnClickListener {
            val bank = (binding.layoutMemoryBankSpinner.editText as? AutoCompleteTextView)?.text.toString()
            val offsetStr = binding.editTextMemoryBankOffset.text.toString()
            val lengthStr = binding.editTextMemoryBankLength.text.toString()

            if (bank.isNotBlank()) {
                viewModel.setMemoryBankInventoryMode(bank, offsetStr, lengthStr) // Kirim sebagai String
            } else {
                showToast(getString(R.string.toast_select_memory_bank))
            }
        }

        // --- 7. Session Gen2 ---
        binding.buttonGetSessionGen2.setOnClickListener { viewModel.getSessionGen2() }
        binding.buttonSetSessionGen2.setOnClickListener {
            val sessionId = (binding.layoutSessionIdSpinner.editText as? AutoCompleteTextView)?.text.toString()
            val target = (binding.layoutInventoriedFlagSpinner.editText as? AutoCompleteTextView)?.text.toString()
            // Ambil Q value dari UI. Asumsi ada AutoCompleteTextView atau EditText untuk Q.
            // Jika menggunakan SeekBar, ambil dari progress.
            val qValueStr = (binding.layoutQValueSpinner.editText as? AutoCompleteTextView)?.text.toString()
            val qValue = qValueStr.toIntOrNull() ?: viewModel.currentQValue.value ?: 4 // Default jika tidak valid atau tidak ada

            if (sessionId.isNotBlank() && target.isNotBlank()) {
                viewModel.setSessionGen2(sessionId, target, qValue)
            } else {
                showToast(getString(R.string.toast_select_session_target))
            }
        }
        // Listener untuk Q Value Spinner/EditText (jika ingin update LiveData saat berubah)
        (binding.layoutQValueSpinner.editText as? AutoCompleteTextView)?.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val adapter = (binding.layoutQValueSpinner.editText as? AutoCompleteTextView)?.adapter
                val selectedQStr = adapter?.getItem(position) as? String
                selectedQStr?.toIntOrNull()?.let {
                    viewModel.onQValueChanged(it)
                }
            }
        // Atau jika EditText biasa untuk Q Value:
        // binding.editTextQValue.addTextChangedListener(object : TextWatcher { ... })


        // --- 8. Fast Inventory ---
        binding.buttonGetFastInventory.setOnClickListener { viewModel.getFastInventoryStatus() }
        binding.buttonSetFastInventory.setOnClickListener {
            val isOpen = binding.radioGroupFastInventory.checkedRadioButtonId == R.id.radioFastInventoryOpen
            viewModel.setFastInventoryStatus(isOpen)
        }

        // --- 9. TagFocus & FastID Switches ---
        binding.switchTagFocus.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTagFocusEnabled(isChecked)
        }
        binding.buttonGetTagFocus?.setOnClickListener { viewModel.getTagFocusStatus() }

        binding.switchFastID.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setFastIDEnabled(isChecked)
        }
        binding.buttonGetFastID?.setOnClickListener { viewModel.getFastIDStatus() }

        // --- 10. Factory Reset ---
        binding.buttonFactoryReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_factory_reset))
                .setMessage(getString(R.string.dialog_message_factory_reset))
                .setPositiveButton(getString(R.string.dialog_button_reset)) { _, _ ->
                    viewModel.performFactoryReset()
                }
                .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarSetup?.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Menonaktifkan semua elemen input saat loading
            val enableState = !isLoading
            binding.buttonSetPower.isEnabled = enableState
            binding.buttonGetPower.isEnabled = enableState
            (binding.layoutPowerSpinner.editText as? AutoCompleteTextView)?.isEnabled = enableState

            binding.buttonSetFrequencyRegion.isEnabled = enableState
            binding.buttonGetFrequencyRegion.isEnabled = enableState
            (binding.layoutFrequencyRegionSpinner.editText as? AutoCompleteTextView)?.isEnabled = enableState

            binding.buttonSetFreqHopping.isEnabled = enableState
            binding.buttonGetFreqHopping?.isEnabled = enableState // Periksa nullability
            binding.radioGroupFreqHoppingType.isEnabled = enableState // Meskipun mungkin tidak digunakan lagi oleh ViewModel
            (binding.layoutFreqHoppingSpinner.editText as? AutoCompleteTextView)?.isEnabled = enableState

            binding.buttonSetProtocol.isEnabled = enableState
            binding.buttonGetProtocol.isEnabled = enableState
            (binding.layoutProtocolSpinner.editText as? AutoCompleteTextView)?.isEnabled = enableState

            binding.buttonSetRFLink.isEnabled = enableState
            binding.buttonGetRFLink.isEnabled = enableState
            (binding.layoutRFLinkSpinner.editText as? AutoCompleteTextView)?.isEnabled = enableState

            binding.buttonSetMemoryBank.isEnabled = enableState
            binding.buttonGetMemoryBank.isEnabled = enableState
            (binding.layoutMemoryBankSpinner.editText as? AutoCompleteTextView)?.isEnabled = enableState
            binding.editTextMemoryBankOffset.isEnabled = enableState
            binding.editTextMemoryBankLength.isEnabled = enableState

            binding.buttonSetSessionGen2.isEnabled = enableState
            binding.buttonGetSessionGen2.isEnabled = enableState
            (binding.layoutSessionIdSpinner.editText as? AutoCompleteTextView)?.isEnabled = enableState
            (binding.layoutInventoriedFlagSpinner.editText as? AutoCompleteTextView)?.isEnabled = enableState
            (binding.layoutQValueSpinner.editText as? AutoCompleteTextView)?.isEnabled = enableState // Untuk Q Value

            binding.buttonSetFastInventory.isEnabled = enableState
            binding.buttonGetFastInventory.isEnabled = enableState
            binding.radioGroupFastInventory.isEnabled = enableState // Seluruh group

            binding.switchTagFocus.isEnabled = enableState
            binding.buttonGetTagFocus?.isEnabled = enableState // Periksa nullability
            binding.switchFastID.isEnabled = enableState
            binding.buttonGetFastID?.isEnabled = enableState // Periksa nullability

            binding.buttonFactoryReset.isEnabled = enableState
        }

        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                showToast(it)
                viewModel.clearToastMessage()
            }
        }

        // Mengisi Spinner/AutoCompleteTextView dengan opsi dari ViewModel
        viewModel.powerOptions.observe(this) { options ->
            setSpinnerAdapter(binding.layoutPowerSpinner.editText, options?.map { it.toString() })
        }
        viewModel.frequencyRegionOptions.observe(this) { options ->
            setSpinnerAdapter(binding.layoutFrequencyRegionSpinner.editText, options)
        }
        viewModel.freqHoppingTableOptions.observe(this) { options ->
            // Ini mungkin hanya ["Custom Float Value"] sekarang
            setSpinnerAdapter(binding.layoutFreqHoppingSpinner.editText, options)
        }
        viewModel.protocolOptions.observe(this) { options ->
            setSpinnerAdapter(binding.layoutProtocolSpinner.editText, options)
        }
        viewModel.rfLinkProfileOptions.observe(this) { options ->
            setSpinnerAdapter(binding.layoutRFLinkSpinner.editText, options)
        }
        viewModel.memoryBankOptions.observe(this) { options ->
            setSpinnerAdapter(binding.layoutMemoryBankSpinner.editText, options)
        }
        viewModel.sessionIdOptions.observe(this) { options ->
            setSpinnerAdapter(binding.layoutSessionIdSpinner.editText, options)
        }
        viewModel.inventoriedFlagOptions.observe(this) { options ->
            setSpinnerAdapter(binding.layoutInventoriedFlagSpinner.editText, options)
        }
        // Adapter untuk Q Value Spinner
        viewModel.qValueOptions.let { options -> // qValueOptions adalah List<Int> langsung, bukan LiveData
            setSpinnerAdapter(binding.layoutQValueSpinner.editText, options.map { it.toString() })
        }


        // Mengupdate UI dengan nilai saat ini dari ViewModel
        viewModel.currentPower.observe(this) { power ->
            (binding.layoutPowerSpinner.editText as? AutoCompleteTextView)?.setText(power?.toString(), false)
        }
        viewModel.currentFrequencyRegion.observe(this) { region ->
            (binding.layoutFrequencyRegionSpinner.editText as? AutoCompleteTextView)?.setText(region, false)
        }

        // Observer untuk currentFreqHoppingType mungkin tidak lagi diperlukan jika UI tidak menampilkannya
        // atau jika hanya ada satu tipe (Custom Float).
        // viewModel.currentFreqHoppingType.observe(this) { type ->
        //    // ... logika untuk RadioGroup jika masih digunakan ...
        // }
        viewModel.currentFreqHoppingTableOrValue.observe(this) { tableOrValue ->
            (binding.layoutFreqHoppingSpinner.editText as? AutoCompleteTextView)?.setText(tableOrValue ?: "", false)
        }
        viewModel.isFreqHoppingContainerVisible.observe(this) { isVisible ->
            // Pastikan ID container di XML adalah containerFreqHopping atau sesuaikan di bawah
            binding.containerFreqHopping?.visibility = if (isVisible == true) View.VISIBLE else View.GONE
        }

        viewModel.currentProtocol.observe(this) { protocol ->
            (binding.layoutProtocolSpinner.editText as? AutoCompleteTextView)?.setText(protocol, false)
        }
        viewModel.currentRFLinkProfile.observe(this) { profile ->
            (binding.layoutRFLinkSpinner.editText as? AutoCompleteTextView)?.setText(profile, false)
        }

        viewModel.currentMemoryBank.observe(this) { bankString ->
            (binding.layoutMemoryBankSpinner.editText as? AutoCompleteTextView)?.setText(bankString, false)
        }
        viewModel.isMemoryBankDetailsVisible.observe(this) { isVisible ->
            // Pastikan ID container di XML adalah containerMemoryBankDetails atau sesuaikan
            binding.containerMemoryBankDetails?.visibility = if (isVisible == true) View.VISIBLE else View.GONE
        }
        viewModel.currentMemoryBankOffset.observe(this) { offsetStr ->
            // Karena LiveData sekarang String, tidak perlu ?.toString()
            binding.editTextMemoryBankOffset.setText(offsetStr ?: "")
        }
        viewModel.currentMemoryBankLength.observe(this) { lengthStr ->
            binding.editTextMemoryBankLength.setText(lengthStr ?: "")
        }

        viewModel.currentSessionId.observe(this) { sessionId ->
            (binding.layoutSessionIdSpinner.editText as? AutoCompleteTextView)?.setText(sessionId, false)
        }
        viewModel.currentInventoriedFlag.observe(this) { flag ->
            (binding.layoutInventoriedFlagSpinner.editText as? AutoCompleteTextView)?.setText(flag, false)
        }
        viewModel.currentQValue.observe(this) { qValue ->
            // Update UI untuk Q Value (misalnya, Spinner atau EditText)
            (binding.layoutQValueSpinner.editText as? AutoCompleteTextView)?.setText(qValue?.toString(), false)
        }


        viewModel.isFastInventoryOpen.observe(this) { isOpen ->
            isOpen?.let {
                if (it) binding.radioGroupFastInventory.check(R.id.radioFastInventoryOpen)
                else binding.radioGroupFastInventory.check(R.id.radioFastInventoryClose)
            } ?: binding.radioGroupFastInventory.clearCheck()
        }

        viewModel.isTagFocusEnabled.observe(this) { isChecked ->
            binding.switchTagFocus.isChecked = isChecked // LiveData sudah non-null
        }
        viewModel.isFastIDEnabled.observe(this) { isChecked ->
            binding.switchFastID.isChecked = isChecked // LiveData sudah non-null
        }
    }

    private fun <T> setSpinnerAdapter(autoCompleteTextView: View?, options: List<T>?) {
        (autoCompleteTextView as? AutoCompleteTextView)?.let { actv ->
            if (options != null) {
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options)
                actv.setAdapter(adapter)
            } else {
                actv.setAdapter(null)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
