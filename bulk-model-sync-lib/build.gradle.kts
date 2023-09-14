plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        browser()
    }
    jvm {
        jvmToolchain(11)
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    @Suppress("UNUSED_VARIABLE", "KotlinRedundantDiagnosticSuppress")
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":model-api"))
                implementation(libs.kotlin.serialization.json)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":model-api"))
                implementation(libs.kotlin.serialization.json)
                implementation(project(":model-client", configuration = "jvmRuntimeElements"))
                implementation(kotlin("test"))
            }
        }
    }
}
