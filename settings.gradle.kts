// settings.gradle.kts
pluginManagement {
    repositories {
        google() // Repositori Google untuk plugin Android
        mavenCentral() // Repositori Maven Central
        gradlePluginPortal() // WAJIB ADA untuk plugin seperti KSP yang dipublikasikan di sini
    }
    resolutionStrategy {
        eachPlugin {
            // Jika Anda memiliki konfigurasi khusus di sini, pastikan tidak mengganggu KSP
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Jika Anda menambahkan repositori lain di sini, pastikan tidak ada masalah
    }
}

rootProject.name = "Aplikasi Stock Opname Perpus"
include(":app")
