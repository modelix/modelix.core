pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
        mavenCentral()
    }
}
plugins {
    id("de.fayard.refreshVersions") version "0.60.2"
}

rootProject.name = "modelix.core"

include("authorization")
include("kotlin-utils")
include("light-model-client")
include("metamodel-export")
include("model-api")
include("model-api-gen")
include("model-api-gen-gradle")
include("model-api-gen-runtime")
include("model-client")
include("model-datastructure")
include("model-server")
include("model-server-api")
include("model-server-lib")
include("model-sync-gradle")
include("model-sync-lib")
include("model-sync-mps")
include("modelql-client")
include("modelql-core")
include("modelql-html")
include("modelql-server")
include("modelql-typed")
include("modelql-untyped")
include("mps-model-adapters")
include("mps-model-server-plugin")
include("ts-model-api")
