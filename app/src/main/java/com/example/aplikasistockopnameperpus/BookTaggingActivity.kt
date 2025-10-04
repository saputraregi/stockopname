package com.example.aplikasistockopnameperpus

import android.app.Application
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
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
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.viewmodel.BookTaggingViewModel
import com.example.aplikasistockopnameperpus.viewmodel.TaggingState
// Pastikan BookRepository diimport jika digunakan langsung di Factory, meskipun biasanya dari MyApplication
import com.example.aplikasistockopnameperpus.data.repository.BookRepository

class BookTaggingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookTaggingBinding
    private val viewModel: BookTaggingViewModel by viewModels {
        // Asumsi MyApplication dan BookRepository sudah benar di sini
        BookTaggingViewModelFactory(
            application,
            (application as MyApplication).bookRepository,
            (application as MyApplication).sdkManager
        )
    }

    private val TRIGGER_KEY_MAIN = 293
    private val TRIGGER_KEY_BACKUP = 139

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookTaggingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // setSupportActionBar(binding.toolbarBookTagging)
        // supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // supportActionBar?.title = getString(R.string.title_activity_book_tagging)

        setupUIListeners()
        observeViewModel()

        if (savedInstanceState == null && viewModel.taggingState.value == TaggingState.IDLE) {
            viewModel.clearProcessAndPrepareForNext()
        }
        Log.d(TAG, "onCreate completed. Initial State: ${viewModel.taggingState.value}")
    }

    // override fun onSupportNavigateUp(): Boolean {
    //     onBackPressedDispatcher.onBackPressed()
    //     return true
    // }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        if (keyCode == TRIGGER_KEY_MAIN || keyCode == TRIGGER_KEY_BACKUP) {
            if (event.repeatCount == 0) {
                Log.d(TAG, "Tombol fisik ditekan (keyCode: $keyCode), memanggil onPhysicalTriggerPressed.")
                onPhysicalTriggerPressed()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onPhysicalTriggerPressed() {
        val currentState = viewModel.taggingState.value
        Log.i(TAG, "Physical trigger pressed. Current tagging state: $currentState")

        val isCurrentlyLoading = viewModel.isLoading.value == true
        // Sesuaikan dengan state baru yang tidak bisa diinterupsi
        val isUninterruptibleState = currentState in listOf(
            TaggingState.BOOK_SEARCHING,
            TaggingState.READING_INITIAL_EPC,      // BARU
            TaggingState.READING_TID_WITH_FILTER, // BARU
            // TaggingState.READING_EPC_FOR_CONFIRMATION, // Ini mungkin tidak lagi jadi state utama
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
                Log.d(TAG, "Physical trigger: State is $currentState. Triggering barcode scan.")
                viewModel.triggerBarcodeScan()
            }
            TaggingState.BOOK_FOUND_UNTAGGED,
            TaggingState.BOOK_FOUND_ALREADY_TAGGED -> {
                Log.d(TAG, "Physical trigger: Book found ($currentState). Requesting start tagging process.")
                viewModel.userRequestsStartTaggingProcess() // Ini akan memulai pembacaan EPC awal
            }
            TaggingState.EPC_CONFIRMED_AWAITING_WRITE -> {
                Log.d(TAG, "Physical trigger: EPC confirmed. Confirming write operation.")
                viewModel.userConfirmsWriteOperation()
            }
            TaggingState.PROCESS_FAILED,
            TaggingState.ERROR_SDK,
            TaggingState.ERROR_CONVERSION -> {
                if (viewModel.currentBook.value != null) {
                    showToast(getString(R.string.toast_retrying_process))
                    Log.d(TAG, "Physical trigger: Process failed ($currentState) with a book. Requesting start tagging process again.")
                    viewModel.userRequestsStartTaggingProcess()
                } else {
                    Log.d(TAG, "Physical trigger: Process failed ($currentState) without a book. Triggering barcode scan.")
                    viewModel.triggerBarcodeScan()
                }
            }
            else -> {
                Log.w(TAG, "Physical trigger: No specific action defined for state $currentState.")
                showToast(getString(R.string.toast_action_not_available_for_state, currentState.toString()))
            }
        }
    }

    private fun setupUIListeners() {
        binding.editTextItemCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Biasanya tidak ada aksi di sini jika pencarian otomatis atau via tombol
            }
        })

        binding.buttonScanBarcode.setOnClickListener {
            Log.d(TAG, "Tombol UI Scan Barcode diklik.")
            // Sesuaikan state di mana scan barcode tidak diizinkan
            if (viewModel.isLoading.value == true ||
                viewModel.taggingState.value in listOf(
                    TaggingState.BOOK_SEARCHING,
                    TaggingState.READING_INITIAL_EPC,
                    TaggingState.READING_TID_WITH_FILTER,
                    TaggingState.WRITING_EPC
                )
            ) {
                showToast(getString(R.string.toast_operation_in_progress))
                return@setOnClickListener
            }
            viewModel.triggerBarcodeScan()
        }

        binding.buttonStartTagging.setOnClickListener {
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
            if (viewModel.currentBook.value == null ||
                currentState == TaggingState.IDLE ||
                currentState == TaggingState.PROCESS_SUCCESS ||
                currentState == TaggingState.PROCESS_FAILED ||
                currentState == TaggingState.ERROR_BOOK_NOT_FOUND ||
                currentState == TaggingState.ERROR_SDK ||
                currentState == TaggingState.ERROR_CONVERSION
            ) {
                viewModel.clearProcessAndPrepareForNext()
            } else {
                showClearConfirmationDialog()
            }
        }

        binding.buttonCancel.setOnClickListener {
            Log.d(TAG, "Tombol Cancel diklik.")
            viewModel.cancelCurrentOperation()
        }
    }

    private fun handleMainActionButtonClick() {
        val currentState = viewModel.taggingState.value
        Log.d(TAG, "handleMainActionButtonClick. State Saat Ini: $currentState")

        val isCurrentlyLoading = viewModel.isLoading.value == true
        // Sesuaikan dengan state baru yang tidak bisa diinterupsi
        val isUninterruptibleState = currentState in listOf(
            TaggingState.BOOK_SEARCHING,
            TaggingState.READING_INITIAL_EPC,
            TaggingState.READING_TID_WITH_FILTER,
            // TaggingState.READING_EPC_FOR_CONFIRMATION,
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
                    showToast(getString(R.string.toast_no_book_to_retry))
                }
            }
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
            // Pastikan TextViewScannedEpcValue ada di layout Anda
            binding.textViewScannedEpcValue.text = epc ?: ""
        }

        viewModel.displayedTid.observe(this) { tid ->
            // Pastikan TextViewScannedTidValue ada di layout Anda
            binding.textViewScannedTidValue.text = tid ?: ""
        }

        viewModel.statusMessage.observe(this) { message ->
            binding.textViewProcessStatus.text = message ?: getString(R.string.status_ready)
            message?.let {
                // Logika Toast Anda sudah terlihat baik, pastikan string resources ada
                if (it.startsWith(getString(R.string.confirm_write_tag_prefix_toast)) ||
                    it.contains(getString(R.string.successfully_saved_indicator_toast), ignoreCase = true) ||
                    it.contains(getString(R.string.error_indicator_toast), ignoreCase = true) ||
                    it.contains(getString(R.string.failed_indicator_toast), ignoreCase = true) ||
                    it.contains(getString(R.string.timeout_indicator_toast), ignoreCase = true) ||
                    it.contains(getString(R.string.not_found_indicator_toast), ignoreCase = true)) {

                    val duration = if (it.startsWith(getString(R.string.confirm_write_tag_prefix_toast)) ||
                        it.contains(getString(R.string.successfully_saved_indicator_toast))) {
                        Toast.LENGTH_LONG
                    } else {
                        Toast.LENGTH_SHORT
                    }
                    showToast(it, duration)
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            // Visibilitas ProgressBar sekarang sepenuhnya dikontrol oleh updateUIBasedOnTaggingState
            // Ini untuk menghindari update ganda atau konflik.
        }
    }

    private fun updateUIBasedOnTaggingState(state: TaggingState?) {
        Log.i(TAG, "Memperbarui UI untuk state: $state. Buku Saat Ini: ${viewModel.currentBook.value?.title}")

        // Default UI states
        binding.editTextItemCode.isEnabled = true
        binding.buttonScanBarcode.isEnabled = true
        binding.buttonStartTagging.visibility = View.GONE
        binding.buttonStartTagging.text = getString(R.string.button_default_action) // Default
        binding.buttonStartTagging.isEnabled = false
        binding.buttonClear.isEnabled = true
        binding.buttonCancel.isEnabled = false // Default cancel button

        binding.textViewScannedTidLabel.visibility = View.GONE
        binding.textViewScannedTidValue.visibility = View.GONE
        binding.textViewScannedEpcLabel.visibility = View.GONE
        binding.textViewScannedEpcValue.visibility = View.GONE
        binding.textViewScannedEpcLabel.text = getString(R.string.label_epc_scanned_on_tag)

        val showProgressBar = viewModel.isLoading.value == true ||
                state in listOf(
            TaggingState.BOOK_SEARCHING,
            TaggingState.READING_INITIAL_EPC,
            TaggingState.READING_TID_WITH_FILTER,
            // TaggingState.READING_EPC_FOR_CONFIRMATION, // Dihapus dari sini jika tidak jadi state utama
            TaggingState.WRITING_EPC,
            TaggingState.SAVING_TO_DB
        )
        binding.progressBarTagging.visibility = if (showProgressBar) View.VISIBLE else View.GONE
        if (showProgressBar) binding.buttonCancel.isEnabled = true // Aktifkan cancel jika loading

        when (state) {
            TaggingState.IDLE -> {
                binding.layoutBookDetails.visibility = View.GONE
                binding.editTextItemCode.text = null
                binding.editTextItemCode.requestFocus()
                // Tombol clear dikelola di akhir fungsi
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
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = true
                binding.buttonStartTagging.text = getString(R.string.button_start_tagging_process_untagged)
                binding.buttonCancel.isEnabled = true
            }
            TaggingState.BOOK_FOUND_ALREADY_TAGGED -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = true
                binding.buttonStartTagging.text = getString(R.string.button_reprocess_tag)
                binding.buttonCancel.isEnabled = true
            }
            TaggingState.READING_INITIAL_EPC -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = false
                binding.buttonStartTagging.text = getString(R.string.button_reading_initial_epc) // PERLU STRING BARU
                binding.buttonCancel.isEnabled = true
            }
            TaggingState.READING_TID_WITH_FILTER -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = false
                binding.buttonStartTagging.text = getString(R.string.button_reading_tid_with_filter) // PERLU STRING BARU

                binding.textViewScannedEpcLabel.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.text = getString(R.string.label_epc_initial_read) // PERLU STRING BARU
                binding.textViewScannedEpcValue.visibility = View.VISIBLE
                // Nilai akan diisi oleh observer viewModel.displayedEpc
                binding.buttonCancel.isEnabled = true
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
                binding.buttonCancel.isEnabled = true
            }
            TaggingState.WRITING_EPC -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.textViewScannedTidLabel.visibility = View.VISIBLE
                binding.textViewScannedTidValue.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.text = getString(R.string.label_epc_target_to_write)
                binding.textViewScannedEpcValue.visibility = View.VISIBLE
                // Nilai akan diisi oleh observer viewModel.displayedEpc (yang seharusnya adalah target EPC)

                binding.editTextItemCode.isEnabled = false
                binding.buttonScanBarcode.isEnabled = false
                binding.buttonStartTagging.visibility = View.VISIBLE
                binding.buttonStartTagging.isEnabled = false
                binding.buttonStartTagging.text = getString(R.string.button_writing_to_tag)
                binding.buttonCancel.isEnabled = true
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
                binding.buttonCancel.isEnabled = false // Proses penting, jangan mudah dicancel, atau true jika ViewModel bisa handle
            }
            TaggingState.PROCESS_SUCCESS -> {
                binding.layoutBookDetails.visibility = View.VISIBLE
                binding.textViewScannedTidLabel.visibility = View.VISIBLE
                binding.textViewScannedTidValue.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.visibility = View.VISIBLE
                binding.textViewScannedEpcLabel.text = getString(R.string.label_epc_newly_written)
                binding.textViewScannedEpcValue.visibility = View.VISIBLE

                binding.editTextItemCode.isEnabled = true
                binding.buttonScanBarcode.isEnabled = true
                binding.buttonStartTagging.visibility = View.GONE
                binding.editTextItemCode.text = null
                binding.editTextItemCode.requestFocus()
                binding.buttonCancel.isEnabled = false
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
                    getString(R.string.button_default_action) // Tombol ini mungkin tidak pernah terlihat jika tidak ada buku

                binding.layoutBookDetails.visibility = if (bookExists) View.VISIBLE else View.GONE
                binding.buttonCancel.isEnabled = true // Izinkan cancel untuk coba lagi atau clear
            }
            TaggingState.ERROR_BOOK_NOT_FOUND -> {
                binding.layoutBookDetails.visibility = View.GONE
                binding.editTextItemCode.isEnabled = true
                binding.buttonScanBarcode.isEnabled = true
                binding.buttonStartTagging.visibility = View.GONE
                binding.buttonCancel.isEnabled = true // Izinkan clear
            }
            // Hapus TaggingState.READING_TID dan TaggingState.READING_EPC_FOR_CONFIRMATION jika tidak lagi digunakan
            // sebagai state utama dalam alur baru.
            null -> {
                binding.layoutBookDetails.visibility = View.GONE
                binding.editTextItemCode.isEnabled = true
                binding.buttonScanBarcode.isEnabled = true
                binding.buttonCancel.isEnabled = false
            }
        }
        // Logika tombol clear, pastikan tidak mengoverride isEnabled dari state spesifik di atas
        val canClearNormally = state == TaggingState.IDLE ||
                state == TaggingState.PROCESS_SUCCESS ||
                state == TaggingState.ERROR_BOOK_NOT_FOUND ||
                (state in listOf(TaggingState.PROCESS_FAILED, TaggingState.ERROR_SDK, TaggingState.ERROR_CONVERSION) && viewModel.currentBook.value == null)

        if (canClearNormally) {
            binding.buttonClear.isEnabled = true
        } else if (state !in listOf(
                TaggingState.BOOK_SEARCHING,
                TaggingState.READING_INITIAL_EPC,
                TaggingState.READING_TID_WITH_FILTER,
                TaggingState.WRITING_EPC,
                TaggingState.SAVING_TO_DB
            ) && viewModel.currentBook.value != null // Bisa clear dengan konfirmasi jika ada buku dan tidak dalam proses krusial
        ) {
            binding.buttonClear.isEnabled = true
        } else if (state in listOf( // Eksplisit nonaktifkan jika sedang proses krusial
                TaggingState.BOOK_SEARCHING,
                TaggingState.READING_INITIAL_EPC,
                TaggingState.READING_TID_WITH_FILTER,
                TaggingState.WRITING_EPC,
                TaggingState.SAVING_TO_DB
            )) {
            binding.buttonClear.isEnabled = false
        }
    }


    private fun displayBookDetails(book: BookMaster?) {
        if (book != null) {
            binding.layoutBookDetails.visibility = View.VISIBLE
            binding.textViewBookTitle.text = book.title
            binding.textViewItemCodeValue.text = book.itemCode

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
            binding.textViewItemCodeValue.text = ""
            binding.textViewCurrentRfidStatus.text = ""
            binding.textViewCurrentRfidTag.text = ""
            binding.textViewCurrentTid.text = ""
        }
        // Bersihkan nilai TID dan EPC yang discan setiap kali detail buku berubah
        // karena ini adalah data sementara dari proses scan tag.
        if (viewModel.taggingState.value !in listOf(
                TaggingState.READING_TID_WITH_FILTER, // Jangan bersihkan jika sedang di state ini dan TID baru masuk
                TaggingState.EPC_CONFIRMED_AWAITING_WRITE,
                TaggingState.WRITING_EPC,
                TaggingState.SAVING_TO_DB,
                TaggingState.PROCESS_SUCCESS
            )
        ) {
            binding.textViewScannedTidValue.text = ""
            binding.textViewScannedEpcValue.text = ""
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    @Suppress("UNCHECKED_CAST")
    class BookTaggingViewModelFactory(
        private val application: Application,
        private val bookRepository: BookRepository, // Pastikan ini adalah BookRepository yang sebenarnya
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
        val currentTaggingState = viewModel.taggingState.value
        // Sesuaikan dengan state baru yang relevan untuk pembatalan
        if (viewModel.isLoading.value == true || currentTaggingState in listOf(
                TaggingState.BOOK_SEARCHING,
                TaggingState.READING_INITIAL_EPC,
                TaggingState.READING_TID_WITH_FILTER,
                // TaggingState.READING_EPC_FOR_CONFIRMATION,
                TaggingState.WRITING_EPC
            )) {
            Log.i(TAG, "onPause: Cancelling current SDK operation due to state $currentTaggingState or isLoading.")
            viewModel.cancelCurrentOperation()
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

