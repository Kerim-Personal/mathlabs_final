package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private val appColorThemes = listOf(
        "Serene Blue", "Red", "Green", "Purple", "Orange",
        "Deep Purple", "Indigo", "Cyan", "Pink", "Brown"
    )

    /**
     * Uygulamanın mevcut ayarlarına (renk ve aydınlık/karanlık mod) göre
     * doğru tema kaynağını (theme resource ID) döndürür.
     * "Sistem Varsayılanı" seçeneğini doğru bir şekilde ele alır.
     */
    fun getThemeResId(context: Context): Int {
        val selectedColorThemeIndex = SharedPreferencesManager.getAppColorTheme(context)
        val savedThemeMode = SharedPreferencesManager.getTheme(context)

        // Cihazın gerçek gece modu durumunu kontrol et
        val isNightMode = when (savedThemeMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> { // MODE_NIGHT_FOLLOW_SYSTEM durumu
                val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }

        // Renk teması ve gece modu durumuna göre uygun stili seç
        return when (selectedColorThemeIndex) {
            0 -> if (isNightMode) R.style.Theme_Pdf_SereneBlue_Dark else R.style.Theme_Pdf_SereneBlue_Light
            1 -> if (isNightMode) R.style.Theme_Pdf_Red_Dark else R.style.Theme_Pdf_Red_Light
            2 -> if (isNightMode) R.style.Theme_Pdf_Green_Dark else R.style.Theme_Pdf_Green_Light
            3 -> if (isNightMode) R.style.Theme_Pdf_Purple_Dark else R.style.Theme_Pdf_Purple_Light
            4 -> if (isNightMode) R.style.Theme_Pdf_Orange_Dark else R.style.Theme_Pdf_Orange_Light
            5 -> if (isNightMode) R.style.Theme_Pdf_DeepPurple_Dark else R.style.Theme_Pdf_DeepPurple_Light
            6 -> if (isNightMode) R.style.Theme_Pdf_Indigo_Dark else R.style.Theme_Pdf_Indigo_Light
            7 -> if (isNightMode) R.style.Theme_Pdf_Cyan_Dark else R.style.Theme_Pdf_Cyan_Light
            8 -> if (isNightMode) R.style.Theme_Pdf_Pink_Dark else R.style.Theme_Pdf_Pink_Light
            9 -> if (isNightMode) R.style.Theme_Pdf_Brown_Dark else R.style.Theme_Pdf_Brown_Light
            else -> if (isNightMode) R.style.Theme_Pdf_SereneBlue_Dark else R.style.Theme_Pdf_SereneBlue_Light
        }
    }

    fun applyAppColorTheme(activity: Activity, themeIndex: Int) {
        SharedPreferencesManager.saveAppColorTheme(activity, themeIndex)
        activity.recreate()
        Log.d("ThemeDebug", "ThemeManager - Renk teması uygulandı: $themeIndex")
    }

    fun getAppColorThemeCount(): Int {
        return appColorThemes.size
    }

    fun getAppColorThemeName(context: Context, themeIndex: Int): String {
        val themeName = when (themeIndex) {
            0 -> context.getString(R.string.theme_color_serene_blue)
            1 -> context.getString(R.string.theme_color_red)
            2 -> context.getString(R.string.theme_color_green)
            3 -> context.getString(R.string.theme_color_purple)
            4 -> context.getString(R.string.theme_color_orange)
            5 -> context.getString(R.string.theme_color_deep_purple)
            6 -> context.getString(R.string.theme_color_indigo)
            7 -> context.getString(R.string.theme_color_cyan)
            8 -> context.getString(R.string.theme_color_pink)
            9 -> context.getString(R.string.theme_color_brown)
            else -> context.getString(R.string.theme_color_unknown)
        }
        Log.d("ThemeDebug", "ThemeManager - Renk teması adı alındı: $themeName (index: $themeIndex)")
        return themeName
    }

    fun applyCurrentThemeColors(context: Context) {
        // Boş bırakılmış, tema uygulama Activity'lere taşındı
        Log.d("ThemeDebug", "ThemeManager - applyCurrentThemeColors çağrıldı (boş)")
    }
}