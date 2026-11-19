package id.tukangkilat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import id.tukangkilat.app.ui.TukangKilatApp
import id.tukangkilat.app.ui.theme.TukangKilatTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TukangKilatTheme {
                TukangKilatApp()
            }
        }
    }
}
