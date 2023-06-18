
plugins {
    id("maven-publish")
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.ktlint)
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
        useCommonJs()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.kotlin.coroutines.core)
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
                implementation(libs.kotlin.coroutines.core)
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
                implementation(libs.kotlin.coroutines.core)
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
        dependsOn(":ts-model-api:patchKotlinExternals")
    }
}
