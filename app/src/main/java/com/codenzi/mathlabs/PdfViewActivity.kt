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
import javax.inject.Inject

@AndroidEntryPoint
class PdfViewActivity : AppCompatActivity(), OnLoadCompleteListener, OnErrorListener, OnPageErrorListener, OnPageChangeListener {

    @Inject
    lateinit var okHttpClient: OkHttpClient
    @Inject
    lateinit var drawingDao: DrawingDao

    // --- View Değişkenleri ---
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
    private var adView: AdView? = null

    // --- Durum Değişkenleri ---
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
        setContentView(R.layout.activity_pdf_view)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

        initializeViews()
        setupToolbar()
        handleWindowInsets()
        setupListeners()

        // Kullanıcı durumunu ve premium özelliklerini Firestore'dan canlı olarak dinle
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
     * Kullanıcı durumunu (premium, vb.) canlı olarak dinler ve arayüzü
     * (reklamlar, ekran görüntüsü izni gibi) anında günceller.
     */
    private fun observeUserStatusAndUpdateUI() {
        lifecycleScope.launch {
            UserRepository.userDataState.collectLatest { userData ->
                val isPremium = userData?.isPremium ?: false

                // 1. Reklamları yönet
                if (isPremium) {
                    adContainerView.visibility = View.GONE
                    adView?.destroy()
                    adView = null
                } else {
                    adContainerView.visibility = View.VISIBLE
                    loadBanner()
                }

                // 2. Ekran görüntüsü iznini yönet
                if (isPremium) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    private fun loadBanner() {
        if (adView != null || !isAdLoadable()) return
        MobileAds.initialize(this) {}
        adView = AdView(this)
        adView?.adUnitId = getString(R.string.admob_banner_unit_id)
        adContainerView.removeAllViews()
        adContainerView.addView(adView)
        adView?.setAdSize(adSize)
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    private fun isAdLoadable(): Boolean {
        return !isFinishing && !isDestroyed
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

    private fun handleDownloadClick() {
        UIFeedbackHelper.provideFeedback(pdfToolbar)
        lifecycleScope.launch {
            val slotResult = UserRepository.requestPdfDownloadSlot()
            if (slotResult.isSuccess && slotResult.getOrNull() == true) {
                pdfBytes?.let {
                    downloadPdf(it)
                } ?: run {
                    showAnimatedToast(getString(R.string.download_failed))
                }
            } else {
                val message = if (UserRepository.isCurrentUserPremium()) {
                    getString(R.string.premium_pdf_limit_reached)
                } else {
                    getString(R.string.premium_feature_for_download)
                }
                Toast.makeText(this@PdfViewActivity, message, Toast.LENGTH_LONG).show()
                val intent = Intent(this@PdfViewActivity, SettingsActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun downloadPdf(pdfBytes: ByteArray) {
        val fileName = pdfTitle?.replace(Regex("[^a-zA-Z0-9.-]"), "_") ?: "MathLabs_File"
        val safeFileName = "$fileName.pdf"
        val pdfId = pdfAssetName ?: safeFileName

        // Daha önce indirildiyse (yerel kayıt), tekrar indirmeye izin verme
        if (SharedPreferencesManager.hasPdfDownloaded(this, pdfId)) {
            showAnimatedToast(getString(R.string.pdf_already_downloaded))
            return
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        // Android 10+ için MediaStore üzerinden, altı için doğrudan dosya sistemi üzerinden varlık kontrolü
        val alreadyExists = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fileExistsInDownloadsApi29Plus(safeFileName)
        } else {
            val existingFile = File(downloadsDir, safeFileName)
            existingFile.exists()
        }

        if (alreadyExists) {
            // Dosya diskte varsa da tekrar indirmeyi engelle
            SharedPreferencesManager.markPdfDownloaded(this, pdfId)
            showAnimatedToast(getString(R.string.pdf_already_downloaded))
            return
        }

        showAnimatedToast(getString(R.string.downloading_pdf))

        // Sayaç artırımını doğru kullanıcıya yazmak için UID'i baştan yakala
        val uidSnapshot = UserRepository.currentUid()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var wroteSuccessfully = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    val outputStream = uri?.let { resolver.openOutputStream(it) }
                    outputStream?.use { os ->
                        os.write(pdfBytes)
                        wroteSuccessfully = true
                    }
                } else {
                    downloadsDir.mkdirs()
                    val file = File(downloadsDir, safeFileName)
                    FileOutputStream(file).use { os ->
                        os.write(pdfBytes)
                        wroteSuccessfully = true
                    }
                }

                if (wroteSuccessfully) {
                    SharedPreferencesManager.markPdfDownloaded(this@PdfViewActivity, pdfId)
                    withContext(Dispatchers.Main) {
                        showAnimatedToast(getString(R.string.download_successful))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showAnimatedToast(getString(R.string.download_failed))
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    showAnimatedToast(getString(R.string.download_failed))
                }
                Log.e("DownloadPDF", "Error saving PDF", e)
            }
        }
    }

    // Android 10+ (API 29+) için Downloads koleksiyonunda aynı isimli PDF var mı kontrolü
    private fun fileExistsInDownloadsApi29Plus(displayName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf(displayName, "application/pdf")
        contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        ).use { cursor ->
            if (cursor == null) return false
            return cursor.moveToFirst()
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

    private fun setupToolbar() {
        setSupportActionBar(pdfToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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

    private fun displayPdfFromFirebaseWithOkHttp(storagePath: String) {
        progressBar.visibility = View.VISIBLE
        val storageRef = Firebase.storage.reference.child(storagePath)
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(uri.toString()).build()
                    val response = OkHttpClient().newCall(request).execute()
                    if (response.isSuccessful) {
                        val downloadedBytes = response.body?.bytes() ?: throw IOException("Response body is null")
                        this@PdfViewActivity.pdfBytes = downloadedBytes

                        withContext(Dispatchers.Main) {
                            if (isAdLoadable()) { // Aktivite hala geçerliyse PDF'i yükle
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

    private fun showAiChatDialog() {
        if (pdfBytes == null) {
            showAnimatedToast(getString(R.string.pdf_text_not_ready))
            return
        }

        // Diyaloğu anında aç; kota kontrolü diyalog içinde asenkron yapılacak
        val chatDialog = ChatAiDialogFragment()
        chatDialog.show(supportFragmentManager, "ChatAiDialog")
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
                            val rewardResult = UserRepository.grantAdReward(3)
                            if (rewardResult.isSuccess) {
                                showAnimatedToast(getString(R.string.reward_granted_toast, rewardResult.getOrNull() ?: 0))
                            } else {
                                showAnimatedToast(getString(R.string.ad_not_ready_toast))
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

    private fun toggleReadingMode() {
        currentReadingModeLevel = (currentReadingModeLevel + 1) % 4
        applyReadingModeFilter(currentReadingModeLevel)
    }

    private fun applyReadingModeFilter(level: Int) {
        val (visibility, color, message) = when (level) {
            0 -> Triple(View.GONE, 0, getString(R.string.reading_mode_off_toast))
            1 -> Triple(View.VISIBLE, "#33FDF6E3".toColorInt(), getString(R.string.reading_mode_low_toast))
            2 -> Triple(View.VISIBLE, "#66FDF6E3".toColorInt(), getString(R.string.reading_mode_medium_toast))
            3 -> Triple(View.VISIBLE, "#99FDF6E3".toColorInt(), getString(R.string.reading_mode_high_toast))
            else -> Triple(View.GONE, 0, "")
        }
        eyeComfortOverlay.visibility = visibility
        eyeComfortOverlay.setBackgroundColor(color)
        showAnimatedToast(message)
    }

    private fun showGoToPageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_go_to_page, null)
        val editTextPageNumber = dialogView.findViewById<EditText>(R.id.editTextPageNumber)

        AlertDialog.Builder(this)
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
            .show()
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

    private val adSize: AdSize
        get() {
            val metrics = resources.displayMetrics
            var adWidthPixels = adContainerView.width.toFloat()
            if (adWidthPixels == 0f) {
                adWidthPixels = metrics.widthPixels.toFloat()
            }
            val density = metrics.density
            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
        }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}
