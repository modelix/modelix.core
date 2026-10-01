import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.modelix.MODELIX_KOTLIN_API_VERSION

// The modelix specific settings for modules that compile against and run the tests with MPS.
// Apply it together with `id("org.modelix.mps.platform")` (or `org.modelix.mps.plugin`) of modelix.mps-build-tools,
// which is declared in the root project. It can't be applied here, because the build-logic can't use the included
// build of modelix.mps-build-tools in the composite build.

plugins {
    id("modelix-kotlin-jvm")
    id("modelix-project-repositories")
}

// The tests run with the Kotlin stdlib bundled with MPS, so the test code is restricted to the same
// API version as the main code.
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.apiVersion.set(MODELIX_KOTLIN_API_VERSION)
}
