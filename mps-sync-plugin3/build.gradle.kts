import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.modelix.buildtools.KnownModuleIds
import org.modelix.buildtools.buildStubsSolutionJar
import org.modelix.configureMpsTestClasspath
import org.modelix.configureMpsTestTask
import org.modelix.copyMps
import org.modelix.mpsHomeDir
import org.modelix.mpsPlatformVersion
import kotlin.io.resolve
import kotlin.jvm.java

buildscript {
    dependencies {
        classpath(libs.modelix.build.tools.lib)
    }
}

plugins {
    `modelix-kotlin-jvm`
    alias(libs.plugins.intellij)
    `modelix-project-repositories`
}

repositories {
    intellijPlatform {
        localPlatformArtifacts()
    }
}

dependencies {
    val excludeMPSLibraries: (ModuleDependency).() -> Unit = {
        exclude("org.slf4j", "slf4j-api")
    }

    implementation(project(":bulk-model-sync-lib"), excludeMPSLibraries)
    implementation(project(":bulk-model-sync-mps"), excludeMPSLibraries)
    implementation(project(":mps-model-adapters"), excludeMPSLibraries)
    implementation(project(":model-client"), excludeMPSLibraries)
    implementation(project(":datastructures"), excludeMPSLibraries)
    implementation(libs.modelix.mpsApi, excludeMPSLibraries)
    implementation(libs.kotlin.logging, excludeMPSLibraries)
    implementation(libs.kotlin.html, excludeMPSLibraries)
    implementation(libs.kotlin.datetime, excludeMPSLibraries)
    implementation(libs.ktor.client.core, excludeMPSLibraries)

    compileOnly(
        fileTree(mpsHomeDir).matching {
            include("lib/**/*.jar")
        },
    )

    // testImplementation("junit:junit:4.13.2")
    testImplementation(libs.testcontainers)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.logback.classic)
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(project(":authorization"), excludeMPSLibraries)
    testImplementation(project(":model-server")) {
        excludeMPSLibraries()
        // The old H2 version required by Apache Ignite conflicts with the H2 MVStore bundled with MPS.
        // The model-server itself runs in a container, so it isn't needed in the tests.
        exclude("com.h2database", "h2")
    }
    testImplementation(libs.ktor.client.cio, excludeMPSLibraries)

    intellijPlatform {
        local(copyMps())
        bundledPlugin("jetbrains.mps.ide.java") // for loading stub models in tests
        testFramework(TestFrameworkType.Bundled)
    }
}

configureMpsTestClasspath()

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false
    autoReload = true
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "251.*"
        }
    }
}

tasks {
    test {
        configureMpsTestTask()
        dependsOn(":model-server:jibDockerBuild")
        jvmArgs("-Dmodelix.model.server.image=modelix/model-server:$version")
        jvmArgs("-Xmx1000m")
    }

    val mpsPluginDir = project.findProperty("mps$mpsPlatformVersion.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        register<Sync>("installMpsPlugin") {
            from(prepareSandbox.flatMap { it.pluginDirectory })
            into(mpsPluginDir.resolve("mps-sync-plugin3"))
        }
    }

    withType(PrepareSandboxTask::class.java) {
        from(project.layout.projectDirectory.dir("src/main/resources/META-INF")) {
            exclude("plugin.xml")
            into(pluginName.map { "$it/META-INF" })
        }
        from(patchPluginXml.flatMap { it.outputFile }) {
            into(pluginName.map { "$it/META-INF" })
        }

        doLast {
            val ownJar: File = pluginJar.get().asFile
            val runtimeJars = configurations.runtimeClasspath.get().files + ownJar
            buildStubsSolutionJar {
                solutionName("org.modelix.mps.sync.stubs")
                solutionId("1dc413a4-9e7d-4996-bbda-f6b4e4e40808")
                ideaPluginId("org.modelix.mps.sync3")
                outputFolder(pluginDirectory.get().asFile.resolve("languages"))
                runtimeJars.forEach {
                    javaJar(it.name)
                }
                moduleDependency(KnownModuleIds.JDK)
                moduleDependency(KnownModuleIds.MPS_OpenAPI)
                moduleDependency(KnownModuleIds.MPS_IDEA)
                moduleDependency(KnownModuleIds.MPS_Core)
                moduleDependency(KnownModuleIds.MPS_Platform)
                moduleDependency(KnownModuleIds.org_jdom)
            }
        }
    }
}

// make the zip consumable in a composite build
val pluginZipElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.ZIP_TYPE)
    }
}
artifacts {
    add(pluginZipElements.name, tasks.buildPlugin) {
        this.builtBy(tasks.buildPlugin)
    }
}

group = "org.modelix.mps"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "mps-sync-plugin3"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
