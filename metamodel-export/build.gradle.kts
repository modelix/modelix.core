import org.gradle.internal.jvm.Jvm
import org.modelix.gradle.mpsbuild.MPSBuildSettings
import org.modelix.mpsHomeDir

plugins {
    base
    alias(libs.plugins.modelix.mps.buildtools)
}

group = "org.modelix.mps"

val generatorLibs by configurations.creating

dependencies {
    generatorLibs(project(":model-api-gen-runtime"))
    generatorLibs(project(":model-api-gen"))
}

val copyLibs by tasks.registering(Sync::class) {
    from(generatorLibs)
    into(projectDir.resolve("org.modelix.metamodel.export/lib"))
    rename { fileName ->
        generatorLibs.resolvedConfiguration.resolvedArtifacts
            .find { it.file.name == fileName }?.let {
                if (it.classifier == null) {
                    "${it.name}.${it.extension}"
                } else {
                    "${it.name}-${it.classifier}.${it.extension}"
                }
            }
            ?: fileName
    }
}

extensions.configure<MPSBuildSettings> {
    javaHome = Jvm.current().javaHome
    mpsHome(mpsHomeDir.get().asFile.absolutePath)
    dependsOn(copyLibs)
    search(".")
    disableParentPublication()

    publication("metamodel-export") {
        module("org.modelix.metamodel.export")
    }
}
