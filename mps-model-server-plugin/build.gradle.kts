import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.16.0"
}

val mpsToIdeaMap = mapOf(
    "2020.3.6" to "203.8084.24", // https://github.com/JetBrains/MPS/blob/2020.3.6/build/version.properties
    "2021.1.4" to "211.7628.21", // https://github.com/JetBrains/MPS/blob/2021.1.4/build/version.properties
    "2021.2.6" to "212.5284.40", // https://github.com/JetBrains/MPS/blob/2021.2.5/build/version.properties (?)
    "2021.3.3" to "213.7172.25", // https://github.com/JetBrains/MPS/blob/2021.3.3/build/version.properties
    "2022.2" to "222.4554.10", // https://github.com/JetBrains/MPS/blob/2021.2.1/build/version.properties
    "2022.3" to "223.8836.41", // https://github.com/JetBrains/MPS/blob/2022.3.0/build/version.properties (?)
)
// use the given MPS version, or 2022.2 (last version with JAVA 11) as default
val mpsVersion = project.findProperty("mps.version")?.toString().takeIf { !it.isNullOrBlank() } ?: "2020.3.6"
if (!mpsToIdeaMap.containsKey(mpsVersion)) {
    throw GradleException("Build for the given MPS version '$mpsVersion' is not supported.")
}
// identify the corresponding intelliJ platform version used by the MPS version
val ideaVersion = mpsToIdeaMap.getValue(mpsVersion)
val mpsJavaVersion = if (mpsVersion >= "2022.3") 17 else 11
println("Building for MPS version $mpsVersion and IntelliJ version $ideaVersion and Java $mpsJavaVersion")

dependencies {
    implementation(project(":model-server-lib"))
    implementation(project(":mps-model-adapters"))
    compileOnly("com.jetbrains:mps-openapi:$mpsVersion")
    compileOnly("com.jetbrains:mps-core:$mpsVersion")
    compileOnly("com.jetbrains:mps-environment:$mpsVersion")
    compileOnly("com.jetbrains:mps-platform:$mpsVersion")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {

    // IDEA platform version used in MPS 2021.1.4: https://github.com/JetBrains/MPS/blob/2021.1.4/build/version.properties#L11
    version.set(ideaVersion)

    // type.set("IC") // Target IDE Platform

    // plugins.set(listOf("jetbrains.mps.core", "com.intellij.modules.mps"))
}

java {
    sourceCompatibility = JavaVersion.toVersion(mpsJavaVersion)
    targetCompatibility = JavaVersion.toVersion(mpsJavaVersion)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(mpsJavaVersion.toString()))
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = mpsJavaVersion.toString()
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
