plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js(IR) {
        browser {
            testTask {
                useMocha {
                    timeout = "30s"
                }
            }
        }
    }
    jvm {
        jvmToolchain(11)
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
