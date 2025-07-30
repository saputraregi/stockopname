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
// import androidx.appcompat.app.AlertDialog // Tidak digunakan saat ini, bisa dihapus jika tidak ada rencana
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aplikasistockopnameperpus.MyApplication
// import com.rscja.deviceapi.RFIDWithUHFUART // Akan di-uncomment nanti
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.FragmentWriteTagBinding
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel
import java.nio.charset.StandardCharsets

class WriteTagFragment : Fragment() {

    private var _binding: FragmentWriteTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReadWriteTagViewModel by activityViewModels()
    private var currentTargetEpcForOperations: String? = null // Diupdate oleh viewModel.targetTagEpc

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWriteTagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val myApp = requireActivity().application as MyApplication
        if (!myApp.isReaderOpened()) {
            Toast.makeText(context, "Simulasi: Reader tidak aktif. Mode tulis mungkin terbatas.", Toast.LENGTH_LONG).show()
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
        binding.textInputLayoutNewEpcHex.isEnabled = !disabled // Enable/disable TextInputLayout
        binding.editTextNewEpcHex.isEnabled = !disabled
        binding.textInputLayoutAccessPassword.isEnabled = !disabled // Enable/disable TextInputLayout
        binding.editTextAccessPassword.isEnabled = !disabled
        binding.spinnerLockType.isEnabled = !disabled
        binding.buttonWriteNewEpc.isEnabled = !disabled
        binding.buttonLockTag.isEnabled = !disabled
    }

