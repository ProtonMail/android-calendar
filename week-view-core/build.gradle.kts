plugins {
    id("com.android.library")
    id("kotlin-android")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.alamkanak.weekview"
    defaultConfig {
        compileSdk = 32
        minSdk = 23
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = JavaVersion.VERSION_17.toString() }

    testOptions {
        unitTests.all {
            it.useJUnit()
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.tools.desugar)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.emoji)
    implementation(libs.androidx.startup)

    testImplementation(libs.test.androidx.ext.junit)
    testImplementation(libs.test.androidx.testrunner)
    testImplementation(libs.test.junit)
    testImplementation(libs.test.google.truth)
    testImplementation(libs.test.mockito.core)
    testImplementation(libs.test.mockito.inline)
    testImplementation(libs.test.mockito.kotlin)
}
