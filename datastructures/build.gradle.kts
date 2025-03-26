plugins {
    `maven-publish`
    `modelix-kotlin-multiplatform`
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlin-utils"))
                api(project(":streams"))
                implementation(libs.kotlin.serialization.core)
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlincrypto.sha2)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.trove4j)
                implementation(libs.apache.commons.collections)
                implementation(libs.kotlin.coroutines.core)
            }
        }
        jsMain {
            dependencies {
            }
        }
    }
}
