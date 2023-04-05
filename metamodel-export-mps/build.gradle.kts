import org.modelix.gradle.mpsbuild.MPSBuildSettings

plugins {
    base
    id("org.modelix.mps.build-tools") version "1.0.11"
}

val generatorLibs by configurations.creating

dependencies {
    generatorLibs(project(":metamodel-runtime"))
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
    dependsOn(copyLibs)
    mpsVersion("2021.1.4")
    search(".")
    disableParentPublication()

    publication("metamodel-export-mps") {
        module("org.modelix.metamodel.export")
    }
}
