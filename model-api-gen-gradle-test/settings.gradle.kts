pluginManagement {
    val modelixCoreVersion: String = file("../version.txt").readText()
    plugins {
        id("org.modelix.model-api-gen") version modelixCoreVersion
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