@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.VariantDimension
import configuration.extensions.protonEnvironment
import org.gradle.api.tasks.testing.logging.TestLogEvent
import configuration.util.getTokenFromCurl
import studio.forface.easygradle.dsl.version
import java.io.FileNotFoundException
import java.util.Properties
import groovy.json.StringEscapeUtils
import studio.forface.easygradle.dsl.testImplementation

plugins {
    alias(libs.plugins.kotlin.serialization)
    id("io.sentry.android.gradle") version libs.versions.sentry.gradle.plugin
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.navigation.safeargs.kotlin")
    id("dagger.hilt.android.plugin")
    id("jacoco")
    alias(libs.plugins.gradlePlugin.proton.environmentConfig)
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(17)
}

sentry {
    autoInstallation {
        sentryVersion = libs.versions.sentry.asProvider()
    }
}

jacoco { toolVersion = "0.8.10" }
kapt { correctErrorTypes = true }

val localProperties = Properties().apply {
    try {
        load(rootDir.resolve("local.properties").inputStream())
    } catch (e: FileNotFoundException) {
        logger.warn("No local.properties found")
    }
}

ksp {
    arg("room.generateKotlin", "false")
}

android {
    ndkVersion = Config.ndkVersion
    compileSdk = Config.compileSdk
    namespace = Config.applicationId

    kotlinOptions { jvmTarget = JavaVersion.VERSION_17.toString() }

    buildFeatures {
        dataBinding = true
        viewBinding = true
        compose = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = Config.applicationId
        minSdk = Config.minSdk
        targetSdk = Config.targetSdk
        versionCode = Config.versionCode
        versionName = Config.versionName
        testInstrumentationRunner = Config.testInstrumentationRunner
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        resourceConfigurations.addAll(Config.resourceConfigurations)

        base {
            archivesName.set(Config.archivesBaseName)
        }

        buildConfigField("String", "ACCOUNT_SENTRY_DSN", null.toBuildConfigValue())

        // performance metrics
        buildConfigField("String", "DYNAMIC_DOMAIN", "proton.black".toBuildConfigValue())
        buildConfigField("String", "LOKI_ENDPOINT", getEnvProperty("LOKI_ENDPOINT").toBuildConfigValue())
        buildConfigField("String", "LOKI_CERTIFICATE", getEnvProperty("LOKI_CERTIFICATE").toBuildConfigValue())
        buildConfigField("String", "LOKI_PRIVATE_KEY", getEnvProperty("LOKI_PRIVATE_KEY").toBuildConfigValue())

        setAssetLinksResValue("proton.me")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }

        protonEnvironment {
            apiPrefix = "calendar-api"
        }
    }

    flavorDimensions.add("env")
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Atlas Proton Calendar")

            val protonBlack = "proton.black"
            val atlasHost: String = localProperties.getProperty("HOST") ?: protonBlack
            protonEnvironment {
                proxyToken = getProxyToken()
                host = atlasHost
            }
            setAssetLinksResValue(atlasHost)
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "Proton Calendar")

            protonEnvironment {
                // If we are creating a custom build (prod build that points to scientist env)
                // do not use the default pins
                useDefaultPins = true
                apiPrefix = "calendar-api"
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")

            val sentryDsn = System.getenv("SENTRY_DSN_NEW")
            buildConfigField("String", "SENTRY_DSN_NEW", sentryDsn.toBuildConfigValue())

            val accountSentryDsn = System.getenv("ACCOUNT_SENTRY_DSN")
            buildConfigField("String", "ACCOUNT_SENTRY_DSN", accountSentryDsn.toBuildConfigValue())
        }
        debug {
            buildConfigField("String", "SENTRY_DSN_NEW", null.toBuildConfigValue())
            enableUnitTestCoverage = true
            isDebuggable = true

            if (isGitlabCI) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    packagingOptions {
        resources.excludes.add("META-INF/licenses/**")
        resources.excludes.add("META-INF/LICENSE*")
        resources.excludes.add("META-INF/AL2.0")
        resources.excludes.add("META-INF/LGPL2.1")
    }

    sourceSets {
        getByName("androidTest").java.srcDirs("src/uiTest/java", "src/androidTest/java")
        getByName("androidTest").assets.srcDirs("src/uiTest/assets")
    }
}

configurations {
    // Remove duplicate classes (keep "org.jetbrains").
    implementation.get().exclude(mapOf("group" to "com.intellij", "module" to "annotations"))
    implementation.get().exclude(mapOf("group" to "org.intellij", "module" to "annotations"))
}

