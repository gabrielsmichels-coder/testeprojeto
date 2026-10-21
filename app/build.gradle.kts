plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.projectorlauncher"
    compileSdk = 35
    defaultConfig { applicationId = "com.example.projectorlauncher"; minSdk = 28; targetSdk = 35; versionCode = 5; versionName = "0.5.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        getByName("debug") {
            // Hosted CI signs explicitly with a stable protected key after compilation.
            // Never distribute a new ephemeral runner debug certificate as an update.
            if (System.getenv("CI") == "true") signingConfig = null
        }
        getByName("release") { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
}

dependencies { testImplementation("junit:junit:4.13.2") }
