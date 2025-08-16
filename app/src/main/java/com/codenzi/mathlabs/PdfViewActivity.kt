package com.codenzi.mathlabs

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.codenzi.mathlabs.database.DrawingDao
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

@AndroidEntryPoint
class PdfViewActivity : AppCompatActivity(), OnLoadCompleteListener, OnErrorListener, OnPageErrorListener, OnPageChangeListener {

    @Inject
    lateinit var okHttpClient: OkHttpClient
    @Inject
    lateinit var drawingDao: DrawingDao

    // --- View Variable Declarations ---
    private lateinit var pdfView: PDFView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabAiChat: FloatingActionButton
    private lateinit var fabReadingMode: FloatingActionButton
    private lateinit var eyeComfortOverlay: View
    private lateinit var pdfToolbar: MaterialToolbar
    private lateinit var notificationTextView: TextView
    private lateinit var drawingManager: DrawingManager
    private lateinit var pageCountCard: MaterialCardView
    private lateinit var pageCountText: TextView
    private lateinit var rootLayout: ViewGroup
    private lateinit var adContainerView: FrameLayout

    // *** DEĞİŞİKLİK 1: AdView nullable yapıldı ***
    private var adView: AdView? = null

    // --- State Variable Declarations ---
    private var pdfAssetName: String? = null
    private var pdfTitle: String? = null
    private var currentReadingModeLevel: Int = 0
    private var pdfBytes: ByteArray? = null
    var currentPage: Int = 0
        private set
    private var totalPages: Int = 0
    private val toastHandler = Handler(Looper.getMainLooper())
    private var toastRunnable: Runnable? = null

    val chatMessages = mutableListOf<ChatMessage>()
    val conversationHistory = mutableListOf<String>()

    companion object {
        const val EXTRA_PDF_ASSET_NAME = "pdf_asset_name"
        const val EXTRA_PDF_TITLE = "pdf_title"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private fun applyAppTheme() {
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf_PdfView)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme()
        super.onCreate(savedInstanceState)

        // Ekran görüntüsü almayı engelle (sadece premium olmayanlar için)
        lifecycleScope.launch {
            if (!UserRepository.isCurrentUserPremium()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContentView(R.layout.activity_pdf_view)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

        initializeViews()
        setupToolbar()
        handleWindowInsets()
        setupListeners()

        // *** DEĞİŞİKLİK 2: Eski reklam mantığı SİLİNDİ ve YENİSİ eklendi ***
        // Kullanıcı durumunu sürekli gözlemle ve arayüzü (reklamı) anında güncelle.
        observeUserStatusAndUpdateUI()

        pdfAssetName = intent.getStringExtra(EXTRA_PDF_ASSET_NAME)
        pdfTitle = intent.getStringExtra(EXTRA_PDF_TITLE) ?: getString(R.string.app_name)
        supportActionBar?.title = pdfTitle

        if (pdfAssetName != null) {
            displayPdfFromFirebaseWithOkHttp(pdfAssetName!!)
        } else {
            showAnimatedToast(getString(R.string.pdf_not_found))
            finish()
        }

        RewardedAdManager.loadAd(this)
    }

    /**
     * *** YENİ EKLENEN FONKSİYON ***
     * Kullanıcı premium durumunu canlı olarak dinler ve reklamı anında gösterir/gizler.
     * Tüm reklam mantığının tek merkezidir.
     */
    private fun observeUserStatusAndUpdateUI() {
        lifecycleScope.launch {
            UserRepository.userDataState.collectLatest { userData ->
                val isPremium = userData?.isPremium ?: false
                if (isPremium) {
                    // KULLANICI PREMIUM İSE
                    adContainerView.visibility = View.GONE
                    adView?.destroy()
                    adView = null
                } else {
                    // KULLANICI PREMIUM DEĞİLSE
                    adContainerView.visibility = View.VISIBLE
                    loadBanner()
                }
            }
        }
    }

    private fun loadBanner() {
        if (adView != null) return // Reklam zaten yüklüyse tekrar yükleme

        MobileAds.initialize(this) {}
        adView = AdView(this)
        adView?.adUnitId = getString(R.string.admob_banner_unit_id)
        adContainerView.removeAllViews()
        adContainerView.addView(adView)
        adView?.setAdSize(adSize)
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    // ... DİĞER TÜM FONKSİYONLARINIZ DEĞİŞİKLİK OLMADAN AYNI KALIR ...
    // ... (initializeViews, setupToolbar, handleDownloadClick, etc.)

    private val adSize: AdSize
        get() {
            val adWidthPixels = resources.displayMetrics.widthPixels.toFloat()
            val density = resources.displayMetrics.density
            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
        }

    private fun handleWindowInsets() {
        rootLayout = findViewById(R.id.root_layout_pdf_view)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            pdfToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBarInsets.top
            }

            adContainerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBarInsets.bottom
            }
            insets
        }
    }

