// ═════════════════════════════════════════════════════════════════════════════
//  :engine — правила бігу. Чистий Kotlin/JVM: без Android, без libGDX.
//
//  Порожній dependencies — це і є суть модуля. Кожна залежність, додана сюди,
//  розширює те, що рушію ДОЗВОЛЕНО знати. Перш ніж додати — див. CLAUDE.md,
//  «Розміщення файлів».
// ═════════════════════════════════════════════════════════════════════════════
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// JVM 11 — як в :app. Вище не можна: D8 у складі app мусить прожувати цей байткод
java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvm.get()))
    }
}

dependencies {
    testImplementation(libs.junit)
}