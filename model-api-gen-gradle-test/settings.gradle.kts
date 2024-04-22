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

rootProject.name = "model-api-gen-gradle-test"
include("metamodel-export")
include("typescript-generation")
include("kotlin-generation")
include("vue-integration")
includeBuild("..")
