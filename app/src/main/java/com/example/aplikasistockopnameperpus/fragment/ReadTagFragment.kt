package com.example.aplikasistockopnameperpus.fragment

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.ReadWriteTagActivity // Import Activity Induk
import com.example.aplikasistockopnameperpus.databinding.FragmentReadTagBinding
import com.example.aplikasistockopnameperpus.interfaces.PhysicalTriggerListener // Import interface
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel

class ReadTagFragment : Fragment(), PhysicalTriggerListener { // Implementasi interface

    private var _binding: FragmentReadTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReadWriteTagViewModel by activityViewModels()
    private lateinit var epcListAdapter: MySimpleStringAdapter

    private var parentActivity: ReadWriteTagActivity? = null // Referensi ke Activity

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ReadWriteTagActivity) {
            parentActivity = context
        } else {
            throw RuntimeException("$context must be ReadWriteTagActivity")
        }
    }

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

    override fun onResume() {
        super.onResume()
        Log.d("ReadTagFragment", "onResume - Mendaftarkan diri sebagai active trigger listener.")
        parentActivity?.setActiveTriggerListener(this)
    }

    // --- HANYA SATU IMPLEMENTASI onPause YANG BENAR ---
    override fun onPause() {
        super.onPause()
        Log.d("ReadTagFragment", "onPause - Membatalkan pendaftaran listener dan menghentikan pembacaan.")
        parentActivity?.setActiveTriggerListener(null)
        viewModel.stopContinuousReading() // Panggil stopContinuousReading dari ViewModel
    }
    // ---------------------------------------------------

    override fun onDetach() {
        super.onDetach()
        parentActivity = null // Hindari memory leak
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
            Log.d("ReadTagFragment", "Tombol UI Start/Single Read diklik.")
            handleReadAction()
        }

        binding.buttonStopRead.setOnClickListener {
            Log.d("ReadTagFragment", "Tombol UI Stop Read diklik.")
            viewModel.stopContinuousReading()
        }

        binding.buttonClearList.setOnClickListener {
            Log.d("ReadTagFragment", "Tombol UI Clear List diklik.")
            epcListAdapter.clearItems()
            viewModel.clearContinuousListFromUI()
            binding.textViewLastEpcValue.text = ""
        }
    }

    private fun handleReadAction() {
        if (!isAdded || context == null) {
            Log.w("ReadTagFragment", "Aksi baca diabaikan, fragment tidak ter-attach atau context null.")
            return
        }

        val isContinuousMode = binding.radioButtonContinuousRead.isChecked
        val isCurrentlyReading = viewModel.isReadingContinuous.value ?: false
        val isLoading = viewModel.isLoading.value ?: false

        if (isCurrentlyReading || (isLoading && !isContinuousMode)) {
            Log.d("ReadTagFragment", "handleReadAction: Menghentikan pembacaan.")
            viewModel.stopContinuousReading()
        } else {
            Log.d("ReadTagFragment", "handleReadAction: Memulai pembacaan (kontinu: $isContinuousMode).")
            viewModel.startReading(isContinuousMode)
        }
    }

    private fun observeViewModel() {
        viewModel.lastReadEpc.observe(viewLifecycleOwner) { epc ->
            if (! (viewModel.isLoading.value == true && viewModel.isReadingContinuous.value == true && epc.isNullOrEmpty()) ) {
                binding.textViewLastEpcValue.text = epc ?: ""
            }
        }

        viewModel.continuousEpcList.observe(viewLifecycleOwner) { epcSet ->
            if (binding.radioButtonContinuousRead.isChecked) {
                epcListAdapter.updateItems(epcSet?.toList()?.sortedDescending() ?: emptyList())
            } else {
                epcListAdapter.clearItems()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { updateUIBasedOnScanState() }
        viewModel.isReadingContinuous.observe(viewLifecycleOwner) { updateUIBasedOnScanState() }

        viewModel.readError.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                }
                binding.textViewStatus.text = getString(R.string.status_error)
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorError, requireActivity().theme))
            }
        }
    }

    private fun updateUIBasedOnScanState() {
        val isLoading = viewModel.isLoading.value ?: false
        val isReadingContinuous = viewModel.isReadingContinuous.value ?: false

        if (isLoading || isReadingContinuous) {
            binding.textViewStatus.text = getString(R.string.status_membaca)
            binding.textViewStatus.setTextColor(resources.getColor(R.color.colorScanning, requireActivity().theme))

            if (binding.radioButtonContinuousRead.isChecked) {
                binding.buttonStartRead.text = getString(R.string.button_stop_read)
                binding.buttonStartRead.isEnabled = true
                binding.buttonStopRead.visibility = View.GONE
            } else {
                binding.buttonStartRead.text = getString(R.string.status_membaca)
                binding.buttonStartRead.isEnabled = false
                binding.buttonStopRead.visibility = View.GONE
            }
            binding.radioGroupReadMode.isEnabled = false
            (0 until binding.radioGroupReadMode.childCount).forEach {
                binding.radioGroupReadMode.getChildAt(it).isEnabled = false
            }
        } else {
            binding.textViewStatus.text = getString(R.string.status_idle)
            binding.textViewStatus.setTextColor(resources.getColor(R.color.colorIdle, requireActivity().theme))

            if (binding.radioButtonContinuousRead.isChecked) {
                binding.buttonStartRead.text = getString(R.string.mulai_baca_kontinu)
                binding.buttonStopRead.visibility = View.VISIBLE
                binding.buttonStopRead.isEnabled = false
            } else {
                binding.buttonStartRead.text = getString(R.string.baca_tunggal)
                binding.buttonStopRead.visibility = View.GONE
            }
            binding.buttonStartRead.isEnabled = true
            binding.radioGroupReadMode.isEnabled = true
            (0 until binding.radioGroupReadMode.childCount).forEach {
                binding.radioGroupReadMode.getChildAt(it).isEnabled = true
            }

            if (binding.textViewLastEpcValue.text == getString(R.string.status_membaca) &&
                !binding.radioButtonContinuousRead.isChecked && viewModel.lastReadEpc.value.isNullOrEmpty()) {
                // binding.textViewLastEpcValue.text = getString(R.string.no_tag_found_placeholder)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d("ReadTagFragment", "onDestroyView.")
    }

    override fun onPhysicalTriggerPressed() {
        Log.i("ReadTagFragment", "Tombol fisik ditekan di ReadTagFragment.")
        if (!isAdded || context == null) {
            Log.w("ReadTagFragment", "Tombol fisik diabaikan, fragment tidak ter-attach atau context null.")
            return
        }
        handleReadAction()
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

        fun updateItems(newItems: List<String>) {
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

        private class MyStringDiffCallback(
            private val oldList: List<String>,
            private val newList: List<String>
        ) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition] == newList[newItemPosition]
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition] == newList[newItemPosition]
            }
        }
    }
}
