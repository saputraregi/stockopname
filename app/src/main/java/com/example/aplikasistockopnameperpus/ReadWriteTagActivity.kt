package com.example.aplikasistockopnameperpus

import android.os.Bundle
import android.widget.Toast // Masih bisa digunakan untuk pesan lain jika perlu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.ActivityReadWriteTagBinding
import com.example.aplikasistockopnameperpus.fragment.ReadTagFragment
import com.example.aplikasistockopnameperpus.fragment.WriteTagFragment
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel
// import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager // Tidak perlu jika tidak digunakan langsung
import com.google.android.material.tabs.TabLayoutMediator

class ReadWriteTagActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadWriteTagBinding
    // ViewModel di-inject dan akan menangani interaksi dengan SDK Manager
    private val viewModel: ReadWriteTagViewModel by viewModels()

    // Tidak lagi memerlukan instance sdkManager atau myApp secara langsung di sini
    // untuk pengecekan status awal.
    // private lateinit var myApp: MyApplication
    // private lateinit var sdkManager: ChainwaySDKManager

    private val tabTitles by lazy { // Menggunakan lazy delegate untuk string resources
        arrayOf(
            getString(R.string.tab_title_read_tag), // Pastikan string ini ada
            getString(R.string.tab_title_write_tag) // Pastikan string ini ada
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadWriteTagBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // myApp = application as MyApplication // Tidak lagi diperlukan di sini
        // sdkManager = myApp.sdkManager     // Tidak lagi diperlukan di sini

        setSupportActionBar(binding.toolbarReadWrite)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_read_write_tag)
        binding.toolbarReadWrite.setNavigationOnClickListener {
            // Menggunakan onBackPressedDispatcher lebih modern
            onBackPressedDispatcher.onBackPressed()
        }

        // Hapus pengecekan sdkManager dan Toast simulasi di sini.
        // ViewModel akan menangani status reader dan Fragment akan menampilkan error jika operasi gagal.
        // if (!sdkManager.isDeviceReady()) {
        //     Toast.makeText(this, "Simulasi: UHF Reader tidak terhubung...", Toast.LENGTH_LONG).show()
        // } else {
        //     Toast.makeText(this, "Simulasi: UHF Reader terhubung.", Toast.LENGTH_SHORT).show()
        // }

        setupViewPagerAndTabs()
        observeViewModelForReaderStatus() // Tambahkan observer untuk status reader
    }

    private fun setupViewPagerAndTabs() {
        // Menggunakan FragmentManager dari supportFragmentManager untuk FragmentStateAdapter
        val adapter = ViewPagerAdapter(this)
        binding.viewPagerReadWrite.adapter = adapter

        TabLayoutMediator(binding.tabLayoutReadWrite, binding.viewPagerReadWrite) { tab, position ->
            tab.text = tabTitles[position]
            // Deskripsi konten untuk aksesibilitas
            val contentDescriptionPrefix = getString(R.string.tab_description_prefix) // Pastikan string ini ada
            tab.contentDescription = "$contentDescriptionPrefix ${tabTitles[position]}"
        }.attach()
    }

    private fun observeViewModelForReaderStatus() {
        // Amati LiveData isReaderReady dari ViewModel
        // Ini bersifat opsional di Activity, karena Fragment juga bisa menangani
        // error jika operasi gagal karena reader tidak siap.
        // Namun, jika Anda ingin pesan global di Activity, ini caranya:
        viewModel.isReaderReady.observe(this) { isReady ->
            if (!isReady) {
                // Tampilkan Toast bahwa reader tidak siap, tapi jangan blok UI sepenuhnya di sini.
                // Biarkan pengguna mencoba operasi di Fragment, dan Fragment akan menampilkan
                // error yang lebih spesifik jika operasi tersebut gagal.
                // Toast.makeText(this, getString(R.string.reader_not_connected_warning_activity), Toast.LENGTH_SHORT).show()
                // Log.w("ReadWriteTagActivity", "Reader status: Not Ready")
            } else {
                // Log.i("ReadWriteTagActivity", "Reader status: Ready")
            }
        }

        // Anda juga bisa mengamati error umum dari ViewModel di sini jika ada
        // viewModel.globalError.observe(this) { error ->
        //    error?.let { Toast.makeText(this, "Error: $it", Toast.LENGTH_LONG).show() }
        // }
    }

    override fun onPause() {
        super.onPause()
        // ViewModel akan menangani penghentian operasi SDK saat diperlukan,
        // terutama jika operasi tersebut kontinu dan dimulai oleh ViewModel.
        // Pemanggilan stopContinuousReading() di sini adalah tindakan pencegahan yang baik.
        viewModel.stopContinuousReading()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Tidak perlu disconnect manual di sini jika lifecycle SDK dikelola oleh MyApplication
        // atau jika ViewModel membersihkan listenernya di onCleared().
        // myApp.disconnectReader() // Ini seharusnya tidak lagi ada atau diperlukan
    }

    // ViewPagerAdapter tetap sebagai inner class
    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = tabTitles.size

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ReadTagFragment.newInstance()
                1 -> WriteTagFragment.newInstance()
                else -> throw IllegalStateException("Posisi tab tidak valid: $position berdasarkan judul ${tabTitles.joinToString()}")
            }
        }
    }
}

