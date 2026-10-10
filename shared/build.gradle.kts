plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.sqldelight)
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
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    add("androidHostTestRuntimeOnly", libs.secp256k1.jvm)
    add("androidHostTestImplementation", libs.sqldelight.jdbc)
}

sqldelight {
    databases {
        register("NookDatabase") {
            packageName.set("io.maxdoe.nook.data.db")
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
    parallel = true
}

tasks.named<dev.detekt.gradle.Detekt>("detekt") {
    setSource(files("src/commonMain/kotlin", "src/commonTest/kotlin"))
}

ktlint {
    filter {
        exclude { it.file.path.contains("build") && it.file.path.contains("generated") }
    }
}
