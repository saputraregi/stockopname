package com.example.aplikasistockopnameperpus // Sesuaikan package Anda

import android.app.Application
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aplikasistockopnameperpus.data.database.PairingStatus // <-- IMPORT PairingStatus
import com.example.aplikasistockopnameperpus.databinding.ActivityBookTaggingBinding
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.viewmodel.BookTaggingViewModel
import com.example.aplikasistockopnameperpus.viewmodel.TaggingState
import com.example.aplikasistockopnameperpus.data.repository.BookRepository

class BookTaggingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookTaggingBinding
    private val viewModel: BookTaggingViewModel by viewModels {
        BookTaggingViewModelFactory(
            application,
            (application as MyApplication).bookRepository,
            (application as MyApplication).sdkManager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookTaggingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUIListeners()
        observeViewModel()

        viewModel.clearProcessAndPrepareForNext()
    }

    private fun setupUIListeners() {
        binding.editTextItemCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Tombol search bisa diaktifkan/dinonaktifkan berdasarkan input
                // binding.buttonSearchManual.isEnabled = !s.isNullOrBlank() // Jika ada tombol search manual
            }
        })

        // Jika Anda memiliki tombol search manual:
        // binding.buttonSearchManual.setOnClickListener {
        //     val itemCode = binding.editTextItemCode.text.toString()
        //     if (itemCode.isNotBlank()) {
        //         viewModel.searchBookByItemCode(itemCode)
        //     } else {
        //         Toast.makeText(this, "Masukkan kode item", Toast.LENGTH_SHORT).show()
        //     }
        // }

        binding.buttonScanBarcode.setOnClickListener {
            viewModel.triggerBarcodeScan()
        }

        binding.buttonStartTagging.setOnClickListener {
            viewModel.startTaggingProcess()
        }

        binding.buttonClear.setOnClickListener {
            viewModel.clearProcessAndPrepareForNext()
            binding.editTextItemCode.text = null
            binding.editTextItemCode.requestFocus()
        }
    }

    private fun observeViewModel() {
        viewModel.taggingState.observe(this) { state ->
            updateUIBasedOnTaggingState(state)
        }

        viewModel.currentBook.observe(this) { book ->
            displayBookDetails(book)
            // Tombol tagging diaktifkan jika buku ditemukan dan siap ditag/retag,
            // dan tidak sedang dalam proses.
            binding.buttonStartTagging.isEnabled = book != null &&
                    (viewModel.taggingState.value == TaggingState.BOOK_FOUND_UNTAGGED ||
                            viewModel.taggingState.value == TaggingState.BOOK_FOUND_ALREADY_TAGGED)
        }

        viewModel.scannedEpcDuringProcess.observe(this) { epc ->
            if (epc != null) {
                binding.textViewScannedEpcLabel.visibility = View.VISIBLE
                binding.textViewScannedEpcValue.visibility = View.VISIBLE
                binding.textViewScannedEpcValue.text = epc
            } else {
                binding.textViewScannedEpcLabel.visibility = View.GONE
                binding.textViewScannedEpcValue.visibility = View.GONE
                binding.textViewScannedEpcValue.text = "" // Bersihkan teks jika null
            }
        }

        viewModel.statusMessage.observe(this) { message ->
            binding.textViewProcessStatus.text = message
            if (message != null && (
                        message.contains("Error", ignoreCase = true) ||
                                message.contains("Gagal", ignoreCase = true) ||
                                message.contains("tidak ditemukan", ignoreCase = true) ||
                                message.contains("Timeout", ignoreCase = true)
                        )
            ) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarTagging.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.editTextItemCode.isEnabled = !isLoading
            binding.buttonScanBarcode.isEnabled = !isLoading
            // Penanganan isEnabled untuk buttonStartTagging lebih baik di observe currentBook dan taggingState
            binding.buttonClear.isEnabled = !isLoading
        }
    }

    private fun updateUIBasedOnTaggingState(state: TaggingState?) {
        Log.d("BookTaggingActivity", "Updating UI for state: $state")

        // Default enable/disable state
        binding.editTextItemCode.isEnabled = true
        binding.buttonScanBarcode.isEnabled = true
        binding.buttonStartTagging.isEnabled = false // Default disabled, diaktifkan secara spesifik

        when (state) {
            TaggingState.IDLE -> {
                binding.layoutBookDetails.visibility = View.GONE
                binding.textViewScannedEpcLabel.visibility = View.GONE
                binding.textViewScannedEpcValue.visibility = View.GONE
            }
            TaggingState.BOOK_FOUND_UNTAGGED, TaggingState.BOOK_FOUND_ALREADY_TAGGED -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = true // Aktifkan jika buku ditemukan
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
            }
            TaggingState.AWAITING_TAG_PLACEMENT,
            TaggingState.WRITING_EPC,
            TaggingState.READING_TID,
            TaggingState.SAVING_TO_DB -> {
                binding.layoutBookDetails.visibility = View.VISIBLE // Detail buku tetap terlihat
                binding.buttonStartTagging.isEnabled = false
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
            }
            TaggingState.PROCESS_SUCCESS -> {
                binding.layoutBookDetails.visibility = View.VISIBLE // Detail buku tetap terlihat
                binding.buttonStartTagging.isEnabled = false // Nonaktifkan setelah sukses, user harus clear
                // binding.editTextItemCode.isEnabled = true // Bisa input baru
                // binding.buttonScanBarcode.isEnabled = true // Bisa scan baru
                Toast.makeText(this, "Proses Penandaan Buku Berhasil!", Toast.LENGTH_LONG).show()
            }
            TaggingState.PROCESS_FAILED,
            TaggingState.ERROR_BOOK_NOT_FOUND,
            TaggingState.ERROR_SDK,
            TaggingState.ERROR_CONVERSION -> {
                // Jika buku ada, tombol tagging bisa diaktifkan untuk mencoba lagi
                binding.buttonStartTagging.isEnabled = viewModel.currentBook.value != null
                if (state == TaggingState.ERROR_BOOK_NOT_FOUND) {
                    binding.layoutBookDetails.visibility = View.GONE
                } else {
                    binding.layoutBookDetails.visibility = View.VISIBLE
                }
            }
            null -> {
                binding.layoutBookDetails.visibility = View.GONE
                binding.textViewScannedEpcLabel.visibility = View.GONE
                binding.textViewScannedEpcValue.visibility = View.GONE
            }
        }
        // Pastikan loading state juga diperbarui karena taggingState bisa mempengaruhi visibilitas progress bar
        if (viewModel.isLoading.value == false &&
            state != TaggingState.WRITING_EPC &&
            state != TaggingState.READING_TID &&
            state != TaggingState.SAVING_TO_DB &&
            state != TaggingState.AWAITING_TAG_PLACEMENT) {
            binding.progressBarTagging.visibility = View.GONE
        } else if (viewModel.isLoading.value == true) {
            binding.progressBarTagging.visibility = View.VISIBLE
        }
    }


    private fun displayBookDetails(book: BookMaster?) {
        if (book != null) {
            binding.layoutBookDetails.visibility = View.VISIBLE
            binding.textViewBookTitle.text = book.title
            //binding.textViewBookAuthor.text = book.author ?: "N/A"

            // Menggunakan enum PairingStatus untuk logika
            val statusText = when (book.pairingStatus) {
                PairingStatus.NOT_PAIRED -> "BELUM DITAG"
                PairingStatus.PAIRING_PENDING -> "PAIRING DITUNDA" // Sesuaikan dengan arti di aplikasi Anda
                PairingStatus.PAIRED_WRITE_PENDING -> "MENUNGGU TULIS EPC"
                PairingStatus.PAIRED_WRITE_SUCCESS -> "BERHASIL DITAG"
                PairingStatus.PAIRED_WRITE_FAILED -> "GAGAL TULIS EPC (SUDAH PAIR)"
                PairingStatus.PAIRING_FAILED -> "GAGAL PAIRING"
            }
            binding.textViewCurrentRfidStatus.text = statusText
            binding.textViewCurrentRfidTag.text = book.rfidTagHex ?: "TIDAK ADA"

            // Teks tombol berdasarkan status pairing
            if (book.pairingStatus == PairingStatus.PAIRED_WRITE_SUCCESS ||
                (book.pairingStatus != PairingStatus.NOT_PAIRED && book.pairingStatus != PairingStatus.PAIRING_FAILED && !book.rfidTagHex.isNullOrBlank())
            ) {
                binding.buttonStartTagging.text = "Ulangi Proses Penandaan (Timpa)"
            } else {
                binding.buttonStartTagging.text = "Mulai Proses Penandaan"
            }

        } else {
            binding.layoutBookDetails.visibility = View.GONE
            binding.buttonStartTagging.text = "Mulai Proses Penandaan" // Default text
        }
    }

    @Suppress("UNCHECKED_CAST")
    class BookTaggingViewModelFactory(
        private val application: Application,
        private val bookRepository: BookRepository,
        private val sdkManager: ChainwaySDKManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BookTaggingViewModel::class.java)) {
                return BookTaggingViewModel(application, bookRepository, sdkManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
