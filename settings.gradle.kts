pluginManagement {
    val kotlinVersion: String by settings
    plugins {
        kotlin("multiplatform") version kotlinVersion apply false
        kotlin("plugin.serialization") version kotlinVersion apply false
    }
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
        mavenCentral()
    }
}

rootProject.name = "modelix.core"

include("authorization")
include("light-model-client")
include("light-model-server")
include("metamodel-export")
include("model-api-gen")
include("model-api-gen-gradle")
include("model-api-gen-runtime")
include("model-api")
include("model-client")
include("model-server")
include("model-server-api")
include("ts-model-api")

