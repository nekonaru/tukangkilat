package id.tukangkilat.app.data.di

import android.content.Context
import coil.ImageLoader
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import id.tukangkilat.app.BuildConfig
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.api.AuthInterceptor
import id.tukangkilat.app.data.api.BaseUrlInterceptor
import id.tukangkilat.app.data.api.TokenAuthenticator
import id.tukangkilat.app.data.local.CatalogDao
import id.tukangkilat.app.data.local.ServerConfigManager
import id.tukangkilat.app.data.local.FavoriteDao
import id.tukangkilat.app.data.local.NotificationDao
import id.tukangkilat.app.data.local.OrderDao
import id.tukangkilat.app.data.local.TukangKilatDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
        baseUrlInterceptor: BaseUrlInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // no request/response logging in release builds
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .addInterceptor(logging)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** Coil loader that sends the bearer token and follows the configured server address. */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        serverConfig: ServerConfigManager
    ): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                if (url.host == "127.0.0.1" || url.host == "localhost") {
                    val current = runBlocking { serverConfig.current() }
                    val uri = java.net.URI(current)
                    val rewritten = url.newBuilder()
                        .scheme(uri.scheme)
                        .host(uri.host)
                        .port(if (uri.port == -1) url.port else uri.port)
                        .build()
                    chain.proceed(request.newBuilder().url(rewritten).build())
                } else {
                    chain.proceed(request)
                }
            }
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(context).okHttpClient { client }.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TukangKilatDatabase =
        Room.databaseBuilder(context, TukangKilatDatabase::class.java, "tukangkilat.db")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationFrom(1, 2, 3)
            .build()

    @Provides
    fun provideFavoriteDao(db: TukangKilatDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideCatalogDao(db: TukangKilatDatabase): CatalogDao = db.catalogDao()

    @Provides
    fun provideOrderDao(db: TukangKilatDatabase): OrderDao = db.orderDao()

    @Provides
    fun provideNotificationDao(db: TukangKilatDatabase): NotificationDao = db.notificationDao()
}

/** Version 4 adds the persisted notification history without clearing favorites or orders. */
private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cached_notifications ADD COLUMN orderId INTEGER")
    }
}

private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cached_notifications (" +
                "id INTEGER PRIMARY KEY NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, read INTEGER NOT NULL)"
        )
    }
}
