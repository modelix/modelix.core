plugins {
    `modelix-kotlin-multiplatform`
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":streams"))
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.serialization.json)
                api(libs.kotlin.coroutines.core)
                api(libs.ktor.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
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
//                implementation(npm("jsdom-global", "3.0.2"))
//                implementation(npm("jsdom", "20.0.2"))
            }
        }
    }
}
