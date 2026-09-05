plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

android {
    namespace = "com.fethica.popupbar"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
    publishing { singleVariant("release") { withSourcesJar() } }
    // AGP 9 built-in Kotlin: the Kotlin DSL lives under android { kotlin { } }.
    // If `explicitApi()` is not resolvable there, use
    // compilerOptions { freeCompilerArgs.add("-Xexplicit-api=strict") } in the same block.
    kotlin {
        explicitApi()
    }
}

dependencies {
    // `api`, not `implementation`: the published API hands consumers Modifier, Color, Shape,
    // PaddingValues, RowScope, TextStyle, AnimationSpec and Saver, and PopupHost installs a
    // PredictiveBackHandler. A consumer that only declares this library must still be able to name
    // those types, and must resolve them at the same versions — hence the BOM is `api` too.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.activity.compose)
    // Internal only (the close button's two glyphs), so it stays off the consumer's compile path.
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

group = property("GROUP") as String
// On JitPack (JITPACK=true) the version must follow the tag being built (VERSION), so an rc tag does not publish a POM versioned with the release VERSION_NAME.
version = if (System.getenv("JITPACK") == "true" && !System.getenv("VERSION").isNullOrBlank()) {
    System.getenv("VERSION")
} else {
    property("VERSION_NAME") as String
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "compose-popupbar"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("compose-popupbar")
                description.set("LNPopupController-style popup bar and interactive popup for Jetpack Compose")
                licenses { license { name.set("MIT") } }
            }
        }
    }
}
