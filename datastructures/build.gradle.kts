plugins {
    `maven-publish`
    `modelix-kotlin-multiplatform`
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlin-utils"))
                api(project(":streams"))
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlincrypto.sha2)
                implementation(libs.kase64)
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
