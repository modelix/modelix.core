plugins {
    `maven-publish`
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

description = "API to access models stored in Modelix"

ktlint {
    filter {
        exclude {
            val kotlinGeneratedFromTypeScript =
                project(":ts-model-api").layout.buildDirectory.get().asFile.toPath().toAbsolutePath()
            it.file.toPath().toAbsolutePath().startsWith(kotlinGeneratedFromTypeScript)
        }
    }
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
                api(project(":kotlin-utils"))
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.kotlin.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
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
            }
        }
    }
}

listOf(
    "sourcesJar",
    "runKtlintCheckOverJsMainSourceSet",
    "jsSourcesJar",
    "jsPackageJson",
    "compileKotlinJs",
    "jsProcessResources",
).forEach {
    tasks.named(it) {
        dependsOn(":ts-model-api:npm_run_build")
        dependsOn(":ts-model-api:patchKotlinExternals")
    }
}
