plugins {
    `maven-publish`
    id("org.jetbrains.kotlin.multiplatform")
    id("com.diffplug.spotless")
    `java-library`
    jacoco
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

val kotlinCoroutinesVersion: String by rootProject
val kotlinLoggingVersion: String by rootProject
val ktorVersion: String by rootProject
val kotlinxSerializationVersion: String by rootProject

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
                api(project(":model-api"))
                api(project(":model-datastructure"))
                api(project(":model-server-api"))
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.collections.immutable)
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.datetime)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))

                implementation(libs.vavr)
                implementation(libs.apache.commons.lang)
                implementation(libs.guava)
                implementation(libs.apache.commons.io)
                implementation("org.json:json:20230618")
                implementation(libs.trove4j)
                implementation(libs.apache.commons.collections)

                implementation("com.google.oauth-client:google-oauth-client:1.34.1")
                implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(npm("uuid", "^8.3.0"))
                implementation(npm("js-sha256", "^0.9.0"))
                implementation(npm("js-base64", "^3.4.5"))
            }
        }
    }
}

tasks.jacocoTestReport {
    classDirectories.setFrom("$buildDir/classes/kotlin/jvm/")
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))
    executionData.setFrom(files("$buildDir/jacoco/jvmTest.exec"))

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
