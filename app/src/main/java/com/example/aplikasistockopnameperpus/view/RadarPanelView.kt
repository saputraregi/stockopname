package com.example.aplikasistockopnameperpus.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.example.aplikasistockopnameperpus.model.RadarUiTag // PENTING: Gunakan RadarUiTag
import kotlin.math.cos
import kotlin.math.sin

class RadarPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val defaultTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8000AFFF") // Biru untuk tag default
        style = Paint.Style.FILL
    }
    private val targetTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFD700") // Kuning/Emas untuk tag target
        style = Paint.Style.FILL
    }

    // --- PERBAIKAN TIPE DATA ---
    private var tagsToDisplay: List<RadarUiTag> = emptyList()
    private var currentTargetEpc: String? = null

    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 0f

    private val defaultTagRadius = 12f
    private val targetTagRadius = 18f

    // Nilai untuk normalisasi. 'distanceValue' dari SDK sudah berupa persentase (0-100).
    private val maxValue = 100 // Nilai maksimum dari SDK (paling dekat)
    private val minValue = 0   // Nilai minimum dari SDK (paling jauh)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = (minOf(w, h) / 2f) * 0.9f // Radius efektif 90%
        Log.d("RadarPanelView", "onSizeChanged: w=$w, h=$h, radius=$radius")
    }

    // --- PERBAIKAN LOGIKA GAMBAR ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0) return // Jangan menggambar jika ukuran belum valid

        tagsToDisplay.forEach { uiTag ->
            val distanceValue = uiTag.distanceValue
            val epc = uiTag.epc
            val uiAngle = uiTag.uiAngle // Ambil sudut yang sudah jadi

            // Normalisasi jarak: nilai 100 (dekat) -> jarak kecil dari pusat
            // Nilai 0 (jauh) -> jarak besar (di tepi)
            val normalizedDistance = (maxValue - distanceValue).toFloat() / (maxValue - minValue).toFloat()
            val distanceOnRadar = normalizedDistance * radius

            // Konversi sudut ke radian untuk fungsi sin/cos
            val angleInRad = Math.toRadians(uiAngle.toDouble() - 90.0) // Kurangi 90 karena 0 derajat di Android adalah jam 3, bukan jam 12.

            // Hitung posisi X dan Y menggunakan trigonometri
            val tagX = centerX + (distanceOnRadar * cos(angleInRad)).toFloat()
            val tagY = centerY + (distanceOnRadar * sin(angleInRad)).toFloat()

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
            Log.d("RadarPanelView_Draw", "Tag EPC: $epc, Angle: $uiAngle, Dist: $distanceOnRadar, X: $tagX, Y: $tagY")
        }
    }

    // --- PERBAIKAN TIPE DATA FUNGSI ---
    fun setTags(newTags: List<RadarUiTag>, targetEpc: String?) {
        this.tagsToDisplay = newTags
        this.currentTargetEpc = targetEpc
        invalidate() // Perintahkan View untuk menggambar ulang
        Log.d("RadarPanelView", "setTags called. Tag count: ${newTags.size}, Target: $targetEpc")
    }

    fun clear() {
        this.tagsToDisplay = emptyList()
        this.currentTargetEpc = null
        invalidate()
        Log.d("RadarPanelView", "Panel cleared")
    }
}
