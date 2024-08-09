plugins {
    `modelix-kotlin-multiplatform`
    kotlin("plugin.serialization")
}

val mpsExtensionsVersion: String by rootProject

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            languageSettings.optIn("kotlin.js.JsExport")
        }
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.serialization.json)
                api(project(":model-api"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlin.serialization.yaml)
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }
        val jsTest by getting {
            dependencies {
            }
        }
    }
}

description = "Runtime for the meta model generator"
