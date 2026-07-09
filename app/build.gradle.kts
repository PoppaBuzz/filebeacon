// In app/build.gradle.kts

plugins {
    // Reference plugins from the version catalog
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.jphat.filebeacon"
    // Use the version from the catalog
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.jphat.filebeacon"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Updated Java compatibility to Java 21
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Updated Kotlin JVM target to match Java 21
    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    // FIX: All dependencies now reference the version catalog for type-safety and easy management
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.nanohttpd)
    implementation("org.nanohttpd:nanohttpd-apache-fileupload:2.3.1")
    implementation(libs.androidx.cardview)
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(libs.jmdns)
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.github.junrar:junrar:7.5.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}