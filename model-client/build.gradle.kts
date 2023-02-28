plugins {
    `maven-publish`
    id("org.jetbrains.kotlin.multiplatform")
    id("com.diffplug.spotless")
    id("org.jlleitschuh.gradle.ktlint")
    `java-library`
    jacoco
}

configurations {
    ktlint
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

ktlint {
    disabledRules.add("no-wildcard-imports")
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

val kotlinCoroutinesVersion: String by rootProject
val kotlinLoggingVersion: String by rootProject
val ktorVersion: String by rootProject

kotlin {
    jvm()
    js(BOTH) {
        // browser {}
        nodejs {
            testTask {
                useMocha {
                    timeout = "30s"
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":model-api"))
                kotlin("stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
                implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
            }
        }
        val commonTest by getting {
            dependencies {
                kotlin("test-common")
                kotlin("test-annotations-common")
            }
        }
        val jvmMain by getting {
            dependencies {
                kotlin("stdlib-jdk8")

                implementation("io.vavr:vavr:0.10.3")
                implementation("org.apache.commons:commons-lang3:3.11")
                implementation("com.google.guava:guava:29.0-jre")
                implementation("org.glassfish.jersey.core:jersey-client:2.31")
                implementation("org.glassfish.jersey.inject:jersey-hk2:2.31")
                implementation("org.glassfish.jersey.media:jersey-media-sse:2.31")
                implementation("javax.xml.bind:jaxb-api:2.3.1")
                implementation("commons-io:commons-io:2.7")
                implementation("org.json:json:20200518")
                implementation("net.sf.trove4j:trove4j:3.0.3")
                implementation("org.apache.commons:commons-collections4:4.4")

                val oauthVersion = "1.34.1"
                implementation("com.google.oauth-client:google-oauth-client:$oauthVersion")
                implementation("com.google.oauth-client:google-oauth-client-jetty:$oauthVersion")

                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("io.ktor:ktor-client-auth:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
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
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
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

spotless {
    kotlin {
        licenseHeader(
            "/*\n" +
                """ * Licensed under the Apache License, Version 2.0 (the "License");""" + "\n" +
                """ * you may not use this file except in compliance with the License.""" + "\n" +
                """ * You may obtain a copy of the License at""" + "\n" +
                """ *""" + "\n" +
                """ *  http://www.apache.org/licenses/LICENSE-2.0""" + "\n" +
                """ *""" + "\n" +
                """ * Unless required by applicable law or agreed to in writing,""" + "\n" +
                """ * software distributed under the License is distributed on an""" + "\n" +
                """ * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY""" + "\n" +
                """ * KIND, either express or implied.  See the License for the""" + "\n" +
                """ * specific language governing permissions and limitations""" + "\n" +
                """ * under the License. """ + "\n" +
                " */\n" +
                "\n"
        )
    }
}
