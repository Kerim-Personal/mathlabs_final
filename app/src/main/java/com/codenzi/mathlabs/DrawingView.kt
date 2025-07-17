package com.codenzi.mathlabs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentPath: Path = Path()
    private var currentPaint: Paint = Paint()
    // Storing DrawingAction objects for undo/redo potential, but not for real-time drawing
    private val drawingActions = mutableListOf<DrawingAction>()

    private var brushSize = 10f
    private var eraserSize = 50f // Larger for easier erasing
    private var currentColor = Color.RED // Default color

    var drawingMode: DrawingMode = DrawingMode.NONE
        set(value) {
            field = value // Assign the new value to the backing field
            // Logic previously in setDrawingMode() function:
            // Enable or disable touch events based on drawing mode
            isClickable = value != DrawingMode.NONE
            isFocusable = value != DrawingMode.NONE
            // Re-initialize paint for new mode
            preparePaintForCurrentMode()
        }

    // A bitmap to draw all paths on, and a canvas for that bitmap
    private var mBitmap: Bitmap? = null
    private var mCanvas: Canvas? = null

    // Store previous touch coordinates for smoother drawing
    private var mX: Float = 0f
    private var mY: Float = 0f
    private val TOUCH_TOLERANCE = 4f // Minimum distance for a new point

    init {
        currentPaint.apply {
            color = currentColor
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = brushSize
            isAntiAlias = true
            isDither = true
        }
        // Initially, the view should not consume touches
        isClickable = false
        isFocusable = false
    }

    enum class DrawingMode {
        NONE, PEN, ERASER
    }

    // Data class to hold a path and its paint properties
    private data class DrawingAction(val path: Path, val paint: Paint, val isEraser: Boolean = false)


    fun setBrushColor(color: Int) {
        this.currentColor = color
        preparePaintForCurrentMode()
    }

    fun setBrushSize(size: Float) {
        this.brushSize = size
        preparePaintForCurrentMode()
    }

    fun clearDrawing() {
        drawingActions.clear()
        currentPath.reset()
        // Clear the off-screen bitmap as well
        mBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate() // Redraw the view
    }

    private fun preparePaintForCurrentMode() {
        currentPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            isDither = true

            when (drawingMode) {
                DrawingMode.PEN -> {
                    color = currentColor
                    strokeWidth = brushSize
                    xfermode = null // Clear any xfermode for drawing
                }
                DrawingMode.ERASER -> {
                    // For erasing, we draw with a transparent color using PorterDuff.Mode.CLEAR
                    // This literally erases pixels on the bitmap.
                    color = Color.TRANSPARENT
                    strokeWidth = eraserSize
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                DrawingMode.NONE -> {
                    xfermode = null
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Re-create the bitmap only if dimensions change significantly
        if (mBitmap == null || mBitmap?.width != w || mBitmap?.height != h) {
            mBitmap?.recycle() // Recycle old bitmap if it exists
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            mCanvas = Canvas(mBitmap!!)
            // Redraw all previous actions onto the new bitmap
            redrawAllActionsToBitmap()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Just draw the off-screen bitmap onto the view's canvas
        mBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
    }

    private fun touchStart(x: Float, y: Float) {
        currentPath.reset()
        currentPath.moveTo(x, y)
        mX = x
        mY = y
        // Store the new DrawingAction when touch starts
        drawingActions.add(DrawingAction(Path(), Paint(currentPaint), drawingMode == DrawingMode.ERASER))
    }

    private fun touchMove(x: Float, y: Float) {
        val dx = Math.abs(x - mX)
        val dy = Math.abs(y - mY)
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            currentPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2)
            mX = x
            mY = y

            // Draw the current path segment to the off-screen bitmap immediately
            // Apply the xfermode and draw color dynamically
            drawingActions.lastOrNull()?.let { lastAction ->
                lastAction.path.set(currentPath) // Update the path in the last action
                mCanvas?.drawPath(lastAction.path, lastAction.paint)
            }
            invalidate() // Request redraw of the view to show the updated bitmap
        }
    }

    private fun touchUp() {
        currentPath.lineTo(mX, mY) // Final point of the path
        // No explicit drawing here, as touchMove already drew segments to bitmap.
        // The last segment is drawn with the final invalidate.
        currentPath.reset() // Reset for the next stroke
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawingMode == DrawingMode.NONE) {
            return false // Don't consume touch events if not in drawing mode
        }

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStart(x, y)
                invalidate() // Redraw to show the starting point
            }
            MotionEvent.ACTION_MOVE -> {
                touchMove(x, y)
            }
            MotionEvent.ACTION_UP -> {
                touchUp()
                invalidate() // Final redraw
            }
            else -> return false
        }
        return true
    }

    // Helper to redraw all actions to the bitmap, used on size change or clear
    private fun redrawAllActionsToBitmap() {
        mBitmap?.eraseColor(Color.TRANSPARENT) // Clear before redrawing
        for (action in drawingActions) {
            action.paint.xfermode = if (action.isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            mCanvas?.drawPath(action.path, action.paint)
        }
    }
}