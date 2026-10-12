plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "nook.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared"))
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
    parallel = true
}

ktlint {
    filter {
        exclude { it.file.path.contains("build") && it.file.path.contains("generated") }
    }
}
