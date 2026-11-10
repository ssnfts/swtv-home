# Phase 2 reference and environment review

Reviewed 10 November 2026. Repository descriptions and README files were read through the connected GitHub tools. No repository script was executed and no source was copied.

| Reference | Observed relevance | Treatment for SWTV |
| --- | --- | --- |
| [abnkei/tv-vibehack](https://github.com/abnkei/tv-vibehack) | Small Java TV home with app dock, grid and explicit D-pad movement; also contains package-changing workflows | Reference for lightweight behavior; its installation/debloat workflow is outside the authorized scope |
| [leanbitlab-org/LtvLauncher](https://github.com/leanbitlab-org/LtvLauncher) | Flutter/Dart launcher with themes, weather, input selection and remote customization; repository metadata reports GPL-3.0 | Feature reference; original native implementation avoids depending on its Flutter application |
| [coraspirin/android-tv-lite-launcher](https://github.com/coraspirin/android-tv-lite-launcher) | Java launcher described as supporting an older 32-bit Android TV reference device; includes installer and companion tools | Reference for constrained-device UI; do not execute installation, default-HOME or debloat scripts |
| [johnpc/telly](https://github.com/johnpc/telly) | Kotlin/Compose IPTV app with guide and playback features | Future TV Hub behavioral reference; streaming remains outside Phase 2 |
| [Thedurancode/owntv](https://github.com/Thedurancode/owntv) | Fork whose README describes a native IPTV player; repository metadata reports GPL-3.0 | Future TV Hub reference; no player dependencies are introduced now |

README claims and performance figures are the repositories' own descriptions, not measurements on SWTV-20AE. License metadata is an initial flag, not a complete reuse audit. Any future source reuse would need examination of the actual relevant license and files first.

At the initial design review, this chat had no attached worktree or application repository. Java, javac, Gradle and Kotlin were not found on PATH, and the usual locations checked did not reveal an Android build installation. The approved implementation subsequently created this standalone repository and provisioned the task-local toolchain described in `TOOLCHAIN.md`. System PATH was not changed.

Google's [AGP 8.7 compatibility documentation](https://developer.android.com/build/releases/agp-8-7-0-release-notes) identifies JDK 17, Gradle 8.9 and a maximum supported compile API of 35. Those versions form the proposed pinned build base. The [TV app setup documentation](https://developer.android.com/training/tv/get-started/create) describes the Leanback launch entry and TV manifest requirements.

The proposed app is a new original Kotlin/native Views project. This review does not change the TV or authorize adopting any external repository's scripts.