    private fun setupListeners() {
        binding.buttonReadTargetTag.setOnClickListener {
            binding.editTextCurrentEpc.setText("Membaca...")
            binding.textViewWriteStatus.text = "Status: Membaca tag target..."
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
                } else if (hexInput.length > binding.textInputLayoutNewEpcHex.counterMaxLength) { // Validasi tambahan jika perlu
                    binding.textInputLayoutNewEpcHex.error = "Panjang EPC melebihi ${binding.textInputLayoutNewEpcHex.counterMaxLength} karakter."
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
        val accessPassword = binding.editTextAccessPassword.text.toString().uppercase() // Umumnya diperlukan untuk menulis

        if (currentTargetEpcForOperations == null || currentTargetEpcForOperations == "Membaca target...") {
            Toast.makeText(context, "Baca tag target terlebih dahulu.", Toast.LENGTH_SHORT).show()
            binding.textViewWriteStatus.text = "Status: Target EPC belum ditentukan."
            return
        }

        if (newEpcHex.isEmpty() || newEpcHex.length % 4 != 0 || !newEpcHex.all { it.isDigit() || it in 'A'..'F' }) {
            binding.textInputLayoutNewEpcHex.error = getString(R.string.error_invalid_hex_format_multiple_of_4)
            Toast.makeText(context, "Format HEX EPC baru tidak valid.", Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.textInputLayoutNewEpcHex.error = null
        }

        // Validasi password opsional di sini jika selalu dibutuhkan untuk write,
        // tapi umumnya SDK akan menanganinya jika diperlukan.
        // if (accessPassword.length != 8 || !accessPassword.all { it.isDigit() || it in 'A'..'F' }) {
        //     binding.textInputLayoutAccessPassword.error = getString(R.string.error_invalid_password_format_8_hex)
        // Toast.makeText(context, "Format password akses tidak valid.", Toast.LENGTH_SHORT).show()
        // return
        // } else {
        // binding.textInputLayoutAccessPassword.error = null
        // }


        binding.textViewWriteStatus.text = "Status: Menulis EPC baru..."
        viewModel.writeEpcToTag(currentTargetEpcForOperations, newEpcHex, accessPassword)
    }

    private fun attemptLockTag() {
        val accessPassword = binding.editTextAccessPassword.text.toString().uppercase()
        val selectedLockTypePosition = binding.spinnerLockType.selectedItemPosition
        // val selectedLockTypeText = binding.spinnerLockType.selectedItem.toString() // Untuk logging

        if (currentTargetEpcForOperations == null || currentTargetEpcForOperations == "Membaca target...") {
            Toast.makeText(context, "Baca tag target terlebih dahulu untuk operasi kunci.", Toast.LENGTH_SHORT).show()
            binding.textViewWriteStatus.text = "Status: Target EPC belum ditentukan."
            return
        }

        if (accessPassword.length != 8 || !accessPassword.all { it.isDigit() || it in 'A'..'F' }) {
            binding.textInputLayoutAccessPassword.error = getString(R.string.error_invalid_password_format_8_hex)
            Toast.makeText(context, "Format password akses tidak valid untuk mengunci.", Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.textInputLayoutAccessPassword.error = null
        }

        // TODO SDK: Konversi selectedLockTypePosition ke tipe enum yang sesuai dengan SDK Chainway
        // Contoh:
        // val lockMem: RFIDWithUHFUART.LockMemMode
        // val lockMode: RFIDWithUHFUART.LockMode
        // when (selectedLockTypePosition) {
        // 0 -> { /* UNLOCK */ lockMem = ...; lockMode = RFIDWithUHFUART.LockMode.UNLOCK; }
        // 1 -> { /* PERMAUNLOCK */ lockMem = ...; lockMode = RFIDWithUHFUART.LockMode.PERMAUNLOCK; }
        // 2 -> { /* LOCK */ lockMem = ...; lockMode = RFIDWithUHFUART.LockMode.LOCK; }
        // 3 -> { /* Kunci Memori EPC */ lockMem = RFIDWithUHFUART.LockMemMode.EPC_MEM; lockMode = ...; }
        // ... dst
        // default -> {
        // Toast.makeText(context, "Tipe kunci tidak valid.", Toast.LENGTH_SHORT).show()
        // return
        // }
        // }

        Log.d("WriteTagFragment", "Attempting to lock tag. Target: $currentTargetEpcForOperations, PW: $accessPassword, LockTypePos: $selectedLockTypePosition")
        binding.textViewWriteStatus.text = "Status: Mengunci tag..."
        // viewModel.lockTagMemory(currentTargetEpcForOperations, accessPassword, lockMem, lockMode) // Panggil dengan parameter SDK
        viewModel.lockTagMemory(currentTargetEpcForOperations, accessPassword) // Untuk simulasi saat ini

    }


    private fun setupSpinner() {
        // ArrayAdapter sudah di-set dari XML android:entries="@array/lock_types_array"
        // Anda bisa menggunakan ArrayAdapter kustom jika ingin tampilan item spinner yang berbeda
        binding.spinnerLockType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedValue = parent?.getItemAtPosition(position).toString()
                Log.d("WriteTagFragment", "Spinner item selected: position $position, value: $selectedValue")
                // Aksi lain jika diperlukan saat item dipilih
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Tidak ada aksi
            }
        }
    }

    private fun observeViewModel() {
        viewModel.targetTagEpc.observe(viewLifecycleOwner) { epc ->
            currentTargetEpcForOperations = epc // Simpan EPC target saat ini
            binding.editTextCurrentEpc.setText(epc ?: getString(R.string.no_tag_found_placeholder))
            if (epc != null && epc != "Membaca target...") {
                binding.textViewWriteStatus.text = "Target EPC: $epc"
            } else if (epc == null && binding.editTextCurrentEpc.text.toString() != "Membaca...") {
                binding.textViewWriteStatus.text = "Target EPC: Belum ada atau gagal dibaca."
            }
        }

        viewModel.writeStatus.observe(viewLifecycleOwner) { result ->
            val (success, message) = result
            val displayMessage = message ?: (if (success) getString(R.string.operation_successful) else getString(R.string.operation_failed))
            Toast.makeText(context, displayMessage, Toast.LENGTH_LONG).show()
            binding.textViewWriteStatus.text = "Status: $displayMessage"
            if (success) {
                binding.editTextNewEpcHex.text?.clear()
                previewHexToAscii("") // Kosongkan preview juga
                // Secara opsional, baca ulang tag target untuk mengonfirmasi EPC baru
                // viewModel.readTargetTagForWrite()
            }
        }

        viewModel.lockStatus.observe(viewLifecycleOwner) { result ->
            val (success, message) = result
            val displayMessage = message ?: (if (success) getString(R.string.lock_operation_successful) else getString(R.string.lock_operation_failed))
            Toast.makeText(context, displayMessage, Toast.LENGTH_LONG).show()
            binding.textViewWriteStatus.text = "Status: $displayMessage"
        }
    }

    private fun previewHexToAscii(hexString: String) {
        if (hexString.isEmpty()) {
            binding.textViewAsciiPreviewValue.text = ""
            return
        }
        // Validasi dasar, pastikan genap dan hanya hex char
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
