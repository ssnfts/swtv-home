# Build toolchain

Pinned project versions: Android Gradle Plugin8.7.3, Kotlin2.0.21, Gradle8.9, Java17, Android compile/target35 and minimum29. Runtime dependencies are limited to Kotlin's standard library and Android platform APIs; unit tests additionally use JUnit4 and a JVM JSON implementation. The separate Android test APK uses AndroidX runner1.6.2 and JUnit extension1.2.1; these are not application runtime dependencies.

The local task uses tools under the task root's `tools/` directory. They are not redistributed in the source ZIP. `build-local.ps1` sets process-local JAVA_HOME, ANDROID_HOME and GRADLE_USER_HOME, then invokes the task-local Gradle. No system PATH or persistent environment setting is modified.

Sources used to prepare this environment:

- [Eclipse Temurin JDK17 API](https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse): archive verified against its API SHA-256; installed version17.0.21+9.
- [Gradle8.9 distribution](https://services.gradle.org/distributions/gradle-8.9-bin.zip): verified against [publisher SHA-256](https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256), `d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab`.
- [Google command-line tools12 archive](https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip): fetched directly over HTTPS; recorded SHA-256 `4d6931209eebb1bfb7c7e8b240a6a3cb3ab24479ea294f3539429574b1eec862`. This recorded hash is not an independently published checksum comparison.
- SDK Manager installed `platforms;android-35`, `build-tools;34.0.0`; Google repository downloads use SDK Manager's package verification.
- Android TV emulator image: `system-images;android-29;android-tv;x86`, with Google's emulator package. Emulator validation status is recorded separately after testing.

Official compatibility reference: [Google AGP8.7 release notes](https://developer.android.com/build/releases/agp-8-7-0-release-notes).

Android Studio can also open this project. Select JDK17 and install SDK35/build-tools34.0.0 in the SDK Manager. Set the SDK location through Android Studio or an untracked `local.properties`; no signing key belongs in Git. Phase2 artifacts use Android's debug signing, and are not production signed releases. Secure release signing and update distribution are Phase4 work.
