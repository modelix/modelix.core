plugins {
    `modelix-kotlin-jvm-with-junit`
    id("org.modelix.bulk-model-sync")
}

val kotlinGenDir = project.layout.buildDirectory.dir("metamodel/kotlin").get().asFile.apply { mkdirs() }

dependencies {
    implementation(libs.kotlin.coroutines.core)
    implementation("org.modelix:model-server")
    implementation("org.modelix:model-api-gen-runtime")
    testImplementation(project(":graph-lang-api"))
    testImplementation("org.modelix", "model-client", "", "jvmRuntimeElements")
    testImplementation("org.modelix:bulk-model-sync-lib")
    testImplementation(kotlin("test"))
    testImplementation(libs.xmlunit.core)
}

val repoDir = project.layout.buildDirectory.dir("test-repo").get().asFile

val copyTestRepo by tasks.registering(Sync::class) {
    from(projectDir.resolve("test-repo"))
    into(repoDir)
}

mpsBuild {
    mpsVersion("2021.2.5")
}

modelSync {
    dependsOn(copyTestRepo)
    direction("testPush") {
        includeModulesByPrefix("GraphSolution")
        fromLocal {
            mpsHeapSize = "4g"
            repositoryDir = repoDir
        }
        toModelServer {
            url = "http://localhost:28309/v2"
            repositoryId = "ci-test"
            branchName = "master"
            metaProperties["metaKey1"] = "metaValue1"
            metaProperties["metaKey2"] = "metaValue2"
        }
    }
    direction("testPull") {
        includeModulesByPrefix("GraphSolution")
        fromModelServer {
            url = "http://localhost:28309/v2"
            repositoryId = "ci-test"
            branchName = "master"
        }
        toLocal {
            repositoryDir = repoDir
            mpsHeapSize = "4g"
        }
    }
}
