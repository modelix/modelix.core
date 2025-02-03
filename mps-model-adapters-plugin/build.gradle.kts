import org.modelix.copyMps
import org.modelix.excludeMPSLibraries
import org.modelix.mpsMajorVersion

plugins {
    `modelix-kotlin-jvm`
    alias(libs.plugins.intellij)
    `modelix-project-repositories`
}

dependencies {
    testImplementation(project(":mps-model-adapters"), excludeMPSLibraries)
    testImplementation(kotlin("test"))
}

intellij {
    localPath = copyMps().absolutePath
    instrumentCode = false
}

tasks {
    patchPluginXml {
        sinceBuild.set("211")
        untilBuild.set("241.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        autoReloadPlugins.set(true)
    }

    test {
        onlyIf { mpsMajorVersion != "2020.3" } // incompatible with the intellij plugin
        jvmArgs("-Dintellij.platform.load.app.info.from.resources=true")
    }

    val mpsPluginDir = project.findProperty("mps.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        create<Sync>("installMpsPlugin") {
            dependsOn(prepareSandbox)
            from(project.layout.buildDirectory.dir("idea-sandbox/plugins/mps-model-adapters-plugin"))
            into(mpsPluginDir.resolve("mps-model-adapters-plugin"))
        }
    }
}

group = "org.modelix.mps"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "mps-model-adapters-plugin"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
