package com.codenzi.mathlabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.textfield.TextInputEditText

class NameEntryActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_name_entry)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val rootView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBarInsets.left,
                top = systemBarInsets.top,
                right = systemBarInsets.right,
                bottom = systemBarInsets.bottom
            )
            insets
        }

        val editTextName: TextInputEditText = findViewById(R.id.editTextName)
        val buttonContinue: Button = findViewById(R.id.buttonContinue)

        buttonContinue.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            val name = editTextName.text.toString().trim()
            if (name.isNotEmpty()) {
                SharedPreferencesManager.saveUserName(this, name)
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, getString(R.string.enter_valid_name_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }
}