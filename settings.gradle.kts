pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "SWTV Custom Home"
include(":app", ":theme-config", ":app-discovery", ":launcher-ui", ":device-diagnostics", ":update-manager", ":tv-hub-contract")
