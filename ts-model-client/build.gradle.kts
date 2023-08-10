plugins {
    kotlin("multiplatform")
    `maven-publish`
    alias(libs.plugins.npm.publish)
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }
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
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
        }
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.logging)
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
                //implementation(npm("@modelix/ts-model-api", rootDir.resolve("ts-model-api")))
                //implementation(npm("@modelix/ts-model-api", "$version"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

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
                name.set("@modelix/ts-model-client")
            }
        }
    }
}
