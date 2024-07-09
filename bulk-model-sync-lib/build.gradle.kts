plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(11)
    js(IR) {
        browser {
            testTask {
                useMocha {
                    timeout = "60s"
                }
            }
        }
    }
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":model-api"))
                implementation(libs.kotlin.serialization.json)
                implementation(libs.kotlin.logging)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.trove4j)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":model-api"))
                implementation(libs.kotlin.serialization.json)
                implementation(project(":model-client"))
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":model-client", configuration = "jvmRuntimeElements"))
            }
        }
    }
}
