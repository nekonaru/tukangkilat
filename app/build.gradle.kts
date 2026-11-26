plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "id.tukangkilat.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.tukangkilat.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 28
        versionName = "2.14.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val baseUrl = (project.findProperty("baseUrl") as String?) ?: "http://127.0.0.1:8080"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            val storeFile = rootProject.file("release.keystore")
            if (storeFile.exists()) {
                this.storeFile = storeFile
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "changeit"
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "tukangkilat"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD") ?: "changeit"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (rootProject.file("release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "META-INF/license/**"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":backend")) {
        // The embedded server uses CIO, not Netty. Flyway/PostgreSQL/Hikari are server-only.
        exclude(group = "io.netty")
        exclude(group = "org.flywaydb")
        exclude(group = "org.postgresql")
        exclude(group = "com.zaxxer")
    }

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.coil.compose)
    implementation(libs.osmdroid.android)
    implementation(libs.datastore.preferences)
    implementation(libs.play.services.location)
    implementation(libs.h2)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui)
}
