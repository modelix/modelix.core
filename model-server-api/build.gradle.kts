plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
    jvm()
    js(IR) {
        browser {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
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
//                implementation(project(":model-api"))
//                implementation(project(":metamodel-runtime"))
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.serialization.json)
                api(libs.kotlin.coroutines.core)
                api(libs.ktor.serialization)
//                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")

//                implementation("io.ktor:ktor-client-core:$ktorVersion")
//                implementation("io.ktor:ktor-client-cio:$ktorVersion")
//                implementation("io.ktor:ktor-client-auth:$ktorVersion")
//                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
//                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
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
//                implementation(npm("jsdom-global", "3.0.2"))
//                implementation(npm("jsdom", "20.0.2"))
            }
        }
    }
}
