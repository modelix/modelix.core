plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.15.0"
}

val syncLib: Configuration by configurations.creating
syncLib.resolutionStrategy {
    force("org.modelix:model-api:2.11.0")
    force("org.modelix:model-client:2.10.9")
}

val mpsVersion = project.findProperty("mps.version")?.toString().takeIf { !it.isNullOrBlank() } ?: "2021.3.3"
val ideaVersion = "213.7172.25"

// TODO: map the version number to the correct idea platform number here and reuse below
// MPS - IDEA VERSION - URL
// 2022.3   - 223.8836.41 - https://github.com/JetBrains/MPS/blob/2022.3.0/build/version.properties ???
// 2022.2   - 222.4554.10 - https://github.com/JetBrains/MPS/blob/2022.2.1/build/version.properties
// 2021.3.3 - 213.7172.25 - https://github.com/JetBrains/MPS/blob/2021.3.3/build/version.properties
// 2021.2.6 - 212.5284.40 - https://github.com/JetBrains/MPS/blob/2021.2.5/build/version.properties ???
// 2021.1.4 - 211.7628.21 - https://github.com/JetBrains/MPS/blob/2021.1.4/build/version.properties
// 2020.3.6 - 203.8084.24 - https://github.com/JetBrains/MPS/blob/2020.3.6/build/version.properties

dependencies {
//    implementation(project(":model-server-lib"))
    api(project(":model-api"))

    implementation(project(":mps-model-adapters"))

    syncLib("org.modelix:mps-sync-lib:2.11.0-SECURE-SNAPSHOT")

    compileOnly("com.jetbrains:mps-openapi:$mpsVersion")
    compileOnly("com.jetbrains:mps-core:$mpsVersion")
    compileOnly("com.jetbrains:mps-environment:$mpsVersion")
    compileOnly("com.jetbrains:mps-platform:$mpsVersion")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set(ideaVersion)

    // only relevant when running MPS 'inside of intellij'
    // plugins.set(listOf("jetbrains.mps.core", "com.intellij.modules.mps"))
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("231.*")
    }

    buildSearchableOptions {
        enabled = false
    }

//    signPlugin {
//        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
//        privateKey.set(System.getenv("PRIVATE_KEY"))
//        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
//    }
//
//    publishPlugin {
//        token.set(System.getenv("PUBLISH_TOKEN"))
//    }

    runIde {
        autoReloadPlugins.set(true)
    }

    val mpsPluginDir = project.findProperty("mps.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        create<Sync>("installMpsPlugin") {
            dependsOn(prepareSandbox)
            from(buildDir.resolve("idea-sandbox/plugins/mps-sync-plugin"))
            into(mpsPluginDir.resolve("mps-sync-plugin"))
        }
    }
}

group = "org.modelix.mps"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sync-plugin"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
