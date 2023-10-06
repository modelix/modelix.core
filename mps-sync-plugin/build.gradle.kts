plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "org.modelix.mps"
val mpsZip by configurations.creating

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
println("Building for MPS version $mpsVersion and IntelliJ version $ideaVersion")

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    api(project(":model-api"))

    implementation(project(":mps-model-adapters"))
    implementation(project(":sync-plugin-lib"))
    implementation(project(":model-client", configuration = "jvmRuntimeElements"))
    implementation(project(":model-api", configuration = "jvmRuntimeElements"))
    implementation(project(":model-datastructure", configuration = "jvmRuntimeElements"))

    implementation(libs.kotlin.reflect)

    // extracting jars from zipped products
    mpsZip("com.jetbrains:mps:$mpsVersion")
    compileOnly(
        zipTree({ mpsZip.singleFile }).matching {
            include("lib/**/*.jar")
        },
    )
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set(ideaVersion)

    // only relevant when running MPS 'inside of intellij'
    // plugins.set(listOf("jetbrains.mps.core", "com.intellij.modules.mps"))
}

tasks {

    // This plugin in intended to be used by all 'supported' MPS versions, as a result we need to use the lowest
    // common java version, which is JAVA 11 to ensure bytecode compatibility.
    // However, when building with MPS >= 2022.3 to ensure compileOnly dependency compatibility, we need to build
    // with JAVA 17 explicitly.
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        if (mpsVersion == "2022.3") {
            kotlinOptions.jvmTarget = "17"
            java.sourceCompatibility = JavaVersion.VERSION_17
            java.targetCompatibility = JavaVersion.VERSION_17
        } else {
            kotlinOptions.jvmTarget = "11"
            java.sourceCompatibility = JavaVersion.VERSION_11
            java.targetCompatibility = JavaVersion.VERSION_11
        }
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

// val buildMpsModules by tasks.registering() {
//    dependsOn(
//
//    )
//    description = "Build all MPS versions"
// }

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
