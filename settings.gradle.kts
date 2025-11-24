import com.android.build.api.dsl.SettingsExtension

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.settings") version "8.3.0"
}

rootProject.name = "amneziawg-android"

include(":tunnel")
include(":ui")

configure<SettingsExtension> {
    buildToolsVersion = "35.0.0"
    compileSdk = 35
    minSdk = 24
    ndkVersion = "26.1.10909125"
}
