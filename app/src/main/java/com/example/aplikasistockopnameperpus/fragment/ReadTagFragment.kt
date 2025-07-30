package com.example.aplikasistockopnameperpus.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.MyApplication
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.FragmentReadTagBinding
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel

class ReadTagFragment : Fragment() {

    private var _binding: FragmentReadTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReadWriteTagViewModel by activityViewModels()
    private lateinit var epcListAdapter: MySimpleStringAdapter // Adapter untuk RecyclerView

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

        val myApp = requireActivity().application as MyApplication
        if (!myApp.isReaderOpened()) {
            Toast.makeText(context, "Simulasi: Reader tidak aktif. Mode baca mungkin terbatas.", Toast.LENGTH_LONG).show()
            // binding.buttonStartRead.isEnabled = false // Bisa diatur berdasarkan status reader
        }
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
                    binding.buttonStartRead.text = "Baca Tunggal"
                }
                R.id.radioButtonContinuousRead -> {
                    binding.textViewListOfEpcsLabel.visibility = View.VISIBLE
                    binding.recyclerViewReadEpcs.visibility = View.VISIBLE
                    binding.buttonStopRead.visibility = View.VISIBLE
                    binding.buttonStartRead.text = "Mulai Baca Kontinu"
                }
            }
        }
        // Atur default
        binding.radioButtonContinuousRead.isChecked = true // Atau single read default


        binding.buttonStartRead.setOnClickListener {
            // Cek lagi status reader sebelum memulai (opsional, karena ViewModel juga cek)
            // val myApp = requireActivity().application as MyApplication
            // if (!myApp.isReaderOpened()) {
            // Toast.makeText(context, "Simulasi: Hubungkan reader terlebih dahulu.", Toast.LENGTH_SHORT).show()
            // return@setOnClickListener
            // }
            val isContinuous = binding.radioButtonContinuousRead.isChecked
            viewModel.startReading(isContinuous)
        }

        binding.buttonStopRead.setOnClickListener {
            viewModel.stopContinuousReading()
        }

        binding.buttonClearList.setOnClickListener {
            epcListAdapter.clearItems()
            viewModel.continuousEpcList.value?.let { current ->
                if (current.isNotEmpty()) {
                    // Jika ingin ViewModel juga tahu list di-clear dari UI
                    // viewModel.clearContinuousList() // Anda perlu menambahkan fungsi ini di ViewModel
                }
            }
            binding.textViewLastEpcValue.text = "" // Juga hapus EPC terakhir
        }
    }

    private fun observeViewModel() {
        viewModel.lastReadEpc.observe(viewLifecycleOwner) { epc ->
            binding.textViewLastEpcValue.text = epc ?: ""
        }

        viewModel.continuousEpcList.observe(viewLifecycleOwner) { epcSet ->
            epcListAdapter.updateItems(epcSet.toList().sortedDescending()) // Tampilkan yang terbaru di atas
        }

        viewModel.isReadingContinuous.observe(viewLifecycleOwner) { isReading ->
            binding.buttonStartRead.isEnabled = !isReading
            if (binding.radioButtonContinuousRead.isChecked) {
                binding.buttonStopRead.isEnabled = isReading
            } else {
                binding.buttonStopRead.isEnabled = false // Tidak ada stop untuk single read eksplisit
            }

            if (isReading) {
                binding.textViewStatus.text = "Status: Membaca..."
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorScanning, null))
            } else {
                binding.textViewStatus.text = "Status: Idle"
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorIdle, null))
                // Jangan hapus text jika sudah ada hasil pembacaan tunggal
                if (binding.textViewLastEpcValue.text == "Membaca...") {
                    binding.textViewLastEpcValue.text = ""
                }
            }
        }

        viewModel.readError.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                binding.textViewStatus.text = "Status: Error"
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorError, null))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopContinuousReading() // Pastikan berhenti saat fragment tidak aktif
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = ReadTagFragment()
    }

    // Adapter sederhana untuk RecyclerView (bisa dipindah ke file sendiri jika lebih kompleks)
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
            holder.textView.setPadding(16, 12, 16, 12) // Tambahkan padding
            holder.textView.setTextIsSelectable(true) // Agar bisa di-copy
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<String>) {
            val oldSize = items.size
            items.clear()
            notifyItemRangeRemoved(0, oldSize)
            items.addAll(newItems)
            notifyItemRangeInserted(0, newItems.size)
            // Atau gunakan DiffUtil untuk performa lebih baik pada list besar
        }

        fun clearItems() {
            val oldSize = items.size
            items.clear()
            notifyItemRangeRemoved(0, oldSize)
        }
    }
}
