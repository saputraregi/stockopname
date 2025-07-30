package com.example.aplikasistockopnameperpus.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.aplikasistockopnameperpus.R
// PASTIKAN IMPORT INI BENAR
import com.example.aplikasistockopnameperpus.MyApplication // Jika MyRadarLocationEntity ada di root package
import com.example.aplikasistockopnameperpus.viewmodel.MyRadarLocationEntity

// ATAU
// import com.example.aplikasistockopnameperpus.viewmodel.MyRadarLocationEntity // Jika ada di package viewmodel

class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var radarBackgroundView: RadarBackgroundView
    private var radarPanelView: RadarPanelView
    private var centerImageView: ImageView // Opsional, jika Anda punya gambar di tengah

    // private var centerImageDrawable: Drawable? = null // Jika mengambil dari atribut

    init {
        // Inflate layout internal RadarView jika Anda menggunakan XML terpisah untuk strukturnya
        // atau tambahkan view secara programatik.
        // Untuk contoh ini, kita tambahkan secara programatik.

        radarBackgroundView = RadarBackgroundView(context, attrs)
        addView(radarBackgroundView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        radarPanelView = RadarPanelView(context, attrs)
        addView(radarPanelView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        centerImageView = ImageView(context)
        // Atur gambar default atau dari atribut
        // centerImageView.setImageResource(R.drawable.ic_radar_center_default) // Ganti dengan drawable Anda
        val centerImageSize = (100 * resources.displayMetrics.density).toInt() // Contoh ukuran 100dp
        val centerParams = LayoutParams(centerImageSize, centerImageSize)
        centerParams.gravity = android.view.Gravity.CENTER
        // addView(centerImageView, centerParams) // Uncomment jika Anda ingin gambar tengah

        // Ambil atribut kustom jika ada (misal centerImage dari attrs)
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.RadarView, 0, 0)
            val centerDrawable = typedArray.getDrawable(R.styleable.RadarView_centerImage)
            if (centerDrawable != null) {
                // centerImageView.setImageDrawable(centerDrawable)
            }
            typedArray.recycle()
        }
    }

    /**
     * Mengikat data tag yang terdeteksi ke RadarPanelView.
     * PASTIKAN TIPE PARAMETER tags BENAR DI SINI.
     */
    fun bindingData(tags: List<MyRadarLocationEntity>, targetEpc: String?) {
        radarPanelView.setTags(tags, targetEpc)
    }

    /**
     * Memulai animasi sapuan radar di background.
     */
    fun startRadar() {
        radarBackgroundView.start()
    }

    /**
     * Menghentikan animasi sapuan radar.
     */
    fun stopRadar() {
        radarBackgroundView.stop()
        radarPanelView.clear() // Bersihkan juga titik-titik tag
    }

    /**
     * (Opsional) Mengatur rotasi untuk seluruh RadarView.
     * Ini bisa digunakan untuk mensimulasikan rotasi antena.
     * Sudut biasanya dalam derajat.
     */
    // override fun setRotation(rotation: Float) {
    //     // Anda mungkin ingin agar hanya background atau panel yang berputar,
    //     // atau seluruh view. Ini tergantung pada efek yang diinginkan.
    //     // Untuk efek antena berputar, mungkin hanya background sapuan yang berputar.
    //     // Namun, jika radarPanelView juga perlu tahu orientasi, maka seluruh view bisa dirotasi.
    //
    //     // Contoh: memutar background saja
    //     // radarBackgroundView.setSweepAngleOffset(rotation) // Anda perlu menambah metode ini di RadarBackgroundView
    //
    //     // Atau memutar seluruh FrameLayout ini
    //      super.setRotation(rotation)
    // }

    fun clearPanel() {
        radarPanelView.clear()
    }
}

