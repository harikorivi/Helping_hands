plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ai.interlink.scheduler"
    compileSdk = 35
    defaultConfig { applicationId = "ai.interlink.scheduler"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "1.3" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
