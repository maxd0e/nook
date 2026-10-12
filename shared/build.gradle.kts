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
    wasmJs {
        browser()
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.quartz)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.quartz)
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js.wasm)
            implementation(npm("nostr-tools", "2.25.2"))
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
