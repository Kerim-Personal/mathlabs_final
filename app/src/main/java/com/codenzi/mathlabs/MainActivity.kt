package com.codenzi.mathlabs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codenzi.mathlabs.databinding.ActivityMainBinding
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var courseAdapter: CourseAdapter
    private val viewModel: MainViewModel by viewModels()
    private lateinit var toolbarTitle: TextView
    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private var mInterstitialAd: InterstitialAd? = null
    private val TAG = "MainActivity"

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            recreate()
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf)

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        viewModel.loadCourses(this)

        // Arayüz hazır olur olmaz, kullanıcı durumunu Firestore'dan canlı olarak gözlemeye başla.
        observeUserStatusAndUpdateUI()
    }

    private fun initializeViews() {
        toolbarTitle = binding.topToolbar.findViewById(R.id.toolbar_title)
        adContainerView = binding.adViewContainer
        adContainerView.visibility = View.GONE // Başlangıçta reklamı gizle
    }

    /**
     * KULLANICI DURUMUNU GÖZLEMLE VE ARAYÜZÜ GÜNCELLE
     * Bu fonksiyon, 'isPremium' durumu her değiştiğinde arayüzü (reklamları) anında günceller.
     * Bu, tüm reklam mantığının TEK merkezidir.
     */
    private fun observeUserStatusAndUpdateUI() {
        lifecycleScope.launch {
            UserRepository.userDataState.collectLatest { userData ->
                val isPremium = userData?.isPremium ?: false
                if (isPremium) {
                    // KULLANICI PREMIUM İSE: Reklamları kaldır ve yok et.
                    adContainerView.visibility = View.GONE
                    adView?.destroy()
                    adView = null
                    mInterstitialAd = null
                } else {
                    // KULLANICI PREMIUM DEĞİLSE: Reklamları yükle ve göster.
                    adContainerView.visibility = View.VISIBLE
                    loadBanner()
                    loadInterstitialAd()
                }
            }
        }
    }

    private fun loadBanner() {
        // Aktivite yok ediliyorsa veya reklam zaten varsa yükleme yapma.
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

    private fun loadInterstitialAd() {
        // Aktivite yok ediliyorsa veya reklam zaten varsa yükleme yapma.
        if (mInterstitialAd != null || !isAdLoadable()) return
        val adRequest = AdRequest.Builder().build()
        val adUnitId = getString(R.string.admob_interstitial_unit_id)

        InterstitialAd.load(this, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError.toString())
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Geçiş reklamı yüklendi.")
                // Reklam sadece premium olmayan kullanıcılar için saklanır.
                if (!UserRepository.isCurrentUserPremium()) {
                    mInterstitialAd = interstitialAd
                }
            }
        })
    }

    private fun setupRecyclerView() {
        courseAdapter = CourseAdapter(
            onTopicClickListener = { courseTitle, topicTitle ->
                UIFeedbackHelper.provideFeedback(window.decorView.rootView)
                val message = getString(R.string.topic_pdf_not_found, courseTitle, topicTitle)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            },
            onPdfClickListener = { courseTitle, topic ->
                UIFeedbackHelper.provideFeedback(window.decorView.rootView)

                // PDF'e tıklanınca premium kontrolünü anlık olarak UserRepository'den yap.
                val isPremium = UserRepository.isCurrentUserPremium()

                if (!isPremium && mInterstitialAd != null) {
                    mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() { navigateToPdfView(courseTitle, topic) }
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) { navigateToPdfView(courseTitle, topic) }
                        override fun onAdShowedFullScreenContent() {
                            mInterstitialAd = null
                            loadInterstitialAd() // Bir sonraki için tekrar yükle
                        }
                    }
                    mInterstitialAd?.show(this@MainActivity)
                } else {
                    navigateToPdfView(courseTitle, topic)
                }
            }
        )
        binding.recyclerViewCourses.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = courseAdapter
        }
    }

    // Bu aktivitenin yok edilip edilmediğini kontrol eden yardımcı fonksiyon
    private fun isAdLoadable(): Boolean {
        return !isFinishing && !isDestroyed
    }

    private fun navigateToPdfView(courseTitle: String, topic: Topic) {
        val intent = Intent(this, PdfViewActivity::class.java).apply {
            putExtra(PdfViewActivity.EXTRA_PDF_ASSET_NAME, topic.pdfAssetName)
            putExtra(PdfViewActivity.EXTRA_PDF_TITLE, "$courseTitle - ${topic.title}")
        }
        startActivity(intent)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.topToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbarTitle.text = getGreetingMessage(this)
        binding.appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            val percentage = (abs(verticalOffset).toFloat() / totalScrollRange.toFloat())
            toolbarTitle.alpha = percentage
            binding.expandedHeader.alpha = 1 - (percentage * 1.5f)
        })
    }

    private fun getGreetingMessage(context: android.content.Context): String {
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName
        if (user == null || name.isNullOrEmpty()) {
            return getString(R.string.app_name)
        }
        val calendar = Calendar.getInstance()
        return when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> getString(R.string.greeting_good_morning, name)
            in 12..17 -> getString(R.string.greeting_good_day, name)
            in 18..21 -> getString(R.string.greeting_good_evening, name)
            else -> getString(R.string.greeting_good_night, name)
        }
    }

    private fun observeViewModel() {
        viewModel.courses.observe(this) { courses ->
            courseAdapter.submitList(courses)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filter(newText)
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                UIFeedbackHelper.provideFeedback(binding.root)
                val intent = Intent(this, SettingsActivity::class.java)
                settingsLauncher.launch(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val adSize: AdSize
        get() {
            val metrics = resources.displayMetrics
            var adWidthPixels = binding.adViewContainer.width.toFloat()
            if (adWidthPixels == 0f) {
                adWidthPixels = metrics.widthPixels.toFloat()
            }
            val density = metrics.density
            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
        }
}
