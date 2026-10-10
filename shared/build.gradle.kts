plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    android {
        namespace = "io.maxdoe.nook.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.quartz)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    add("androidHostTestRuntimeOnly", libs.secp256k1.jvm)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
    parallel = true
}

tasks.named<dev.detekt.gradle.Detekt>("detekt") {
    setSource(files("src/commonMain/kotlin", "src/commonTest/kotlin"))
}
