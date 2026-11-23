package id.tukangkilat.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import id.tukangkilat.app.data.local.LocalBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltAndroidApp
class TukangKilatApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // A stale token must never crash the app. Clear it so the next screen shows login.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable is HttpException && throwable.code() == 401) {
                // The authenticator already clears the session; ignore the stale call.
            } else {
                previous?.uncaughtException(thread, throwable)
            }
        }
        // Start off the main thread; the UI shows a loading gate until ready.
        scope.launch { LocalBackend.start(this@TukangKilatApplication) }
    }
}
