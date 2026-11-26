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
        // Log every uncaught error and keep the platform default handling. Swallowing an
        // exception here freezes the UI on a blank screen with no diagnostic.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("TukangKilat", "Uncaught on ${thread.name}", throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        // Start off the main thread; the UI shows a loading gate until ready.
        scope.launch { LocalBackend.start(this@TukangKilatApplication) }
    }
}
