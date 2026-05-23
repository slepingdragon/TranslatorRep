pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Foojay toolchain resolver — lets Gradle auto-download a matching JDK
// (per `jvmToolchain(17)` in app/build.gradle.kts) instead of requiring
// one to be pre-installed on the machine. Fixes the "No locally installed
// toolchains match and toolchain download repositories have not been
// configured" error on first sync.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack — required for LiveKit Android SDK's transitive dependency
        // `com.github.davidliu:audioswitch` (the original audioswitch library
        // by David Liu, distributed via JitPack as git-commit-hash artifacts).
        maven { url = uri("https://jitpack.io") }
        // Sonatype snapshots — kept in case a future SDK version needs it.
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    }
}

rootProject.name = "TranslatorRep"
include(":app")
