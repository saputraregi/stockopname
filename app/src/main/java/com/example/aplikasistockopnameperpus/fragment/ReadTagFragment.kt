package com.example.aplikasistockopnameperpus.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil // Import DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.FragmentReadTagBinding
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel
import java.util.Collections // Untuk Collections.min jika menggunakan minOf tidak tersedia (tapi seharusnya tersedia)
import kotlin.math.min // Alternatif untuk minOf

class ReadTagFragment : Fragment() {

    private var _binding: FragmentReadTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReadWriteTagViewModel by activityViewModels()
    private lateinit var epcListAdapter: MySimpleStringAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadTagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        epcListAdapter = MySimpleStringAdapter(mutableListOf())
        binding.recyclerViewReadEpcs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewReadEpcs.adapter = epcListAdapter

        binding.radioGroupReadMode.setOnCheckedChangeListener { _, checkedId ->
            viewModel.stopContinuousReading()
            binding.textViewLastEpcValue.text = ""
            epcListAdapter.clearItems()

            when (checkedId) {
                R.id.radioButtonSingleRead -> {
                    binding.textViewListOfEpcsLabel.visibility = View.GONE
                    binding.recyclerViewReadEpcs.visibility = View.GONE
                    binding.buttonStopRead.visibility = View.GONE
                    binding.buttonStartRead.text = getString(R.string.baca_tunggal)
                }
                R.id.radioButtonContinuousRead -> {
                    binding.textViewListOfEpcsLabel.visibility = View.VISIBLE
                    binding.recyclerViewReadEpcs.visibility = View.VISIBLE
                    binding.buttonStopRead.visibility = View.VISIBLE
                    binding.buttonStartRead.text = getString(R.string.mulai_baca_kontinu)
                }
            }
        }
        if (binding.radioGroupReadMode.checkedRadioButtonId == -1) {
            binding.radioButtonContinuousRead.isChecked = true
        }

        binding.buttonStartRead.setOnClickListener {
            val isContinuous = binding.radioButtonContinuousRead.isChecked
            viewModel.startReading(isContinuous)
        }

        binding.buttonStopRead.setOnClickListener {
            viewModel.stopContinuousReading()
        }

        binding.buttonClearList.setOnClickListener {
            epcListAdapter.clearItems()
            viewModel.clearContinuousListFromUI()
            binding.textViewLastEpcValue.text = ""
        }
    }

    private fun observeViewModel() {
        viewModel.lastReadEpc.observe(viewLifecycleOwner) { epc ->
            binding.textViewLastEpcValue.text = epc ?: ""
            if (!viewModel.isReadingContinuous.value!! && epc.isNullOrEmpty() && binding.textViewStatus.text.toString() == getString(R.string.status_idle)) {
                // binding.textViewLastEpcValue.text = getString(R.string.no_tag_found_placeholder)
            }
        }

        viewModel.continuousEpcList.observe(viewLifecycleOwner) { epcSet ->
            epcListAdapter.updateItems(epcSet?.toList()?.sortedDescending() ?: emptyList())
        }

        viewModel.isReadingContinuous.observe(viewLifecycleOwner) { isReading ->
            binding.buttonStartRead.isEnabled = !isReading
            if (binding.radioButtonContinuousRead.isChecked) {
                binding.buttonStopRead.isEnabled = isReading
            } else {
                binding.buttonStopRead.isEnabled = false
            }

            if (isReading) {
                binding.textViewStatus.text = getString(R.string.status_membaca)
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorScanning, requireActivity().theme))
            } else {
                binding.textViewStatus.text = getString(R.string.status_idle)
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorIdle, requireActivity().theme))
                if (binding.textViewLastEpcValue.text == getString(R.string.status_membaca) ||
                    (viewModel.lastReadEpc.value.isNullOrEmpty() && !binding.radioButtonContinuousRead.isChecked)) {
                    // binding.textViewLastEpcValue.text = ""
                }
            }
        }

        viewModel.readError.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                if (isAdded) {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                }
                binding.textViewStatus.text = getString(R.string.status_error)
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorError, requireActivity().theme))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopContinuousReading()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = ReadTagFragment()
    }

    class MySimpleStringAdapter(private var items: MutableList<String>) :
        RecyclerView.Adapter<MySimpleStringAdapter.ViewHolder>() {

        class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val textView = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = items[position]
            holder.textView.setPadding(16, 12, 16, 12)
            holder.textView.setTextIsSelectable(true)
        }

        override fun getItemCount() = items.size

        /**
         * Updates the items in the adapter.
         * For better performance and animations, consider using DiffUtil.
         */
        fun updateItems(newItems: List<String>) {
            val oldSize = this.items.size
            // Hitung commonSize di awal setelah oldSize dan newItems.size diketahui
            val commonSizeValue = min(oldSize, newItems.size) // Menggunakan min dari kotlin.math

            // Jika Anda ingin tetap menggunakan logika notifikasi manual (kurang direkomendasikan untuk kasus kompleks):
            this.items.clear()
            this.items.addAll(newItems)

            if (oldSize > newItems.size) {
                // Notifikasi untuk item yang dihapus
                notifyItemRangeRemoved(newItems.size, oldSize - newItems.size)
                // Notifikasi untuk item yang mungkin berubah kontennya (yang tersisa)
                if (commonSizeValue > 0) {
                    notifyItemRangeChanged(0, commonSizeValue)
                }
            } else if (newItems.size > oldSize) {
                // Notifikasi untuk item yang baru ditambahkan
                notifyItemRangeInserted(oldSize, newItems.size - oldSize)
                // Notifikasi untuk item yang mungkin berubah kontennya (yang lama)
                if (commonSizeValue > 0) {
                    notifyItemRangeChanged(0, commonSizeValue)
                }
            } else { // Ukuran sama (oldSize == newItems.size), mungkin konten berubah
                if (commonSizeValue > 0) {
                    notifyItemRangeChanged(0, commonSizeValue)
                }
            }

            // REKOMENDASI: Gunakan DiffUtil untuk pembaruan yang lebih efisien dan benar

            val diffCallback = MyStringDiffCallback(this.items, newItems)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            this.items.clear()
            this.items.addAll(newItems)
            diffResult.dispatchUpdatesTo(this)

        }

        fun clearItems() {
            val oldSize = items.size
            if (oldSize > 0) {
                items.clear()
                notifyItemRangeRemoved(0, oldSize)
            }
        }

        // Contoh implementasi DiffUtil.Callback (jika Anda ingin menggunakannya)
        private class MyStringDiffCallback(
            private val oldList: List<String>,
            private val newList: List<String>
        ) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                // Jika string Anda unik, ini cukup. Jika tidak, Anda mungkin perlu ID.
                return oldList[oldItemPosition] == newList[newItemPosition]
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                // Untuk string, jika itemnya sama, kontennya juga sama.
                return oldList[oldItemPosition] == newList[newItemPosition]
            }
        }
    }
}

