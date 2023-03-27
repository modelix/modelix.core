
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
    }
}

ktlint {
    disabledRules.add("no-wildcard-imports")
    outputToConsole.set(true)
    this.filter {
        this.exclude {
            it.file.toPath().toAbsolutePath().startsWith(buildDir.toPath().toAbsolutePath())
        }
    }
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

tasks.named("sourcesJar") {
    dependsOn("jsGenerateExternalsIntegrated")
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
                api(npm("@modelix/ts-model-api", rootDir.resolve("ts-model-api"), generateExternals = true))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

tasks.named("runKtlintCheckOverJsMainSourceSet") {
    dependsOn("jsGenerateExternalsIntegrated")
}

tasks.named("jsSourcesJar") {
    dependsOn("jsGenerateExternalsIntegrated")
}
