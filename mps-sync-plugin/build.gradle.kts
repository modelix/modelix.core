plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.15.0"
}

val syncLib: Configuration by configurations.creating
syncLib.resolutionStrategy {
    force("org.modelix:model-api:2.11.0")
    force("org.modelix:model-client:2.10.9")
}

dependencies {
//    implementation(project(":model-server-lib"))
    implementation(project(":mps-model-adapters"))

    syncLib("org.modelix:mps-sync-lib:2.11.0-SECURE-SNAPSHOT")

    compileOnly("com.jetbrains:mps-openapi:2021.1.4")
    compileOnly("com.jetbrains:mps-core:2021.1.4")
    compileOnly("com.jetbrains:mps-environment:2021.1.4")
    compileOnly("com.jetbrains:mps-platform:2021.1.4")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {

    // IDEA platform version used in MPS 2021.1.4: https://github.com/JetBrains/MPS/blob/2021.1.4/build/version.properties#L11
    version.set("211.7628.21")
    // MPS 2020.3
    // version.set("203.8084.24")

    // type.set("IC") // Target IDE Platform

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
