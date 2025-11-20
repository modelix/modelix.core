import com.github.gradle.node.NodeExtension
import com.github.gradle.node.NodePlugin
import dev.petuska.npm.publish.task.NpmPublishTask
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.kotlin.dsl.withType

plugins {
    `maven-publish`
    `version-catalog`
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.gitVersion)
    alias(libs.plugins.node) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.npm.publish) apply false
}

group = "org.modelix"
description = "Projectional Editor"
version = computeVersion()
println("Version: $version")
val isPrerelease = !version.toString().matches(Regex("""\d+\.\d+.\d+"""))

fun computeVersion(): Any {
    val versionFile = file("version.txt")
    val gitVersion: groovy.lang.Closure<String> by extra
    return if (versionFile.exists()) {
        versionFile.readText().trim()
    } else {
        gitVersion()
            // Avoid duplicated "-SNAPSHOT" ending
            .let { if (it.endsWith("-SNAPSHOT")) it else "$it-SNAPSHOT" }
            // Normalize the version so that is always a valid NPM version.
            .let { if (it.matches("""\d+\.\d+.\d+-.*""".toRegex())) it else "0.0.1-$it" }
            .also { versionFile.writeText(it) }
    }
}

dependencies {
    // Generate a combined coverage report
    project.subprojects.filterNot { it.name in setOf("model-server-openapi") }.forEach {
        kover(it)
    }
}

val parentProject = project

subprojects {
    val subproject = this
    apply(plugin = "maven-publish")
    if (subproject.name !in setOf("model-server-openapi")) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }

    version = rootProject.version
    group = rootProject.group

    subproject.plugins.withType<NodePlugin> {
        subproject.extensions.configure<NodeExtension> {
            version.set(libs.versions.node)
            download.set(true)
        }
    }

    // Configure detekt including our custom rule sets
    apply(plugin = "io.gitlab.arturbosch.detekt")
    tasks.withType<Detekt> {
        reports {
            sarif.required.set(true)
            html.required.set(true)
        }
    }
    detekt {
        parallel = true
        // For now, we only use the results here as hints
        ignoreFailures = true

        buildUponDefaultConfig = true
        config.setFrom(parentProject.projectDir.resolve(".detekt.yml"))
    }
}

allprojects {
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
        }
    }

    // Set maven metadata for all known publishing tasks. The exact tasks and names are only known after evaluation.
    afterEvaluate {
        tasks.withType<AbstractPublishToMaven>() {
            this.publication?.apply {
                setMetadata()
            }
        }
    }
}

fun MavenPublication.setMetadata() {
    pom {
        url.set("https://github.com/modelix/modelix.core")
        scm {
            connection.set("scm:git:https://github.com/modelix/modelix.core.git")
            url.set("https://github.com/modelix/modelix.core")
        }
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask> {
    dependsOn(":ts-model-api:npm_run_build")
}

catalog {
    versionCatalog {
        from(files("gradle/libs.versions.toml"))
    }
}

publishing {
    publications {
        create<MavenPublication>("versionCatalog") {
            groupId = "org.modelix"
            artifactId = "core-version-catalog"
            from(components["versionCatalog"])

            setMetadata()
        }
    }
}

// make all 'packJsPackage' tasks depend on all 'kotlinNodeJsSetup' tasks, because gradle complained about this being missing
tasks.register("setupNodeEverywhere") {
    dependsOn(":bulk-model-sync-lib:kotlinNodeJsSetup")
    dependsOn(":kotlin-utils:kotlinNodeJsSetup")
    dependsOn(":model-api:kotlinNodeJsSetup")
    dependsOn(":model-api-gen-runtime:kotlinNodeJsSetup")
    dependsOn(":model-client:kotlinNodeJsSetup")
    dependsOn(":model-datastructure:kotlinNodeJsSetup")
    dependsOn(":model-server-api:kotlinNodeJsSetup")
    dependsOn(":modelql-client:kotlinNodeJsSetup")
    dependsOn(":modelql-core:kotlinNodeJsSetup")
    dependsOn(":modelql-html:kotlinNodeJsSetup")
    dependsOn(":modelql-typed:kotlinNodeJsSetup")
    dependsOn(":modelql-untyped:kotlinNodeJsSetup")
    dependsOn(":streams:kotlinNodeJsSetup")
    dependsOn(":model-client:integration-tests:kotlinNodeJsSetup")
}

if (isPrerelease) {
    allprojects {
        tasks.withType<NpmPublishTask> {
            tag = "snapshots"
        }
    }
}
