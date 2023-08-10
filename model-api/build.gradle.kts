plugins {
    id("maven-publish")
    id("org.jetbrains.kotlin.multiplatform")
    kotlin("plugin.serialization")
}

description = "API to access models stored in Modelix"

ktlint {
    filter {
        exclude {
            it.file.toPath().toAbsolutePath().startsWith(project(":ts-model-api").buildDir.toPath().toAbsolutePath())
        }
    }
}

kotlin {
    jvm()
    js(IR) {
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
        useCommonJs()
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
        }
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.kotlin.coroutines.core)
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
                implementation(kotlin("stdlib-jdk8"))
                implementation(libs.kotlin.coroutines.core)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(libs.kotlin.coroutines.core)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
