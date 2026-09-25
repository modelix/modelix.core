import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.modelix.configureMpsTestClasspath
import org.modelix.configureMpsTestTask
import org.modelix.copyMps

// Compiles against and runs the tests with the MPS selected by `mps.version.major`/`mps.version` (see CopyMps.kt).
// Modules that build an actual MPS plugin apply `modelix-mps-plugin` instead.

plugins {
    id("modelix-kotlin-jvm")
    id("org.jetbrains.intellij.platform")
    id("modelix-project-repositories")
}

repositories {
    intellijPlatform {
        localPlatformArtifacts()
    }
}

dependencies {
    intellijPlatform {
        local(copyMps())
        testFramework(TestFrameworkType.Bundled)
    }
}

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

configureMpsTestClasspath()

tasks.test {
    configureMpsTestTask()
}
