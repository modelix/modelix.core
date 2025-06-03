plugins {
    `maven-publish`
    `modelix-kotlin-multiplatform`
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":kotlin-utils"))
                api(project(":model-api"))
                api(project(":datastructures"))
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.datetime)
                implementation(libs.kotlin.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.trove4j)
                implementation(libs.apache.commons.collections)
                implementation(libs.kotlin.coroutines.core)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.logback.classic)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("@aws-crypto/sha256-js", "^5.0.0"))
            }
        }
    }
}
