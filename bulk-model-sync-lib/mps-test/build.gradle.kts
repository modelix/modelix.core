import org.modelix.copyMps
import org.modelix.mpsMajorVersion

plugins {
    `modelix-kotlin-jvm`
    // We are not building an actual plugin here.
    // We use/abuse the gradle-intellij-plugin run tests with MPS.
    // (With enough time and effort,
    // one could inspect what the plugin does under the hood
    // and build something custom using the relevant parts.
    // For the time being, this solution works without much overhead and great benefit.)
    alias(libs.plugins.intellij)
    `modelix-project-repositories`
}

dependencies {
    testImplementation(project(":bulk-model-sync-lib"))
    testImplementation(project(":bulk-model-sync-mps"))
    testImplementation(project(":mps-model-adapters"))
    testImplementation(project(":model-datastructure"))
    testImplementation(libs.kotlin.serialization.json)
    testImplementation(libs.xmlunit.matchers)
    testImplementation(libs.jimfs)
    testImplementation(libs.modelix.mpsApi)
}

intellij {
    localPath = copyMps().absolutePath
    instrumentCode = false
}

tasks {
    // Workaround:
    // * Execution failed for task ':bulk-model-sync-lib-mps-test:buildSearchableOptions'.
    // * > Cannot find IDE platform prefix. Please create a bug report at https://github.com/jetbrains/gradle-intellij-plugin. As a workaround specify `idea.platform.prefix` system property for task `buildSearchableOptions` manually.
    buildSearchableOptions {
        enabled = false
    }

    test {
        onlyIf { mpsMajorVersion != "2020.3" } // incompatible with the intellij plugin
    }
}
