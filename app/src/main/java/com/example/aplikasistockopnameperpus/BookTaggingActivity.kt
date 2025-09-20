package com.example.aplikasistockopnameperpus

import android.app.Application
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent // <-- Pastikan import ini ada
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aplikasistockopnameperpus.data.database.PairingStatus
import com.example.aplikasistockopnameperpus.databinding.ActivityBookTaggingBinding
import com.example.aplikasistockopnameperpus.data.database.BookMaster
// MyApplication sudah di-import oleh ViewModelFactory
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

    // --- AWAL PENAMBAHAN UNTUK TOMBOL FISIK ---
    private val TRIGGER_KEY_MAIN = 239 // Sesuaikan jika berbeda untuk perangkat Anda
    private val TRIGGER_KEY_BACKUP = 139 // Sesuaikan jika berbeda
    // --- AKHIR PENAMBAHAN UNTUK TOMBOL FISIK ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookTaggingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar setup jika ada (sesuai komentar Anda)
        // setSupportActionBar(binding.toolbarBookTagging) // Ganti dengan ID toolbar Anda jika ada
        // supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // supportActionBar?.title = getString(R.string.title_activity_book_tagging)


        setupUIListeners()
        observeViewModel()

        if (savedInstanceState == null && viewModel.taggingState.value == TaggingState.IDLE) {
            viewModel.clearProcessAndPrepareForNext()
        }
        Log.d(TAG, "onCreate completed. Initial State: ${viewModel.taggingState.value}")
    }

    // Jika ada Toolbar dengan tombol kembali:
    // override fun onSupportNavigateUp(): Boolean {
    //     onBackPressedDispatcher.onBackPressed()
    //     return true
    // }

    // --- AWAL PENAMBAHAN: Metode untuk menangani onKeyDown dan aksi tombol fisik ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        if (keyCode == TRIGGER_KEY_MAIN || keyCode == TRIGGER_KEY_BACKUP) {
            if (event.repeatCount == 0) {
                Log.d(TAG, "Tombol fisik ditekan (keyCode: $keyCode), memanggil onPhysicalTriggerPressed.")
                onPhysicalTriggerPressed()
            }
            return true // Event sudah ditangani
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onPhysicalTriggerPressed() {
        val currentState = viewModel.taggingState.value
        Log.i(TAG, "Physical trigger pressed. Current tagging state: $currentState")

        // Nonaktifkan trigger jika sedang dalam proses yang tidak interaktif
        // atau jika ViewModel menandakan sedang loading.
        val isCurrentlyLoading = viewModel.isLoading.value == true
        val isUninterruptibleState = currentState in listOf(
            TaggingState.BOOK_SEARCHING,
            TaggingState.READING_TID,
            TaggingState.READING_EPC_FOR_CONFIRMATION,
            TaggingState.WRITING_EPC,
            TaggingState.SAVING_TO_DB
        )

        if (isCurrentlyLoading || isUninterruptibleState) {
            val reason = when {
                isCurrentlyLoading -> "ViewModel is loading"
                isUninterruptibleState -> "State $currentState is uninterruptible"
                else -> "Operation in progress"
            }
            showToast(getString(R.string.toast_operation_in_progress))
            Log.d(TAG, "Physical trigger ignored: $reason.")
            return
        }


        when (currentState) {
            TaggingState.IDLE,
            TaggingState.ERROR_BOOK_NOT_FOUND,
            TaggingState.PROCESS_SUCCESS -> {
                // Proses pertama atau setelah selesai/error tidak ada buku: Membaca barcode buku
                Log.d(TAG, "Physical trigger: State is $currentState. Triggering barcode scan.")
                viewModel.triggerBarcodeScan()
            }
            TaggingState.BOOK_FOUND_UNTAGGED,
            TaggingState.BOOK_FOUND_ALREADY_TAGGED -> {
                // Setelah buku ditemukan: Memulai proses tagging (yang akan melibatkan pembacaan TID)
                Log.d(TAG, "Physical trigger: Book found ($currentState). Requesting start tagging process (will read TID).")
                viewModel.userRequestsStartTaggingProcess()
            }
            TaggingState.EPC_CONFIRMED_AWAITING_WRITE -> {
                // Setelah TID terbaca dan EPC (jika ada) dikonfirmasi: Melakukan penulisan EPC
                Log.d(TAG, "Physical trigger: EPC confirmed. Confirming write operation.")
                viewModel.userConfirmsWriteOperation()
            }
            TaggingState.PROCESS_FAILED,
            TaggingState.ERROR_SDK,
            TaggingState.ERROR_CONVERSION -> {
                if (viewModel.currentBook.value != null) {
                    showToast(getString(R.string.toast_retrying_process))
                    Log.d(TAG, "Physical trigger: Process failed ($currentState) with a book. Requesting start tagging process again.")
                    viewModel.userRequestsStartTaggingProcess() // Coba lagi proses tagging untuk buku ini
                } else {
                    // Jika tidak ada buku (misal error SDK terjadi sebelum buku ditemukan)
                    Log.d(TAG, "Physical trigger: Process failed ($currentState) without a book. Triggering barcode scan.")
                    viewModel.triggerBarcodeScan()
                }
            }
            // null case ditangani oleh default atau tidak perlu aksi spesifik dari tombol fisik
            // karena state biasanya tidak akan null setelah onCreate.
            else -> {
                Log.w(TAG, "Physical trigger: No specific action defined for state $currentState.")
                showToast(getString(R.string.toast_action_not_available_for_state, currentState.toString()))
            }
        }
    }
    // --- AKHIR PENAMBAHAN: Metode untuk menangani onKeyDown dan aksi tombol fisik ---

    private fun setupUIListeners() {
        binding.editTextItemCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Tidak ada tombol search manual di XML yang diberikan,
                // jadi tidak ada aksi di sini.
            }
        })

        binding.buttonScanBarcode.setOnClickListener {
            Log.d(TAG, "Tombol UI Scan Barcode diklik.")
            if (viewModel.isLoading.value == true ||
                viewModel.taggingState.value == TaggingState.BOOK_SEARCHING ||
                viewModel.taggingState.value == TaggingState.READING_TID || // Tambahkan state lain jika perlu
                viewModel.taggingState.value == TaggingState.WRITING_EPC
            ) {
                showToast(getString(R.string.toast_operation_in_progress))
                return@setOnClickListener
            }
            viewModel.triggerBarcodeScan()
        }

        binding.buttonStartTagging.setOnClickListener { // Tombol Aksi Utama di UI
            Log.d(TAG, "Tombol UI Aksi Utama (buttonStartTagging) diklik. Current state: ${viewModel.taggingState.value}")
            if (viewModel.isLoading.value == true) {
                showToast(getString(R.string.toast_operation_in_progress))
                return@setOnClickListener
            }
            handleMainActionButtonClick()
        }

        binding.buttonClear.setOnClickListener {
            Log.d(TAG, "Tombol Clear diklik.")
            val currentState = viewModel.taggingState.value
            // Izinkan clear langsung jika proses sudah selesai (sukses/gagal) atau di awal, atau jika tidak ada buku
            if (viewModel.currentBook.value == null || // Jika tidak ada buku, selalu bisa clear
                currentState == TaggingState.IDLE ||
                currentState == TaggingState.PROCESS_SUCCESS ||
                currentState == TaggingState.PROCESS_FAILED ||
                currentState == TaggingState.ERROR_BOOK_NOT_FOUND ||
                currentState == TaggingState.ERROR_SDK ||
                currentState == TaggingState.ERROR_CONVERSION
            ) {
                viewModel.clearProcessAndPrepareForNext()
                binding.editTextItemCode.text = null
                binding.editTextItemCode.requestFocus()
            } else {
                // Jika ada buku dan sedang dalam proses aktif, tampilkan konfirmasi
                showClearConfirmationDialog()
            }
        }
    }

    private fun handleMainActionButtonClick() {
        val currentState = viewModel.taggingState.value
        Log.d(TAG, "handleMainActionButtonClick. State Saat Ini: $currentState")

        val isCurrentlyLoading = viewModel.isLoading.value == true
        val isUninterruptibleState = currentState in listOf(
            TaggingState.BOOK_SEARCHING,
            TaggingState.READING_TID,
            TaggingState.READING_EPC_FOR_CONFIRMATION,
            TaggingState.WRITING_EPC,
            TaggingState.SAVING_TO_DB
        )

        if (isCurrentlyLoading || isUninterruptibleState) {
            showToast(getString(R.string.toast_operation_in_progress))
            Log.d(TAG, "Main action button (buttonStartTagging) ignored: Operation in progress or loading.")
            return
        }

        when (currentState) {
            TaggingState.BOOK_FOUND_UNTAGGED, TaggingState.BOOK_FOUND_ALREADY_TAGGED -> {
                viewModel.userRequestsStartTaggingProcess()
            }
            TaggingState.EPC_CONFIRMED_AWAITING_WRITE -> {
                viewModel.userConfirmsWriteOperation()
            }
            TaggingState.PROCESS_FAILED, TaggingState.ERROR_SDK, TaggingState.ERROR_CONVERSION -> {
                if (viewModel.currentBook.value != null) {
                    showToast(getString(R.string.toast_retrying_process))
                    viewModel.userRequestsStartTaggingProcess()
                } else {
                    // Tombol ini mungkin tidak visible/enabled jika tidak ada buku dan error
                    showToast(getString(R.string.toast_no_book_to_retry))
                }
            }
            // Untuk state seperti IDLE, PROCESS_SUCCESS, ERROR_BOOK_NOT_FOUND,
            // tombol buttonStartTagging seharusnya tidak visible berdasarkan updateUIBasedOnTaggingState.
            // Jika tetap bisa diklik, ini adalah kondisi tak terduga atau UI belum update.
            else -> {
                showToast(getString(R.string.toast_action_not_available_for_state, currentState.toString()))
            }
        }
    }

    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_confirm_clear))
            .setMessage(getString(R.string.dialog_message_confirm_clear))
            .setPositiveButton(getString(R.string.dialog_button_yes_clear)) { _, _ ->
                viewModel.clearProcessAndPrepareForNext()
                binding.editTextItemCode.text = null
                binding.editTextItemCode.requestFocus()
            }
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.taggingState.observe(this) { state ->
            updateUIBasedOnTaggingState(state)
        }

        viewModel.currentBook.observe(this) { book ->
            displayBookDetails(book)
        }

        viewModel.displayedEpc.observe(this) { epc ->
            binding.textViewScannedEpcValue.text = epc ?: ""
        }

        viewModel.displayedTid.observe(this) { tid ->
            binding.textViewScannedTidValue.text = tid ?: ""
        }

        viewModel.statusMessage.observe(this) { message ->
            binding.textViewProcessStatus.text = message ?: getString(R.string.status_ready)
            message?.let {
                if (it.startsWith(getString(R.string.confirm_write_tag_prefix_toast)) || // Menggunakan string resource
                    it.contains(getString(R.string.successfully_saved_indicator_toast), ignoreCase = true) || // Menggunakan string resource
                    it.contains(getString(R.string.error_indicator_toast), ignoreCase = true) || // Menggunakan string resource
                    it.contains(getString(R.string.failed_indicator_toast), ignoreCase = true) || // Menggunakan string resource
                    it.contains(getString(R.string.timeout_indicator_toast), ignoreCase = true) || // Menggunakan string resource
                    it.contains(getString(R.string.not_found_indicator_toast), ignoreCase = true)) { // Menggunakan string resource

                    val duration = if (it.startsWith(getString(R.string.confirm_write_tag_prefix_toast)) || it.contains(getString(R.string.successfully_saved_indicator_toast))) {
                        Toast.LENGTH_LONG
                    } else {
                        Toast.LENGTH_SHORT
                    }
                    showToast(it, duration)
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            // Visibilitas progressBarTagging akan dikontrol oleh updateUIBasedOnTaggingState
            // yang juga mempertimbangkan state. Mengaturnya di sini bisa menyebabkan kedipan.
            // Jika ingin tetap di sini, pastikan tidak konflik dengan updateUIBasedOnTaggingState.
            // Namun, lebih baik dikontrol di satu tempat.
            // binding.progressBarTagging.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun updateUIBasedOnTaggingState(state: TaggingState?) {
        Log.i(TAG, "Memperbarui UI untuk state: $state. Buku Saat Ini: ${viewModel.currentBook.value?.title}")

        // Default UI states
        binding.editTextItemCode.isEnabled = true
        binding.buttonScanBarcode.isEnabled = true // Tombol UI scan barcode
        binding.buttonStartTagging.visibility = View.GONE // Tombol UI "Mulai Tagging/Konfirmasi Tulis"
        binding.buttonStartTagging.text = getString(R.string.button_default_action)
        binding.buttonStartTagging.isEnabled = false
        binding.buttonClear.isEnabled = true

        binding.textViewScannedTidLabel.visibility = View.GONE
        binding.textViewScannedTidValue.visibility = View.GONE
        binding.textViewScannedEpcLabel.visibility = View.GONE
        binding.textViewScannedEpcValue.visibility = View.GONE
        binding.textViewScannedEpcLabel.text = getString(R.string.label_epc_scanned_on_tag) // Default

        val showProgressBar = viewModel.isLoading.value == true ||
                state in listOf(
            TaggingState.BOOK_SEARCHING,
            TaggingState.READING_TID,
            TaggingState.READING_EPC_FOR_CONFIRMATION,
            TaggingState.WRITING_EPC,
            TaggingState.SAVING_TO_DB
        )
        binding.progressBarTagging.visibility = if (showProgressBar) View.VISIBLE else View.GONE


        when (state) {
            TaggingState.IDLE -> {
                binding.layoutBookDetails.visibility = View.GONE
                binding.editTextItemCode.text = null // Bersihkan editText saat IDLE
                binding.editTextItemCode.requestFocus()
                // Tombol UI buttonScanBarcode aktif, buttonStartTagging GONE
            }
            TaggingState.BOOK_SEARCHING -> {
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonClear.isEnabled = false
                binding.layoutBookDetails.visibility = View.GONE
            }
            TaggingState.BOOK_FOUND_UNTAGGED -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false // Tombol UI scan barcode dinonaktifkan
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = true
                binding.buttonStartTagging.text = getString(R.string.button_start_tagging_process_untagged)
            }
            TaggingState.BOOK_FOUND_ALREADY_TAGGED -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = true
                binding.buttonStartTagging.text = getString(R.string.button_reprocess_tag)
            }
            TaggingState.READING_TID -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE // Bisa tetap terlihat tapi disabled
                binding.buttonStartTagging.isEnabled = false
                binding.buttonStartTagging.text = getString(R.string.button_reading_tid)
            }
            TaggingState.READING_EPC_FOR_CONFIRMATION -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.textViewScannedTidLabel.visibility = View.VISIBLE
                binding.textViewScannedTidValue.visibility = View.VISIBLE

                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = false
                binding.buttonStartTagging.text = getString(R.string.button_reading_epc)
            }
            TaggingState.EPC_CONFIRMED_AWAITING_WRITE -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.textViewScannedTidLabel.visibility = View.VISIBLE
                binding.textViewScannedTidValue.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.text = getString(R.string.label_epc_current_on_tag)
                binding.textViewScannedEpcValue.visibility = View.VISIBLE

                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = true
                binding.buttonStartTagging.text = getString(R.string.button_confirm_write_tag)
            }
            TaggingState.WRITING_EPC -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.textViewScannedTidLabel.visibility = View.VISIBLE
                binding.textViewScannedTidValue.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.text = getString(R.string.label_epc_target_to_write)
                binding.textViewScannedEpcValue.visibility = View.VISIBLE
                binding.textViewScannedEpcValue.text = viewModel.displayedEpc.value ?: ""

                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = false
                binding.buttonStartTagging.text = getString(R.string.button_writing_to_tag)
            }
            TaggingState.SAVING_TO_DB -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.textViewScannedTidLabel.visibility = View.VISIBLE
                binding.textViewScannedTidValue.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.text = getString(R.string.label_epc_newly_written)
                binding.textViewScannedEpcValue.visibility = View.VISIBLE

                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.GONE
            }
            TaggingState.PROCESS_SUCCESS -> {
                binding.layoutBookDetails.visibility = View.VISIBLE // Tetap tampilkan detail buku terakhir
                binding.textViewScannedTidLabel.visibility = View.VISIBLE
                binding.textViewScannedTidValue.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.text = getString(R.string.label_epc_newly_written)
                binding.textViewScannedEpcValue.visibility = View.VISIBLE

                binding.editTextItemCode.isEnabled = true // Siap untuk item baru
                binding.buttonScanBarcode.isEnabled = true // Tombol UI scan barcode aktif
                binding.buttonStartTagging.visibility = View.GONE // Tombol UI "Start Tagging" tidak relevan
                binding.editTextItemCode.text = null // Kosongkan untuk input buku berikutnya
                binding.editTextItemCode.requestFocus()
            }
            TaggingState.PROCESS_FAILED, TaggingState.ERROR_SDK, TaggingState.ERROR_CONVERSION -> {
                val bookExists = viewModel.currentBook.value != null
                binding.editTextItemCode.isEnabled = !bookExists
                binding.buttonScanBarcode.isEnabled = !bookExists
                binding.buttonStartTagging.visibility = if (bookExists) View.VISIBLE else View.GONE
                binding.buttonStartTagging.isEnabled = bookExists
                binding.buttonStartTagging.text = if (bookExists)
                    getString(R.string.button_retry_process)
                else
                    getString(R.string.button_default_action)

                binding.layoutBookDetails.visibility = if (bookExists) View.VISIBLE else View.GONE
            }
            TaggingState.ERROR_BOOK_NOT_FOUND -> {
                binding.layoutBookDetails.visibility = View.GONE
                binding.editTextItemCode.isEnabled = true
                binding.buttonScanBarcode.isEnabled = true
                binding.buttonStartTagging.visibility = View.GONE
            }
            null -> { // State awal sebelum ViewModel siap atau jika ada reset tak terduga
                binding.layoutBookDetails.visibility = View.GONE
                binding.editTextItemCode.isEnabled = true
                binding.buttonScanBarcode.isEnabled = true
            }
        }
        // Pastikan tombol clear dinonaktifkan jika progressBarTagging terlihat
        // atau jika state adalah BOOK_SEARCHING atau proses aktif lainnya.
        val enableClearButton = binding.progressBarTagging.visibility == View.GONE &&
                state != TaggingState.BOOK_SEARCHING &&
                state != TaggingState.READING_TID &&
                state != TaggingState.READING_EPC_FOR_CONFIRMATION &&
                state != TaggingState.WRITING_EPC &&
                state != TaggingState.SAVING_TO_DB
        binding.buttonClear.isEnabled = enableClearButton
    }

    private fun displayBookDetails(book: BookMaster?) {
        if (book != null) {
            binding.layoutBookDetails.visibility = View.VISIBLE
            binding.textViewBookTitle.text = book.title
            binding.textViewItemCodeValue.text = book.itemCode // Dari DB

            val statusText = when (book.pairingStatus) {
                PairingStatus.NOT_PAIRED -> getString(R.string.status_pairing_not_paired)
                PairingStatus.PAIRING_PENDING -> getString(R.string.status_pairing_pending)
                PairingStatus.PAIRED_WRITE_PENDING -> getString(R.string.status_pairing_write_pending)
                PairingStatus.PAIRED_WRITE_SUCCESS -> getString(R.string.status_pairing_write_success)
                PairingStatus.PAIRED_WRITE_FAILED -> getString(R.string.status_pairing_write_failed)
                PairingStatus.PAIRING_FAILED -> getString(R.string.status_pairing_failed)
            }
            binding.textViewCurrentRfidStatus.text = statusText
            binding.textViewCurrentRfidTag.text = book.rfidTagHex ?: getString(R.string.status_no_rfid_data)
            binding.textViewCurrentTid.text = book.tid ?: getString(R.string.status_no_tid_data)
        } else {
            binding.layoutBookDetails.visibility = View.GONE
            binding.textViewItemCodeValue.text = "" // Kosongkan jika buku null
            binding.textViewCurrentRfidStatus.text = ""
            binding.textViewCurrentRfidTag.text = ""
            binding.textViewCurrentTid.text = ""
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
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
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private const val TAG = "BookTaggingActivity"
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called.")
        // Menghentikan operasi SDK jika sedang berjalan saat Activity di-pause
        // untuk menghemat baterai dan menghindari error.
        // Cek state dari ViewModel untuk memutuskan apakah perlu membatalkan operasi.
        val currentTaggingState = viewModel.taggingState.value
        if (viewModel.isLoading.value == true || currentTaggingState in listOf(
                TaggingState.BOOK_SEARCHING,
                TaggingState.READING_TID,
                TaggingState.READING_EPC_FOR_CONFIRMATION,
                TaggingState.WRITING_EPC
            )) {
            Log.i(TAG, "onPause: Cancelling current SDK operation due to state $currentTaggingState or isLoading.")
            viewModel.cancelCurrentOperation() // Pastikan ada implementasi yang sesuai di ViewModel
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called.")
        // ViewModel.onCleared() akan menangani pelepasan listener SDK.
        // Tidak perlu memanggil sdkManager.stop... secara eksplisit di sini jika ViewModel
        // sudah bersih-bersih dengan benar.
    }
}
