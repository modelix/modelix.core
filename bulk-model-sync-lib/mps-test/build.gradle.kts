import org.modelix.excludeMPSLibraries

plugins {
    // We are not building an actual plugin here.
    // We use/abuse the IntelliJ Platform Gradle Plugin to run tests with MPS.
    // (With enough time and effort,
    // one could inspect what the plugin does under the hood
    // and build something custom using the relevant parts.
    // For the time being, this solution works without much overhead and great benefit.)
    `modelix-mps-platform`
}

dependencies {
    testImplementation(project(":bulk-model-sync-lib"), excludeMPSLibraries)
    testImplementation(project(":bulk-model-sync-mps"), excludeMPSLibraries)
    testImplementation(project(":mps-model-adapters"), excludeMPSLibraries)
    testImplementation(project(":model-datastructure"), excludeMPSLibraries)
    testImplementation(libs.kotlin.serialization.json)
    testImplementation(libs.xmlunit.matchers)
    testImplementation(libs.jimfs)
    testImplementation(libs.modelix.mpsApi)
    // The IntelliJ Platform Gradle Plugin doesn't put the JUnit bundled with MPS on the classpath.
    testImplementation(libs.junit)
}
