import dev.petuska.npm.publish.task.NpmPackTask

plugins {
    `maven-publish`
    kotlin("multiplatform")
    alias(libs.plugins.spotless)
    alias(libs.plugins.npm.publish)
    `java-library`
    jacoco
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

val kotlinCoroutinesVersion: String by rootProject
val kotlinLoggingVersion: String by rootProject
val ktorVersion: String by rootProject
val kotlinxSerializationVersion: String by rootProject

kotlin {
    jvmToolchain(11)
    jvm()
    js(IR) {
        browser {
            testTask {
                useMocha {
                    timeout = "30s"
                }
            }
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "30s"
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
                api(project(":model-api"))
                api(project(":model-datastructure"))
                api(project(":model-server-api"))
                implementation(project(":modelql-client"))
                api(project(":modelql-core"))
                implementation(kotlin("stdlib-common"))
                implementation(libs.modelix.incremental)
                implementation(libs.kotlin.collections.immutable)
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.datetime)
                implementation(libs.kotlin.serialization.json)
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
        val jsMain by getting {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(npm("uuid", "^8.3.0"))
                implementation(npm("js-sha256", "^0.9.0"))
                implementation(npm("js-base64", "^3.4.5"))
            }
        }
    }
}

tasks.jacocoTestReport {
    classDirectories.setFrom(project.layout.buildDirectory.dir("classes/kotlin/jvm/"))
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))
    executionData.setFrom(project.layout.buildDirectory.file("jacoco/jvmTest.exec"))

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

spotless {
    kotlin {
        licenseHeader(
            "/*\n" +
                """ * Licensed under the Apache License, Version 2.0 (the "License");""" + "\n" +
                """ * you may not use this file except in compliance with the License.""" + "\n" +
                """ * You may obtain a copy of the License at""" + "\n" +
                """ *""" + "\n" +
                """ *  http://www.apache.org/licenses/LICENSE-2.0""" + "\n" +
                """ *""" + "\n" +
                """ * Unless required by applicable law or agreed to in writing,""" + "\n" +
                """ * software distributed under the License is distributed on an""" + "\n" +
                """ * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY""" + "\n" +
                """ * KIND, either express or implied.  See the License for the""" + "\n" +
                """ * specific language governing permissions and limitations""" + "\n" +
                """ * under the License.""" + "\n" +
                " */\n" +
                "\n",
        )
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

        // Add correct TypeScript imports and mark exports as experimental.
        val typescriptDeclaration =
            preparedProductionLibraryOutputDirectory.get().file("modelix.core-model-client.d.ts").asFile
        val originalTypescriptDeclarationContent = typescriptDeclaration.readLines()
        val experimentalDeclaration = """

        /**
         * @experimental This feature is expected to be finalized with https://issues.modelix.org/issue/MODELIX-500.
         */
        """.trimIndent()
        typescriptDeclaration.writer().use {
            it.appendLine("""import { INodeJS } from "@modelix/ts-model-api";""")
                .appendLine()
            for (line in originalTypescriptDeclarationContent) {
                // Only mark the parts of the client (`org.modelix.model.client2`) experimental.
                // Reported declarations from `org.modelix.model.api` should not be annotated as experimental.
                if (line.startsWith("export declare namespace org.modelix.model.client2")) {
                    it.appendLine(experimentalDeclaration)
                }
                it.appendLine(line)
            }
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
