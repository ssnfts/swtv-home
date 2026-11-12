plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val releaseVersion = providers.gradleProperty("swtvVersionName").orElse("0.4.0").get()
require(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(releaseVersion)) { "swtvVersionName must have three canonical numeric segments" }
val releaseCodeText = providers.gradleProperty("swtvVersionCode").orElse("4").get()
val releaseCode = releaseCodeText.toIntOrNull()
require(releaseCode != null && releaseCode > 0 && releaseCode.toString() == releaseCodeText) { "swtvVersionCode must be a canonical positive Int" }
val updateFeed = providers.gradleProperty("swtvUpdateFeed").orNull
val updateCert = providers.gradleProperty("swtvUpdateCert").orNull
require((updateFeed == null) == (updateCert == null)) { "Provide both swtvUpdateFeed and swtvUpdateCert, or neither" }
if (updateFeed != null) {
    val feedMatch = Regex("https://github\\.com/([A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?)/([A-Za-z0-9_.-]{1,100})/releases/latest/download/swtv-home-update-v1\\.json").matchEntire(updateFeed)
    require(feedMatch != null && feedMatch.groupValues[2] !in setOf(".", "..")) { "swtvUpdateFeed must be a canonical GitHub release feed" }
    require(Regex("[0-9a-f]{64}").matches(updateCert!!)) { "swtvUpdateCert must be 64 lowercase hex characters" }
}
val instrumentationBuild = providers.gradleProperty("swtvTestBuildType").orElse("debug").get()
require(instrumentationBuild in setOf("debug", "release")) { "swtvTestBuildType must be debug or release" }
android {
    namespace = "app.swtv.home"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.swtv.home"
        minSdk = 29
        targetSdk = 35
        versionCode = releaseCode
        versionName = releaseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    testBuildType = instrumentationBuild
    buildTypes {
        getByName("release") {
            isDebuggable = false
            signingConfig = null
            manifestPlaceholders["swtvUpdateFeed"] = updateFeed ?: ""
            manifestPlaceholders["swtvUpdateCert"] = updateCert ?: ""
        }
    }
    providers.gradleProperty("swtvTestAssetsDir").orNull?.let { sourceSets.getByName("androidTest").assets.srcDir(it) }
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
