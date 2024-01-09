pluginManagement {
    val modelixCoreVersion: String = file("../version.txt").readText()
    val modelixRegex = "org\\.modelix.*"
    plugins {
        id("org.modelix.model-api-gen") version modelixCoreVersion
    }
    repositories {
        mavenLocal {
            content {
                includeGroupByRegex(modelixRegex)
            }
        }
        gradlePluginPortal {
            content {
                excludeGroupByRegex(modelixRegex)
            }
        }
        maven {
            url = uri("https://artifacts.itemis.cloud/repository/maven-mps/")
            content {
                includeGroupByRegex(modelixRegex)
                includeGroup("com.jetbrains")
            }
        }
        mavenCentral {
            content {
                excludeGroupByRegex(modelixRegex)
            }
        }
    }
    dependencyResolutionManagement {
        repositories {
            mavenLocal {
                content {
                    includeGroupByRegex(modelixRegex)
                }
            }
            gradlePluginPortal {
                content {
                    excludeGroupByRegex(modelixRegex)
                }
            }
            maven {
                url = uri("https://artifacts.itemis.cloud/repository/maven-mps/")
                content {
                    includeGroupByRegex(modelixRegex)
                }
            }
            mavenCentral {
                content {
                    excludeGroupByRegex(modelixRegex)
                }
            }
        }
        versionCatalogs {
            create("libs") {
                from("org.modelix:core-version-catalog:$modelixCoreVersion")
            }
        }
    }
}

rootProject.name = "model-api-gen-gradle-test"
include("metamodel-export")
include("typescript-generation")
include("kotlin-generation")
include("vue-integration")
