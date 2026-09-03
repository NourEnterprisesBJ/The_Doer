pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // fallback si vosk-android ne se résout pas depuis mavenCentral
    }
}

rootProject.name = "TheDoer"
include(":app")
