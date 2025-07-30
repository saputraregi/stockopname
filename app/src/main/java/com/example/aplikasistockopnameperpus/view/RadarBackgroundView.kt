package com.example.aplikasistockopnameperpus.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.sin

class RadarBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG) // Untuk latar belakang jika diperlukan
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG) // Untuk efek sapuan radar

    private var sweepShader: SweepGradient? = null
    private var matrixScan = Matrix()

    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 0f
    private var currentScanAngle = 0f // Sudut sapuan saat ini

    private var animationJob: Job? = null
    private var isScanning = false

    private val scaleCount = 4 // Jumlah lingkaran skala (tidak termasuk batas luar)
    private val angleLabels = listOf("0°", "90°", "180°", "270°") // Label sudut

    init {
        // bgPaint.color = Color.parseColor("#1AFFFFFF") // Latar belakang sangat transparan, opsional
        // bgPaint.style = Paint.Style.FILL

        linePaint.color = Color.parseColor("#4DFFFFFF") // Putih agak transparan untuk garis
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 2f

        textPaint.color = Color.parseColor("#B3FFFFFF") // Putih cukup jelas untuk teks
        textPaint.textSize = 28f // Sesuaikan ukuran teks
        textPaint.textAlign = Paint.Align.CENTER

        scanPaint.style = Paint.Style.FILL_AND_STROKE // Pastikan shader mengisi area sapuan
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        // Radius efektif adalah 90% dari setengah dimensi terkecil
        radius = (Math.min(w, h) / 2f) * 0.9f

        if (radius > 0) {
            // Setup shader untuk sapuan radar
            // Warna dari transparan ke hijau semi-transparan, lalu kembali ke transparan
            // Ini menciptakan efek "balok" sapuan yang lebih lembut
            sweepShader = SweepGradient(
                centerX, centerY,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#0D00FF00"), // Hijau sangat transparan (awal gradien)
                    Color.parseColor("#8000FF00"), // Hijau semi-transparan (puncak gradien)
                    Color.parseColor("#0D00FF00"), // Hijau sangat transparan (akhir gradien)
                    Color.TRANSPARENT
                ),
                // floatArrayOf(0f, 0.45f, 0.5f, 0.55f, 1f) // Distribusi warna untuk sapuan lebih tipis
                floatArrayOf(0f, 0.15f, 0.25f, 0.35f, 1f) // Distribusi untuk sapuan lebih lebar
            )
            scanPaint.shader = sweepShader
        }
        Log.d("RadarBackgroundView", "onSizeChanged: w=$w, h=$h, radius=$radius")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0) return // Jangan menggambar jika ukuran belum valid

        // 0. (Opsional) Gambar latar belakang
        // canvas.drawCircle(centerX, centerY, radius, bgPaint)

        // 1. Gambar lingkaran skala konsentris
        linePaint.alpha = 100 // Atur transparansi untuk garis skala
        for (i in 1..scaleCount) {
            canvas.drawCircle(centerX, centerY, radius * (i.toFloat() / scaleCount), linePaint)
        }
        // Gambar lingkaran terluar dengan opasitas penuh
        linePaint.alpha = 200
        canvas.drawCircle(centerX, centerY, radius, linePaint)


        // 2. Gambar garis sumbu (silang)
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, linePaint) // Horizontal
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, linePaint) // Vertikal

        // 3. Gambar label sudut (0, 90, 180, 270)
        val textRadiusOffset = textPaint.textSize / 3 // Sedikit offset agar tidak menempel garis
        val textPathRadius = radius + textPaint.textSize / 2 + textRadiusOffset

        // 0° (Kanan)
        canvas.drawText(angleLabels[0], centerX + textPathRadius - textRadiusOffset *2 , centerY + textPaint.textSize/3, textPaint)
        // 90° (Bawah - dalam sistem koordinat Android, sudut positif adalah searah jarum jam dari sumbu X positif)
        canvas.drawText(angleLabels[1], centerX, centerY + textPathRadius, textPaint)
        // 180° (Kiri)
        canvas.drawText(angleLabels[2], centerX - textPathRadius + textRadiusOffset*2, centerY + textPaint.textSize/3, textPaint)
        // 270° (Atas)
        canvas.drawText(angleLabels[3], centerX, centerY - textPathRadius + textPaint.textSize, textPaint)


        // 4. Gambar sapuan radar jika sedang scanning
        if (isScanning && sweepShader != null) {
            // Kita perlu merotasi canvas SEBELUM menggambar shader,
            // atau merotasi shader itu sendiri menggunakan matrix.
            // Di sini kita rotasi matrix shader agar lebih mudah dikelola.
            matrixScan.setRotate(currentScanAngle - 90, centerX, centerY) // -90 untuk memulai dari atas
            sweepShader?.setLocalMatrix(matrixScan)
            canvas.drawCircle(centerX, centerY, radius, scanPaint)
        }
    }

    /**
     * Mulai animasi sapuan radar.
     */
    fun start() {
        if (isScanning) return
        isScanning = true
        Log.d("RadarBackgroundView", "Starting scan animation")
        animationJob?.cancel() // Batalkan job sebelumnya jika ada
        animationJob = CoroutineScope(Dispatchers.Main).launch {
            while (isScanning && isActive) {
                currentScanAngle = (currentScanAngle + 2f) % 360 // Kecepatan sapuan
                postInvalidate() // Minta redraw di UI thread
                delay(16) // Sekitar 60 FPS (1000ms / 60fps = 16.66ms)
            }
        }
    }

    /**
     * Hentikan animasi sapuan radar.
     */
    fun stop() {
        if (!isScanning) return
        isScanning = false
        animationJob?.cancel()
        animationJob = null
        currentScanAngle = 0f // Reset sudut sapuan
        postInvalidate() // Redraw sekali lagi untuk menghilangkan sapuan
        Log.d("RadarBackgroundView", "Scan animation stopped")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop() // Pastikan animasi berhenti saat view dilepas
    }
}

