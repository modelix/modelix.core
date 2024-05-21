plugins {
    `maven-publish`
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js(IR) {
        browser {
            testTask {
                useMocha {
                    timeout = "30s"
                }
            }
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "30s"
                }
            }
        }
        useCommonJs()
    }
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
