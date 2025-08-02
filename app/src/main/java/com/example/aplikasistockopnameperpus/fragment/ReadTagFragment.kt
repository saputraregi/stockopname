package com.example.aplikasistockopnameperpus.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
// import androidx.compose.ui.semantics.text // Hapus jika tidak digunakan
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikasistockopnameperpus.MyApplication // Pastikan import ini benar
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.example.aplikasistockopnameperpus.databinding.FragmentReadTagBinding
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel


class ReadTagFragment : Fragment() {

    private var _binding: FragmentReadTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReadWriteTagViewModel by activityViewModels()
    private lateinit var epcListAdapter: MySimpleStringAdapter // Adapter untuk RecyclerView
    private lateinit var sdkManager: ChainwaySDKManager // Tetap lateinit

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadTagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ===== INISIALISASI sdkManager DI SINI =====
        // 1. Dapatkan instance MyApplication
        val myApp = requireActivity().application as MyApplication
        // 2. Akses properti sdkManager dari instance MyApplication
        sdkManager = myApp.sdkManager // Pastikan 'sdkManager' adalah nama properti yang benar di MyApplication Anda

        // ===== LANJUTKAN DENGAN KODE ANDA YANG LAIN =====
        setupUI()
        observeViewModel()

        // Sekarang sdkManager sudah diinisialisasi, Anda bisa menggunakannya
        if (!sdkManager.connectDevices()) {
            Toast.makeText(requireContext(), "Reader tidak aktif. Mode baca mungkin terbatas.", Toast.LENGTH_LONG).show()
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
                    binding.buttonStartRead.text = getString(R.string.baca_tunggal) // Gunakan string resource
                }
                R.id.radioButtonContinuousRead -> {
                    binding.textViewListOfEpcsLabel.visibility = View.VISIBLE
                    binding.recyclerViewReadEpcs.visibility = View.VISIBLE
                    binding.buttonStopRead.visibility = View.VISIBLE
                    binding.buttonStartRead.text = getString(R.string.mulai_baca_kontinu) // Gunakan string resource
                }
            }
        }
        // Atur default
        binding.radioButtonContinuousRead.isChecked = true


        binding.buttonStartRead.setOnClickListener {
            // Cek lagi status reader sebelum memulai (opsional, karena ViewModel juga cek)
            // Anda bisa menggunakan instance sdkManager yang sudah diinisialisasi di sini jika perlu
            // if (!sdkManager.isReaderOpened()) { // Ganti dengan metode yang sesuai dari SDK Anda
            //     Toast.makeText(requireContext(), "Hubungkan reader terlebih dahulu.", Toast.LENGTH_SHORT).show()
            //     return@setOnClickListener
            // }
            val isContinuous = binding.radioButtonContinuousRead.isChecked
            viewModel.startReading(isContinuous)
        }

        binding.buttonStopRead.setOnClickListener {
            viewModel.stopContinuousReading()
        }

        binding.buttonClearList.setOnClickListener {
            epcListAdapter.clearItems()
            // viewModel.continuousEpcList.value?.let { current ->
            //     if (current.isNotEmpty()) {
            //         // Jika ingin ViewModel juga tahu list di-clear dari UI
            //         // viewModel.clearContinuousList() // Anda perlu menambahkan fungsi ini di ViewModel
            //     }
            // }
            viewModel.clearContinuousListFromUI() // Panggil metode ViewModel jika ada
            binding.textViewLastEpcValue.text = "" // Juga hapus EPC terakhir
        }
    }

    private fun observeViewModel() {
        viewModel.lastReadEpc.observe(viewLifecycleOwner) { epc ->
            binding.textViewLastEpcValue.text = epc ?: ""
        }

        viewModel.continuousEpcList.observe(viewLifecycleOwner) { epcSet ->
            // Pastikan epcSet tidak null sebelum diolah lebih lanjut
            epcListAdapter.updateItems(epcSet?.toList()?.sortedDescending() ?: emptyList()) // Tampilkan yang terbaru di atas
        }

        viewModel.isReadingContinuous.observe(viewLifecycleOwner) { isReading ->
            binding.buttonStartRead.isEnabled = !isReading
            if (binding.radioButtonContinuousRead.isChecked) {
                binding.buttonStopRead.isEnabled = isReading
            } else {
                binding.buttonStopRead.isEnabled = false // Tidak ada stop untuk single read eksplisit
            }

            if (isReading) {
                binding.textViewStatus.text = getString(R.string.status_membaca) // Gunakan string resource
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorScanning, requireActivity().theme))
            } else {
                binding.textViewStatus.text = getString(R.string.status_idle) // Gunakan string resource
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorIdle, requireActivity().theme))
                // Jangan hapus text jika sudah ada hasil pembacaan tunggal
                if (binding.textViewLastEpcValue.text == getString(R.string.status_membaca)) { // Bandingkan dengan string resource
                    binding.textViewLastEpcValue.text = ""
                }
            }
        }

        viewModel.readError.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                binding.textViewStatus.text = getString(R.string.status_error) // Gunakan string resource
                binding.textViewStatus.setTextColor(resources.getColor(R.color.colorError, requireActivity().theme))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopContinuousReading() // Pastikan berhenti saat fragment tidak aktif
        // Pertimbangkan untuk memanggil sdkManager.disconnectDevices() atau metode serupa di sini
        // if (::sdkManager.isInitialized) { // Cek apakah sudah diinisialisasi sebelum digunakan
        //     sdkManager.disconnectDevices()
        // }
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
            // Cara update yang lebih aman dan efisien dengan DiffUtil direkomendasikan untuk list besar
            // Namun untuk kesederhanaan, kita akan retain cara lama dengan sedikit perbaikan
            val oldSize = items.size
            items.clear()
            items.addAll(newItems)
            if (oldSize > newItems.size) {
                notifyItemRangeRemoved(newItems.size, oldSize - newItems.size)
            }
            if (newItems.size > oldSize) {
                notifyItemRangeInserted(oldSize, newItems.size - oldSize)
            }
            // Notifikasi untuk item yang mungkin berubah kontennya
            val commonSize = minOf(oldSize, newItems.size)
            if (commonSize > 0) {
                notifyItemRangeChanged(0, commonSize)
            }
        }


        fun clearItems() {
            val oldSize = items.size
            items.clear()
            notifyItemRangeRemoved(0, oldSize)
        }
    }
}
