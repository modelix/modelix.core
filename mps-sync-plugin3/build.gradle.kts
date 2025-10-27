import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.modelix.buildtools.KnownModuleIds
import org.modelix.buildtools.buildStubsSolutionJar
import org.modelix.copyMps
import org.modelix.mpsHomeDir
import org.modelix.mpsMajorVersion
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

intellij {
    localPath = copyMps().absolutePath
    instrumentCode = false
    plugins = listOf(
        "jetbrains.mps.ide.java", // for loading stub models in tests
    )
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
    testImplementation(project(":authorization"), excludeMPSLibraries)
    testImplementation(project(":model-server"), excludeMPSLibraries)
    testImplementation(libs.ktor.client.cio, excludeMPSLibraries)
}

tasks {
    patchPluginXml {
        sinceBuild.set("211")
        untilBuild.set("241.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        autoReloadPlugins.set(true)
    }

    test {
        dependsOn(":model-server:jibDockerBuild")
        jvmArgs("-Dmodelix.model.server.image=modelix/model-server:$version")
        onlyIf {
            !setOf(
                "2020.3", // incompatible with the intellij plugin
            ).contains(mpsMajorVersion)
        }
        jvmArgs("-Dintellij.platform.load.app.info.from.resources=true")
        jvmArgs("-Xmx1000m")

        val arch = System.getProperty("os.arch")
        val jnaDir = mpsHomeDir.get().asFile.resolve("lib/jna/$arch")
        if (jnaDir.exists()) {
            jvmArgs("-Djna.boot.library.path=${jnaDir.absolutePath}")
            jvmArgs("-Djna.noclasspath=true")
            jvmArgs("-Djna.nosys=true")
        }
    }

    val mpsPluginDir = project.findProperty("mps$mpsPlatformVersion.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        create<Sync>("installMpsPlugin") {
            dependsOn(prepareSandbox)
            from(project.layout.buildDirectory.dir("idea-sandbox/plugins/mps-sync-plugin3"))
            into(mpsPluginDir.resolve("mps-sync-plugin3"))
        }
    }

    withType(PrepareSandboxTask::class.java) {
        intoChild(pluginName.map { "$it/META-INF" })
            .from(project.layout.projectDirectory.file("src/main/resources/META-INF"))
            .exclude("plugin.xml")
        intoChild(pluginName.map { "$it/META-INF" })
            .from(patchPluginXml.flatMap { it.outputFiles })

        doLast {
            val ownJar: File = pluginJar.get().asFile
            val runtimeJars = configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).resolvedConfiguration.files + ownJar
            buildStubsSolutionJar {
                solutionName("org.modelix.mps.sync.stubs")
                solutionId("1dc413a4-9e7d-4996-bbda-f6b4e4e40808")
                ideaPluginId("org.modelix.mps.sync3")
                outputFolder(defaultDestinationDir.get().resolve(project.name).resolve("languages"))
                runtimeJars.forEach {
                    javaJar(it.name)
                }
                moduleDependency(KnownModuleIds.JDK)
                moduleDependency(KnownModuleIds.MPS_OpenAPI)
                moduleDependency(KnownModuleIds.MPS_IDEA)
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

// disable coroutines agent
if (mpsPlatformVersion < 241) {
    afterEvaluate {
        val testTask = tasks.test.get()
        val originalProviders = testTask.jvmArgumentProviders.toList()
        testTask.jvmArgumentProviders.clear()
        testTask.jvmArgumentProviders.add(object : CommandLineArgumentProvider {
            override fun asArguments(): Iterable<String> {
                return originalProviders.flatMap { it.asArguments() }.filterNot { it.contains("coroutines-javaagent.jar") }
            }
        })
    }
}
