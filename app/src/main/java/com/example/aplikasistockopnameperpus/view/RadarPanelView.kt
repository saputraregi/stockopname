package com.example.aplikasistockopnameperpus.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
// PERBAIKAN IMPORT: Pastikan ini mengarah ke data class Anda, BUKAN MyApplication
import com.example.aplikasistockopnameperpus.viewmodel.MyRadarLocationEntity
import kotlin.math.cos
import kotlin.math.sin

class RadarPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val defaultTagPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targetTagPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // PERBAIKAN TIPE DATA: Gunakan List<MyRadarLocationEntity>
    private var tagsToDisplay: List<com.rscja.deviceapi.entity.RadarLocationEntity> = emptyList()
    private var currentTargetEpc: String? = null

    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 0f

    private val defaultTagRadius = 12f
    private val targetTagRadius = 18f
    private val maxRssiValue = 100

    init {
        defaultTagPaint.color = Color.parseColor("#8000AFFF")
        defaultTagPaint.style = Paint.Style.FILL

        targetTagPaint.color = Color.parseColor("#FFFFD700")
        targetTagPaint.style = Paint.Style.FILL

        textPaint.color = Color.WHITE
        textPaint.textSize = 20f
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = (Math.min(w, h) / 2f) * 0.9f
        Log.d("RadarPanelView", "onSizeChanged: w=$w, h=$h, radius=$radius")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0) return

        // Sekarang tagInfo adalah com.rscja.deviceapi.entity.RadarLocationEntity
        // Anda perlu mengakses properti yang benar dari kelas SDK ini.
        // Misal: tagInfo.value, tagInfo.angle, tagInfo.tag (atau tagInfo.getEpc(), tagInfo.getRssi(), tagInfo.getAngle())
        tagsToDisplay.forEach { tagInfoSdk ->
            // GANTI tagInfo.value, tagInfo.angle, tagInfo.tag DENGAN PROPERTI YANG BENAR DARI KELAS SDK
            // Contoh (ANDA HARUS MEMVERIFIKASI NAMA PROPERTI DARI RadarLocationEntity SDK):
            val rssi = tagInfoSdk.value // Asumsi 'value' adalah RSSI di kelas SDK
            val angle = tagInfoSdk.angle // Asumsi 'angle' adalah sudut di kelas SDK
            val epc = tagInfoSdk.tag    // Asumsi 'tag' adalah EPC di kelas SDK

            val normalizedDistance = 1.0f - (rssi.toFloat() / maxRssiValue) // maxRssiValue mungkin perlu disesuaikan
            val distanceOnRadar = normalizedDistance * radius
            val angleInRadians = Math.toRadians(angle.toDouble())

            val tagX = centerX + (distanceOnRadar * cos(angleInRadians)).toFloat()
            val tagY = centerY + (distanceOnRadar * sin(angleInRadians)).toFloat()

            val paintToUse: Paint
            val pointRadius: Float
            if (epc == currentTargetEpc && !currentTargetEpc.isNullOrEmpty()) {
                paintToUse = targetTagPaint
                pointRadius = targetTagRadius
            } else {
                paintToUse = defaultTagPaint
                pointRadius = defaultTagRadius
            }
            canvas.drawCircle(tagX, tagY, pointRadius, paintToUse)
        }
    }

    /**
     * Set daftar tag yang akan ditampilkan dan EPC target.
     * PERBAIKAN TIPE PARAMETER: Gunakan List<MyRadarLocationEntity>
     */
    fun setTags(newTags: List<com.rscja.deviceapi.entity.RadarLocationEntity>, targetEpc: String?) {
        this.tagsToDisplay = newTags
        this.currentTargetEpc = targetEpc
        invalidate()
    }

    fun clear() {
        this.tagsToDisplay = emptyList()
        this.currentTargetEpc = null
        invalidate()
        Log.d("RadarPanelView", "Panel cleared")
    }
}
