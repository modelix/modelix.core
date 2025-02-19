import org.jetbrains.kotlin.gradle.tasks.KotlinTest

// Tests for a model client that cannot run in isolation.
// One such case is starting a server and using the model client from JS at the same time.
// This integration tests start a mock server with Docker Compose.
//
// They are in a subproject so that they can be easily run in isolation or be excluded.
// An alternative to a separate project would be to have a custom compilation.
// I failed to configure custom compilation, and for now, subproject was a more straightforward configuration.
// See https://kotlinlang.org/docs/multiplatform-configure-compilations.html#create-a-custom-compilation
//
// Using docker compose to startup containers with Gradle is not ideal.
// Ideally, each test should do the setup it needs by themselves.
// A good solution would be https://testcontainers.com/.
// But there is no unified Kotlin Multiplatform API and no REST API
// to start containers from web browser executing tests.
// The solution with Docker Compose works for now
// because the number of tests is small and only one container configuration is enough.
plugins {
    `modelix-kotlin-multiplatform`
    alias(libs.plugins.docker.compose)
}

kotlin {
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(project(":model-client"))
                implementation(libs.ktor.client.core)
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(project(":model-client", configuration = "jvmRuntimeElements"))
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

dockerCompose {
    dockerExecutable = findExecutableAbsolutePath("docker").also { println("docker: $it") }
}

// The tasks "jsNodeTest" and "jsBrowserTest" are of this type.
tasks.withType(KotlinTest::class).all {
    dockerCompose.isRequiredBy(this)
}
// The task "jvmTest" is of this type.
tasks.withType(Test::class).all {
    dockerCompose.isRequiredBy(this)
}

fun findExecutableAbsolutePath(name: String): String {
    return System.getenv("PATH")
        ?.split(File.pathSeparatorChar)
        ?.map { File(it).resolve(name) }
        ?.firstOrNull { it.isFile && it.exists() }
        ?.absolutePath
        ?: name
}
