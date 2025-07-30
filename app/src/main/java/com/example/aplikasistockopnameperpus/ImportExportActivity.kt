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
import androidx.compose.ui.semantics.text
import androidx.glance.visibility
import com.example.aplikasistockopnameperpus.databinding.ActivityImportExportBinding // Gunakan ViewBinding
import com.example.aplikasistockopnameperpus.util.FileType // Anda perlu membuat enum FileType
import com.example.aplikasistockopnameperpus.viewmodel.ImportExportStatus
import com.example.aplikasistockopnameperpus.viewmodel.ImportExportViewModel
import kotlin.collections.toTypedArray

class ImportExportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportExportBinding
    private val viewModel: ImportExportViewModel by viewModels() // Delegasi untuk ViewModel

    // Launcher untuk memilih file impor
    private lateinit var importFileLauncher: ActivityResultLauncher<Array<String>>

    // Launcher untuk membuat file ekspor
    private lateinit var exportFileLauncher: ActivityResultLauncher<String>
    private var currentExportType: FileType? = null // Untuk melacak tipe file yang akan diexport
    private var currentExportAction: ExportAction? = null // Untuk melacak aksi export (Master/Opname)


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
            uri?.let {
                binding.textViewSelectedFileImportMaster.text = "File terpilih: ${it.lastPathSegment ?: "Tidak diketahui"}"
                binding.buttonStartImportMaster.isEnabled = true
                // Simpan URI untuk digunakan ViewModel, atau langsung kirim ke ViewModel
                viewModel.setSelectedFileForImport(it)
            } ?: run {
                binding.textViewSelectedFileImportMaster.text = "Tidak ada file dipilih"
                binding.buttonStartImportMaster.isEnabled = false
                viewModel.clearSelectedFileForImport()
            }
        }

        exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            uri?.let {
                currentExportType?.let { fileType ->
                    when(currentExportAction) {
                        ExportAction.MASTER_DATA -> viewModel.startExportMasterData(it, fileType)
                        ExportAction.OPNAME_RESULT -> viewModel.startExportOpnameResult(it, fileType)
                        null -> Toast.makeText(this, "Aksi export tidak diketahui", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonSelectFileImportMaster.setOnClickListener {
            // Tentukan tipe MIME berdasarkan file yang ingin diimpor (CSV, XLS, TXT)
            // Anda bisa menampilkan dialog untuk memilih tipe file jika perlu
            importFileLauncher.launch(arrayOf("text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain"))
        }

        binding.buttonStartImportMaster.setOnClickListener {
            // Tampilkan dialog konfirmasi sebelum memulai import
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
        val formats = FileType.values().map { it.displayName }.toTypedArray() // Misal: "CSV", "Excel (XLSX)", "TXT"
        AlertDialog.Builder(this)
            .setTitle("Pilih Format Export")
            .setItems(formats) { _, which ->
                currentExportType = FileType.values()[which]
                val suggestedName = "$defaultFileNamePrefix.${currentExportType?.extension}"
                exportFileLauncher.launch(suggestedName)
            }
            .show()
    }


    private fun observeViewModel() {
        viewModel.importStatus.observe(this) { status ->
            when (status) {
                is ImportExportStatus.Loading -> {
                    binding.progressBarImportMaster.visibility = View.VISIBLE
                    binding.progressBarImportMaster.isIndeterminate = false // Atur jika ada progress spesifik
                    binding.buttonStartImportMaster.isEnabled = false
                    binding.textViewImportExportStatus.text = "Status: Sedang memproses import..."
                }
                is ImportExportStatus.Success -> {
                    binding.progressBarImportMaster.visibility = View.GONE
                    binding.buttonStartImportMaster.isEnabled = true // Atau false jika file sudah diproses
                    binding.textViewImportExportStatus.text = "Status: ${status.message}"
                    Toast.makeText(this, status.message, Toast.LENGTH_LONG).show()
                    // Reset tampilan file terpilih jika perlu
                    binding.textViewSelectedFileImportMaster.text = "Pilih file untuk import baru"
                    binding.buttonStartImportMaster.isEnabled = false
                    viewModel.clearSelectedFileForImport()
                }
                is ImportExportStatus.Error -> {
                    binding.progressBarImportMaster.visibility = View.GONE
                    binding.buttonStartImportMaster.isEnabled = true
                    binding.textViewImportExportStatus.text = "Status: Error - ${status.errorMessage}"
                    Toast.makeText(this, "Error: ${status.errorMessage}", Toast.LENGTH_LONG).show()
                }
                is ImportExportStatus.Progress -> {
                    binding.progressBarImportMaster.visibility = View.VISIBLE
                    binding.progressBarImportMaster.isIndeterminate = false
                    binding.progressBarImportMaster.progress = status.percentage
                    binding.textViewImportExportStatus.text = "Status: Import ${status.percentage}% (${status.processedItems}/${status.totalItems})"
                }
                null -> { // Idle state
                    binding.progressBarImportMaster.visibility = View.GONE
                    binding.textViewImportExportStatus.text = "Status: Siap"
                }
            }
        }

        viewModel.exportStatus.observe(this) { status ->
            when (status) {
                is ImportExportStatus.Loading -> {
                    // Tampilkan progress bar atau loading indicator untuk export jika perlu
                    binding.textViewImportExportStatus.text = "Status: Sedang mengekspor data..."
                    // Disable tombol export selama proses
                }
                is ImportExportStatus.Success -> {
                    binding.textViewImportExportStatus.text = "Status: ${status.message}"
                    Toast.makeText(this, status.message, Toast.LENGTH_LONG).show()
                }
                is ImportExportStatus.Error -> {
                    binding.textViewImportExportStatus.text = "Status: Error Export - ${status.errorMessage}"
                    Toast.makeText(this, "Error Export: ${status.errorMessage}", Toast.LENGTH_LONG).show()
                }
                null -> { // Idle state
                    binding.textViewImportExportStatus.text = "Status: Siap"
                }
                else -> { /* No-op atau handle progress jika ada */ }
            }
        }
    }

    // Enum untuk tipe file (buat di file terpisah atau di dalam Activity jika sederhana)
    // Misalnya di utils/FileType.kt
    // enum class FileType(val mimeType: String, val extension: String, val displayName: String) {
    //    CSV("text/csv", "csv", "CSV"),
    //    EXCEL_XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx", "Excel (XLSX)"),
    //    // EXCEL_XLS("application/vnd.ms-excel", "xls", "Excel (XLS)"), // Jika mendukung XLS
    //    TXT("text/plain", "txt", "TXT")
    // }

    // Enum untuk aksi export
    private enum class ExportAction {
        MASTER_DATA, OPNAME_RESULT
    }
}

