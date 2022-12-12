import org.modelix.gradle.mpsbuild.MPSBuildSettings

buildscript {
    repositories {
        mavenLocal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
    }
    dependencies {
        classpath("org.modelix.mpsbuild:gradle-mpsbuild-plugin:1.0.8")
    }
}

plugins {
    base
}

apply(plugin = "modelix-gradle-mpsbuild-plugin")

val generatorLibs by configurations.creating

dependencies {
    generatorLibs(project(":metamodel-runtime"))
    generatorLibs(project(":metamodel-generator"))
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

    publication("metamodel-export-mps") {
        module("org.modelix.metamodel.export")
    }
}
