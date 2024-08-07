/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
    jvm()
    js(IR) {
        browser {
            testTask {
                useMocha {
                    timeout = "60s"
                }
            }
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "60s"
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
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        val jvmTest by getting {
            dependencies {

                implementation(project(":model-client", configuration = "jvmRuntimeElements"))
                implementation(project(":modelql-server"))
                implementation(project(":modelql-html"))

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
                implementation(libs.logback.classic)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(npm("jsdom-global", "3.0.2"))
                implementation(npm("jsdom", "20.0.2"))
            }
        }
    }
}
