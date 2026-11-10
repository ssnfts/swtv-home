plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.swtv.launcher.ui"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
}
dependencies {
    implementation(project(":theme-config"))
    implementation(project(":app-discovery"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
