import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.modelix.mpsHomeDir

plugins {
    alias(libs.plugins.intellij2)
    `modelix-kotlin-jvm-with-junit`
    `modelix-project-repositories`
}

repositories {
    intellijPlatform {
        defaultRepositories()
        localPlatformArtifacts()
    }
}

intellijPlatform {
}

dependencies {
    intellijPlatform {
        local(mpsHomeDir.map { it.asFile.absolutePath }.get())
        // intellijIdeaCommunity("2024.3")
        testFramework(TestFrameworkType.Platform)
    }

    implementation(project(":bulk-model-sync-lib"))
    implementation(project(":bulk-model-sync-mps"))
    implementation(project(":mps-model-adapters"))
    implementation(project(":model-client"))
    implementation(libs.modelix.mpsApi)
    implementation(libs.kotlin.logging)

    compileOnly(
        fileTree(mpsHomeDir).matching {
            include("lib/**/*.jar")
        },
    )

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.testcontainers)
    testImplementation(libs.kotlin.coroutines.test)
}

group = "org.modelix.mps"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "mps-sync-plugin3"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
