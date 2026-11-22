package id.tukangkilat.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import id.tukangkilat.app.data.local.LocalBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TukangKilatApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Start off the main thread; the UI shows a loading gate until ready.
        scope.launch { LocalBackend.start(this@TukangKilatApplication) }
    }
}
