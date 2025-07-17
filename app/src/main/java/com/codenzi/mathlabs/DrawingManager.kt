package com.codenzi.mathlabs

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * PdfViewActivity üzerindeki çizimle ilgili tüm UI ve mantık işlemlerini yöneten sınıf.
 * Bu sınıf, çizim araçlarını, renk ve boyut seçimlerini ve ilgili butonların durumlarını kontrol eder.
 */
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
    private val showSnackbar: (String) -> Unit // Snackbar göstermek için bir lambda fonksiyonu
) {

    private var isDrawingActive: Boolean = false
    private var currentPenColor: Int = Color.RED
    private var currentPenSize: Float = 10f
    private var currentEraserSize: Float = 50f

    init {
        setupInitialState()
        setupClickListeners()
    }

    private fun setupInitialState() {
        currentPenColor = SharedPreferencesManager.getPenColor(context)
        currentPenSize = getPenSizeFromPreferences()
        currentEraserSize = getEraserSizeFromPreferences()

        drawingView.setBrushColor(currentPenColor)
        drawingView.drawingMode = DrawingView.DrawingMode.NONE
        setDrawingButtonState()
    }

    private fun setupClickListeners() {
        fabToggleDrawing.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            togglePenMode()
        }

        fabEraser.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            toggleEraserMode()
        }

        fabClearAll.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            drawingView.clearDrawing()
            showSnackbar(context.getString(R.string.all_drawings_cleared_toast))
        }

        btnColorRed.setOnClickListener { handleColorSelection(it, R.color.red) }
        btnColorBlue.setOnClickListener { handleColorSelection(it, R.color.blue) }
        btnColorBlack.setOnClickListener { handleColorSelection(it, R.color.black) }

        btnSizeSmall.setOnClickListener { handleSizeSelection(it, 5f, 25f, DrawingModeType.SMALL) }
        btnSizeMedium.setOnClickListener { handleSizeSelection(it, 10f, 50f, DrawingModeType.MEDIUM) }
        btnSizeLarge.setOnClickListener { handleSizeSelection(it, 20f, 75f, DrawingModeType.LARGE) }
    }

    private fun handleColorSelection(selectedView: View, colorResId: Int) {
        UIFeedbackHelper.provideFeedback(selectedView)
        currentPenColor = ContextCompat.getColor(context, colorResId)
        drawingView.setBrushColor(currentPenColor)
        SharedPreferencesManager.savePenColor(context, currentPenColor)
        updateColorSelection(selectedView)
    }

    private fun handleSizeSelection(selectedView: View, penSize: Float, eraserSize: Float, type: DrawingModeType) {
        UIFeedbackHelper.provideFeedback(selectedView)
        if (drawingView.drawingMode == DrawingView.DrawingMode.PEN) {
            currentPenSize = penSize
            drawingView.setBrushSize(currentPenSize)
            SharedPreferencesManager.savePenSizeType(context, type.ordinal)
        } else if (drawingView.drawingMode == DrawingView.DrawingMode.ERASER) {
            currentEraserSize = eraserSize
            drawingView.setBrushSize(currentEraserSize)
            SharedPreferencesManager.saveEraserSizeType(context, type.ordinal)
        }
        updateSizeSelection(selectedView)
    }

    private fun togglePenMode() {
        if (isDrawingActive && drawingView.drawingMode == DrawingView.DrawingMode.PEN) {
            deactivateDrawing()
        } else {
            activatePenMode()
        }
        setDrawingButtonState()
    }

    private fun toggleEraserMode() {
        if (isDrawingActive && drawingView.drawingMode == DrawingView.DrawingMode.ERASER) {
            deactivateDrawing()
        } else {
            activateEraserMode()
        }
        setDrawingButtonState()
    }

    private fun activatePenMode() {
        isDrawingActive = true
        drawingView.drawingMode = DrawingView.DrawingMode.PEN
        drawingView.setBrushColor(currentPenColor)
        drawingView.setBrushSize(getPenSizeFromPreferences())

        showDrawingPanel(showColorOptions = true)
        updatePenStateFromPreferences()
        showSnackbar(context.getString(R.string.drawing_mode_pencil_toast))
    }

    private fun activateEraserMode() {
        isDrawingActive = true
        drawingView.drawingMode = DrawingView.DrawingMode.ERASER
        drawingView.setBrushSize(getEraserSizeFromPreferences())

        showDrawingPanel(showColorOptions = false)
        updateEraserStateFromPreferences()
        showSnackbar(context.getString(R.string.drawing_mode_eraser_toast))
    }

    private fun deactivateDrawing() {
        isDrawingActive = false
        drawingView.drawingMode = DrawingView.DrawingMode.NONE
        drawingOptionsPanel.visibility = View.GONE
        fabClearAll.visibility = View.GONE
        showSnackbar(context.getString(R.string.drawing_mode_off_toast))
    }

    private fun showDrawingPanel(showColorOptions: Boolean) {
        drawingOptionsPanel.visibility = View.VISIBLE
        fabClearAll.visibility = View.VISIBLE
        colorOptions.visibility = if (showColorOptions) View.VISIBLE else View.GONE
        sizeOptions.visibility = View.VISIBLE
    }

    private fun updatePenStateFromPreferences() {
        when (SharedPreferencesManager.getPenSizeType(context)) {
            DrawingModeType.SMALL.ordinal -> updateSizeSelection(btnSizeSmall)
            DrawingModeType.MEDIUM.ordinal -> updateSizeSelection(btnSizeMedium)
            DrawingModeType.LARGE.ordinal -> updateSizeSelection(btnSizeLarge)
        }
        when (SharedPreferencesManager.getPenColor(context)) {
            ContextCompat.getColor(context, R.color.red) -> updateColorSelection(btnColorRed)
            ContextCompat.getColor(context, R.color.blue) -> updateColorSelection(btnColorBlue)
            ContextCompat.getColor(context, R.color.black) -> updateColorSelection(btnColorBlack)
        }
    }

    private fun updateEraserStateFromPreferences() {
        when (SharedPreferencesManager.getEraserSizeType(context)) {
            DrawingModeType.SMALL.ordinal -> updateSizeSelection(btnSizeSmall)
            DrawingModeType.MEDIUM.ordinal -> updateSizeSelection(btnSizeMedium)
            DrawingModeType.LARGE.ordinal -> updateSizeSelection(btnSizeLarge)
        }
    }

    private fun setDrawingButtonState() {
        val activeColor = Color.BLACK
        val inactiveColor = Color.WHITE

        fabToggleDrawing.imageTintList = if (isDrawingActive && drawingView.drawingMode == DrawingView.DrawingMode.PEN) ColorStateList.valueOf(activeColor) else ColorStateList.valueOf(inactiveColor)
        fabEraser.imageTintList = if (isDrawingActive && drawingView.drawingMode == DrawingView.DrawingMode.ERASER) ColorStateList.valueOf(activeColor) else ColorStateList.valueOf(inactiveColor)
    }

    private fun updateColorSelection(selectedView: View) {
        btnColorRed.isSelected = false
        btnColorBlue.isSelected = false
        btnColorBlack.isSelected = false
        selectedView.isSelected = true
    }

    private fun updateSizeSelection(selectedView: View) {
        btnSizeSmall.isSelected = false
        btnSizeMedium.isSelected = false
        btnSizeLarge.isSelected = false
        selectedView.isSelected = true
    }

    private fun getPenSizeFromPreferences(): Float = when (SharedPreferencesManager.getPenSizeType(context)) {
        DrawingModeType.SMALL.ordinal -> 5f
        DrawingModeType.MEDIUM.ordinal -> 10f
        DrawingModeType.LARGE.ordinal -> 20f
        else -> 10f
    }

    private fun getEraserSizeFromPreferences(): Float = when (SharedPreferencesManager.getEraserSizeType(context)) {
        DrawingModeType.SMALL.ordinal -> 25f
        DrawingModeType.MEDIUM.ordinal -> 50f
        DrawingModeType.LARGE.ordinal -> 75f
        else -> 50f
    }
}

/**
 * Çizim modundaki fırça/silgi boyutlarını temsil eder.
 */
enum class DrawingModeType {
    SMALL, MEDIUM, LARGE
}