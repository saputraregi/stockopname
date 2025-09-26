package com.example.aplikasistockopnameperpus // Sesuaikan package Anda

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.geometry.isEmpty
import androidx.glance.visibility
import androidx.lifecycle.Observer
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.adapter.BookListAdapter // Gunakan adapter yang sudah ada jika cocok
import com.example.aplikasistockopnameperpus.databinding.ActivityBookListDetailBinding
import com.example.aplikasistockopnameperpus.model.FilterCriteria // Pastikan ini parcelable
import com.example.aplikasistockopnameperpus.viewmodel.BookListDetailViewModel
import com.example.aplikasistockopnameperpus.viewmodel.BookListDetailViewModelFactory
import com.example.aplikasistockopnameperpus.viewmodel.BookMasterDisplayWrapper // atau tipe data yang sesuai
import com.example.aplikasistockopnameperpus.viewmodel.StockOpnameUiState // Jika ingin reuse UI state untuk loading/error

class BookListDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookListDetailBinding
    private val viewModel: BookListDetailViewModel by viewModels {
        BookListDetailViewModelFactory(
            (application as MyApplication).bookRepository,
            application
        )
    }
    private lateinit var bookListAdapter: BookListAdapter // Atau adapter spesifik jika perlu

    companion object {
        const val EXTRA_FILTER_CRITERIA = "extra_filter_criteria"
        const val EXTRA_TITLE = "extra_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookListDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filterCriteria = intent.getParcelableExtra<FilterCriteria>(EXTRA_FILTER_CRITERIA)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.title_activity_book_list_detail_default)

        setupToolbar(title)
        setupRecyclerView()
        observeViewModel()

        if (filterCriteria != null) {
            viewModel.loadBooks(filterCriteria)
        } else {
            // Handle kasus jika filter criteria tidak ada (seharusnya tidak terjadi jika navigasi benar)
            binding.textViewNoBooksDetail.text = getString(R.string.error_filter_not_provided)
            binding.textViewNoBooksDetail.visibility = View.VISIBLE
        }
    }

    private fun setupToolbar(title: String) {
        setSupportActionBar(binding.toolbarBookListDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = title
    }

    private fun setupRecyclerView() {
        bookListAdapter = BookListAdapter(
            onItemClick = { bookWrapper ->
                // Aksi saat item buku diklik di activity detail (jika perlu)
                // Toast.makeText(this, "Buku: ${bookWrapper.bookMaster.title}", Toast.LENGTH_SHORT).show()
            },
            onItemLongClick = { /* Aksi long click jika perlu */ true }
        )
        binding.recyclerViewBookDetailList.apply {
            adapter = bookListAdapter
            layoutManager = LinearLayoutManager(this@BookListDetailActivity)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this, Observer { state ->
            when (state) {
                is BookListDetailViewModel.UiState.Loading -> {
                    binding.progressBarBookListDetail.visibility = View.VISIBLE
                    binding.recyclerViewBookDetailList.visibility = View.GONE
                    binding.textViewNoBooksDetail.visibility = View.GONE
                }
                is BookListDetailViewModel.UiState.Success -> {
                    binding.progressBarBookListDetail.visibility = View.GONE
                    if (state.books.isEmpty()) {
                        binding.textViewNoBooksDetail.text = getString(R.string.message_no_books_match_filter)
                        binding.textViewNoBooksDetail.visibility = View.VISIBLE
                        binding.recyclerViewBookDetailList.visibility = View.GONE
                    } else {
                        binding.textViewNoBooksDetail.visibility = View.GONE
                        binding.recyclerViewBookDetailList.visibility = View.VISIBLE
                        bookListAdapter.submitList(state.books)
                    }
                }
                is BookListDetailViewModel.UiState.Error -> {
                    binding.progressBarBookListDetail.visibility = View.GONE
                    binding.recyclerViewBookDetailList.visibility = View.GONE
                    binding.textViewNoBooksDetail.text = state.message
                    binding.textViewNoBooksDetail.visibility = View.VISIBLE
                }
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
