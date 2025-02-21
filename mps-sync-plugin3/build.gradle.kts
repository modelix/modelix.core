import org.modelix.copyMps
import org.modelix.mpsHomeDir
import org.modelix.mpsMajorVersion
import org.modelix.mpsPlatformVersion

plugins {
    `modelix-kotlin-jvm`
    alias(libs.plugins.intellij)
    `modelix-project-repositories`
}

intellij {
    localPath = copyMps().absolutePath
    instrumentCode = false
}

dependencies {
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

    // testImplementation("junit:junit:4.13.2")
    testImplementation(libs.testcontainers)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.logback.classic)
    testImplementation(kotlin("test"))
}

tasks {
    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("241.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        autoReloadPlugins.set(true)
    }

    test {
        dependsOn(":model-server:assemble")
        onlyIf {
            !setOf(
                "2020.3", // incompatible with the intellij plugin
            ).contains(mpsMajorVersion)
        }
        jvmArgs("-Dintellij.platform.load.app.info.from.resources=true")
        jvmArgs("-Xmx1000m")

        val arch = System.getProperty("os.arch")
        val jnaDir = mpsHomeDir.get().asFile.resolve("lib/jna/$arch")
        if (jnaDir.exists()) {
            jvmArgs("-Djna.boot.library.path=${jnaDir.absolutePath}")
            jvmArgs("-Djna.noclasspath=true")
            jvmArgs("-Djna.nosys=true")
        }
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
            artifactId = "mps-sync-plugin3"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}

// disable coroutines agent
if (mpsPlatformVersion < 241) {
    afterEvaluate {
        val testTask = tasks.test.get()
        val originalProviders = testTask.jvmArgumentProviders.toList()
        testTask.jvmArgumentProviders.clear()
        testTask.jvmArgumentProviders.add(object : CommandLineArgumentProvider {
            override fun asArguments(): Iterable<String> {
                return originalProviders.flatMap { it.asArguments() }.filterNot { it.contains("coroutines-javaagent.jar") }
            }
        })
    }
}
