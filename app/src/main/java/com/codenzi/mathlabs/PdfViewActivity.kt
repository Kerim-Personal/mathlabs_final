package com.codenzi.mathlabs

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.Comparator
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PdfViewActivity : AppCompatActivity(), OnLoadCompleteListener, OnErrorListener, OnPageErrorListener {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    // Değişkenler...
    private lateinit var pdfView: PDFView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabAiChat: FloatingActionButton
    private lateinit var fabReadingMode: FloatingActionButton
    private lateinit var eyeComfortOverlay: View
    private lateinit var pdfToolbar: MaterialToolbar
    private lateinit var notificationTextView: TextView
    private lateinit var drawingManager: DrawingManager
    private var pdfAssetName: String? = null
    private var currentReadingModeLevel: Int = 0
    private var fullPdfText: String? = null
    private val toastHandler = Handler(Looper.getMainLooper())
    private var toastRunnable: Runnable? = null
    private val conversationHistory = mutableListOf<String>()

    private val generativeModel by lazy {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) {
            Log.e("GeminiAI", "API Anahtarı BuildConfig içerisinde bulunamadı veya geçersiz.")
            showAnimatedToast(getString(R.string.ai_assistant_api_key_not_configured))
        }
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    }

    companion object {
        const val EXTRA_PDF_ASSET_NAME = "pdf_asset_name"
        const val EXTRA_PDF_TITLE = "pdf_title"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    /**
     * Bu aktivite için doğru temayı uygular.
     * Önce genel aydınlık/karanlık mod ayarını, ardından bu aktiviteye özel stili yükler.
     * Bu, çökme sorununu gideren ana düzeltmedir.
     */
    private fun applyAppTheme() {
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf_PdfView)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme() // TEMAYI ÖNCE UYGULA
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_pdf_view)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        setupToolbar()
        initializeViews()
        setupListeners()
        pdfAssetName = intent.getStringExtra(EXTRA_PDF_ASSET_NAME)
        val pdfTitle = intent.getStringExtra(EXTRA_PDF_TITLE) ?: getString(R.string.app_name)
        supportActionBar?.title = pdfTitle
        if (pdfAssetName != null) {
            displayPdfFromFirebaseWithOkHttp(pdfAssetName!!)
        } else {
            showAnimatedToast(getString(R.string.pdf_not_found))
            finish()
        }
    }

    private fun displayPdfFromFirebaseWithOkHttp(storagePath: String) {
        progressBar.visibility = View.VISIBLE
        val storageRef = Firebase.storage.reference.child(storagePath)
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(uri.toString()).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body ?: throw IOException("Response body is null")
                        val pdfBytes = responseBody.bytes()
                        extractTextFromPdfStream(pdfBytes.inputStream())
                        withContext(Dispatchers.Main) {
                            pdfView.fromBytes(pdfBytes)
                                .defaultPage(0)
                                .enableSwipe(true)
                                .swipeHorizontal(false)
                                .onLoad(this@PdfViewActivity)
                                .onError(this@PdfViewActivity)
                                .onPageError(this@PdfViewActivity)
                                .load()
                        }
                    } else {
                        throw IOException("Sunucudan beklenmeyen kod: $response")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Log.e("OkHttpError", "PDF indirme hatası: $storagePath", e)
                        showAnimatedToast(getString(R.string.pdf_load_failed_with_error, e.localizedMessage))
                        finish()
                    }
                }
            }
        }.addOnFailureListener { exception ->
            progressBar.visibility = View.GONE
            Log.e("FirebaseStorage", "Download URL alınamadı: $storagePath", exception)
            showAnimatedToast(getString(R.string.pdf_load_failed_with_error, exception.localizedMessage))
            finish()
        }
    }

    private fun extractTextFromPdfStream(inputStream: InputStream) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(applicationContext)
                inputStream.use { stream ->
                    PDDocument.load(stream).use { document ->
                        val stripper = PDFTextStripper()
                        val text = stripper.getText(document)
                        fullPdfText = text
                        Log.d("PdfTextExtraction", "Metin başarıyla çıkarıldı.")
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfTextExtraction", "PDF metni çıkarılırken hata oluştu.", e)
                withContext(Dispatchers.Main) {
                    showAnimatedToast(getString(R.string.pdf_text_extraction_failed, e.localizedMessage))
                }
            }
        }
    }

    private fun findRelevantChunks(question: String, context: String, maxChunks: Int = 3): String {
        data class ScoredChunk(val text: String, val score: Int)
        val questionLower = question.lowercase(Locale.getDefault())

        val generalCommandRoots = listOf("anlat", "özet", "içerik", "konu", "sayfa", "hakkında", "tell", "summarize", "about", "content", "page")

        val isGeneralQuery = generalCommandRoots.any { root -> questionLower.contains(root) }

        if (isGeneralQuery) {
            Log.d("ChunkFinder", "Genel komut algılandı. PDF'in başı döndürülüyor.")
            return context.take(2000)
        }

        val specificKeywords = questionLower.split(" ").filter { it.length > 3 }.toSet()

        if (specificKeywords.isEmpty() || context.isBlank()) {
            return ""
        }

        Log.d("ChunkFinder", "Spesifik arama yapılıyor...")
        val chunkSize = 500
        val stepSize = 250
        val scoredChunks = mutableListOf<ScoredChunk>()

        var i = 0
        while (i < context.length) {
            val end = (i + chunkSize).coerceAtMost(context.length)
            val chunkText = context.substring(i, end)

            val foundKeywords = mutableSetOf<String>()
            for (keyword in specificKeywords) {
                if (chunkText.lowercase(Locale.getDefault()).contains(keyword)) {
                    foundKeywords.add(keyword)
                }
            }
            val score = foundKeywords.size
            if (score > 0) {
                scoredChunks.add(ScoredChunk(chunkText, score))
            }
            i += stepSize
        }

        if (scoredChunks.isEmpty()) {
            return ""
        }

        val descendingComparator = object : Comparator<ScoredChunk> {
            override fun compare(c1: ScoredChunk, c2: ScoredChunk): Int {
                return c2.score.compareTo(c1.score)
            }
        }
        scoredChunks.sortWith(descendingComparator)

        return scoredChunks.take(maxChunks).joinToString("\n\n...\n\n") { it.text }
    }


    private fun showAiChatDialog() {
        if (fullPdfText == null) {
            showAnimatedToast(getString(R.string.pdf_text_not_ready))
            return
        }
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_chat, null)
        builder.setView(dialogView)
        val editTextQuestion = dialogView.findViewById<EditText>(R.id.editTextQuestion)
        val buttonSend = dialogView.findViewById<Button>(R.id.buttonSend)
        val textViewAnswer = dialogView.findViewById<TextView>(R.id.textViewAnswer)
        val progressChat = dialogView.findViewById<ProgressBar>(R.id.progressChat)
        val dialog = builder.create()
        buttonSend.setOnClickListener {
            val question = editTextQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                textViewAnswer.text = ""
                textViewAnswer.visibility = View.GONE
                progressChat.visibility = View.VISIBLE
                buttonSend.isEnabled = false
                editTextQuestion.isEnabled = false
                conversationHistory.add("Kullanıcı: $question")
                lifecycleScope.launch {
                    var aiResponse = ""
                    try {
                        val relevantContext = findRelevantChunks(question, fullPdfText!!)

                        if (relevantContext.isBlank()) {
                            aiResponse = getString(R.string.ai_info_not_found)
                        } else {
                            val historyText = conversationHistory.joinToString("\n")
                            val currentLanguage = SharedPreferencesManager.getLanguage(this@PdfViewActivity) ?: "tr"
                            val prompt: String

                            if (currentLanguage == "en") {
                                prompt = """
                                Conversation History:
                                $historyText

                                --- Text Excerpts from the PDF ---
                                $relevantContext
                                ------------------------------------

                                Answer the user's LAST QUESTION based on the conversation history and the text excerpts.
                                Respond in English and use Markdown format.
                                If the answer is not in the text, say 'This information is not available in the PDF.'

                                USER'S LAST QUESTION: "$question"
                                """.trimIndent()
                            } else {
                                prompt = """
                                Önceki Konuşma Geçmişi:
                                $historyText

                                --- PDF'ten Alınan Metin Parçaları ---
                                $relevantContext
                                -----------------------

                                Kullanıcının SON SORUSUNU önceki konuşmayı ve metin parçalarını dikkate alarak yanıtla: "$question"
                                Cevabınızı Markdown formatında ve Türkçe olarak verin.
                                Metinde cevap yoksa, 'Bu konu hakkında bilgi PDF'te bulunmuyor.' deyin.
                                """.trimIndent()
                            }

                            val responseFlow = generativeModel.generateContentStream(prompt)
                                .catch { e: Throwable ->
                                    withContext(Dispatchers.Main) {
                                        textViewAnswer.text = getString(R.string.ai_chat_error_with_details, e.localizedMessage ?: "Unknown error")
                                        textViewAnswer.visibility = View.VISIBLE
                                    }
                                }
                            val stringBuilder = StringBuilder()
                            responseFlow.collect { chunk ->
                                stringBuilder.append(chunk.text)
                            }
                            aiResponse = stringBuilder.toString()
                        }

                        withContext(Dispatchers.Main) {
                            textViewAnswer.text = aiResponse
                            textViewAnswer.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        aiResponse = getString(R.string.ai_chat_error_with_details, e.localizedMessage ?: "Unknown error")
                        withContext(Dispatchers.Main) {
                            textViewAnswer.text = aiResponse
                            Log.e("GeminiError", "AI Hatası: ", e)
                            textViewAnswer.visibility = View.VISIBLE
                        }
                    } finally {
                        if (aiResponse.isNotBlank()) {
                            conversationHistory.add("Asistan: $aiResponse")
                        }
                        while (conversationHistory.size > 6) {
                            conversationHistory.removeAt(0)
                        }
                        withContext(Dispatchers.Main) {
                            progressChat.visibility = View.GONE
                            buttonSend.isEnabled = true
                            editTextQuestion.isEnabled = true
                            editTextQuestion.text?.clear()
                        }
                    }
                }
            } else {
                showAnimatedToast(getString(R.string.please_enter_a_question))
            }
        }
        dialog.show()
    }

    private fun setupToolbar() {
        pdfToolbar = findViewById(R.id.pdfToolbar)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(pdfToolbar) { view, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBarInsets.top
            }
            insets
        }
        setSupportActionBar(pdfToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initializeViews() {
        pdfView = findViewById(R.id.pdfView)
        progressBar = findViewById(R.id.progressBarPdf)
        fabAiChat = findViewById(R.id.fab_ai_chat)
        fabReadingMode = findViewById(R.id.fab_reading_mode)
        eyeComfortOverlay = findViewById(R.id.eyeComfortOverlay)
        notificationTextView = findViewById(R.id.notificationTextView)
        drawingManager = DrawingManager(
            context = this,
            drawingView = findViewById(R.id.drawingView),
            fabToggleDrawing = findViewById(R.id.fab_toggle_drawing),
            fabEraser = findViewById(R.id.fab_eraser),
            fabClearAll = findViewById(R.id.fab_clear_all),
            drawingOptionsPanel = findViewById(R.id.drawingOptionsPanel),
            colorOptions = findViewById(R.id.colorOptions),
            sizeOptions = findViewById(R.id.sizeOptions),
            btnColorRed = findViewById(R.id.btn_color_red),
            btnColorBlue = findViewById(R.id.btn_color_blue),
            btnColorBlack = findViewById(R.id.btn_color_black),
            btnSizeSmall = findViewById(R.id.btn_size_small),
            btnSizeMedium = findViewById(R.id.btn_size_medium),
            btnSizeLarge = findViewById(R.id.btn_size_large),
            showSnackbar = { message -> showAnimatedToast(message) }
        )
    }

    private fun setupListeners() {
        fabAiChat.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty()) {
                showAnimatedToast(getString(R.string.ai_assistant_api_key_not_configured))
                return@setOnClickListener
            }
            showAiChatDialog()
        }
        fabReadingMode.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            toggleReadingMode()
        }
    }

    private fun toggleReadingMode() {
        currentReadingModeLevel = (currentReadingModeLevel + 1) % 4
        applyReadingModeFilter(currentReadingModeLevel)
    }

    private fun applyReadingModeFilter(level: Int) {
        when (level) {
            0 -> {
                eyeComfortOverlay.visibility = View.GONE
                showAnimatedToast(getString(R.string.reading_mode_off_toast))
            }
            1 -> {
                eyeComfortOverlay.visibility = View.VISIBLE
                eyeComfortOverlay.setBackgroundColor("#33FDF6E3".toColorInt())
                showAnimatedToast(getString(R.string.reading_mode_low_toast))
            }
            2 -> {
                eyeComfortOverlay.visibility = View.VISIBLE
                eyeComfortOverlay.setBackgroundColor("#66FDF6E3".toColorInt())
                showAnimatedToast(getString(R.string.reading_mode_medium_toast))
            }
            3 -> {
                eyeComfortOverlay.visibility = View.VISIBLE
                eyeComfortOverlay.setBackgroundColor("#99FDF6E3".toColorInt())
                showAnimatedToast(getString(R.string.reading_mode_high_toast))
            }
        }
    }

    override fun loadComplete(nbPages: Int) {
        progressBar.visibility = View.GONE
        showAnimatedToast(getString(R.string.pdf_loaded_toast, nbPages))
        val fadeInAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.fade_in)
        fabAiChat.startAnimation(fadeInAnimation)
        fabAiChat.visibility = View.VISIBLE
    }

    override fun onError(t: Throwable?) {
        progressBar.visibility = View.GONE
        showAnimatedToast(getString(R.string.error_toast, t?.localizedMessage ?: "Bilinmeyen PDF hatası"))
        Log.e("PdfView_onError", "PDF Yükleme Hatası", t)
        finish()
    }

    override fun onPageError(page: Int, t: Throwable?) {
        showAnimatedToast(getString(R.string.page_load_error_toast, page, t?.localizedMessage ?: "Bilinmeyen sayfa hatası"))
        Log.e("PdfView_onPageError", "Sayfa Yükleme Hatası: $page", t)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun showAnimatedToast(message: String) {
        toastRunnable?.let { toastHandler.removeCallbacks(it) }
        notificationTextView.text = message
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        notificationTextView.startAnimation(fadeIn)
        notificationTextView.visibility = View.VISIBLE
        toastRunnable = Runnable {
            val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)
            fadeOut.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    notificationTextView.visibility = View.GONE
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
            notificationTextView.startAnimation(fadeOut)
        }
        toastHandler.postDelayed(toastRunnable!!, 2000)
    }
}