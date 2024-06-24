pluginManagement {
    includeBuild("..")
    includeBuild("../build-logic")
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                from(files("../gradle/libs.versions.toml"))
            }
        }
    }
}

plugins {
    id("modelix-repositories")
}

rootProject.name = "model-client-js-test"
include("replicated-model-test")
includeBuild("..")
