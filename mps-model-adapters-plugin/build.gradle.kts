import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.modelix.buildtools.KnownModuleIds
import org.modelix.buildtools.buildStubsSolutionJar
import org.modelix.configureMpsTestClasspath
import org.modelix.configureMpsTestTask
import org.modelix.copyMps
import org.modelix.excludeMPSLibraries
import org.modelix.mpsMajorVersion
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

dependencies {
    implementation(project(":mps-model-adapters"), excludeMPSLibraries)
    testImplementation(kotlin("test"))
}

configureMpsTestClasspath()

intellij {
    localPath = copyMps().absolutePath
    instrumentCode = false
}

tasks {
    patchPluginXml {
        sinceBuild.set("222")
        untilBuild.set("251.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        autoReloadPlugins.set(true)
    }

    test {
        configureMpsTestTask()
        onlyIf {
            !setOf(
                "2022.2", // hangs when executed on CI
            ).contains(mpsMajorVersion)
        }
    }

    val mpsPluginDir = project.findProperty("mps.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        create<Sync>("installMpsPlugin") {
            dependsOn(prepareSandbox)
            from(project.layout.buildDirectory.dir("idea-sandbox/plugins/mps-model-adapters-plugin"))
            into(mpsPluginDir.resolve("mps-model-adapters-plugin"))
        }
    }

    withType(PrepareSandboxTask::class.java) {
        dependsOn(":mps-repository-concepts:assembleMpsModules")
        intoChild(pluginName.map { "$it/languages" })
            .from(project(":mps-repository-concepts").layout.buildDirectory.map { it.dir("mpsbuild/packaged-modules") })

        intoChild(pluginName.map { "$it/META-INF" })
            .from(project.layout.projectDirectory.file("src/main/resources/META-INF"))
            .exclude("plugin.xml")
        intoChild(pluginName.map { "$it/META-INF" })
            .from(patchPluginXml.flatMap { it.outputFiles })

        doLast {
            val ownJar: File = pluginJar.get().asFile
            val classpathJars = configurations.runtimeClasspath.get().resolve()
            val stubModelJars = configurations.compileClasspath.get().resolve().intersect(classpathJars)
            buildStubsSolutionJar {
                solutionName("org.modelix.mps.model.adapters.stubs")
                solutionId("83727c3c-e8b0-4bdd-a1fc-cb4fea831777")
                outputFolder(defaultDestinationDir.get().resolve(project.name).resolve("languages"))
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
