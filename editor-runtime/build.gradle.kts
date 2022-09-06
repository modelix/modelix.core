plugins {
    kotlin("multiplatform")
    `maven-publish`
}

val kotlinVersion: String by rootProject
val kotlinCoroutinesVersion: String by rootProject
val ktorVersion: String by rootProject
val kotlinLoggingVersion: String by rootProject
val modelApiVersion: String by rootProject

kotlin {
    jvm()
    js() {
        browser {}
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.modelix:metamodel-runtime:$modelApiVersion")
                implementation(kotlin("stdlib-common"))
                implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinCoroutinesVersion")
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$kotlinCoroutinesVersion")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}