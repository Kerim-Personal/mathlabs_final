package com.codenzi.mathlabs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.codenzi.mathlabs.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

/**
 * Uygulamanın ana ekranını temsil eden Activity.
 * Hilt tarafından yönetilir (@AndroidEntryPoint) ve ViewModel'den gelen verilerle UI'ı günceller.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // View Binding nesnesi, XML layout'taki view'lara güvenli ve null-safe erişim sağlar.
    private lateinit var binding: ActivityMainBinding
    private lateinit var courseAdapter: CourseAdapter
    // 'by viewModels()' Kotlin property delegate'i kullanarak Hilt uyumlu ViewModel'i oluşturur.
    private val viewModel: MainViewModel by viewModels()

    // Ayarlar ekranından geri dönüldüğünde temayı/dili yeniden uygulamak için kullanılır.
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Dil veya tema değişikliği sonrası aktiviteyi yeniden oluştur.
            // Bu, hem arayüzün (başlık, metin yönü vb.) hem de veri listesinin güncellenmesini sağlar.
            recreate()
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private fun applyThemeAndColor() {
        setTheme(ThemeManager.getThemeResId(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeAndColor()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Kenardan kenara (edge-to-edge) görünümü etkinleştir.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Toolbar'ı ayarla.
        setSupportActionBar(binding.topToolbar)
        supportActionBar?.title = getGreetingMessage(this)

        // Toolbar'ın durum çubuğunun (status bar) altına itilmesini sağla.
        ViewCompat.setOnApplyWindowInsetsListener(binding.topToolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top
            }
            WindowInsetsCompat.CONSUMED
        }

        setupRecyclerView()
        observeViewModel()

        // Veriyi yüklerken bu Activity'nin güncel Context'ini kullan.
        // Bu, recreate() sonrası doğru dil kaynaklarının kullanılmasını sağlar.
        viewModel.loadCourses(this)
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
                val intent = Intent(this, PdfViewActivity::class.java).apply {
                    putExtra(PdfViewActivity.EXTRA_PDF_ASSET_NAME, topic.pdfAssetName)
                    putExtra(PdfViewActivity.EXTRA_PDF_TITLE, "$courseTitle - ${topic.title}")
                }
                startActivity(intent)
            }
        )

        binding.recyclerViewCourses.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = courseAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        // ViewModel'deki 'courses' LiveData'sını gözlemle.
        // Veri değiştiğinde, adapter'ın listesi otomatik olarak ve verimli bir şekilde güncellenir.
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
                // Filtreleme işlemini doğrudan ViewModel'e delege et.
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