// For some projects we need to redeclare repositories on project level
// because plugins like npm and intellij may override our settings
repositories {
    val modelixRegex = "org\\.modelix.*"
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
