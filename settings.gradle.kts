pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("modelix-repositories")
}

rootProject.name = "modelix.core"

include("authorization")
include("bulk-model-sync-gradle")
include("bulk-model-sync-lib")
include("bulk-model-sync-mps")
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
include("model-server-test")
include("modelql-client")
include("modelql-core")
include("modelql-html")
include("modelql-server")
include("modelql-typed")
include("modelql-untyped")
include("mps-model-adapters")
include("mps-model-adapters-plugin")
include("mps-model-server-plugin")
include("ts-model-api")
include("vue-model-api")
