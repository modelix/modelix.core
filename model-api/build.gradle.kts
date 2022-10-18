
plugins {
    id("maven-publish")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jlleitschuh.gradle.ktlint")
}

configurations {
    ktlint
}

description = "API to access models stored in Modelix"

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "11"
    }
}

ktlint {
    disabledRules.add("no-wildcard-imports")
    outputToConsole.set(true)
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.dukat.IntegratedDukatTask> {
    dependsOn(":ts-model-api:npm_run_build")
}

tasks.named("sourcesJar") {
    dependsOn("jsIrGenerateExternalsIntegrated")
}

kotlin {
    jvm()
    js(BOTH) {
        nodejs {
            testTask {
                useMocha {
                    timeout = "10000"
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("io.github.microutils:kotlin-logging:2.1.23")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
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
                implementation(kotlin("stdlib-js"))
                api(npm("ts-model-api", rootDir.resolve("ts-model-api")))
                api(npm("@types/ts-model-api", rootDir.resolve("ts-model-api"), generateExternals = true))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
