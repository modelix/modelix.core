plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val kotlinVersion: String by rootProject
val kotlinLoggingVersion: String by rootProject
val mpsExtensionsVersion: String by rootProject

kotlin {
    jvm()
    js(IR) {
        browser {
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            languageSettings.optIn("kotlin.js.JsExport")
        }
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
                api(project(":model-api"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.charleskorn.kaml:kaml:0.48.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

description = "Runtime for the meta model generator"