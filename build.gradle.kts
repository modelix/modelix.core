plugins {
    kotlin("multiplatform") apply false
    kotlin("plugin.serialization") apply false
    `maven-publish`
    id("com.palantir.git-version") version "0.13.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0" apply false
    id("com.diffplug.spotless") version "5.0.0" apply false
    id("com.dorongold.task-tree") version "2.1.0"
}

group = "org.modelix"
description = "Projectional Editor"
version = computeVersion()
println("Version: $version")

fun computeVersion(): Any {
    val versionFile = file("version.txt")
    val gitVersion: groovy.lang.Closure<String> by extra
    return if (versionFile.exists()) {
        versionFile.readText().trim()
    } else {
        gitVersion().let { if (it.endsWith("-SNAPSHOT")) it else "$it-SNAPSHOT" }.also { versionFile.writeText(it) }
    }
}

subprojects {
    apply(plugin = "maven-publish")
    version = rootProject.version
    group = rootProject.group

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
        }
    }

    repositories {
        mavenLocal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
        mavenCentral()
    }

    publishing {
        repositories {
            maven {
                name = "itemis"
                url = if (version.toString().contains("SNAPSHOT")) {
                    uri("https://artifacts.itemis.cloud/repository/maven-mps-snapshots/")
                } else {
                    uri("https://artifacts.itemis.cloud/repository/maven-mps-releases/")
                }
                credentials {
                    username = project.findProperty("artifacts.itemis.cloud.user").toString()
                    password = project.findProperty("artifacts.itemis.cloud.pw").toString()
                }
            }
            maven {
                name = "GitHubPackages"
                // we moved some components from modelix/modelix to modelix/modelix.core but github packages
                // cannot handle this situation. basically we suffer from what is described here:
                //     https://github.com/orgs/community/discussions/23474
                // this is a simple workaround for the affected components.
                // consequently, when obtaining these dependencies, the repo url is the old modelix/modelix one...
                if (project.name in arrayOf("model-client",
                                "model-client-js",
                                "model-client-jvm",
                                "model-server",
                                "model-server-api")) {
                    url = uri("https://maven.pkg.github.com/modelix/modelix")
                } else {
                    url = uri("https://maven.pkg.github.com/modelix/modelix.core")
                }
                credentials {
                    username = project.findProperty("gpr.user") as? String ?: System.getenv("GITHUB_ACTOR")
                    password = project.findProperty("gpr.key") as? String ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask> {
    dependsOn(":ts-model-api:npm_run_build")
}
