package com.example.aplikasistockopnameperpus // Sesuaikan dengan package Anda

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.data.database.OpnameStatus
import com.example.aplikasistockopnameperpus.databinding.ActivityFilterStockOpnameBinding
import com.example.aplikasistockopnameperpus.model.FilterCriteria

class FilterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilterStockOpnameBinding
    private var currentFilterCriteria: FilterCriteria? = null

    companion object {
        const val REQUEST_CODE_FILTER = 101
        const val EXTRA_CURRENT_FILTER_CRITERIA = "current_filter_criteria"
        const val EXTRA_RESULT_FILTER_CRITERIA = "result_filter_criteria"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilterStockOpnameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarFilter)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Ambil kriteria filter yang ada jika dikirim dari StockOpnameActivity
        if (intent.hasExtra(EXTRA_CURRENT_FILTER_CRITERIA)) {
            currentFilterCriteria = intent.getParcelableExtra(EXTRA_CURRENT_FILTER_CRITERIA)
        }
        populateUiWithCriteria(currentFilterCriteria ?: FilterCriteria()) // Gunakan default jika null

        binding.buttonApplyFilter.setOnClickListener {
            applyAndReturnFilters()
        }

        binding.buttonResetFilter.setOnClickListener {
            resetFilters()
            // Opsional: langsung terapkan filter kosong setelah reset
            // applyAndReturnFilters()
        }
    }

    private fun populateUiWithCriteria(criteria: FilterCriteria) {
        // Status Opname
        when (criteria.opnameStatus) {
            OpnameStatus.FOUND -> binding.radioGroupOpnameStatus.check(R.id.radioButtonStatusFound)
            OpnameStatus.NOT_SCANNED, OpnameStatus.MISSING -> binding.radioGroupOpnameStatus.check(R.id.radioButtonStatusNotFound) // Gabungkan
            OpnameStatus.NEW_ITEM -> binding.radioGroupOpnameStatus.check(R.id.radioButtonStatusNew)
            null -> binding.radioGroupOpnameStatus.check(R.id.radioButtonStatusAll) // Default atau semua
        }

        binding.editTextFilterTitle.setText(criteria.titleQuery)
        binding.editTextFilterItemCode.setText(criteria.itemCodeQuery)
        binding.editTextFilterLocation.setText(criteria.locationQuery)
        binding.editTextFilterEpc.setText(criteria.epcQuery)

        // Untuk boolean isNewOrUnexpected
        binding.checkboxFilterIsNewOrUnexpected.isChecked = criteria.isNewOrUnexpected ?: false
        // Jika Anda ingin checkbox memiliki 3 state (true, false, null/indeterminate),
        // Anda perlu logika UI yang berbeda, atau cukup true/false.
        // Untuk saat ini, kita anggap null berarti false (tidak dicentang).
    }

    private fun resetFilters() {
        populateUiWithCriteria(FilterCriteria()) // Isi UI dengan filter default/kosong
    }

    private fun applyAndReturnFilters() {
        val selectedStatusId = binding.radioGroupOpnameStatus.checkedRadioButtonId
        val selectedRadioButton = findViewById<RadioButton>(selectedStatusId)

        val opnameStatusFilter: OpnameStatus? = when (selectedRadioButton.id) {
            R.id.radioButtonStatusFound -> OpnameStatus.FOUND
            R.id.radioButtonStatusNotFound -> OpnameStatus.NOT_SCANNED // Atau MISSING, tergantung bagaimana Anda ingin memfilternya di DAO
            R.id.radioButtonStatusNew -> OpnameStatus.NEW_ITEM
            else -> null // radioButtonStatusAll atau default
        }

        val titleQuery = binding.editTextFilterTitle.text.toString().trim().ifEmpty { null }
        val itemCodeQuery = binding.editTextFilterItemCode.text.toString().trim().ifEmpty { null }
        val locationQuery = binding.editTextFilterLocation.text.toString().trim().ifEmpty { null }
        val epcQuery = binding.editTextFilterEpc.text.toString().trim().ifEmpty { null }
        val isNewOrUnexpectedFilter = if (binding.checkboxFilterIsNewOrUnexpected.isChecked) true else null
        // Atau jika checkboxFilterIsNewOrUnexpected hanya memfilter 'true' dan jika tidak dicentang tidak memfilter:
        // val isNewOrUnexpectedFilter = if (binding.checkboxFilterIsNewOrUnexpected.isChecked) true else null
        // Atau jika Anda ingin null berarti "tidak peduli", true berarti "hanya yang baru", false berarti "hanya yang tidak baru":
        // Ini perlu disesuaikan dengan bagaimana FilterCriteria.isNewOrUnexpected Anda akan digunakan di query DAO.
        // Untuk sederhana: jika dicentang, isNewOrUnexpected = true. Jika tidak, isNewOrUnexpected = null (tidak memfilter berdasarkan ini).
        // Mari kita asumsikan jika dicentang, filter untuk true. Jika tidak, tidak ada filter untuk field ini (null).
        val finalIsNewOrUnexpectedCriteria = if (binding.checkboxFilterIsNewOrUnexpected.isChecked) true else null


        val newFilterCriteria = FilterCriteria(
            opnameStatus = opnameStatusFilter,
            titleQuery = titleQuery,
            itemCodeQuery = itemCodeQuery,
            locationQuery = locationQuery,
            epcQuery = epcQuery,
            isNewOrUnexpected = finalIsNewOrUnexpectedCriteria
        )

        val resultIntent = Intent()
        resultIntent.putExtra(EXTRA_RESULT_FILTER_CRITERIA, newFilterCriteria)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            android.R.id.home -> {
                // setResult(Activity.RESULT_CANCELED) // Opsional jika pengguna menekan back
                finish() // Kembali tanpa menerapkan filter
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
