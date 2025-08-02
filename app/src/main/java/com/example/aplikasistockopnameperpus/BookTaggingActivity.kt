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
import com.example.aplikasistockopnameperpus.R // Pastikan R diimport dengan benar
import com.example.aplikasistockopnameperpus.databinding.ActivityBookTaggingBinding // Import ViewBinding
import com.example.aplikasistockopnameperpus.MyApplication // Asumsi untuk akses Repository/SDK Manager
import com.example.aplikasistockopnameperpus.data.database.BookMaster
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.viewmodel.BookTaggingViewModel
import com.example.aplikasistockopnameperpus.viewmodel.TaggingState
import com.example.aplikasistockopnameperpus.data.repository.BookRepository

class BookTaggingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookTaggingBinding
    private val viewModel: BookTaggingViewModel by viewModels {
        // Anda PERLU ViewModelFactory untuk meng-inject dependensi
        // Ini adalah contoh sederhana, sesuaikan dengan implementasi factory Anda
        BookTaggingViewModelFactory(
            application,
            (application as MyApplication).bookRepository, // Asumsi akses dari Application class
            (application as MyApplication).sdkManager    // Asumsi akses dari Application class
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookTaggingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUIListeners()
        observeViewModel()

        // Setel status awal UI
        viewModel.clearProcessAndPrepareForNext() // Memastikan state awal bersih
    }

    private fun setupUIListeners() {
        binding.editTextItemCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Tombol search diaktifkan jika ada teks
                // Namun, pencarian otomatis mungkin tidak diinginkan, jadi kita cari via tombol
                // Jika ingin pencarian otomatis setelah delay, implementasikan di sini
            }
        })

        // Listener untuk tombol Search manual (jika ada, atau kita langsung cari saat barcode scan)
        // Jika tidak ada tombol search khusus, fungsi searchBookByItemCode dipanggil
        // setelah scan barcode atau setelah enter di EditText.
        // Untuk sekarang, kita asumsikan pencarian dipicu oleh scan atau setelah user selesai mengetik
        // dan mungkin menekan tombol 'Enter' pada keyboard (tidak dihandle di sini secara eksplisit).

        binding.buttonScanBarcode.setOnClickListener {
            viewModel.triggerBarcodeScan()
        }

        binding.buttonStartTagging.setOnClickListener {
            viewModel.startTaggingProcess()
        }

        binding.buttonClear.setOnClickListener {
            viewModel.clearProcessAndPrepareForNext()
            binding.editTextItemCode.text = null // Bersihkan juga input field
            binding.editTextItemCode.requestFocus()
        }
    }

    private fun observeViewModel() {
        viewModel.taggingState.observe(this) { state ->
            updateUIBasedOnTaggingState(state)
        }

        viewModel.currentBook.observe(this) { book ->
            displayBookDetails(book)
            // Tombol tagging hanya aktif jika buku ditemukan dan siap ditag atau sudah ditag (untuk re-tag)
            binding.buttonStartTagging.isEnabled = book != null
        }

        viewModel.scannedEpcDuringProcess.observe(this) { epc ->
            if (epc != null) {
                binding.textViewScannedEpcLabel.visibility = View.VISIBLE
                binding.textViewScannedEpcValue.visibility = View.VISIBLE
                binding.textViewScannedEpcValue.text = epc
            } else {
                binding.textViewScannedEpcLabel.visibility = View.GONE
                binding.textViewScannedEpcValue.visibility = View.GONE
            }
        }

        viewModel.statusMessage.observe(this) { message ->
            binding.textViewProcessStatus.text = message
            if (message != null && (
                        message.contains("Error", ignoreCase = true) ||
                                message.contains("Gagal", ignoreCase = true) ||
                                message.contains("tidak ditemukan", ignoreCase = true)
                        )
            ) {
                // Tampilkan Toast untuk error agar lebih terlihat
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarTagging.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Disable interaksi UI saat loading
            binding.editTextItemCode.isEnabled = !isLoading
            binding.buttonScanBarcode.isEnabled = !isLoading
            // binding.buttonStartTagging.isEnabled HANYA jika buku ada DAN tidak loading
            binding.buttonClear.isEnabled = !isLoading
        }
    }

    private fun updateUIBasedOnTaggingState(state: TaggingState?) {
        Log.d("BookTaggingActivity", "Updating UI for state: $state")
        when (state) {
            TaggingState.IDLE -> {
                binding.layoutBookDetails.visibility = View.GONE
                binding.buttonStartTagging.isEnabled = false
                binding.textViewScannedEpcLabel.visibility = View.GONE
                binding.textViewScannedEpcValue.visibility = View.GONE
                binding.editTextItemCode.isEnabled = true
                binding.buttonScanBarcode.isEnabled = true
            }
            TaggingState.BOOK_FOUND_UNTAGGED, TaggingState.BOOK_FOUND_ALREADY_TAGGED -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = true
                binding.editTextItemCode.isEnabled = false // Disable input setelah buku ditemukan
                binding.buttonScanBarcode.isEnabled = false
            }
            TaggingState.AWAITING_TAG_PLACEMENT,
            TaggingState.WRITING_EPC,
            TaggingState.READING_TID,
            TaggingState.SAVING_TO_DB -> {
                binding.buttonStartTagging.isEnabled = false // Disable selama proses
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
            }
            TaggingState.PROCESS_SUCCESS -> {
                binding.buttonStartTagging.isEnabled = false // Setelah sukses, user harus clear/next
                binding.editTextItemCode.isEnabled = true // Atau false sampai di-clear? Tergantung flow
                binding.buttonScanBarcode.isEnabled = true
                Toast.makeText(this, "Penandaan Buku Berhasil!", Toast.LENGTH_LONG).show()
            }
            TaggingState.PROCESS_FAILED, TaggingState.ERROR_BOOK_NOT_FOUND, TaggingState.ERROR_SDK -> {
                binding.buttonStartTagging.isEnabled = viewModel.currentBook.value != null // Aktif jika buku ada, agar bisa coba lagi
                binding.editTextItemCode.isEnabled = true // Boleh input baru jika gagal
                binding.buttonScanBarcode.isEnabled = true
            }
            null -> {
                // Handle null state if necessary, e.g., revert to IDLE
                binding.layoutBookDetails.visibility = View.GONE
                binding.buttonStartTagging.isEnabled = false
            }
        }
        // Update loading state juga, karena taggingState berubah bisa jadi loading selesai/mulai
        binding.progressBarTagging.visibility = if (viewModel.isLoading.value == true) View.VISIBLE else View.GONE
    }


    private fun displayBookDetails(book: BookMaster?) {
        if (book != null) {
            binding.layoutBookDetails.visibility = View.VISIBLE
            binding.textViewBookTitle.text = book.title
            binding.textViewBookAuthor.text = book.author ?: "N/A"
            binding.textViewCurrentRfidStatus.text = book.rfidPairingStatus ?: "BELUM_DITAG"
            binding.textViewCurrentRfidTag.text = book.rfidTagHex ?: "TIDAK ADA"

            if (book.rfidPairingStatus == "BERHASIL_DITAG" || !book.rfidTagHex.isNullOrBlank()) {
                binding.buttonStartTagging.text = "Ulangi Proses Penandaan (Timpa)"
            } else {
                binding.buttonStartTagging.text = "Mulai Proses Penandaan"
            }

        } else {
            binding.layoutBookDetails.visibility = View.GONE
            binding.buttonStartTagging.text = "Mulai Proses Penandaan"
        }
    }

    // --- ViewModelFactory Sederhana (Contoh) ---
    // Pindahkan ini ke file terpisah jika lebih kompleks atau digunakan di banyak tempat
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
