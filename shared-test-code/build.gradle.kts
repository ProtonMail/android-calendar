plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "me.proton.android.calendar.test.shared"
    compileSdk = 35

    defaultConfig {
        compileSdk = 35
        minSdk = 23
        multiDexEnabled = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    packagingOptions {
        resources.excludes.add("META-INF/LICENSE*")
    }

    flavorDimensions.add("env")
    productFlavors {
        create("dev") { dimension = "env" }
        create("prod") { dimension = "env" }
    }
}

dependencies {
    coreLibraryDesugaring(libs.tools.desugar)
    implementation(project(":app"))
    implementation(libs.dagger.hilt.android)
    implementation(libs.core.utilKotlin)
    implementation(libs.core.domain)
    implementation(libs.core.user)
    implementation(libs.core.userSettings)
    implementation(libs.biweekly)
    implementation(libs.kotlinx.serialization.json)
    kapt(libs.dagger.hilt.android.compiler)
}
