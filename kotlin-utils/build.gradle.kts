plugins {
    `maven-publish`
    `modelix-kotlin-multiplatform`
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlin.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.trove4j)
                implementation(libs.guava)
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinJs)
            }
        }
        val jsTest by getting {
            dependencies {
            }
        }
    }
}