dependencies {
    coreLibraryDesugaring(libs.tools.desugar)

    // Local
    implementation(project(":week-view-core"))
    implementation(files("../../proton-libs/gopenpgp/gopenpgp.aar"))

    // Hilt Android.
    implementation(libs.dagger.hilt.android)
    kapt(libs.dagger.hilt.android.compiler)
    kaptAndroidTest(libs.dagger.hilt.android.compiler)

    // Assisted Inject.
    compileOnly(libs.assistedInject)
    kapt(libs.assistedInject)

    // Retrofit
    implementation(libs.retrofit)

    // Shared preferences
    implementation(libs.androidx.preference)

    implementation(libs.biweekly)
    implementation(libs.bcrypt)
    implementation(libs.sentry)
    implementation(libs.ezVcard)
    implementation(libs.timber)
    implementation(libs.material)
    implementation(libs.tink) // it"s included in security-crypto
    implementation(libs.logging.interceptor)
    implementation(libs.sqlCipher)
    implementation(libs.guava)

    // Proton Core libraries.
    implementation(libs.core.account)
    implementation(libs.core.accountManager)
    implementation(libs.core.accountRecovery)
    implementation(libs.core.auth)
    implementation(libs.core.auth.fido)
    implementation(libs.core.biometric)
    implementation(libs.core.contact)
    implementation(libs.core.country)
    implementation(libs.core.crypto)
    implementation(libs.core.cryptoValidator)
    implementation(libs.core.data)
    implementation(libs.core.dataRoom)
    implementation(libs.core.deviceMigration)
    implementation(libs.core.domain)
    implementation(libs.core.eventManager)
    implementation(libs.core.featureFlag)
    implementation(libs.core.humanVerification)
    implementation(libs.core.key)
    implementation(libs.core.keyTransparency)
    implementation(libs.core.mailMessage)
    implementation(libs.core.mailSettings)
    implementation(libs.core.notification)
    implementation(libs.core.network)
    implementation(libs.core.observability)
    implementation(libs.core.passValidator)
    implementation(libs.core.payment)
    implementation(libs.core.paymentIap)
    implementation(libs.core.plan)
    implementation(libs.core.proguard.rules)
    implementation(libs.core.presentation)
    implementation(libs.core.push)
    implementation(libs.core.telemetry)
    implementation(libs.core.user)
    implementation(libs.core.userSettings)
    implementation(libs.core.utilAndroidDagger)
    implementation(libs.core.config.data)
    releaseImplementation(libs.core.config.dagger.staticDefaults)
    debugImplementation(libs.core.config.dagger.contentProvider)
    implementation(libs.core.utilAndroidSentry)
    implementation(libs.core.utilKotlin)
    implementation(libs.core.challenge)
    implementation(libs.core.challengePresentation)
    implementation(libs.core.userRecovery)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.layout.swiperefresh)
    implementation(libs.androidx.layout.constraint)
    implementation(libs.androidx.layout.coordinator)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime)

    // Google play review
    implementation(libs.google.play.review)
    implementation(libs.google.play.review.ktx)

    // Alpha version needed for custom language selection.
    // This should be replaced as soon as a stable version is available
    implementation(libs.androidx.appcompat.alpha)

    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.compiler)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.navigation.fragment)

    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.koin.android)
    implementation(libs.koin.android.viewmodel)
    testImplementation(libs.koin.test)

    implementation(libs.playServices.auth)

    debugImplementation(libs.leakCanary)

    testImplementation(libs.jupiter.api)
    testRuntimeOnly(libs.jupiter.engine)

    testImplementation(libs.test.mockk)
    testImplementation(libs.test.assertk.jvm)
    testImplementation(libs.core.test.kotlin)
    testImplementation(project(":shared-test-code"))
    testImplementation(libs.cash.turbine)

    androidTestImplementation(libs.test.mockk.android)
    androidTestImplementation(libs.test.androidx.core)
    androidTestImplementation(libs.test.androidx.testrunner)
    androidTestImplementation(libs.test.androidx.rules)
    androidTestImplementation(libs.test.androidx.arch)
    androidTestImplementation(libs.test.androidx.compose.ui.test.junit)
    androidTestImplementation(libs.test.fusion)
    androidTestImplementation(libs.test.androidx.ext.junit)
    androidTestImplementation(libs.core.accountRecovery.test)
    androidTestImplementation(libs.core.auth.test)
    androidTestImplementation(libs.core.plan.test)
    androidTestImplementation(libs.core.userSettings.test)
    androidTestImplementation(project(":shared-test-code"))
    androidTestImplementation(libs.dagger.hilt.android.testing)
    androidTestImplementation(libs.test.espresso.core)
    androidTestImplementation(libs.core.userRecovery.test)
    androidTestImplementation(libs.core.test.android.test.rule)
    androidTestImplementation(libs.core.test.android.test.performance)

    androidTestUtil(libs.test.androidx.orchestrator)
    androidTestUtil(libs.test.androidx.services)
    testImplementation(kotlin("test"))
}


