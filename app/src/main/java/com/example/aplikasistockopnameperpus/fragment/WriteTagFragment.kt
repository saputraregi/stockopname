package com.example.aplikasistockopnameperpus.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
// Hapus import MyApplication dan ChainwaySDKManager jika tidak lagi digunakan secara langsung
// import com.example.aplikasistockopnameperpus.MyApplication
// import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.FragmentWriteTagBinding
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel
import java.nio.charset.StandardCharsets
// Import untuk Lock Type jika Anda menggunakan enum dari SDK secara langsung di Fragment (kurang direkomendasikan)
// import com.rscja.deviceapi.RFIDWithUHFUART

class WriteTagFragment : Fragment() {

    private var _binding: FragmentWriteTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReadWriteTagViewModel by activityViewModels()
    private var currentTargetEpcForOperations: String? = null
    // Hapus sdkManager lokal
    // private lateinit var sdkManager: ChainwaySDKManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWriteTagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Tidak ada lagi inisialisasi atau penggunaan sdkManager lokal di sini.
        // Pengecekan status reader dan penonaktifan UI akan dikontrol oleh ViewModel.

        setupListeners()
        setupSpinner() // Spinner setup tetap di sini
        observeViewModel()
        previewHexToAscii(binding.editTextNewEpcHex.text.toString())

