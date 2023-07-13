plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
    alias(libs.plugins.ktlint)
}

apply(plugin = "kotlinx-atomicfu")

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

val generateVersionVariable by tasks.creating {
    doLast {
        val outputDir = buildDir.resolve("version_gen/org/modelix/modelql/core")
        outputDir.mkdirs()
        outputDir.resolve("Version.kt").writeText(
            """
            package org.modelix.modelql.core
            
            const val modelqlVersion: String = "$version"
            
            """.trimIndent()
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    dependsOn(generateVersionVariable)
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon>().all {
    dependsOn(generateVersionVariable)
    kotlinOptions {
    }
}
