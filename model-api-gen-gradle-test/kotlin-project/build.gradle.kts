
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

repositories {
    mavenLocal()
    maven { url = uri("https://repo.maven.apache.org/maven2") }
    maven { url = uri("https://plugins.gradle.org/m2/") }
    mavenCentral()
    maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("base")
    alias(libs.plugins.npm.publish)
}

val mps by configurations.creating

fun scriptFile(relativePath: String): File {
    return file("$rootDir/build/$relativePath")
}

val mpsDir = buildDir.resolve("mps")

val modelixCoreVersion: String = projectDir.resolve("../../version.txt").readText()

val kotlinGenDir = buildDir.resolve("metamodel/kotlin_gen")

kotlin {
    jvm()
    js(IR) {
        browser {}
        nodejs {}
        useCommonJs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.reflect)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.serialization.json)
                api(libs.kotlin.coroutines.core)
            }
            kotlin.srcDir(buildDir.resolve("version_gen"))
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.coroutines.test)
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
                implementation(project(":apigen-project"))

                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(kotlin("test"))
                implementation(kotlin("reflect"))

                implementation("org.modelix:model-api:$modelixCoreVersion")
                implementation("org.modelix:model-client:$modelixCoreVersion")
                implementation("org.modelix:model-server-lib:$modelixCoreVersion")
                implementation("org.modelix:modelql-client:$modelixCoreVersion")

                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cors)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.html.builder)
                implementation(libs.ktor.server.auth)
                implementation(libs.ktor.server.auth.jwt)
                implementation(libs.ktor.server.status.pages)
                implementation(libs.ktor.server.forwarded.header)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.test.host)
                implementation(libs.logback.classic)                
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

npmPublish {
    packages {
        named("js") {
            packageJson {
                name.set("@modelix/api-gen-test-kotlin-project")
            }
        }
    }
}
