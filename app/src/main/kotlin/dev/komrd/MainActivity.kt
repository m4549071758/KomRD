package dev.komrd

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.navigation.KomrdApp
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val prefs = newBase.getSharedPreferences("locale", Context.MODE_PRIVATE)
            val tag = prefs.getString("app_locale", "system")
            if (tag != null && tag != "system") {
                val locale = Locale.forLanguageTag(tag)
                val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
                super.attachBaseContext(newBase.createConfigurationContext(config))
                return
            }
        }
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KomrdTheme {
                KomrdApp()
            }
        }
    }
}
