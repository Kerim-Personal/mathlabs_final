package com.codenzi.mathlabs

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.codenzi.mathlabs.database.DrawingPath

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // --- DEĞİŞKENLER ---
    private var currentPath = Path()
    private var currentPaint = createPaint()
    private val completedPaths = mutableListOf<Pair<Path, Paint>>()
    private var bufferBitmap: Bitmap? = null
    private var bufferCanvas: Canvas? = null
    private var motionTouchEventX = 0f
    private var motionTouchEventY = 0f

    var onPathFinishedListener: ((DrawingPath) -> Unit)? = null
    var brushType: BrushType = BrushType.NONE
        set(value) {
            field = value
            val isDrawingEnabled = value != BrushType.NONE
            isClickable = isDrawingEnabled
            isFocusable = isDrawingEnabled
            if (value == BrushType.PEN) {
                setPenColor(currentPaint.color)
            }
        }

    // --- GÖRÜNÜM YAŞAM DÖNGÜSÜ ---
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bufferBitmap?.recycle()
        bufferBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bufferCanvas = Canvas(bufferBitmap!!)
        redrawCompletedPaths()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Önce tamamlanmış ve kaydedilmiş yolları içeren bitmap'i çiz
        bufferBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        // Sonra, o anda çizilmekte olan yolu direkt olarak ana canvas'a çiz
        if (brushType != BrushType.NONE) {
            canvas.drawPath(currentPath, currentPaint)
        }
    }

    // --- DOKUNMA OLAYLARI ---
    private fun touchStart(x: Float, y: Float) {
        currentPath.reset()
        currentPath.moveTo(x, y)
        motionTouchEventX = x
        motionTouchEventY = y
    }

    private fun touchMove(x: Float, y: Float) {
        val dx = Math.abs(x - motionTouchEventX)
        val dy = Math.abs(y - motionTouchEventY)
        if (dx >= 4f || dy >= 4f) {
            currentPath.quadTo(motionTouchEventX, motionTouchEventY, (x + motionTouchEventX) / 2, (y + motionTouchEventY) / 2)
            motionTouchEventX = x
            motionTouchEventY = y
        }
    }

    private fun touchUp() {
        if (!currentPath.isEmpty) {
            // Çizim bitince, bu yolu bitmiş yolların çizileceği buffer'a ekle
            bufferCanvas?.drawPath(currentPath, currentPaint)

            // Veritabanına kaydetmek için kopyalarını oluştur ve listeye ekle
            val pathToSave = Path(currentPath)
            val paintToSave = Paint(currentPaint)
            completedPaths.add(Pair(pathToSave, paintToSave))

            val serialized = PathConverter.serialize(pathToSave)
            if (serialized.isNotBlank()) {
                onPathFinishedListener?.invoke(
                    DrawingPath(
                        pdfAssetName = "",
                        pageIndex = 0,
                        color = paintToSave.color,
                        strokeWidth = paintToSave.strokeWidth,
                        isEraser = brushType == BrushType.ERASER,
                        serializedPath = serialized
                    )
                )
            }
        }
        currentPath.reset()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (brushType == BrushType.NONE) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStart(x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                touchMove(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                touchUp()
                invalidate()
            }
        }
        return true
    }

    // --- KONTROL FONKSİYONLARI ---
    fun setPenColor(color: Int) {
        currentPaint = createPaint().apply {
            this.color = color
            this.strokeWidth = currentPaint.strokeWidth
        }
    }

    fun setBrushSize(size: Float) {
        currentPaint.strokeWidth = size
    }

    fun activateEraser() {
        currentPaint = createPaint().apply {
            strokeWidth = currentPaint.strokeWidth
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }

    fun clearAllDrawings() {
        completedPaths.clear()
        bufferBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    fun loadDrawings(drawingPaths: List<DrawingPath>) {
        clearAllDrawings()
        for (dp in drawingPaths) {
            val path = PathConverter.deserialize(dp.serializedPath)
            val paint = createPaint().apply {
                color = dp.color
                strokeWidth = dp.strokeWidth
                if (dp.isEraser) {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
            }
            completedPaths.add(Pair(path, paint))
        }
        redrawCompletedPaths()
    }

    // --- YARDIMCI FONKSİYONLAR ---
    private fun redrawCompletedPaths() {
        bufferBitmap?.eraseColor(Color.TRANSPARENT)
        for ((path, paint) in completedPaths) {
            bufferCanvas?.drawPath(path, paint)
        }
        invalidate()
    }

    private fun createPaint(): Paint {
        return Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 16f // Varsayılan fırça boyutu
            color = Color.RED // Varsayılan renk
        }
    }
}

object PathConverter {
    fun serialize(path: Path): String {
        val pathMeasure = PathMeasure(path, false)
        if (pathMeasure.length == 0f) return "" // Boş yolları en başta kontrol et

        val points = mutableListOf<FloatArray>()
        var distance = 0f
        val stepSize = 1.0f

        while (distance < pathMeasure.length) {
            val pos = FloatArray(2)
            pathMeasure.getPosTan(distance, pos, null)
            points.add(pos)
            distance += stepSize
        }

        if (points.isEmpty()) return ""

        // Son noktayı da ekle
        val lastPos = FloatArray(2)
        pathMeasure.getPosTan(pathMeasure.length, lastPos, null)
        points.add(lastPos)

        return points.joinToString(";") { "${it[0]},${it[1]}" }
    }

    fun deserialize(serializedPath: String): Path {
        val path = Path()
        if (serializedPath.isBlank()) return path

        val points = serializedPath.split(';').mapNotNull {
            try {
                val coords = it.split(',')
                if (coords.size == 2) floatArrayOf(coords[0].toFloat(), coords[1].toFloat()) else null
            } catch (e: NumberFormatException) {
                null
            }
        }

        if (points.isNotEmpty()) {
            path.moveTo(points[0][0], points[0][1])
            for (i in 1 until points.size) {
                path.lineTo(points[i][0], points[i][1])
            }
        }
        return path
    }
}