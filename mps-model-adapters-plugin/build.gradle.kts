import org.modelix.copyMps
import org.modelix.mpsJavaVersion

plugins {
    kotlin("jvm")
    alias(libs.plugins.intellij)
}

dependencies {
    testImplementation(project(":mps-model-adapters"))
}

intellij {
    localPath = copyMps().absolutePath
    instrumentCode = false
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = mpsJavaVersion.toString()
    }

    patchPluginXml {
        sinceBuild.set("211")
        untilBuild.set("232.10072.781")
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
            from(project.layout.buildDirectory.dir("idea-sandbox/plugins/mps-model-server-plugin"))
            into(mpsPluginDir.resolve("mps-model-server-plugin"))
        }
    }
}

group = "org.modelix.mps"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "model-server-plugin"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
