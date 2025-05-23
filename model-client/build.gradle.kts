import dev.petuska.npm.publish.task.NodeExecTask
import dev.petuska.npm.publish.task.NpmPackTask

plugins {
    `maven-publish`
    `modelix-kotlin-multiplatform`
    alias(libs.plugins.npm.publish)
    alias(libs.plugins.kotlin.serialization)
}

val kotlinCoroutinesVersion: String by rootProject
val kotlinLoggingVersion: String by rootProject
val ktorVersion: String by rootProject
val kotlinxSerializationVersion: String by rootProject

kotlin {
    js(IR) {
        binaries.library()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":model-api"))
                api(project(":model-datastructure"))
                api(project(":model-server-api"))
                implementation(project(":modelql-client"))
                api(project(":modelql-core"))
                implementation(project(":mps-multiplatform-lib"))
                implementation(kotlin("stdlib-common"))
                implementation(libs.modelix.incremental)
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.datetime)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(project(":mps-model-adapters"))

                implementation(libs.vavr)
                implementation(libs.apache.commons.lang)
                implementation(libs.guava)
                implementation(libs.apache.commons.io)
                implementation(libs.org.json)
                implementation(libs.trove4j)
                implementation(libs.apache.commons.collections)

                implementation(libs.google.oauth.client)
                implementation(libs.google.oauth.clientjetty)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.logback.classic)
                implementation(libs.testcontainers)
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.server.auth)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
        val jsMain by getting {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(npm("uuid", "^8.3.0"))
                implementation(npm("js-sha256", "^0.9.0"))
                implementation(npm("js-base64", "^3.4.5"))

                // Version fixed because of CVE-2024-37890
                implementation(npm("ws", "^8.17.1"))
            }
        }
    }
}

val productionLibraryByKotlinOutputDirectory = layout.buildDirectory.dir("compileSync/js/main/productionLibrary/kotlin")
val preparedProductionLibraryOutputDirectory = layout.buildDirectory.dir("npmPublication")

val patchTypesScriptInProductionLibrary = tasks.register("patchTypesScriptInProductionLibrary") {
    dependsOn("compileProductionLibraryKotlinJs")
    inputs.dir(productionLibraryByKotlinOutputDirectory)
    outputs.dir(preparedProductionLibraryOutputDirectory)
    outputs.cacheIf { true }
    doLast {
        // Delete old data
        delete {
            delete(preparedProductionLibraryOutputDirectory)
        }

        // Copy over library create by Kotlin
        copy {
            from(productionLibraryByKotlinOutputDirectory)
            into(preparedProductionLibraryOutputDirectory)
        }

        // Add correct TypeScript imports.
        val typescriptDeclaration =
            preparedProductionLibraryOutputDirectory.get().file("modelix.core-model-client.d.ts").asFile
        val originalTypescriptDeclarationContent = typescriptDeclaration.readText()
        typescriptDeclaration.writer().use {
            it.appendLine("""import { INodeJS } from "@modelix/ts-model-api";""")
                .appendLine()
                .append(originalTypescriptDeclarationContent)
        }
    }
}

tasks.named<NpmPackTask>("packJsPackage") {
    dependsOn("assembleJsPackage")
    packageDir.set(layout.buildDirectory.dir("packages/js"))
    outputFile.set(layout.buildDirectory.file("npmDevPackage/${project.name}.tgz"))
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
            files {
                // The files need to be set manually because we patch
                // the JS/TS produces by `compileProductionLibraryKotlinJs`
                // with the `patchTypesScriptInProductionLibrary` task
                setFrom(patchTypesScriptInProductionLibrary)
            }
            packageJson {
                name.set("@modelix/model-client")
                homepage.set("https://modelix.org/")
                repository {
                    type.set("git")
                    url.set("https://github.com/modelix/modelix.core.git")
                    directory.set(project.name)
                }
            }
            dependencies {
                // The model client NPM package uses the types from this @modelix/ts-model-api
                normal("@modelix/ts-model-api", rootProject.version.toString())
            }
        }
    }
}

tasks.withType(NodeExecTask::class) {
    dependsOn(":setupNodeEverywhere")
}

tasks.jvmTest {
    dependsOn(":model-server:jibDockerBuild")
    jvmArgs("-Dmodelix.model.server.image=modelix/model-server:$version")
    environment("KEYCLOAK_VERSION", libs.versions.keycloak.get())
}
