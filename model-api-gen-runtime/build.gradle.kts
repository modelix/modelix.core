plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.npm.publish)
}

val mpsExtensionsVersion: String by rootProject

kotlin {
    jvm()
    js(IR) {
        browser {
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
        binaries.library()
        generateTypeScriptDefinitions()
        useCommonJs()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            languageSettings.optIn("kotlin.js.JsExport")
        }
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.serialization.json)
                implementation(project(":kotlin-utils"))
                api(project(":model-api"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlin.serialization.yaml)
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
            }
        }
    }
}

description = "Runtime for the meta model generator"

npmPublish {
    registries {
        register("itemis-npm-open") {
            uri.set("https://artifacts.itemis.cloud/repository/npm-open")
            System.getenv("NODE_AUTH_TOKEN").takeIf { !it.isNullOrBlank() }?.let {
                authToken.set(it)
            }
        }
    }
    packages {
        named("js") {
            packageJson {
                name.set("@modelix/model-api-gen-runtime")
                homepage.set("https://modelix.org/")
                repository {
                    type.set("git")
                    url.set("https://github.com/modelix/modelix.core.git")
                    directory.set(project.name)
                }
            }
        }
    }
}
