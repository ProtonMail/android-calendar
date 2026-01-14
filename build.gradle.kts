buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }

    dependencies {
        classpath(libs.gradle.plugin.android)
        classpath(libs.gradle.plugin.kotlin)
        classpath(libs.gradle.plugin.navigation)
        classpath(libs.gradle.plugin.hilt)
        classpath(libs.jacoco)
        classpath(libs.gradle.plugin.compose.compiler)
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle.kts files
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
        maven("https://jitpack.io")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

plugins {
    alias(libs.plugins.proton.detekt)
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}

