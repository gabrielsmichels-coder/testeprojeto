plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.projectorlauncher"
    compileSdk = 35
    defaultConfig { applicationId = "com.example.projectorlauncher"; minSdk = 28; targetSdk = 35; versionCode = 3; versionName = "0.3.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { getByName("release") { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
}

dependencies { testImplementation("junit:junit:4.13.2") }
