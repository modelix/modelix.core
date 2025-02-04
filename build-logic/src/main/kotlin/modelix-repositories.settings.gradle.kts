val modelixRegex = "org\\.modelix.*"
pluginManagement {
    repositories {
        gradlePluginPortal {
            content {
                excludeGroupByRegex(modelixRegex)
            }
        }
        mavenCentral {
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
        mavenLocal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        gradlePluginPortal {
            content {
                excludeGroupByRegex(modelixRegex)
            }
        }
        mavenCentral {
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
        mavenLocal()
    }
}
