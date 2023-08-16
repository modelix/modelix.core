pluginManagement {
    val modelixCoreVersion: String = file("../version.txt").readText()
    plugins {
        id("org.modelix.model-api-gen") version modelixCoreVersion apply false
    }
    resolutionStrategy {
    }
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
        mavenCentral()
    }
    dependencyResolutionManagement {
        repositories {
            mavenLocal()
            gradlePluginPortal()
            maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
            mavenCentral()
        }
        versionCatalogs {
            create("libs") {
                from("org.modelix:core-version-catalog:$modelixCoreVersion")
            }
        }
    }
}

include("apigen-project")
include("kotlin-project")
include("typescript-project")
