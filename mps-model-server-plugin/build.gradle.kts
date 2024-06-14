import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.modelix.mpsHomeDir

plugins {
    kotlin("jvm")
    alias(libs.plugins.intellij)
    id("modelix-project-repositories")
}

dependencies {
    implementation(project(":model-server-lib"))
    implementation(project(":mps-model-adapters"))
    testImplementation(project(":light-model-client", configuration = "jvmRuntimeElements"))

    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.cors)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.html.builder)
    testImplementation(libs.ktor.server.auth)
    testImplementation(libs.ktor.server.auth.jwt)
    testImplementation(libs.ktor.server.status.pages)
    testImplementation(libs.ktor.server.forwarded.header)
    testImplementation(libs.ktor.server.websockets)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.resources)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    localPath = mpsHomeDir.get().asFile.absolutePath
    instrumentCode = false
}

java {
    sourceCompatibility = JavaVersion.toVersion(11)
    targetCompatibility = JavaVersion.toVersion(11)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("241.*")
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
