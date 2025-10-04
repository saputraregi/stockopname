package com.example.aplikasistockopnameperpus

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText // Pastikan EditText di-import dari android.widget
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.text
// HAPUS Impor Compose UI yang tidak perlu:
// import androidx.compose.ui.semantics.dismiss
// import androidx.compose.ui.semantics.setText
// import androidx.compose.ui.semantics.text
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.adapter.ReportAdapter
import com.example.aplikasistockopnameperpus.data.database.StockOpnameReport
import com.example.aplikasistockopnameperpus.databinding.ActivityReportBinding
import com.example.aplikasistockopnameperpus.viewmodel.ReportFilterCriteria
import com.example.aplikasistockopnameperpus.viewmodel.ReportSortOrder
import com.example.aplikasistockopnameperpus.viewmodel.ReportViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collect // Bisa digunakan jika Anda ingin lebih eksplisit atau untuk koleksi lain
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.util.Log

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private val reportViewModel: ReportViewModel by viewModels()
    private lateinit var reportAdapter: ReportAdapter

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        // setupOptionalLayoutFilters() // Dibiarkan jika Anda tidak punya filter langsung di layout utama
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarReport)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_report)
        binding.toolbarReport.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        reportAdapter = ReportAdapter { selectedReport ->
            Toast.makeText(
                this,
                getString(R.string.message_report_selected, selectedReport.reportName),
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(this, ReportDetailActivity::class.java).apply {
                putExtra(ReportDetailActivity.EXTRA_REPORT_ID, selectedReport.reportId)
                putExtra(ReportDetailActivity.EXTRA_TITLE, selectedReport.reportName)
            }
            startActivity(intent)
        }
        binding.recyclerViewReports.apply {
            adapter = reportAdapter
            layoutManager = LinearLayoutManager(this@ReportActivity)
            val dividerItemDecoration = DividerItemDecoration(context, (layoutManager as LinearLayoutManager).orientation)
            addItemDecoration(dividerItemDecoration)
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        reportViewModel.allStockOpnameReports.observe(this) { reports ->
            if (reports.isNullOrEmpty()) {
                binding.recyclerViewReports.visibility = View.GONE
                binding.textViewNoReports.visibility = View.VISIBLE
                val currentFilter = reportViewModel.currentFilterCriteria.value
                if (currentFilter != null && (currentFilter.startDate != null || currentFilter.endDate != null || !currentFilter.nameQuery.isNullOrBlank())) {
                    binding.textViewNoReports.text = getString(R.string.message_no_reports_match_filter)
                } else {
                    binding.textViewNoReports.text = getString(R.string.message_no_reports_available)
                }
            } else {
                binding.recyclerViewReports.visibility = View.VISIBLE
                binding.textViewNoReports.visibility = View.GONE
                reportAdapter.submitList(reports)
            }
            // Update indikator filter aktif setelah daftar diperbarui
            updateActiveFilterSortIndicator(
                reportViewModel.currentFilterCriteria.value,
                reportViewModel.currentSortOrder.value
            )
        }

        lifecycleScope.launch {
            reportViewModel.currentSortOrder.collect { sortOrder ->
                invalidateOptionsMenu()
                updateActiveFilterSortIndicator(reportViewModel.currentFilterCriteria.value, sortOrder)
            }
        }
        lifecycleScope.launch {
            reportViewModel.currentFilterCriteria.collect { filter ->
                invalidateOptionsMenu()
                updateActiveFilterSortIndicator(filter, reportViewModel.currentSortOrder.value)
            }
        }
    }

    // Fungsi untuk menampilkan indikator filter/sort aktif di UI
    private fun updateActiveFilterSortIndicator(filter: ReportFilterCriteria?, sortOrder: ReportSortOrder) {
        val filterParts = mutableListOf<String>()
        filter?.nameQuery?.takeIf { it.isNotBlank() }?.let {
            filterParts.add(getString(R.string.filter_indicator_name, it))
        }
        filter?.startDate?.let {
            val dateRangeStr = if (filter.endDate != null) {
                "${sdf.format(it)} - ${sdf.format(filter.endDate!!)}" // Pastikan endDate tidak null di sini jika dipakai
            } else {
                "Mulai ${sdf.format(it)}"
            }
            filterParts.add(getString(R.string.filter_indicator_date, dateRangeStr))
        }

        val sortText = when (sortOrder) {
            ReportSortOrder.DATE_DESC -> getString(R.string.sort_indicator_date_desc)
            ReportSortOrder.DATE_ASC -> getString(R.string.sort_indicator_date_asc)
            ReportSortOrder.NAME_ASC -> getString(R.string.sort_indicator_name_asc)
            ReportSortOrder.NAME_DESC -> getString(R.string.sort_indicator_name_desc)
        }
        val sortIndicator = getString(R.string.sort_indicator_prefix, sortText)

        val infoTextBuilder = StringBuilder()
        if (filterParts.isNotEmpty()) {
            infoTextBuilder.append(filterParts.joinToString(", "))
        }

        val isDefaultSort = sortOrder == ReportSortOrder.DATE_DESC // Asumsi ini adalah default
        if (filterParts.isNotEmpty() || !isDefaultSort) {
            if (infoTextBuilder.isNotEmpty()) {
                infoTextBuilder.append(" | ")
            }
            infoTextBuilder.append(sortIndicator)
        }

        if (infoTextBuilder.isNotEmpty()) {
            binding.textViewActiveFilterSortInfo?.text = infoTextBuilder.toString() // Gunakan safe call jika opsional
            binding.textViewActiveFilterSortInfo?.visibility = View.VISIBLE
        } else {
            binding.textViewActiveFilterSortInfo?.visibility = View.GONE
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_report, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val currentSort = reportViewModel.currentSortOrder.value
        // Gunakan safe call ?. untuk menghindari NullPointerException jika item tidak ditemukan
        menu?.findItem(R.id.action_sort_date_desc)?.isChecked = currentSort == ReportSortOrder.DATE_DESC
        menu?.findItem(R.id.action_sort_date_asc)?.isChecked = currentSort == ReportSortOrder.DATE_ASC
        menu?.findItem(R.id.action_sort_name_asc)?.isChecked = currentSort == ReportSortOrder.NAME_ASC
        menu?.findItem(R.id.action_sort_name_desc)?.isChecked = currentSort == ReportSortOrder.NAME_DESC
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Cek apakah item ada di dalam grup sebelum mencoba mengubah isChecked
        // Ini lebih aman jika ID group_sort_order tidak selalu ada atau jika item bukan bagian dari grup itu
        if (item.groupId == R.id.group_sort_order) { // Pastikan R.id.group_sort_order ada di XML menu Anda
            if (item.isCheckable) { // Langsung periksa item yang diklik
                // Perilaku untuk grup dengan checkableBehavior="single" (radio button):
                // Menu akan secara otomatis menangani unchecking item lain di grup yang sama.
                // Kita hanya perlu memastikan item yang diklik menjadi checked.
                // Namun, jika Anda ingin toggle manual (misalnya jika bukan single choice),
                // Anda bisa menggunakan item.isChecked = !item.isChecked
                // Untuk single choice, cukup set ke true, atau biarkan sistem menanganinya
                // setelah invalidateOptionsMenu() dipanggil oleh perubahan state ViewModel.
                // Pilihan yang lebih aman dan sering berhasil adalah membiarkan onPrepareOptionsMenu
                // menangani status checked berdasarkan state ViewModel.
                // Untuk saat ini, kita bisa set item.isChecked = true dan biarkan onPrepareOptionsMenu
                // mengkonfirmasi atau memperbaiki jika perlu saat menu digambar ulang.
                item.isChecked = true
            }
        }


        when (item.itemId) {
            R.id.action_filter_reports -> {
                showFilterDialog()
                return true // Kembalikan true jika event sudah dihandle
            }
            R.id.action_sort_date_desc -> {
                reportViewModel.applySortOrder(ReportSortOrder.DATE_DESC)
                return true
            }
            R.id.action_sort_date_asc -> {
                reportViewModel.applySortOrder(ReportSortOrder.DATE_ASC)
                return true
            }
            R.id.action_sort_name_asc -> {
                reportViewModel.applySortOrder(ReportSortOrder.NAME_ASC)
                return true
            }
            R.id.action_sort_name_desc -> {
                reportViewModel.applySortOrder(ReportSortOrder.NAME_DESC)
                return true
            }
            R.id.action_clear_filters_sort -> {
                reportViewModel.clearFiltersAndSort()
                Toast.makeText(this, getString(R.string.message_filters_cleared), Toast.LENGTH_SHORT).show()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun showFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_report_filter, null)
        val editTextStartDate = dialogView.findViewById<EditText>(R.id.editText_filter_start_date)
        val editTextEndDate = dialogView.findViewById<EditText>(R.id.editText_filter_end_date)
        val editTextNameQuery = dialogView.findViewById<EditText>(R.id.editText_filter_name_query)

        // Variabel untuk menyimpan Date objek yang sudah disesuaikan waktunya
        var selectedStartDateObj: Date? = null
        var selectedEndDateObj: Date? = null

        val currentFilter = reportViewModel.currentFilterCriteria.value
        currentFilter?.startDate?.let {
            editTextStartDate.setText(sdf.format(it))
            selectedStartDateObj = it // Inisialisasi dari filter yang sudah ada
        }
        currentFilter?.endDate?.let {
            editTextEndDate.setText(sdf.format(it))
            selectedEndDateObj = it // Inisialisasi dari filter yang sudah ada
        }
        currentFilter?.nameQuery?.let { editTextNameQuery.setText(it) }

        val setupDatePicker = { editText: EditText, isEndDate: Boolean ->
            editText.isFocusable = false
            editText.isClickable = true
            editText.setOnClickListener {
                val calendar = Calendar.getInstance()
                // Tentukan tanggal awal untuk picker
                val initialDateForPicker = if (isEndDate) selectedEndDateObj else selectedStartDateObj
                initialDateForPicker?.let { calendar.time = it }

                DatePickerDialog(
                    this,
                    { _, year, month, dayOfMonth ->
                        val selectedCalendar = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                        if (isEndDate) {
                            selectedCalendar.set(Calendar.HOUR_OF_DAY, 23)
                            selectedCalendar.set(Calendar.MINUTE, 59)
                            selectedCalendar.set(Calendar.SECOND, 59)
                            selectedCalendar.set(Calendar.MILLISECOND, 999) // Akhir hari
                            selectedEndDateObj = selectedCalendar.time // Simpan Date objek yang sudah disesuaikan
                        } else {
                            selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
                            selectedCalendar.set(Calendar.MINUTE, 0)
                            selectedCalendar.set(Calendar.SECOND, 0)
                            selectedCalendar.set(Calendar.MILLISECOND, 0) // Awal hari
                            selectedStartDateObj = selectedCalendar.time // Simpan Date objek yang sudah disesuaikan
                        }
                        editText.setText(sdf.format(selectedCalendar.time)) // Tetap update EditText untuk tampilan
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        setupDatePicker(editTextStartDate, false)
        setupDatePicker(editTextEndDate, true)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_title_filter_reports))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                // Gunakan selectedStartDateObj dan selectedEndDateObj yang sudah disimpan
                if (selectedStartDateObj != null && selectedEndDateObj != null && selectedStartDateObj!!.after(selectedEndDateObj!!)) {
                    Toast.makeText(this, getString(R.string.error_invalid_date_range), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val nameQuery = editTextNameQuery.text.toString().trim()
                val newFilter = ReportFilterCriteria(
                    startDate = selectedStartDateObj, // Gunakan objek Date yang sudah disesuaikan
                    endDate = selectedEndDateObj,     // Gunakan objek Date yang sudah disesuaikan
                    nameQuery = if (nameQuery.isEmpty()) null else nameQuery
                )
                Log.d("FilterDebug", "Activity: Applying criteria: Start=${selectedStartDateObj?.time}, End=${selectedEndDateObj?.time}, Name=$nameQuery")
                reportViewModel.applyFilterCriteria(newFilter)
                Toast.makeText(this, getString(R.string.message_filter_applied), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setNeutralButton(getString(R.string.clear_filter_short)) { _, _ ->
                // Kosongkan juga selectedDateObj saat clear filter
                selectedStartDateObj = null
                selectedEndDateObj = null
                editTextStartDate.text = null // Kosongkan tampilan EditText
                editTextEndDate.text = null   // Kosongkan tampilan EditText
                editTextNameQuery.text = null // Kosongkan nama
                reportViewModel.applyFilterCriteria(null) // Kirim null untuk menghapus semua filter di ViewModel
                Toast.makeText(this, getString(R.string.message_filters_cleared_from_dialog), Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
