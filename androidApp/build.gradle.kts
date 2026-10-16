plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val releaseStoreFile = providers.gradleProperty("nookReleaseStoreFile").orNull
val releaseStorePassword = providers.gradleProperty("nookReleaseStorePassword").orNull
val releaseKeyAlias = providers.gradleProperty("nookReleaseKeyAlias").orNull
val releaseKeyPassword = providers.gradleProperty("nookReleaseKeyPassword").orNull

android {
    namespace = "io.maxdoe.nook"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.maxdoe.nook"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig =
                signingConfigs.create("release") {
                    storeFile = releaseStoreFile?.let(::file)
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.quartz)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.sqldelight.android)
    implementation(libs.ktor.client.core)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
    parallel = true
}
