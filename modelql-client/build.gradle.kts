plugins {
    kotlin("multiplatform")
    `maven-publish`
    alias(libs.plugins.ktlint)
}

kotlin {
    jvm()
    js(IR) {
        browser {}
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
        useCommonJs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":model-api"))
                implementation(project(":model-api-gen-runtime"))
                implementation(project(":modelql-core"))
                implementation(project(":modelql-untyped"))

                implementation(libs.ktor.client.core)
                implementation(libs.kotlin.stdlib.common)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlin.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))

                implementation(project(":model-client", configuration = "jvmRuntimeElements"))
                implementation(project(":model-server-lib"))

                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cors)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.html.builder)
                implementation(libs.ktor.server.auth)
                implementation(libs.ktor.server.auth.jwt)
                implementation(libs.ktor.server.status.pages)
                implementation(libs.ktor.server.forwarded.header)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.test.host)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                implementation(npm("jsdom-global", "3.0.2"))
                implementation(npm("jsdom", "20.0.2"))
            }
        }
    }
}
