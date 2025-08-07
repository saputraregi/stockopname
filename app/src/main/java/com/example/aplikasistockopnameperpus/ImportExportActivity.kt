package com.example.aplikasistockopnameperpus

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope // <-- Tambahkan import ini
import com.example.aplikasistockopnameperpus.databinding.ActivityImportExportBinding
import com.example.aplikasistockopnameperpus.util.FileType
import com.example.aplikasistockopnameperpus.util.StorageHelper // Pastikan import ini benar
import com.example.aplikasistockopnameperpus.viewmodel.ImportExportStatus
import com.example.aplikasistockopnameperpus.viewmodel.ImportExportViewModel
import kotlinx.coroutines.launch // <-- Tambahkan import ini

class ImportExportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportExportBinding
    private val viewModel: ImportExportViewModel by viewModels()

    private lateinit var importFileLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var exportFileLauncher: ActivityResultLauncher<String>
    private var currentExportType: FileType? = null
    private var currentExportAction: ExportAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportExportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLaunchers()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupLaunchers() {
        importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { selectedUri -> // Ubah nama variabel agar tidak bentrok dengan 'it' di dalam launch
                // Jalankan dalam coroutine yang terikat dengan lifecycle Activity
                lifecycleScope.launch {
                    val fileName = StorageHelper.getFileName(this@ImportExportActivity, selectedUri)
                    binding.textViewSelectedFileImportMaster.text = "File terpilih: ${fileName ?: selectedUri.lastPathSegment ?: "Tidak diketahui"}"
                    binding.buttonStartImportMaster.isEnabled = true
                    viewModel.setSelectedFileForImport(selectedUri)
                    binding.textViewImportExportStatus.text = "Status: File dipilih, siap untuk import."
                }
            } ?: run {
                binding.textViewSelectedFileImportMaster.text = "Tidak ada file dipilih"
                binding.buttonStartImportMaster.isEnabled = false
                viewModel.clearSelectedFileForImport()
                binding.textViewImportExportStatus.text = "Status: Siap"
            }
        }

        exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            uri?.let { outputUri ->
                currentExportType?.let { fileType ->
                    when (currentExportAction) {
                        ExportAction.MASTER_DATA -> viewModel.startExportMasterData(outputUri, fileType)
                        ExportAction.OPNAME_RESULT -> viewModel.startExportOpnameResult(outputUri, fileType)
                        null -> Toast.makeText(this, "Aksi export tidak diketahui", Toast.LENGTH_SHORT).show()
                    }
                } ?: Toast.makeText(this, "Tipe file export tidak dipilih", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "Pembuatan file export dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        binding.buttonSelectFileImportMaster.setOnClickListener {
            viewModel.clearSelectedFileForImport()
            binding.textViewImportExportStatus.text = "Status: Memilih file..."
            importFileLauncher.launch(arrayOf("text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain", "*/*"))
        }

        binding.buttonStartImportMaster.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Konfirmasi Import")
                .setMessage("Apakah Anda yakin ingin mengimpor data dari file ini? Data master yang ada mungkin akan terganti atau diperbarui.")
                .setPositiveButton("Import") { _, _ ->
                    viewModel.startImportMasterData()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        binding.buttonExportMasterData.setOnClickListener {
            currentExportAction = ExportAction.MASTER_DATA
            showExportFormatDialog("DataMasterBuku")
        }

        binding.buttonExportStockOpnameResult.setOnClickListener {
            currentExportAction = ExportAction.OPNAME_RESULT
            showExportFormatDialog("HasilStockOpname")
        }
    }

    private fun showExportFormatDialog(defaultFileNamePrefix: String) {
        val availableFileTypes = FileType.values().filter { it.canExport } // Menggunakan properti canExport
        if (availableFileTypes.isEmpty()) {
            Toast.makeText(this, "Tidak ada format export yang tersedia.", Toast.LENGTH_SHORT).show()
            return
        }
        val formats = availableFileTypes.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih Format Export")
            .setItems(formats) { _, which ->
                currentExportType = availableFileTypes[which]
                // Jalankan dalam coroutine untuk mendapatkan nama file jika createPublicExportFile juga suspend
                // Namun, untuk SAF, nama file sudah disarankan oleh sistem atau dipilih pengguna.
                // Jika Anda ingin nama default yang lebih dinamis dari StorageHelper (yang suspend),
                // maka perlu coroutine juga di sini.
                // Untuk sekarang, kita asumsikan currentExportType.extension tidak suspend.
                val suggestedName = "$defaultFileNamePrefix.${currentExportType?.extension}"
                exportFileLauncher.launch(suggestedName)
            }
            .show()
    }

    private fun observeViewModel() {
        viewModel.selectedImportFileUri.observe(this) { uri ->
            if (uri == null) {
                binding.textViewSelectedFileImportMaster.text = "Tidak ada file dipilih"
                binding.buttonStartImportMaster.isEnabled = false
            } else {
                // Jika URI di-set dari luar launcher (misalnya dari ViewModel setelah validasi),
                // kita mungkin perlu memperbarui nama file lagi di sini juga menggunakan coroutine.
                lifecycleScope.launch {
                    val fileName = StorageHelper.getFileName(this@ImportExportActivity, uri)
                    binding.textViewSelectedFileImportMaster.text = "File terpilih: ${fileName ?: uri.lastPathSegment ?: "Tidak diketahui"}"
                    binding.buttonStartImportMaster.isEnabled = true
                }
            }
        }


        viewModel.importStatus.observe(this) { status ->
            // ... (kode observe importStatus tetap sama) ...
            when (status) {
                is ImportExportStatus.Loading -> {
                    binding.progressBarImportMaster.visibility = View.VISIBLE
                    binding.progressBarImportMaster.isIndeterminate = true
                    binding.buttonStartImportMaster.isEnabled = false
                    binding.buttonSelectFileImportMaster.isEnabled = false
                    binding.textViewImportExportStatus.text = "Status: Sedang memproses import..."
                }
                is ImportExportStatus.Success -> {
                    binding.progressBarImportMaster.visibility = View.GONE
                    binding.buttonStartImportMaster.isEnabled = false
                    binding.buttonSelectFileImportMaster.isEnabled = true

                    val baseMessage = status.message
                    if (status.warnings.isNullOrEmpty()) {
                        binding.textViewImportExportStatus.text = "Status: $baseMessage"
                        Toast.makeText(this, baseMessage, Toast.LENGTH_LONG).show()
                    } else {
                        val warningsText = status.warnings.joinToString("\n- ")
                        val fullMessageToDisplay = "$baseMessage\n\nNamun terdapat beberapa catatan/peringatan:\n- $warningsText"
                        binding.textViewImportExportStatus.text = "Status: $baseMessage (dengan catatan)"
                        AlertDialog.Builder(this)
                            .setTitle("Import Selesai dengan Catatan")
                            .setMessage(fullMessageToDisplay)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    binding.textViewSelectedFileImportMaster.text = "Pilih file untuk import baru"
                    viewModel.clearSelectedFileForImport()
                }
                is ImportExportStatus.Error -> {
                    binding.progressBarImportMaster.visibility = View.GONE
                    binding.buttonStartImportMaster.isEnabled = true
                    binding.buttonSelectFileImportMaster.isEnabled = true
                    val errorMessage = status.errorMessage
                    binding.textViewImportExportStatus.text = "Status: Error Import"
                    AlertDialog.Builder(this)
                        .setTitle("Import Gagal")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show()
                }
                is ImportExportStatus.Progress -> {
                    binding.progressBarImportMaster.visibility = View.VISIBLE
                    binding.progressBarImportMaster.isIndeterminate = false
                    binding.progressBarImportMaster.progress = status.percentage
                    binding.textViewImportExportStatus.text = "Status: Import ${status.percentage}% (${status.processedItems}/${status.totalItems})"
                    binding.buttonStartImportMaster.isEnabled = false
                    binding.buttonSelectFileImportMaster.isEnabled = false
                }
                null -> {
                    binding.progressBarImportMaster.visibility = View.GONE
                    binding.buttonStartImportMaster.isEnabled = viewModel.selectedImportFileUri.value != null
                    binding.buttonSelectFileImportMaster.isEnabled = true
                    binding.textViewImportExportStatus.text = "Status: Siap"
                }
            }
        }

        viewModel.exportStatus.observe(this) { status ->
            // ... (kode observe exportStatus tetap sama, pastikan sudah exhaustive) ...
            val isExporting = status is ImportExportStatus.Loading || status is ImportExportStatus.Progress
            binding.buttonExportMasterData.isEnabled = !isExporting
            binding.buttonExportStockOpnameResult.isEnabled = !isExporting

            when (status) {
                is ImportExportStatus.Loading -> {
                    binding.textViewImportExportStatus.text = "Status: Sedang mengekspor data..."
                }
                is ImportExportStatus.Progress -> {
                    binding.textViewImportExportStatus.text = "Status: Ekspor ${status.percentage}% (${status.processedItems}/${status.totalItems})"
                }
                is ImportExportStatus.Success -> {
                    binding.textViewImportExportStatus.text = "Status: ${status.message}"
                    Toast.makeText(this, status.message, Toast.LENGTH_LONG).show()
                    if (!status.warnings.isNullOrEmpty()){
                        AlertDialog.Builder(this)
                            .setTitle("Export Selesai dengan Catatan")
                            .setMessage("Peringatan:\n- ${status.warnings.joinToString("\n- ")}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    viewModel.clearExportStatus()
                }
                is ImportExportStatus.Error -> {
                    val errorMessage = status.errorMessage
                    binding.textViewImportExportStatus.text = "Status: Error Export"
                    AlertDialog.Builder(this)
                        .setTitle("Export Gagal")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show()
                    viewModel.clearExportStatus()
                }
                null -> {
                    binding.textViewImportExportStatus.text = "Status: Siap"
                }
            }
        }
    }

    private enum class ExportAction {
        MASTER_DATA, OPNAME_RESULT
    }
}
