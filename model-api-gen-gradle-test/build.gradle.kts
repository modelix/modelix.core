import com.github.gradle.node.NodeExtension
import com.github.gradle.node.NodePlugin

plugins {
    base
    alias(libs.plugins.node) apply false
}

subprojects {
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

    plugins.withType<NodePlugin> {
        project.extensions.configure<NodeExtension> {
            version.set(libs.versions.node)
            download.set(true)
        }
    }
}
