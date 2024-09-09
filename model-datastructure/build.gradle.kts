plugins {
    `maven-publish`
    `modelix-kotlin-multiplatform`
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":kotlin-utils"))
                api(project(":model-api"))
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.datetime)
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
        val jsMain by getting {
            dependencies {
                implementation(npm("@aws-crypto/sha256-js", "^5.0.0"))
            }
        }
    }
}
