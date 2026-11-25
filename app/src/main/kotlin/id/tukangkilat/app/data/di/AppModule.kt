package id.tukangkilat.app.data.di

import android.content.Context
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
import id.tukangkilat.app.data.local.OrderDao
import id.tukangkilat.app.data.local.TukangKilatDatabase
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCatalogDao(db: TukangKilatDatabase): CatalogDao = db.catalogDao()

    @Provides
    fun provideOrderDao(db: TukangKilatDatabase): OrderDao = db.orderDao()
}
