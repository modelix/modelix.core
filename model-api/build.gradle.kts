
plugins {
    id("maven-publish")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jlleitschuh.gradle.ktlint")
    kotlin("plugin.serialization")
}

configurations {
    ktlint
}

description = "API to access models stored in Modelix"

ktlint {
    disabledRules.add("no-wildcard-imports")
    outputToConsole.set(true)
    this.filter {
        this.exclude {
            it.file.toPath().toAbsolutePath().startsWith(project(":ts-model-api").buildDir.toPath().toAbsolutePath())
        }
    }
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

val kotlinLoggingVersion: String by rootProject
val kotlinxSerializationVersion: String by rootProject

kotlin {
    jvm()
    js(IR) {
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
                implementation(kotlin("stdlib-common"))
                implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
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
                api(npm("@modelix/ts-model-api", rootDir.resolve("ts-model-api")))
            }
            kotlin.srcDir(rootDir.resolve("ts-model-api").resolve("build/dukat"))
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

listOf("sourcesJar", "runKtlintCheckOverJsMainSourceSet", "jsSourcesJar", "jsPackageJson", "compileKotlinJs", "jsProcessResources").forEach {
    tasks.named(it) {
        dependsOn(":ts-model-api:npm_run_build")
        dependsOn(":ts-model-api:npm_run_generateKotlin")
    }
}
