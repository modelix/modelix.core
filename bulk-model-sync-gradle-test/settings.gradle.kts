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

includeBuild("..")
include("graph-lang-api")
