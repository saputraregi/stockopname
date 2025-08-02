package com.example.aplikasistockopnameperpus

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
// import com.example.aplikasistockopnameperpus.MyApplication // Sudah ada
import com.example.aplikasistockopnameperpus.R
import com.example.aplikasistockopnameperpus.databinding.ActivityReadWriteTagBinding
import com.example.aplikasistockopnameperpus.fragment.ReadTagFragment
import com.example.aplikasistockopnameperpus.fragment.WriteTagFragment
import com.example.aplikasistockopnameperpus.viewmodel.ReadWriteTagViewModel
import com.example.aplikasistockopnameperpus.sdk.ChainwaySDKManager
import com.google.android.material.tabs.TabLayoutMediator

class ReadWriteTagActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadWriteTagBinding
    private val viewModel: ReadWriteTagViewModel by viewModels()
    private lateinit var myApp: MyApplication
    private lateinit var sdkManager: ChainwaySDKManager

    // Tetap gunakan ini sebagai member kelas
    // Untuk lokalisasi yang lebih baik, isi array ini dengan getString(R.string.xxx)
    // Contoh: private val tabTitles by lazy { arrayOf(getString(R.string.tab_title_read), getString(R.string.tab_title_write)) }
    // Tapi untuk sekarang, kita biarkan hardcoded agar sesuai dengan pertanyaan Anda.
    private val tabTitles = arrayOf("Baca Tag", "Tulis Tag")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadWriteTagBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myApp = application as MyApplication
        sdkManager = myApp.sdkManager

        setSupportActionBar(binding.toolbarReadWrite)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_read_write_tag)
        binding.toolbarReadWrite.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (!sdkManager.isDeviceReady()) {
            Toast.makeText(this, "Simulasi: UHF Reader tidak terhubung. Beberapa fitur mungkin tidak berfungsi.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Simulasi: UHF Reader terhubung.", Toast.LENGTH_SHORT).show()
        }

        setupViewPagerAndTabs()
    }

    private fun setupViewPagerAndTabs() {
        val adapter = ViewPagerAdapter(this) // Adapter akan menggunakan this.tabTitles
        binding.viewPagerReadWrite.adapter = adapter

        // Gunakan tabTitles yang merupakan member kelas
        TabLayoutMediator(binding.tabLayoutReadWrite, binding.viewPagerReadWrite) { tab, position ->
            tab.text = tabTitles[position] // Menggunakan member kelas tabTitles

            val contentDescriptionPrefix = getString(R.string.tab_description_prefix)
            tab.contentDescription = "$contentDescriptionPrefix ${tabTitles[position]}"
        }.attach()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopContinuousReading()
    }

    override fun onDestroy() {
        super.onDestroy()
        // myApp.disconnectReader()
    }

    // ViewPagerAdapter adalah inner class, jadi ia BISA mengakses member dari ReadWriteTagActivity
    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        // Ini akan merujuk ke ReadWriteTagActivity.this.tabTitles.size
        override fun getItemCount(): Int = tabTitles.size // SEHARUSNYA TIDAK ADA ERROR DI SINI

        override fun createFragment(position: Int): Fragment {
            // Ini akan merujuk ke ReadWriteTagActivity.this.tabTitles
            return when (position) {
                0 -> ReadTagFragment.newInstance() // Asumsi fragment ini tidak butuh tabTitles[position]
                1 -> WriteTagFragment.newInstance() // Asumsi fragment ini tidak butuh tabTitles[position]
                else -> throw IllegalStateException("Posisi tab tidak valid: $position berdasarkan judul ${tabTitles.joinToString()}")
            }
        }
    }
}
