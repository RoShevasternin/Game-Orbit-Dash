import com.android.build.api.dsl.ApplicationBuildType

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace  = "com.lewydo.orbitdash"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lewydo.orbitdash"
        minSdk      = 24
        targetSdk   = 37
        versionCode = 6
        versionName = "1.0.1-test" // test

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Field ------------------------------------------------------------------------
        //buildConfigField("String", "TIKTOK_APP_SECRET", "\"aaa\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Field ------------------------------------------------------------------------
            admob("debug")
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Field ------------------------------------------------------------------------
            admob("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
    }
    sourceSets {
        getByName("main") {
            jniLibs.directories.add("libs")
            res.directories += setOf("src/main/res", "src/main/res/launcher")
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvm.get()))
    }
}

val natives: Configuration = configurations.create("natives") {
    isCanBeConsumed = false   // цю конфігурацію не публікуємо назовні
    isCanBeResolved = true    // але самі резолвимо, щоб дістати .so з jar-ів
}

dependencies {
    // Усі версії — у gradle/libs.versions.toml. Тут лише ЩО підключаємо.

    // Modules --------------------------------------------------------------------------
    implementation(project(":engine"))

    // Test Core ------------------------------------------------------------------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // AndroidX Core ------------------------------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.ktx)
    implementation(libs.androidx.datastore)

    // LibGDX Core ------------------------------------------------------------------------
    implementation(libs.gdx.backend.android)
    implementation(libs.gdx.freetype)

    // Натівні .so на кожен ABI. Класифікатор не вміщається в каталог — додаємо тут
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
        natives(variantOf(libs.gdx.platform)          { classifier("natives-$abi") })
        natives(variantOf(libs.gdx.freetype.platform) { classifier("natives-$abi") })
    }

    // Other Core ------------------------------------------------------------------------
    implementation(libs.shapedrawer)
    implementation(libs.kotlinx.serialization.json)

    // Other ------------------------------------------------------------------------

    // Firebase — версію кожної бібліотеки задає BOM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.messaging)

    // TikTok
    implementation(libs.tiktok.business.sdk)

    // Billing
    implementation(libs.billing.ktx)

    // Install Referrer
    implementation(libs.installreferrer)

    // AdMob
    implementation(libs.play.services.ads)

    // Gson (парсинг JSON з Gist)
    implementation(libs.gson)

    // Google Play Services v2
    implementation(libs.play.services.games.v2)
}

tasks.register("copyAndroidNatives") {
    description = "Розпаковує .so з natives-jar у libs/<abi>"
    doFirst {
        natives.files.forEach { jar ->
            val outputDir = file("libs/" + jar.nameWithoutExtension.substringAfterLast("natives-"))
            outputDir.mkdirs()
            copy {
                from(zipTree(jar))
                into(outputDir)
                include("*.so")
            }
        }
    }
}
tasks.configureEach {
    if ("package" in name) {
        dependsOn("copyAndroidNatives")
    }
}

// ------------------------------------------------------------------------
// Helper
// ------------------------------------------------------------------------

// ID AdMob для типу збірки — з gradle.properties (admob.<type>.*)
fun ApplicationBuildType.admob(type: String) {
    fun id(key: String) = providers.gradleProperty("admob.$type.$key").orNull
        ?: error("Missing 'admob.$type.$key' in gradle.properties")

    manifestPlaceholders["admobAppId"] = id("appId")
    buildConfigField("String", "ADMOB_BANNER_ID",   "\"${id("bannerId")}\"")
    buildConfigField("String", "ADMOB_REWARDED_ID", "\"${id("rewardedId")}\"")
}