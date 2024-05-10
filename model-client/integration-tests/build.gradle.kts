// Tests for a model client that cannot run in isolation.
// One such case is starting a server and using the model client from JS at the same time.
// This integration tests start a mock server with Docker compose.
//
// They are in a subproject so that they can be easily run in isolation or be excluded.
// An alternative to a separate project would be to have a custom compilation.
// I failed to configure custom compilation and for now, subproject was more straightforward configuration.
// See https://kotlinlang.org/docs/multiplatform-configure-compilations.html#create-a-custom-compilation

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.docker.compose)
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
        val commonTest by getting {
            dependencies {
                implementation(project(":model-client"))
                implementation(libs.ktor.client.core)
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(project(":model-client", configuration = "jvmRuntimeElements"))
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

dockerCompose.isRequiredBy(tasks.named("jsBrowserTest"))
dockerCompose.isRequiredBy(tasks.named("jsNodeTest"))
dockerCompose.isRequiredBy(tasks.named("jvmTest"))