        // Contoh bagaimana ViewModel bisa mengontrol UI berdasarkan kesiapan reader
        // viewModel.isReaderReadyForWrite.observe(viewLifecycleOwner) { isReady ->
        //     disableWriteUI(!isReady)
        //     if (!isReady && isAdded) {
        //         Toast.makeText(requireContext(), "Reader tidak aktif. Mode tulis mungkin terbatas.", Toast.LENGTH_LONG).show()
        //     }
        // }
        // Untuk sekarang, kita asumsikan ViewModel akan menampilkan error jika operasi gagal karena reader tidak siap.
        // Dan fungsionalitas disableWriteUI akan lebih dikontrol oleh state operasi (misalnya, saat sedang menulis).
        disableWriteUI(false) // Default UI enabled, ViewModel akan mengontrol lebih lanjut
    }

    private fun disableWriteUI(disabled: Boolean) {
        // Logika ini mungkin perlu disesuaikan atau dipicu oleh lebih banyak state dari ViewModel
        // (misalnya, isWriting, isLocking, isReaderReady)
        binding.buttonReadTargetTag.isEnabled = !disabled
        binding.textInputLayoutNewEpcHex.isEnabled = !disabled
        binding.editTextNewEpcHex.isEnabled = !disabled
        binding.textInputLayoutAccessPassword.isEnabled = !disabled
        binding.editTextAccessPassword.isEnabled = !disabled
        binding.spinnerLockType.isEnabled = !disabled
        binding.buttonWriteNewEpc.isEnabled = !disabled
        binding.buttonLockTag.isEnabled = !disabled
    }

    private fun setupListeners() {
        binding.buttonReadTargetTag.setOnClickListener {
            // UI update sebelum memanggil ViewModel
            binding.editTextCurrentEpc.setText(getString(R.string.status_membaca_target))
            binding.textViewWriteStatus.text = getString(R.string.status_membaca_tag_target_detail)
            viewModel.readTargetTagForWrite()
        }

        binding.buttonWriteNewEpc.setOnClickListener { attemptWriteNewEpc() }
        binding.buttonLockTag.setOnClickListener { attemptLockTag() }

        binding.editTextNewEpcHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hexInput = s.toString().uppercase()
                previewHexToAscii(hexInput)
                // Validasi panjang dan format
                val maxLength = binding.textInputLayoutNewEpcHex.counterMaxLength
                if (hexInput.isNotEmpty()) {
                    if (hexInput.length % 4 != 0 || !hexInput.all { it.isDigit() || it in 'A'..'F' }) {
                        binding.textInputLayoutNewEpcHex.error = getString(R.string.error_invalid_hex_format_multiple_of_4)
                    } else if (maxLength > 0 && hexInput.length > maxLength) { // CounterMaxLength bisa 0 jika tidak diset
                        binding.textInputLayoutNewEpcHex.error = getString(R.string.error_epc_length_exceeded, maxLength)
                    } else {
                        binding.textInputLayoutNewEpcHex.error = null
                    }
                } else {
                    binding.textInputLayoutNewEpcHex.error = null // Kosong berarti tidak ada error format
                }
            }
        })

        binding.editTextAccessPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hexInput = s.toString().uppercase()
                if (hexInput.isNotEmpty() && (hexInput.length != 8 || !hexInput.all { it.isDigit() || it in 'A'..'F' })) {
                    binding.textInputLayoutAccessPassword.error = getString(R.string.error_invalid_password_format_8_hex)
                } else {
                    binding.textInputLayoutAccessPassword.error = null
                }
            }
        })
    }

    private fun attemptWriteNewEpc() {
        val newEpcHex = binding.editTextNewEpcHex.text.toString().uppercase()
        val accessPassword = binding.editTextAccessPassword.text.toString().uppercase()

        // Validasi EPC target
        if (currentTargetEpcForOperations.isNullOrEmpty() || currentTargetEpcForOperations == getString(R.string.status_membaca_target)) {
            Toast.makeText(requireContext(), getString(R.string.error_read_target_tag_first), Toast.LENGTH_SHORT).show()
            binding.textViewWriteStatus.text = getString(R.string.status_target_epc_undefined)
            return
        }

        // Validasi format New EPC
        val maxLengthNewEpc = binding.textInputLayoutNewEpcHex.counterMaxLength
        if (newEpcHex.isEmpty() || newEpcHex.length % 4 != 0 || !newEpcHex.all { it.isDigit() || it in 'A'..'F' }) {
            binding.textInputLayoutNewEpcHex.error = getString(R.string.error_invalid_hex_format_multiple_of_4)
            Toast.makeText(requireContext(), getString(R.string.error_invalid_new_epc_hex_format), Toast.LENGTH_SHORT).show()
            return
        } else if (maxLengthNewEpc > 0 && newEpcHex.length > maxLengthNewEpc) {
            binding.textInputLayoutNewEpcHex.error = getString(R.string.error_epc_length_exceeded, maxLengthNewEpc)
            Toast.makeText(requireContext(), getString(R.string.error_epc_length_exceeded, maxLengthNewEpc), Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.textInputLayoutNewEpcHex.error = null
        }

        // Validasi format Password (opsional, bisa juga hanya dicek jika tidak kosong)
        if (accessPassword.isNotEmpty() && (accessPassword.length != 8 || !accessPassword.all { it.isDigit() || it in 'A'..'F' })) {
            binding.textInputLayoutAccessPassword.error = getString(R.string.error_invalid_password_format_8_hex)
            Toast.makeText(requireContext(), getString(R.string.error_invalid_password_format_8_hex), Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.textInputLayoutAccessPassword.error = null
        }


        binding.textViewWriteStatus.text = getString(R.string.status_writing_new_epc)
        // Pastikan currentTargetEpcForOperations tidak null di sini karena sudah divalidasi
        viewModel.writeEpcToTag(currentTargetEpcForOperations!!, newEpcHex, accessPassword.ifEmpty { "00000000" })
    }

    private fun attemptLockTag() {
        val accessPassword = binding.editTextAccessPassword.text.toString().uppercase()
        // Dapatkan pilihan dari spinner. Ini akan menjadi integer atau string
        // yang perlu Anda petakan ke nilai integer yang benar untuk SDK.
        val selectedLockTypeItem = binding.spinnerLockType.selectedItem.toString()
        val selectedLockTypePosition = binding.spinnerLockType.selectedItemPosition // Index

        // Validasi EPC target
        if (currentTargetEpcForOperations.isNullOrEmpty() || currentTargetEpcForOperations == getString(R.string.status_membaca_target)) {
            Toast.makeText(requireContext(), getString(R.string.error_read_target_tag_for_lock), Toast.LENGTH_SHORT).show()
            binding.textViewWriteStatus.text = getString(R.string.status_target_epc_undefined)
            return
        }

        // Validasi Password
        if (accessPassword.length != 8 || !accessPassword.all { it.isDigit() || it in 'A'..'F' }) {
            binding.textInputLayoutAccessPassword.error = getString(R.string.error_invalid_password_format_8_hex)
            Toast.makeText(requireContext(), getString(R.string.error_invalid_access_password_for_lock), Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.textInputLayoutAccessPassword.error = null
        }

        // --- PEMETAAN SPINNER KE PARAMETER SDK ---
        // Anda perlu memetakan selectedLockTypeItem atau selectedLockTypePosition
        // ke nilai integer yang benar untuk `lockBankInt` dan `lockActionInt`
        // yang akan digunakan oleh `viewModel.lockTagMemory`.
        // Contoh sederhana (Anda perlu menyesuaikan ini dengan string.xml dan dokumentasi SDK):
        // Misalnya, spinner Anda berisi: "User - Lock", "EPC - Permanent Lock", dll.
        // Atau Anda memiliki dua spinner: satu untuk BANK, satu untuk ACTION.

        var lockBankInt = -1
        var lockActionInt = -1

        // Contoh jika spinner Anda memiliki kombinasi bank dan aksi dalam satu item:
        // Ini adalah contoh kasar, Anda perlu logika yang lebih baik atau dua spinner.
        // Sebaiknya, ViewModel atau utilitas yang melakukan pemetaan ini.
        when (selectedLockTypeItem) { // Ganti dengan item dari R.array.lock_options Anda
            "User - Lock" -> { // Contoh string dari array Anda
                lockBankInt = 0 // Ganti dengan nilai int dari dokumentasi SDK untuk User Bank
                lockActionInt = 0 // Ganti dengan nilai int dari dokumentasi SDK untuk Lock Action
            }
            "EPC - Permanent Lock" -> {
                lockBankInt = 2 // Ganti dengan nilai int dari dokumentasi SDK untuk EPC Bank
                lockActionInt = 2 // Ganti dengan nilai int dari dokumentasi SDK untuk Permanent Lock Action
            }
            // Tambahkan case lain sesuai opsi di spinner Anda
            else -> {
                Toast.makeText(requireContext(), "Tipe kunci tidak valid dipilih.", Toast.LENGTH_SHORT).show()
                return
            }
        }
        // Pastikan lockBankInt dan lockActionInt sudah benar sebelum memanggil ViewModel
        if (lockBankInt == -1 || lockActionInt == -1) {
            Toast.makeText(requireContext(), "Gagal memetakan tipe kunci.", Toast.LENGTH_SHORT).show()
            return
        }


        Log.d("WriteTagFragment", "Attempting to lock tag. Target: $currentTargetEpcForOperations, PW: $accessPassword, BankInt: $lockBankInt, ActionInt: $lockActionInt")
        binding.textViewWriteStatus.text = getString(R.string.status_locking_tag)
        // Pastikan currentTargetEpcForOperations tidak null
        viewModel.lockTagMemory(
            currentTargetEpcForOperations!!,
            accessPassword,
            lockBankInt,
            lockActionInt
        )
    }

    private fun setupSpinner() {
        // ArrayAdapter menggunakan array dari R.array.lock_options (pastikan ini ada di strings.xml)
        // ArrayAdapter.createFromResource(
        //     requireContext(),
        //     R.array.lock_options, // Anda perlu membuat array ini di strings.xml
        //     android.R.layout.simple_spinner_item
        // ).also { adapter ->
        //     adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        //     binding.spinnerLockType.adapter = adapter
        // }

        binding.spinnerLockType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedValue = parent?.getItemAtPosition(position).toString()
                Log.d("WriteTagFragment", "Spinner item selected: position $position, value: $selectedValue")
                // Anda bisa langsung melakukan pemetaan di sini jika diperlukan,
                // atau saat tombol "Lock Tag" ditekan.
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        // Set default selection jika perlu, atau biarkan item pertama terpilih.
    }

    private fun observeViewModel() {
        viewModel.targetTagEpc.observe(viewLifecycleOwner) { epc ->
            currentTargetEpcForOperations = epc
            binding.editTextCurrentEpc.setText(epc ?: getString(R.string.no_tag_found_placeholder))

            if (epc != null && epc != getString(R.string.status_membaca_target)) {
                binding.textViewWriteStatus.text = getString(R.string.target_epc_status, epc)
                // Aktifkan tombol tulis dan lock jika EPC target sudah ada
                // binding.buttonWriteNewEpc.isEnabled = true // Atau berdasarkan validasi input EPC baru
                // binding.buttonLockTag.isEnabled = true
            } else if (epc == null && binding.editTextCurrentEpc.text.toString() != getString(R.string.status_membaca_target_simple)) {
                binding.textViewWriteStatus.text = getString(R.string.target_epc_not_found_or_failed)
                // binding.buttonWriteNewEpc.isEnabled = false
                // binding.buttonLockTag.isEnabled = false
            } else {
                // Saat masih "Membaca target..."
                // binding.buttonWriteNewEpc.isEnabled = false
                // binding.buttonLockTag.isEnabled = false
            }
        }

        viewModel.writeStatus.observe(viewLifecycleOwner) { result ->
            val (success, message) = result
            val displayMessage = message ?: (if (success) getString(R.string.operation_successful) else getString(R.string.operation_failed))
            if (isAdded) Toast.makeText(requireContext(), displayMessage, Toast.LENGTH_LONG).show()
            binding.textViewWriteStatus.text = getString(R.string.status_prefix, displayMessage)
            if (success) {
                binding.editTextNewEpcHex.text?.clear()
                previewHexToAscii("") // Kosongkan preview
            }
            // Aktifkan/nonaktifkan UI berdasarkan status operasi
            // disableWriteUI(false) // Misalnya, aktifkan kembali UI setelah operasi selesai
        }

        viewModel.lockStatus.observe(viewLifecycleOwner) { result ->
            val (success, message) = result
            val displayMessage = message ?: (if (success) getString(R.string.lock_operation_successful) else getString(R.string.lock_operation_failed))
            if (isAdded) Toast.makeText(requireContext(), displayMessage, Toast.LENGTH_LONG).show()
            binding.textViewWriteStatus.text = getString(R.string.status_prefix, displayMessage)
            // disableWriteUI(false) // Aktifkan kembali UI
        }

        // Anda mungkin ingin LiveData terpisah untuk mengontrol progress bar atau status loading
        // viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
        //     binding.progressBarWrite.visibility = if (isLoading) View.VISIBLE else View.GONE
        //     disableWriteUI(isLoading) // Nonaktifkan UI saat loading
        // }
    }

    private fun previewHexToAscii(hexString: String) {
        if (hexString.isEmpty()) {
            binding.textViewAsciiPreviewValue.text = ""
            return
        }
        if (hexString.length % 2 != 0 || !hexString.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) {
            binding.textViewAsciiPreviewValue.text = getString(R.string.invalid_hex_for_ascii_preview)
            return
        }

        try {
            val bytes = ByteArray(hexString.length / 2)
            for (i in bytes.indices) {
                val index = i * 2
                val byteStr = hexString.substring(index, index + 2)
                bytes[i] = byteStr.toInt(16).toByte()
            }
            binding.textViewAsciiPreviewValue.text = String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e("WriteTagFragment", "Error converting hex to ASCII: ${e.message}")
            binding.textViewAsciiPreviewValue.text = getString(R.string.ascii_conversion_error)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = WriteTagFragment()
    }
}
