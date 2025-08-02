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
// import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aplikasistockopnameperpus.MyApplication // Pastikan import ini benar
// import com.rscja.deviceapi.RFIDWithUHFUART
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.FragmentWriteTagBinding
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import java.nio.charset.StandardCharsets

class WriteTagFragment : Fragment() {

    private var _binding: FragmentWriteTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReadWriteTagViewModel by activityViewModels()
    private var currentTargetEpcForOperations: String? = null
    private lateinit var sdkManager: ChainwaySDKManager // Tetap lateinit

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWriteTagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ===== INISIALISASI sdkManager DI SINI =====
        // 1. Dapatkan instance MyApplication
        val myApp = requireActivity().application as MyApplication
        // 2. Akses properti sdkManager dari instance MyApplication
        sdkManager = myApp.sdkManager // Pastikan 'sdkManager' adalah nama properti yang benar di MyApplication Anda

        // ===== LANJUTKAN DENGAN KODE ANDA YANG LAIN =====
        // Sekarang sdkManager sudah diinisialisasi
        if (!sdkManager.connectDevices()) {
            Toast.makeText(requireContext(), "Reader tidak aktif. Mode tulis mungkin terbatas.", Toast.LENGTH_LONG).show()
            disableWriteUI(true)
        } else {
            disableWriteUI(false) // Pastikan UI enable jika reader aktif
        }

        setupListeners()
        setupSpinner()
        observeViewModel()
        previewHexToAscii(binding.editTextNewEpcHex.text.toString()) // Preview initial text
    }

    private fun disableWriteUI(disabled: Boolean) {
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
            binding.editTextCurrentEpc.setText(getString(R.string.status_membaca_target)) // Gunakan string resource
            binding.textViewWriteStatus.text = getString(R.string.status_membaca_tag_target_detail) // Gunakan string resource
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
                if (hexInput.isNotEmpty() && (hexInput.length % 4 != 0 || !hexInput.all { it.isDigit() || it in 'A'..'F' })) {
                    binding.textInputLayoutNewEpcHex.error = getString(R.string.error_invalid_hex_format_multiple_of_4)
                } else if (hexInput.length > binding.textInputLayoutNewEpcHex.counterMaxLength) {
                    binding.textInputLayoutNewEpcHex.error = getString(R.string.error_epc_length_exceeded, binding.textInputLayoutNewEpcHex.counterMaxLength) // Gunakan string resource
                }
                else {
                    binding.textInputLayoutNewEpcHex.error = null
                }
            }
        })

        binding.editTextAccessPassword.addTextChangedListener(object: TextWatcher{
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

        if (currentTargetEpcForOperations == null || currentTargetEpcForOperations == getString(R.string.status_membaca_target)) {
            Toast.makeText(requireContext(), getString(R.string.error_read_target_tag_first), Toast.LENGTH_SHORT).show()
            binding.textViewWriteStatus.text = getString(R.string.status_target_epc_undefined)
            return
        }

        if (newEpcHex.isEmpty() || newEpcHex.length % 4 != 0 || !newEpcHex.all { it.isDigit() || it in 'A'..'F' }) {
            binding.textInputLayoutNewEpcHex.error = getString(R.string.error_invalid_hex_format_multiple_of_4)
            Toast.makeText(requireContext(), getString(R.string.error_invalid_new_epc_hex_format), Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.textInputLayoutNewEpcHex.error = null
        }

        binding.textViewWriteStatus.text = getString(R.string.status_writing_new_epc)
        viewModel.writeEpcToTag(currentTargetEpcForOperations, newEpcHex, accessPassword)
    }

    private fun attemptLockTag() {
        val accessPassword = binding.editTextAccessPassword.text.toString().uppercase()
        val selectedLockTypePosition = binding.spinnerLockType.selectedItemPosition

        if (currentTargetEpcForOperations == null || currentTargetEpcForOperations == getString(R.string.status_membaca_target)) {
            Toast.makeText(requireContext(), getString(R.string.error_read_target_tag_for_lock), Toast.LENGTH_SHORT).show()
            binding.textViewWriteStatus.text = getString(R.string.status_target_epc_undefined)
            return
        }

        if (accessPassword.length != 8 || !accessPassword.all { it.isDigit() || it in 'A'..'F' }) {
            binding.textInputLayoutAccessPassword.error = getString(R.string.error_invalid_password_format_8_hex)
            Toast.makeText(requireContext(), getString(R.string.error_invalid_access_password_for_lock), Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.textInputLayoutAccessPassword.error = null
        }

        Log.d("WriteTagFragment", "Attempting to lock tag. Target: $currentTargetEpcForOperations, PW: $accessPassword, LockTypePos: $selectedLockTypePosition")
        binding.textViewWriteStatus.text = getString(R.string.status_locking_tag)
        viewModel.lockTagMemory(currentTargetEpcForOperations, accessPassword) // Sesuaikan jika parameter SDK sudah ada

    }


    private fun setupSpinner() {
        binding.spinnerLockType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedValue = parent?.getItemAtPosition(position).toString()
                Log.d("WriteTagFragment", "Spinner item selected: position $position, value: $selectedValue")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun observeViewModel() {
        viewModel.targetTagEpc.observe(viewLifecycleOwner) { epc ->
            currentTargetEpcForOperations = epc
            binding.editTextCurrentEpc.setText(epc ?: getString(R.string.no_tag_found_placeholder))
            if (epc != null && epc != getString(R.string.status_membaca_target)) {
                binding.textViewWriteStatus.text = getString(R.string.target_epc_status, epc)
            } else if (epc == null && binding.editTextCurrentEpc.text.toString() != getString(R.string.status_membaca_target_simple)) { // "Membaca..." beda dengan "Membaca target..."
                binding.textViewWriteStatus.text = getString(R.string.target_epc_not_found_or_failed)
            }
        }

        viewModel.writeStatus.observe(viewLifecycleOwner) { result ->
            val (success, message) = result
            val displayMessage = message ?: (if (success) getString(R.string.operation_successful) else getString(R.string.operation_failed))
            Toast.makeText(requireContext(), displayMessage, Toast.LENGTH_LONG).show()
            binding.textViewWriteStatus.text = getString(R.string.status_prefix, displayMessage)
            if (success) {
                binding.editTextNewEpcHex.text?.clear()
                previewHexToAscii("")
            }
        }

        viewModel.lockStatus.observe(viewLifecycleOwner) { result ->
            val (success, message) = result
            val displayMessage = message ?: (if (success) getString(R.string.lock_operation_successful) else getString(R.string.lock_operation_failed))
            Toast.makeText(requireContext(), displayMessage, Toast.LENGTH_LONG).show()
            binding.textViewWriteStatus.text = getString(R.string.status_prefix, displayMessage)
        }
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
