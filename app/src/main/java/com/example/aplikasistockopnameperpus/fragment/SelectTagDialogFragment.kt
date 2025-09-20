package com.example.aplikasistockopnameperpus.fragment // atau package yang sesuai

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// Hapus impor yang tidak digunakan jika ada, contoh:
// import androidx.compose.foundation.layout.size
// import androidx.compose.ui.semantics.text
// import androidx.glance.visibility
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.R // Pastikan R diimpor jika menggunakan resource string
import com.example.aplikasistockopnameperpus.adapter.SelectableBookAdapter
// import com.example.aplikasistockopnameperpus.data.database.BookMaster // Tidak digunakan secara langsung di sini
import com.example.aplikasistockopnameperpus.databinding.DialogSelectRfidTagBinding
import com.example.aplikasistockopnameperpus.viewmodel.SelectTagUiState
import com.example.aplikasistockopnameperpus.viewmodel.SelectTagViewModel
import com.example.aplikasistockopnameperpus.viewmodel.SelectTagViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SelectTagDialogFragment : DialogFragment() {

    private var _binding: DialogSelectRfidTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SelectTagViewModel by viewModels {
        SelectTagViewModelFactory(
            (requireActivity().application as MyApplication).bookRepository
        )
    }
    private lateinit var selectableBookAdapter: SelectableBookAdapter

    interface OnTagSelectedListener {
        fun onTagSelected(selectedEpc: String)
    }

    var listener: OnTagSelectedListener? = null

    companion object {
        const val TAG = "SelectTagDialogFragment"
        fun newInstance(): SelectTagDialogFragment {
            return SelectTagDialogFragment()
        }
        private val LOG_TAG_LIFECYCLE = "${TAG}_Lifecycle"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(LOG_TAG_LIFECYCLE, "onAttach called")
        if (context is OnTagSelectedListener) {
            listener = context
        } else if (parentFragment is OnTagSelectedListener) {
            listener = parentFragment as OnTagSelectedListener
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(LOG_TAG_LIFECYCLE, "onCreateDialog called")
        // Inflasi binding tetap di sini karena dibutuhkan oleh setView()
        _binding = DialogSelectRfidTagBinding.inflate(LayoutInflater.from(requireContext()))

        // !!! HAPUS PEMANGGILAN INI DARI SINI !!!
        // setupRecyclerView()
        // setupSearch()
        // observeViewModelState()

        // Panggil initialLoad di sini atau di onResume/onStart jika perlu refresh setiap kali dialog muncul
        if (savedInstanceState == null) {
            viewModel.initialLoad()
            Log.d(TAG, "Initial data load requested.")
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.title_select_rfid_tag_dialog))
            .setView(binding.root) // binding.root sudah tersedia dari _binding
            .setNegativeButton(getString(R.string.action_cancel)) { dialog, _ ->
                Log.d(TAG, "Cancel button clicked.")
                this.dismiss()
            }
            .create()
    }

    // onCreateView tetap diperlukan jika Anda ingin memanipulasi view lebih lanjut,
    // atau sebagai tempat standar untuk inflasi jika tidak menggunakan setView di onCreateDialog.
    // Untuk DialogFragment, jika Anda sudah setView di onCreateDialog, onCreateView mungkin tidak banyak
    // melakukan apa-apa selain mengembalikan view yang sudah ada atau di-inflate di sana.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(LOG_TAG_LIFECYCLE, "onCreateView called")
        // Jika _binding belum di-inflate di onCreateDialog (misalnya jika Anda tidak meng-override onCreateDialog
        // dan hanya menggunakan onCreateView untuk dialog kustom biasa), Anda akan meng-inflate di sini.
        // Namun, karena Anda meng-inflate di onCreateDialog dan menggunakan setView(),
        // _binding seharusnya sudah ada. Kita bisa jaga-jaga.
        if (_binding == null) {
            Log.d(TAG, "Inflating binding in onCreateView as it was null (should have been inflated in onCreateDialog).")
            _binding = DialogSelectRfidTagBinding.inflate(inflater, container, false)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(LOG_TAG_LIFECYCLE, "onViewCreated called")

        // --- PINDAHKAN PEMANGGILAN KE SINI ---
        // Pastikan _binding tidak null di sini. Karena view sudah dibuat, _binding seharusnya sudah ada.
        setupRecyclerView()
        setupSearch()
        observeViewModelState() // Sekarang aman untuk memanggil ini
        // --- AKHIR PEMINDAHAN ---
    }

    private fun setupRecyclerView() {
        // Pastikan _binding tidak null. Ini seharusnya aman karena dipanggil dari onViewCreated.
        if (_binding == null) {
            Log.e(TAG, "setupRecyclerView called when _binding is null. This should not happen if called from onViewCreated.")
            return
        }
        Log.d(TAG, "setupRecyclerView called")
        selectableBookAdapter = SelectableBookAdapter { selectedBook ->
            selectedBook.rfidTagHex?.let { epc ->
                if (epc.isNotBlank()) {
                    Log.i(TAG, "Book selected: ${selectedBook.title}, EPC: $epc")
                    listener?.onTagSelected(epc)
                    dismiss()
                } else {
                    Log.w(TAG, "Selected book '${selectedBook.title}' has blank EPC, selection ignored.")
                }
            } ?: run {
                Log.w(TAG, "Selected book '${selectedBook.title}' has null EPC, selection ignored.")
            }
        }
        binding.recyclerViewSelectableBooks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = selectableBookAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        if (_binding == null) {
            Log.e(TAG, "setupSearch called when _binding is null.")
            return
        }
        Log.d(TAG, "setupSearch called")
        binding.editTextSearchBookQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                Log.d(TAG, "Search query changed: $query")
                viewModel.setSearchQuery(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeViewModelState() {
        // Tidak perlu cek _binding di sini karena viewLifecycleOwner adalah fokusnya
        Log.d(TAG, "observeViewModelState called")
        // viewLifecycleOwner aman digunakan di sini karena kita berada setelah onViewCreated
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d(TAG, "New UI State received: ${state::class.java.simpleName}")
                    // Pastikan _binding tidak null sebelum mengakses propertinya
                    _binding?.let { currentBinding ->
                        when (state) {
                            is SelectTagUiState.Loading -> {
                                currentBinding.progressBarLoadingBooks.visibility = View.VISIBLE
                                currentBinding.recyclerViewSelectableBooks.visibility = View.GONE
                                currentBinding.textViewNoBooksFound.visibility = View.GONE
                            }
                            is SelectTagUiState.Success -> {
                                currentBinding.progressBarLoadingBooks.visibility = View.GONE
                                currentBinding.recyclerViewSelectableBooks.visibility = View.VISIBLE
                                currentBinding.textViewNoBooksFound.visibility = View.GONE
                                selectableBookAdapter.submitList(state.books)
                                Log.d(TAG, "Success state: Displaying ${state.books.size} books.")
                            }
                            is SelectTagUiState.Error -> {
                                currentBinding.progressBarLoadingBooks.visibility = View.GONE
                                currentBinding.recyclerViewSelectableBooks.visibility = View.GONE
                                currentBinding.textViewNoBooksFound.visibility = View.VISIBLE
                                currentBinding.textViewNoBooksFound.text = getString(R.string.error_loading_books_message, state.message)
                                Log.e(TAG, "Error state: ${state.message}")
                            }
                            is SelectTagUiState.Empty -> {
                                currentBinding.progressBarLoadingBooks.visibility = View.GONE
                                currentBinding.recyclerViewSelectableBooks.visibility = View.GONE
                                currentBinding.textViewNoBooksFound.visibility = View.VISIBLE
                                currentBinding.textViewNoBooksFound.text = getString(R.string.message_no_books_found_or_tagged)
                                selectableBookAdapter.submitList(emptyList())
                                Log.d(TAG, "Empty state: No books to display.")
                            }
                        }
                    } ?: run {
                        Log.e(TAG, "observeViewModelState collected new state but _binding was null. UI will not update.")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(LOG_TAG_LIFECYCLE, "onDestroyView called, _binding is nulled.")
        // Penting untuk membersihkan binding untuk mencegah memory leak, terutama dengan ViewBinding di Fragment.
        // Cek binding sebelum mengakses propertinya untuk menghindari NullPointerException jika onDestroyView dipanggil lebih dari sekali
        _binding?.recyclerViewSelectableBooks?.adapter = null // Lepaskan adapter
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(LOG_TAG_LIFECYCLE, "onDetach called, listener is nulled.")
        listener = null
    }
}
