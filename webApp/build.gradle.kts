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
