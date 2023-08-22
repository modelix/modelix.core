import org.modelix.gradle.mpsbuild.MPSBuildSettings

plugins {
    base
    alias(libs.plugins.modelix.mps.buildtools)
}

val generatorLibs: Configuration by configurations.creating

dependencies {
    generatorLibs(project(":model-sync-lib"))
    generatorLibs(project(":mps-model-adapters"))
}

val copyLibs by tasks.registering(Sync::class) {
    from(generatorLibs)
    into(projectDir.resolve("solutions/org.modelix.model.sync.mps/lib").apply { mkdirs() })
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

    publication("model-sync-mps") {
        module("org.modelix.model.sync.mps")
    }
}
