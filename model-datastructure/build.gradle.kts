plugins {
    `maven-publish`
    id("org.jetbrains.kotlin.multiplatform")
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
    @Suppress("UNUSED_VARIABLE", "KotlinRedundantDiagnosticSuppress")
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":kotlin-utils"))
                api(project(":model-api"))
//                api(project(":model-server-api"))
//                kotlin("stdlib-common")
//                implementation(libs.kotlin.collections.immutable)
//                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.datetime)
//                implementation(libs.kotlin.serialization.json)
//                implementation(libs.ktor.client.core)
//                implementation(libs.ktor.client.content.negotiation)
//                implementation(libs.ktor.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
//                kotlin("stdlib-jdk8")
//
//                implementation(libs.vavr)
//                implementation(libs.apache.commons.lang)
//                implementation(libs.guava)
//                implementation(libs.apache.commons.io)
//                implementation("org.json:json:20230618")
//                implementation(libs.trove4j)
                implementation(libs.apache.commons.collections)
//
//                implementation("com.google.oauth-client:google-oauth-client:1.34.1")
//                implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
//
//                implementation(libs.ktor.client.core)
//                implementation(libs.ktor.client.cio)
//                implementation(libs.ktor.client.auth)
//                implementation(libs.ktor.client.content.negotiation)
//                implementation(libs.ktor.serialization.json)
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }
        val jsMain by getting {
            dependencies {
//                implementation(kotlin("stdlib-js"))
//                implementation(npm("uuid", "^8.3.0"))
//                implementation(npm("js-sha256", "^0.9.0"))
                implementation(npm("@aws-crypto/sha256-js", "^5.0.0"))
//                implementation(npm("js-base64", "^3.4.5"))
            }
        }
        val jsTest by getting {
            dependencies {
            }
        }
    }
}