    private fun showAiChatDialog() {
        if (pdfBytes == null) {
            showAnimatedToast(getString(R.string.pdf_text_not_ready))
            return
        }

        lifecycleScope.launch {
            if (AiQueryManager.canPerformQuery()) {
                val chatDialog = ChatAiDialogFragment()
                chatDialog.show(supportFragmentManager, "ChatAiDialog")
            } else {
                if (chatMessages.isEmpty()) {
                    showWatchAdDialog()
                } else {
                    val chatDialog = ChatAiDialogFragment()
                    chatDialog.show(supportFragmentManager, "ChatAiDialog")
                }
            }
        }
    }

    suspend fun extractTextForAI(): String {
        val bytes = this.pdfBytes ?: return "".also {
            Log.e("PdfTextExtraction", "AI text extraction failed: PDF bytes are null.")
        }

        return withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(applicationContext)
                PDDocument.load(bytes).use { document ->
                    val stripper = PDFTextStripper()
                    val boxCurrentPage = currentPage + 1
                    stripper.startPage = boxCurrentPage
                    stripper.endPage = boxCurrentPage
                    stripper.getText(document)
                }
            } catch (e: Exception) {
                Log.e("PdfTextExtraction", "Error extracting text for AI", e)
                ""
            }
        }
    }

    fun showWatchAdDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ai_quota_exceeded_title))
            .setMessage(getString(R.string.watch_ad_for_reward_prompt))
            .setPositiveButton(getString(R.string.watch_ad_button)) { dialog, _ ->
                RewardedAdManager.showAd(
                    activity = this,
                    onRewardEarned = {
                        lifecycleScope.launch {
                            val currentUserData = UserRepository.getUserDataOnce()
                            currentUserData?.let {
                                val newRewardedCount = it.rewardedQueries + 3
                                UserRepository.updateUserField("rewardedQueries", newRewardedCount)
                                showAnimatedToast(getString(R.string.reward_granted_toast, 3))
                            }
                        }
                    },
                    onAdFailedToShow = {
                        showAnimatedToast(getString(R.string.ad_not_ready_toast))
                    }
                )
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun setupToolbar() {
        setSupportActionBar(pdfToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pdf_view_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_download_pdf -> {
                handleDownloadClick()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeViews() {
        pdfToolbar = findViewById(R.id.pdfToolbar)
        pdfView = findViewById(R.id.pdfView)
        progressBar = findViewById(R.id.progressBarPdf)
        fabAiChat = findViewById(R.id.fab_ai_chat)
        fabReadingMode = findViewById(R.id.fab_reading_mode)
        eyeComfortOverlay = findViewById(R.id.eyeComfortOverlay)
        notificationTextView = findViewById(R.id.notificationTextView)
        pageCountCard = findViewById(R.id.pageCountCard)
        pageCountText = findViewById(R.id.pageCountText)
        adContainerView = findViewById(R.id.ad_view_container_pdf)

        // *** DEĞİŞİKLİK 3: Reklam alanı başlangıçta gizlenir ***
        // Bu, premium durumu kontrol edilirken reklamın anlık görünmesini (flicker) engeller.
        adContainerView.visibility = View.GONE

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
            showSnackbar = { message -> showAnimatedToast(message) },
            dao = drawingDao,
            coroutineScope = lifecycleScope
        )
    }

    private fun displayPdfFromFirebaseWithOkHttp(storagePath: String) {
        progressBar.visibility = View.VISIBLE
        val storageRef = Firebase.storage.reference.child(storagePath)
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(uri.toString()).build()
                    val response = okhttp3.OkHttpClient().newCall(request).execute()
                    if (response.isSuccessful) {
                        val downloadedBytes = response.body?.bytes() ?: throw IOException("Response body is null")
                        this@PdfViewActivity.pdfBytes = downloadedBytes

                        withContext(Dispatchers.Main) {
                            pdfView.fromBytes(downloadedBytes)
                                .defaultPage(0)
                                .enableSwipe(true)
                                .swipeHorizontal(true)
                                .pageFling(true)
                                .pageSnap(true)
                                .onLoad(this@PdfViewActivity)
                                .onError(this@PdfViewActivity)
                                .onPageError(this@PdfViewActivity)
                                .onPageChange(this@PdfViewActivity)
                                .load()
                        }
                    } else {
                        throw IOException("Unexpected server response: $response")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Log.e("OkHttpError", "PDF download error: $storagePath", e)
                        showAnimatedToast(getString(R.string.pdf_load_failed_with_error, e.localizedMessage))
                        finish()
                    }
                }
            }
        }.addOnFailureListener { exception ->
            progressBar.visibility = View.GONE
            Log.e("FirebaseStorage", "Could not get download URL: $storagePath", exception)
            showAnimatedToast(getString(R.string.pdf_load_failed_with_error, exception.localizedMessage))
            finish()
        }
    }

    private fun handleDownloadClick() {
        UIFeedbackHelper.provideFeedback(pdfToolbar)
        lifecycleScope.launch {
            if (UserRepository.isCurrentUserPremium()) {
                pdfBytes?.let {
                    downloadPdf(it)
                } ?: run {
                    showAnimatedToast(getString(R.string.download_failed))
                }
            } else {
                Toast.makeText(this@PdfViewActivity, getString(R.string.premium_feature_for_download), Toast.LENGTH_LONG).show()
                val intent = Intent(this@PdfViewActivity, SettingsActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun downloadPdf(pdfBytes: ByteArray) {
        val fileName = pdfTitle?.replace(Regex("[^a-zA-Z0-9.-]"), "_") ?: "MathLabs_File"
        val safeFileName = "$fileName.pdf"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val existingFile = File(downloadsDir, safeFileName)

        if (existingFile.exists()) {
            showAnimatedToast(getString(R.string.pdf_already_downloaded))
            return
        }

        showAnimatedToast(getString(R.string.downloading_pdf))

        lifecycleScope.launch(Dispatchers.IO) {
            val outputStream: OutputStream?
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    outputStream = uri?.let { resolver.openOutputStream(it) }
                } else {
                    downloadsDir.mkdirs()
                    val file = File(downloadsDir, safeFileName)
                    outputStream = FileOutputStream(file)
                }

                outputStream?.use { it.write(pdfBytes) }

                withContext(Dispatchers.Main) {
                    showAnimatedToast(getString(R.string.download_successful))
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    showAnimatedToast(getString(R.string.download_failed))
                }
                Log.e("DownloadPDF", "Error saving PDF", e)
            }
        }
    }

    private fun setupListeners() {
        fabAiChat.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            showAiChatDialog()
        }
        fabReadingMode.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            toggleReadingMode()
        }
        pageCountCard.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            if (totalPages > 0) {
                showGoToPageDialog()
            }
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

    private fun showGoToPageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_go_to_page, null)
        val editTextPageNumber = dialogView.findViewById<EditText>(R.id.editTextPageNumber)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.go_to_page_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.go_to_page_go)) { d, _ ->
                val pageStr = editTextPageNumber.text.toString()
                if (pageStr.isNotEmpty()) {
                    try {
                        val pageNum = pageStr.toInt()
                        if (pageNum in 1..totalPages) {
                            pdfView.jumpTo(pageNum - 1, true)
                            d.dismiss()
                        } else {
                            showAnimatedToast(getString(R.string.go_to_page_invalid_number, totalPages))
                        }
                    } catch (_: NumberFormatException) {
                        showAnimatedToast(getString(R.string.go_to_page_enter_number))
                    }
                }
            }
            .setNegativeButton(getString(R.string.go_to_page_cancel), null)
            .create()

        dialog.show()
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
        toastHandler.postDelayed(toastRunnable!!, 3000)
    }

    override fun loadComplete(nbPages: Int) {
        this.totalPages = nbPages
        progressBar.visibility = View.GONE
        showAnimatedToast(getString(R.string.pdf_loaded_toast, nbPages))
        val fadeInAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.fade_in)
        fabAiChat.startAnimation(fadeInAnimation)
        fabAiChat.visibility = View.VISIBLE
        pageCountCard.startAnimation(fadeInAnimation)
        pageCountCard.visibility = View.VISIBLE

        pdfAssetName?.let {
            drawingManager.loadDrawingsForPage(it, 0)
        }
    }

    override fun onError(t: Throwable?) {
        progressBar.visibility = View.GONE
        showAnimatedToast(getString(R.string.error_toast, t?.localizedMessage ?: "Unknown PDF error"))
        Log.e("PdfView_onError", "PDF Load Error", t)
        finish()
    }

    override fun onPageError(page: Int, t: Throwable?) {
        showAnimatedToast(getString(R.string.page_load_error_toast, page, t?.localizedMessage ?: "Unknown page error"))
        Log.e("PdfView_onPageError", "Page Load Error: $page", t)
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        this.currentPage = page
        this.totalPages = pageCount
        pageCountText.text = getString(R.string.page_count_format, page + 1, pageCount)

        pdfAssetName?.let {
            drawingManager.loadDrawingsForPage(it, page)
        }
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}