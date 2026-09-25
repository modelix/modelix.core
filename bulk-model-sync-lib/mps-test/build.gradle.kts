import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.modelix.configureMpsTestClasspath
import org.modelix.configureMpsTestTask
import org.modelix.copyMps
import org.modelix.excludeMPSLibraries

plugins {
    `modelix-kotlin-jvm`
    // We are not building an actual plugin here.
    // We use/abuse the IntelliJ Platform Gradle Plugin to run tests with MPS.
    // (With enough time and effort,
    // one could inspect what the plugin does under the hood
    // and build something custom using the relevant parts.
    // For the time being, this solution works without much overhead and great benefit.)
    alias(libs.plugins.intellij)
    `modelix-project-repositories`
}

repositories {
    intellijPlatform {
        localPlatformArtifacts()
    }
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

    intellijPlatform {
        local(copyMps())
        testFramework(TestFrameworkType.Bundled)
    }
}

configureMpsTestClasspath()

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false
    pluginVerification {
        ides {
            // Without any IDEs configured, the recommended ones would be downloaded (e.g. by the IDE sync).
            current()
        }
    }
}

tasks {
    test {
        configureMpsTestTask()
    }
}
