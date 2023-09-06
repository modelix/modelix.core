import org.modelix.gradle.mpsbuild.MPSBuildSettings

plugins {
    base
    alias(libs.plugins.modelix.mps.buildtools)
}

group = "org.modelix.mps"

val generatorLibs: Configuration by configurations.creating

dependencies {
    "generatorLibs"(project(":bulk-model-sync-lib"))
    generatorLibs(project(":mps-model-adapters"))
}

val copyLibs by tasks.registering(Sync::class) {
    from(generatorLibs)
    into(projectDir.resolve("solutions/org.modelix.mps.model.sync.bulk/lib").apply { mkdirs() })
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

    publication("bulk-model-sync-solution") {
        module("org.modelix.mps.model.sync.bulk")
    }
}
