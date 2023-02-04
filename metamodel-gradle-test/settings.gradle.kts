pluginManagement {
    val modelixCoreVersion: String = file("../version.txt").readText()
    plugins {
        id("org.modelix.metamodel.gradle") version modelixCoreVersion
    }
    resolutionStrategy {
    }
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
        mavenCentral()
    }
}