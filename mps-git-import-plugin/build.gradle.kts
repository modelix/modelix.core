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
    val excludeMPSLibraries: (ModuleDependency).() -> Unit = {
        exclude("org.slf4j", "slf4j-api")
    }

    implementation(project(":mps-git-import"), excludeMPSLibraries)
    implementation(project(":bulk-model-sync-lib"), excludeMPSLibraries)
    implementation(project(":bulk-model-sync-mps"), excludeMPSLibraries)
    implementation(project(":mps-model-adapters"), excludeMPSLibraries)
    implementation(project(":model-client"), excludeMPSLibraries)
    implementation(project(":datastructures"), excludeMPSLibraries)
    implementation(libs.modelix.mpsApi, excludeMPSLibraries)
    implementation(libs.kotlin.logging, excludeMPSLibraries)
    implementation(libs.kotlin.datetime, excludeMPSLibraries)
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.5.0.202512021534-r")
    implementation("com.github.ajalt.clikt:clikt:5.0.3")

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
        sinceBuild.set("241")
        untilBuild.set("241.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        autoReloadPlugins.set(true)
    }

    test {
        dependsOn(":model-server:jibDockerBuild")
        jvmArgs("-Dmodelix.model.server.image=modelix/model-server:$version")
        onlyIf {
            mpsMajorVersion == "2024.1"
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

    val mpsPluginDir = project.findProperty("mps$mpsPlatformVersion.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        create<Sync>("installMpsPlugin") {
            dependsOn(prepareSandbox)
            from(project.layout.buildDirectory.dir("idea-sandbox/plugins/mps-git-import-plugin"))
            into(mpsPluginDir.resolve("mps-git-import-plugin"))
        }
    }
}

group = "org.modelix.mps"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "mps-git-import-plugin"
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
