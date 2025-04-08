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
include("bulk-model-sync-lib:mps-test")
include("bulk-model-sync-mps")
include("datastructures")
include("kotlin-utils")
include("metamodel-export")
include("model-api")
include("model-api-gen")
include("model-api-gen-gradle")
include("model-api-gen-runtime")
include("model-client")
include("model-client:integration-tests")
include("model-datastructure")
include("model-server")
include("model-server-api")
include("model-server-openapi")
include("modelql-client")
include("modelql-core")
include("modelql-html")
include("modelql-server")
include("modelql-typed")
include("modelql-untyped")
include("mps-git-import-plugin")
include("mps-model-adapters")
include("mps-model-adapters-plugin")
include("mps-sync-plugin3")
include("streams")
include("ts-model-api")
include("vue-model-api")
