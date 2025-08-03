package com.codenzi.mathlabs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.codenzi.mathlabs.databinding.ActivityMainBinding
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var courseAdapter: CourseAdapter
    private val viewModel: MainViewModel by viewModels()
    private lateinit var toolbarTitle: TextView

    // Geçiş reklamını tutmak için değişken
    private var mInterstitialAd: InterstitialAd? = null
    private val TAG = "MainActivity"

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
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

        toolbarTitle = binding.topToolbar.findViewById(R.id.toolbar_title)

        MobileAds.initialize(this) {}

        // Premium durumuna göre banner reklamı yönetimi
        if (!SharedPreferencesManager.isUserPremium(this)) {
            val adRequest = AdRequest.Builder().build()
            binding.adView.loadAd(adRequest)
        } else {
            binding.adView.visibility = View.GONE
        }

        setupToolbar()
        setupRecyclerView()
        observeViewModel()

        viewModel.loadCourses(this)

        // Premium durumuna göre geçiş reklamı yüklemesi
        if (!SharedPreferencesManager.isUserPremium(this)) {
            loadInterstitialAd()
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        val adUnitId = getString(R.string.admob_interstitial_unit_id)

        InterstitialAd.load(this, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError.toString())
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Ad was loaded.")
                mInterstitialAd = interstitialAd
            }
        })
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
        val name = SharedPreferencesManager.getUserName(context)
        if (name.isNullOrEmpty()) {
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

    private fun setupRecyclerView() {
        courseAdapter = CourseAdapter(
            onTopicClickListener = { courseTitle, topicTitle ->
                UIFeedbackHelper.provideFeedback(window.decorView.rootView)
                val message = getString(R.string.topic_pdf_not_found, courseTitle, topicTitle)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            },
            onPdfClickListener = { courseTitle, topic ->
                UIFeedbackHelper.provideFeedback(window.decorView.rootView)

                val isPremium = SharedPreferencesManager.isUserPremium(this)

                // Sadece premium olmayanlar için reklam göster ve reklam yüklenmişse
                if (!isPremium && mInterstitialAd != null) {
                    mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            navigateToPdfView(courseTitle, topic)
                            loadInterstitialAd() // Yeni bir reklam yükle
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            navigateToPdfView(courseTitle, topic) // Hata olursa da devam et
                        }

                        override fun onAdShowedFullScreenContent() {
                            mInterstitialAd = null // Reklam gösterildi, tekrar kullanılamaz
                        }
                    }
                    mInterstitialAd?.show(this)
                } else {
                    // Premium kullanıcılar veya reklam hazır değilse doğrudan PDF'i aç
                    navigateToPdfView(courseTitle, topic)
                }
            }
        )

        binding.recyclerViewCourses.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = courseAdapter
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
}