import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `modelix-kotlin-multiplatform`
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.logging)
                api(project(":modelql-core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                api(libs.ktor.server.html.builder)
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll("-Xjvm-default=all-compatibility", "-Xcontext-receivers")
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon>().all {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}
