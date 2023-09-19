plugins {
    `maven-publish`
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    js(IR) {
        browser {}
        nodejs {
            testTask(
                Action {
                    useMocha {
                        timeout = "30s"
                    }
                },
            )
        }
        useCommonJs()
    }
    @Suppress("UNUSED_VARIABLE", "KotlinRedundantDiagnosticSuppress")
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
