
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.aplikasistockopnameperpus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aplikasistockopnameperpus"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            }
        }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    tasks.withType<org.gradle.api.tasks.compile.JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked") // Anda juga bisa menambahkan ini untuk detail unchecked operations
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    // Untuk semua file .jar dan .aar di dalam direktori libs
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2") // Ganti dengan versi terbaru jika perlu
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-ktx:1.9.0") // Ganti dengan versi terbaru jika perlu
    implementation("androidx.fragment:fragment-ktx:1.8.1") // Juga berguna untuk fragment by viewModels()

    val room_version = "2.7.2" // Gunakan versi terbaru yang stabil (ubah def menjadi val untuk Kotlin)

    implementation("androidx.room:room-runtime:$room_version")
    // annotationProcessor "androidx.room:room-compiler:$room_version" // Hapus ini
    ksp("androidx.room:room-compiler:$room_version")

    // Opsional - Kotlin Extensions and Coroutines support for Room
    implementation("androidx.room:room-ktx:$room_version")

    // Untuk file .xls (HSSF)
    implementation("org.apache.poi:poi:5.2.5")
    // Untuk file .xlsx (XSSF)
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    implementation ("com.google.android.flexbox:flexbox:3.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation ("com.google.android.material:material:1.12.0")
    // Dependensi lainnya
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.generativeai)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.glance:glance:1.2.0-alpha01")
    implementation("androidx.glance:glance-appwidget:1.2.0-alpha01")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:X.Y.Z") // Ganti X.Y.Z dengan versi terbaru
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:X.Y.Z") // Ganti X.Y.Z dengan versi terbaru

}