dependencies {
    // fix for missing `beginTransactionReadOnly()` method in Room due to sqlite version mismatch
    val sqliteVersion = "2.6.1"
    implementation("androidx.sqlite:sqlite:${sqliteVersion}") {
        exclude(group = "io.sentry", module = "sentry-android-sqlite")
    }
    implementation("androidx.sqlite:sqlite-ktx:${sqliteVersion}") {
        exclude(group = "io.sentry", module = "sentry-android-sqlite")
    }
    configurations.configureEach {
        resolutionStrategy {
            force("androidx.sqlite:sqlite:${sqliteVersion}")
            force("androidx.sqlite:sqlite-ktx:${sqliteVersion}")
        }
    }
}

tasks.register("createBuildEnv") {
    fun String.toEnvVar() = replace("-", "_").toUpperCase()

    File(projectDir, "build.env").apply {
        writeText("")
        arrayOf("dev-debug", "dev-debug-androidTest", "prod-debug").forEach {
            project.buildOutputs.getByName(it).let { variant ->
                appendText("APK_PATH_${it.toEnvVar()}=\"${variant.outputFile.path}\"\n")
                appendText("APK_NAME_${it.toEnvVar()}=\"${variant.outputFile.name}\"\n")
            }
        }
        appendText("APK_VERSION=\"${Config.versionName}\"")
    }
}

tasks.withType(Test::class) {
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
    }
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Get env property first from Env, afterwards prioritize local.properties
fun getEnvProperty(key: String, defaultValue: String? = null, escapeValue: Boolean = true): String? {
    val value = System.getenv(key) ?: localProperties.getProperty(key, defaultValue)
    return if (escapeValue && value != null) {
        StringEscapeUtils.escapeJava(value)
    } else {
        value
    }
}

fun String?.toBuildConfigValue() = if (this != null) "\"$this\"" else "null"

val isGitlabCI: Boolean get() = !System.getenv("CI_SERVER_NAME").isNullOrEmpty()

object Config {
    const val applicationId = "me.proton.android.calendar"
    const val compileSdk = 36
    const val minSdk = 23
    const val ndkVersion = "21.3.6528147"
    const val targetSdk = 35
    const val versionCode = 334
    const val testInstrumentationRunner = "me.proton.android.calendar.uitest.extension.HiltTestRunner"
    const val versionName = "2.30.0"
    const val archivesBaseName = "ProtonCalendar-$versionName($versionCode)"
    val resourceConfigurations
        get() = listOf(
            "en", // English
            "ca", // Catalan
            "cs", // Czech
            "da", // Danish
            "de", // German
            "es-rES", // Spanish (Spain)
            "b+es+419", // Spanish (Latin America)
            "es-rMX", // Spanish (Mexico)
            "fr", // French
            "fi", // Finnish
            "hu", // Hungarian
            "it", // Italian
            "in", // Indonesian, not supported by Locale class starting from Android 15, replaced with "id"
            "nl", // Nederlands
            "pl", // Polish
            "pt-rBR", // Portuguese (Brazil)
            "pt-rPT", // Portuguese (Portugal)
            "ro", // Romanian
            "sv-rSE", // Swedish
            "tr", // Turkish
            "be", // Belarusian
            "ru", // Russian
            "uk", // Ukrainian
            "ka", // Georgian
            "hi", // Hindi
            "zh-rTW", // Chinese Traditional (Taiwan)
        )
}

fun getProxyToken(): String {
    val proxyTokenUrl = System.getenv("ATLAS_PROXY_URL") ?: return ""
    return getTokenFromCurl(proxyTokenUrl)
}

fun VariantDimension.setAssetLinksResValue(host: String) {
    resValue(
        type = "string", name = "asset_statements",
        value = """
            [{
              "relation": ["delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"],
              "target": { "namespace": "web", "site": "https://$host" }
            }]
        """.trimIndent()
    )
}
