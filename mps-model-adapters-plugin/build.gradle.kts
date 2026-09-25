import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.modelix.buildtools.KnownModuleIds
import org.modelix.buildtools.buildStubsSolutionJar
import org.modelix.excludeMPSLibraries
import org.modelix.includeMetaInfFolder

buildscript {
    dependencies {
        classpath(libs.modelix.build.tools.lib)
    }
}

plugins {
    `modelix-mps-plugin`
}

dependencies {
    implementation(project(":mps-model-adapters"), excludeMPSLibraries)
    testImplementation(kotlin("test"))
}

tasks {
    withType(PrepareSandboxTask::class.java) {
        dependsOn(":mps-repository-concepts:assembleMpsModules")
        from(project(":mps-repository-concepts").layout.buildDirectory.dir("mpsbuild/packaged-modules")) {
            into(pluginName.map { "$it/languages" })
        }

        includeMetaInfFolder()

        doLast {
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
