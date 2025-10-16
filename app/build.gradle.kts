plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.ptvisdatteknikutama"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ptvisdatteknikutama"
        minSdk = 26   // ✅ minimal SDK
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.fragment)

    // ✅ OSMDroid Map
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    // ✅ Preference library for OSMDroid configuration and other preferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // ✅ Firebase Firestore
    implementation(libs.firebase.firestore)

    // ✅ Animasi Lottie
    implementation("com.airbnb.android:lottie:6.1.0")

    // ✅ Material Components
    implementation("com.google.android.material:material:1.12.0")

    // ✅ HTTP client for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ✅ Preference
    implementation(libs.preference)

    // Toast
    implementation("com.google.android.material:material:1.9.0")
    implementation(libs.exifinterface)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
