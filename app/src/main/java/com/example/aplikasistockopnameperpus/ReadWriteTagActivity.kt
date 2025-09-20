package com.example.aplikasistockopnameperpus

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent // Pastikan import ini ada
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2 // Pastikan import ini ada
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.ActivityReadWriteTagBinding
import com.example.aplikasistockopnameperpus.fragment.ReadTagFragment
import com.example.aplikasistockopnameperpus.fragment.WriteTagFragment
import com.example.aplikasistockopnameperpus.interfaces.PhysicalTriggerListener // Pastikan path interface benar
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel
import com.google.android.material.tabs.TabLayoutMediator

class ReadWriteTagActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadWriteTagBinding
    private val viewModel: ReadWriteTagViewModel by viewModels() // ViewModel sudah benar

    private val TRIGGER_KEY_MAIN = 239
    private val TRIGGER_KEY_BACKUP = 139

    private val tabTitles by lazy {
        arrayOf(
            getString(R.string.tab_title_read_tag),
            getString(R.string.tab_title_write_tag)
        )
    }

    private var currentActiveFragment: PhysicalTriggerListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadWriteTagBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarReadWrite)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_read_write_tag)
        binding.toolbarReadWrite.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupViewPagerAndTabs()
        observeViewModelGlobalStates() // Ganti nama fungsi observasi
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        if (keyCode == TRIGGER_KEY_MAIN || keyCode == TRIGGER_KEY_BACKUP) {
            if (event.repeatCount == 0) {
                Log.d("ReadWriteTagActivity", "Tombol fisik ditekan (keyCode: $keyCode), mendelegasikan ke fragment aktif: ${currentActiveFragment?.javaClass?.simpleName}")
                currentActiveFragment?.onPhysicalTriggerPressed()
                    ?: Log.w("ReadWriteTagActivity", "Tidak ada PhysicalTriggerListener aktif untuk menangani tombol fisik.")
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setupViewPagerAndTabs() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPagerReadWrite.adapter = adapter

        binding.viewPagerReadWrite.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Logika untuk set currentActiveFragment akan dihandle oleh onResume dari masing-masing Fragment.
                // Log.d("ReadWriteTagActivity", "Tab dipilih, posisi: $position. currentActiveFragment akan di-set oleh Fragment.")
            }
        })

        TabLayoutMediator(binding.tabLayoutReadWrite, binding.viewPagerReadWrite) { tab, position ->
            tab.text = tabTitles[position]
            val contentDescriptionPrefix = getString(R.string.tab_description_prefix)
            tab.contentDescription = "$contentDescriptionPrefix ${tabTitles[position]}"
        }.attach()
    }

    fun setActiveTriggerListener(listener: PhysicalTriggerListener?) {
        currentActiveFragment = listener
        Log.d("ReadWriteTagActivity", "Active trigger listener diubah menjadi: ${listener?.javaClass?.simpleName}")
    }

    private fun observeViewModelGlobalStates() {
        // Mengamati status kesiapan reader dari ViewModel
        viewModel.isReaderReady.observe(this) { isReady ->
            Log.i("ReadWriteTagActivity", "Status Reader dari ViewModel: ${if (isReady) "Siap" else "Tidak Siap"}")
            if (!isReady) {
                // Anda bisa menampilkan pesan global di sini jika reader tiba-tiba terputus
                // Namun, Fragment juga akan menangani error jika operasi gagal karena reader tidak siap.
                // Contoh: Toast.makeText(this, getString(R.string.reader_not_connected_warning_activity), Toast.LENGTH_LONG).show()
            }
        }

        // Mengamati error umum dari operasi baca atau SDK
        // Fragment ReadTagFragment akan mengobservasi _readError secara lebih spesifik.
        // Activity bisa mengobservasi error yang lebih umum atau yang tidak ditangani Fragment.
        // Untuk sekarang, kita biarkan Fragment yang menangani _readError spesifiknya.
        // Jika Anda memiliki error global yang ingin ditampilkan di Activity:
        // viewModel.readError.observe(this) { errorMessage ->
        //     errorMessage?.let {
        //         Toast.makeText(this, "Global Read Error: $it", Toast.LENGTH_LONG).show()
        //         // Pertimbangkan untuk memanggil fungsi di ViewModel untuk clear error ini
        //         // setelah ditampilkan agar tidak muncul berulang kali.
        //         // viewModel.clearGlobalError() // Contoh
        //     }
        // }

        // Mengamati status loading umum jika diperlukan di Activity
        // Biasanya, Fragment akan mengelola ProgressBar masing-masing.
        // viewModel.isLoading.observe(this) { isLoading ->
        //     Log.d("ReadWriteTagActivity", "ViewModel isLoading: $isLoading")
        //     // Update UI Activity jika ada ProgressBar global
        //     // binding.globalProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        // }
    }

    override fun onPause() {
        super.onPause()
        Log.d("ReadWriteTagActivity", "onPause, memanggil viewModel.stopContinuousReading()")
        // Memastikan pembacaan kontinu dihentikan jika Activity di-pause
        viewModel.stopContinuousReading()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ReadWriteTagActivity", "onDestroy.")
        // ViewModel.onCleared() akan menangani pembersihan listener SDK.
    }

    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = tabTitles.size

        override fun createFragment(position: Int): Fragment {
            Log.d("ViewPagerAdapter", "Membuat fragment untuk posisi: $position")
            return when (position) {
                0 -> ReadTagFragment.newInstance()
                1 -> WriteTagFragment.newInstance()
                else -> throw IllegalStateException("Posisi tab tidak valid: $position berdasarkan judul ${tabTitles.joinToString()}")
            }
        }
    }
}

