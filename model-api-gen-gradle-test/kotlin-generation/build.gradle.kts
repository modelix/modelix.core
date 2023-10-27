plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.npm.publish)
}

val modelixCoreVersion: String = projectDir.resolve("../../version.txt").readText()

kotlin {
    jvm()
    js(IR) {
        browser {}
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
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.modelix:model-api-gen-runtime:$modelixCoreVersion")
                implementation("org.modelix:modelql-typed:$modelixCoreVersion")
                implementation("org.modelix:modelql-untyped:$modelixCoreVersion")
                implementation("org.modelix:model-client:$modelixCoreVersion")
            }
            kotlin.srcDir(layout.buildDirectory.dir("kotlin_gen"))
        }
        val commonTest by getting {
            dependencies {
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
            }
        }
    }
}

tasks.all {
    if (name.contains("compileKotlin")) {
        dependsOn(":metamodel-export:generateMetaModelSources")
    }
}

npmPublish {
    packages {
        named("js") {
            packageJson {
                name.set("@modelix/model-client")
                version.set("1.0.0")
            }
        }
    }
}
