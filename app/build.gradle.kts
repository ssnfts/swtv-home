plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.swtv.home"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.swtv.home"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
}
dependencies {
    implementation(project(":theme-config"))
    implementation(project(":app-discovery"))
    implementation(project(":launcher-ui"))
    implementation(project(":device-diagnostics"))
    implementation(project(":update-manager"))
    implementation(project(":tv-hub-contract"))

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
