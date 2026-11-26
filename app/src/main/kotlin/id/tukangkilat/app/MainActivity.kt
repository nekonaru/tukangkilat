package id.tukangkilat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import id.tukangkilat.app.data.local.UiPreferencesManager
import id.tukangkilat.app.ui.TukangKilatApp
import id.tukangkilat.app.ui.theme.TukangKilatTheme
import id.tukangkilat.app.ui.theme.ThemeMode
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var uiPreferences: UiPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by uiPreferences.themeMode.collectAsState(initial = ThemeMode.System)
            TukangKilatTheme(themeMode = themeMode) {
                TukangKilatApp()
            }
        }
    }
}
