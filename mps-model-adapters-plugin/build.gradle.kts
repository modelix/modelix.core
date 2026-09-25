import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.modelix.buildtools.KnownModuleIds
import org.modelix.buildtools.buildStubsSolutionJar
import org.modelix.configureMpsTestClasspath
import org.modelix.configureMpsTestTask
import org.modelix.copyMps
import org.modelix.excludeMPSLibraries
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
    implementation(project(":mps-model-adapters"), excludeMPSLibraries)
    testImplementation(kotlin("test"))

    intellijPlatform {
        local(copyMps())
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
            untilBuild = "261.*"
        }
    }
}

tasks {
    test {
        configureMpsTestTask()
    }

    val mpsPluginDir = project.findProperty("mps.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        register<Sync>("installMpsPlugin") {
            from(prepareSandbox.flatMap { it.pluginDirectory })
            into(mpsPluginDir.resolve("mps-model-adapters-plugin"))
        }
    }

    withType(PrepareSandboxTask::class.java) {
        dependsOn(":mps-repository-concepts:assembleMpsModules")
        from(project(":mps-repository-concepts").layout.buildDirectory.dir("mpsbuild/packaged-modules")) {
            into(pluginName.map { "$it/languages" })
        }

        from(project.layout.projectDirectory.dir("src/main/resources/META-INF")) {
            exclude("plugin.xml")
            into(pluginName.map { "$it/META-INF" })
        }
        from(patchPluginXml.flatMap { it.outputFile }) {
            into(pluginName.map { "$it/META-INF" })
        }

        doLast {
            val ownJar: File = pluginJar.get().asFile
            val classpathJars = configurations.runtimeClasspath.get().resolve()
            val stubModelJars = configurations.compileClasspath.get().resolve().intersect(classpathJars)
            buildStubsSolutionJar {
                solutionName("org.modelix.mps.model.adapters.stubs")
                solutionId("83727c3c-e8b0-4bdd-a1fc-cb4fea831777")
                outputFolder(pluginDirectory.get().asFile.resolve("languages"))
                classpathJars.forEach { classpathJar(it.name) }
                stubModelJars.forEach { javaStubsJar(it.name) }
                moduleDependency(KnownModuleIds.Annotations)
                moduleDependency(KnownModuleIds.JDK)
                moduleDependency(KnownModuleIds.MPS_OpenAPI)
                moduleDependency(KnownModuleIds.MPS_Core)
                moduleDependency(KnownModuleIds.MPS_IDEA)
            }
        }
    }
}

group = "org.modelix.mps"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "mps-model-adapters-plugin"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
