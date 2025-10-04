package com.example.aplikasistockopnameperpus.view

import android.content.Context
import android.graphics.drawable.Drawable // Jika Anda menggunakan atribut centerImage
import android.util.AttributeSet
import android.util.Log // Tambahkan Log jika belum ada
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.aplikasistockopnameperpus.R // Pastikan R.styleable.RadarView ada jika Anda menggunakannya
import com.example.aplikasistockopnameperpus.model.RadarUiTag
// Import untuk List<com.rscja.deviceapi.entity.RadarLocationEntity> sudah benar di fungsi bindingData

// Komentar tentang MyRadarLocationEntity mungkin tidak relevan jika Anda langsung menggunakan entitas SDK
// import com.example.aplikasistockopnameperpus.MyApplication
// import com.example.aplikasistockopnameperpus.viewmodel.MyRadarLocationEntity

class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var radarBackgroundView: RadarBackgroundView
    private var radarPanelView: RadarPanelView
    private var centerImageView: ImageView // Opsional

    init {
        radarBackgroundView = RadarBackgroundView(context, attrs)
        addView(radarBackgroundView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        radarPanelView = RadarPanelView(context, attrs)
        addView(radarPanelView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Inisialisasi centerImageView tetap sama
        centerImageView = ImageView(context)
        // Contoh: centerImageView.setImageResource(R.drawable.ic_radar_center_default)
        val centerImageSize = (60 * resources.displayMetrics.density).toInt() // Ukuran contoh untuk gambar tengah
        val centerParams = LayoutParams(centerImageSize, centerImageSize)
        centerParams.gravity = android.view.Gravity.CENTER
        // addView(centerImageView, centerParams) // Uncomment jika Anda ingin menambahkan gambar di tengah

        // Pengambilan atribut kustom tetap sama
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.RadarView, 0, 0)
            // Contoh jika Anda punya atribut 'centerImage' di styleable RadarView
            val centerDrawable = typedArray.getDrawable(R.styleable.RadarView_centerImage)
            if (centerDrawable != null) {
                // centerImageView.setImageDrawable(centerDrawable)
            }
            typedArray.recycle()
        }
    }

    /**
     * Mengikat data tag yang terdeteksi ke RadarPanelView.
     * Sudut tidak lagi diproses di sini karena rotasi ditangani oleh FrameLayout ini.
     */
    fun bindingData(tags: List<RadarUiTag>, targetEpc: String?) {
        radarPanelView.setTags(tags, targetEpc)
    }

    /**
     * Memulai animasi sapuan radar di background.
     * Nama diubah agar lebih jelas ini untuk animasi latar.
     */
    fun startRadarAnimation() {
        radarBackgroundView.start()
    }

    /**
     * Menghentikan animasi sapuan radar di background.
     * Nama diubah agar lebih jelas ini untuk animasi latar.
     */
    fun stopRadarAnimation() {
        radarBackgroundView.stop()
        // Anda mungkin ingin memanggil clearPanel() dari Activity/ViewModel
        // setelah radar dihentikan, bukan secara otomatis di sini.
        // radarPanelView.clear()
    }

    /**
     * Mengatur rotasi untuk seluruh RadarView (FrameLayout ini).
     * Ini akan memutar RadarBackgroundView dan RadarPanelView di dalamnya.
     * @param angle Sudut dalam derajat yang diterima dari SDK (seharusnya sudah disesuaikan, misal -sdkAngle).
     */
    fun setRadarRotation(angle: Float) {
        this.rotation = angle // `rotation` adalah properti standar dari android.view.View
        // Log.d("RadarView", "FrameLayout rotation set to: $angle")
    }

    /**
     * Membersihkan titik-titik tag yang ditampilkan di RadarPanelView.
     */
    fun clearPanel() {
        radarPanelView.clear()
    }

    // Fungsi setRotation() yang di-override bisa dihapus jika tidak ada logika khusus.
    // Metode setRadarRotation() di atas lebih eksplisit untuk tujuan kita.
    // override fun setRotation(rotation: Float) {
    //     super.setRotation(rotation)
    // }
}

