import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.16.0"
}

val mpsVersion = project.findProperty("mps.version").toString()
val ideaVersion = project.findProperty("mps.platform.version").toString()
val mpsJavaVersion = project.findProperty("mps.java.version").toString()
val mpsHome = rootProject.layout.buildDirectory.dir("mps-$mpsVersion")

dependencies {
    implementation(project(":model-server-lib"))
    implementation(project(":mps-model-adapters"))

    val mpsTree = mpsHome.map {
        it.asFileTree.matching {
            include("lib/**/*.jar")
        }
    }
    compileOnly(mpsTree)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    localPath = mpsHome.map { it.asFile.absolutePath }
    // plugins.set(listOf("jetbrains.mps.core", "com.intellij.modules.mps"))
    instrumentCode = false
}

java {
    sourceCompatibility = JavaVersion.toVersion(mpsJavaVersion)
    targetCompatibility = JavaVersion.toVersion(mpsJavaVersion)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(mpsJavaVersion))
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = mpsJavaVersion
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

tasks.register("mpsCompatibility") { dependsOn("build") }
