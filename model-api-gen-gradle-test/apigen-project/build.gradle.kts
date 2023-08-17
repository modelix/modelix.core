
buildscript {
    repositories {
        mavenLocal()
        maven { url = uri("https://repo.maven.apache.org/maven2") }
        maven { url = uri("https://plugins.gradle.org/m2/") }
        mavenCentral()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
    }

    dependencies {
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("base")
    id("org.modelix.model-api-gen")
}

val mps by configurations.creating
val mpsDir = buildDir.resolve("mps")
val modelixCoreVersion: String = projectDir.resolve("../../version.txt").readText()
val kotlinGenDir = buildDir.resolve("apigen/kotlin_gen")

dependencies {
    mps("com.jetbrains:mps:2021.1.4")
}


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
        useCommonJs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.modelix:model-api-gen-runtime:$modelixCoreVersion")
                api("org.modelix:modelql-typed:$modelixCoreVersion")
                api("org.modelix:modelql-untyped:$modelixCoreVersion")
            }
            kotlin {
                srcDir(kotlinGenDir)
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

val resolveMps by tasks.registering(Sync::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}

metamodel {
    mpsHeapSize = "2g"
    dependsOn(resolveMps)
    mpsHome = mpsDir
    kotlinDir = kotlinGenDir
    modelqlKotlinDir = kotlinGenDir
    kotlinProject = project
    generateKotlinCommon()
    typescriptDir = projectDir.resolve("../typescript-project/src/gen")
    npmPackageName = "@modelix/api-gen-test-kotlin-project"
    includeNamespace("jetbrains")
    exportModules("jetbrains.mps.baseLanguage")

    names {
        languageClass.prefix = "L_"
        languageClass.baseNameConversion = { it.replace(".", "_") }
        typedNode.prefix = ""
        typedNodeImpl.suffix = "Impl"
    }
    registrationHelperName = "org.modelix.apigen.test.ApigenTestLanguages"
}
