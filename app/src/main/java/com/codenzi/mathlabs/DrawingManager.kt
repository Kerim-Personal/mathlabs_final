package com.codenzi.mathlabs

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.codenzi.mathlabs.database.DrawingDao
import com.codenzi.mathlabs.database.DrawingPath
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DrawingManager(
    private val context: Context,
    private val drawingView: DrawingView,
    private val fabToggleDrawing: FloatingActionButton,
    private val fabEraser: FloatingActionButton,
    private val fabClearAll: FloatingActionButton,
    private val drawingOptionsPanel: LinearLayout,
    private val colorOptions: LinearLayout,
    private val sizeOptions: LinearLayout,
    private val btnColorRed: ImageButton,
    private val btnColorBlue: ImageButton,
    private val btnColorBlack: ImageButton,
    private val btnSizeSmall: ImageButton,
    private val btnSizeMedium: ImageButton,
    private val btnSizeLarge: ImageButton,
    private val showSnackbar: (String) -> Unit,
    private val dao: DrawingDao,
    private val coroutineScope: CoroutineScope
) {
    private var currentPdfName: String = ""
    private var currentPageIndex: Int = 0
    private var eraserClickCount = 0
    private var lastEraserClickTime: Long = 0

    init {
        setupClickListeners()
        setupDrawingViewListener()
    }

    fun loadDrawingsForPage(pdfName: String, pageIndex: Int) {
        this.currentPdfName = pdfName
        this.currentPageIndex = pageIndex
        coroutineScope.launch {
            val paths = dao.getPathsForPage(pdfName, pageIndex)
            drawingView.loadDrawings(paths)
        }
    }

    private fun setupDrawingViewListener() {
        drawingView.onPathFinishedListener = { drawingPath ->
            coroutineScope.launch {
                val pathWithContext = drawingPath.copy(
                    pdfAssetName = currentPdfName,
                    pageIndex = currentPageIndex
                )
                dao.insertPath(pathWithContext)
            }
        }
    }

    private fun setupClickListeners() {
        fabToggleDrawing.setOnClickListener { togglePenMode() }

        fabEraser.setOnClickListener {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastEraserClickTime < 1000) { // 1 saniyeden az bir süre içinde tıklandıysa
                eraserClickCount++
            } else {
                eraserClickCount = 1
            }
            lastEraserClickTime = currentTime

            if (eraserClickCount == 3) {
                UIFeedbackHelper.provideFeedback(it)
                coroutineScope.launch {
                    dao.clearPage(currentPdfName, currentPageIndex)
                    drawingView.clearAllDrawings()
                }
                showSnackbar(context.getString(R.string.all_drawings_cleared_toast))
                eraserClickCount = 0 // Sayacı sıfırla
            } else {
                toggleEraserMode()
            }
        }

        fabClearAll.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            coroutineScope.launch {
                dao.clearPage(currentPdfName, currentPageIndex)
                drawingView.clearAllDrawings()
            }
            showSnackbar(context.getString(R.string.all_drawings_cleared_toast))
        }

        btnColorRed.setOnClickListener { handleColorSelection(it, R.color.red) }
        btnColorBlue.setOnClickListener { handleColorSelection(it, R.color.blue) }
        btnColorBlack.setOnClickListener { handleColorSelection(it, R.color.black) }

        btnSizeSmall.setOnClickListener { handleSizeSelection(it, BrushSize.SMALL) }
        btnSizeMedium.setOnClickListener { handleSizeSelection(it, BrushSize.MEDIUM) }
        btnSizeLarge.setOnClickListener { handleSizeSelection(it, BrushSize.LARGE) }
    }

    private fun handleColorSelection(selectedView: View, colorResId: Int) {
        val color = ContextCompat.getColor(context, colorResId)
        drawingView.setPenColor(color)
        updateColorSelection(selectedView)
    }

    private fun handleSizeSelection(selectedView: View, size: BrushSize) {
        val strokeWidth = when (size) {
            BrushSize.SMALL -> if (drawingView.brushType == BrushType.ERASER) 30f else 8f
            BrushSize.MEDIUM -> if (drawingView.brushType == BrushType.ERASER) 60f else 16f
            BrushSize.LARGE -> if (drawingView.brushType == BrushType.ERASER) 100f else 32f
        }
        drawingView.setBrushSize(strokeWidth)
        updateSizeSelection(selectedView)
    }

    private fun togglePenMode() {
        if (drawingView.brushType == BrushType.PEN) {
            deactivateDrawing()
        } else {
            drawingView.brushType = BrushType.PEN
            drawingView.setPenColor(Color.RED) // Default color
            handleSizeSelection(btnSizeMedium, BrushSize.MEDIUM)
            updateColorSelection(btnColorRed)
            showDrawingPanel(true)
            showSnackbar(context.getString(R.string.drawing_mode_pencil_toast))
        }
        updateButtonStates()
    }

    private fun toggleEraserMode() {
        if (drawingView.brushType == BrushType.ERASER) {
            deactivateDrawing()
        } else {
            drawingView.brushType = BrushType.ERASER
            drawingView.activateEraser()
            handleSizeSelection(btnSizeMedium, BrushSize.MEDIUM)
            showDrawingPanel(false)
            showSnackbar(context.getString(R.string.drawing_mode_eraser_toast))
        }
        updateButtonStates()
    }

    private fun deactivateDrawing() {
        drawingView.brushType = BrushType.NONE
        drawingOptionsPanel.visibility = View.GONE
        fabClearAll.visibility = View.GONE
        showSnackbar(context.getString(R.string.drawing_mode_off_toast))
        updateButtonStates()
    }

    private fun showDrawingPanel(showColor: Boolean) {
        drawingOptionsPanel.visibility = View.VISIBLE
        fabClearAll.visibility = View.VISIBLE
        colorOptions.visibility = if (showColor) View.VISIBLE else View.GONE
        sizeOptions.visibility = View.VISIBLE
    }

    private fun updateButtonStates() {
        // İsteğe bağlı olarak butonların seçili durumlarını güncelleyebilirsiniz.
    }

    private fun updateColorSelection(selectedView: View) {
        btnColorRed.isSelected = false
        btnColorBlue.isSelected = false
        btnColorBlack.isSelected = false
        (selectedView as ImageButton).isSelected = true
    }

    private fun updateSizeSelection(selectedView: View) {
        btnSizeSmall.isSelected = false
        btnSizeMedium.isSelected = false
        btnSizeLarge.isSelected = false
        (selectedView as ImageButton).isSelected = true
    }
}