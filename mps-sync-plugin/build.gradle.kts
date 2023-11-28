plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.16.0"
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
    "2023.2" to "232.10072.27", // https://github.com/JetBrains/MPS/blob/2023.2.0/build/version.properties (?)
)
val mpsVersion = project.findProperty("mps.version")?.toString().takeIf { !it.isNullOrBlank() }!!
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
    implementation(project(":mps-sync-plugin-lib"))
    implementation(project(":model-client", configuration = "jvmRuntimeElements"))
    implementation(project(":model-api", configuration = "jvmRuntimeElements"))
    implementation(project(":model-datastructure", configuration = "jvmRuntimeElements"))

    implementation(libs.kotlin.reflect)

    // extracting jars from zipped products
    mpsZip("com.jetbrains:mps:$mpsVersion")
//    val mpsZipTree = zipTree({ mpsZip.singleFile }).matching {
//        include("lib/**/*.jar")
//    }
//    compileOnly(mpsZipTree)
//    testImplementation(mpsZipTree)

    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(project(":model-server"))
    testImplementation(project(":authorization"))
//    implementation(libs.ktor.server.core)
//    implementation(libs.ktor.server.cors)
//    implementation(libs.ktor.server.netty)
//    implementation(libs.ktor.server.html.builder)
//    implementation(libs.ktor.server.auth)
//    implementation(libs.ktor.server.auth.jwt)
//    implementation(libs.ktor.server.status.pages)
//    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
//    implementation(libs.ktor.serialization.json)
}

val mpsHome = project.layout.buildDirectory.dir("mps")

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {

    // You can only set 'localPath' or 'version'. We want 'localPath', but that has to exist at configuration time.
    if (mpsHome.get().asFile.exists()) {
        localPath.set(mpsHome.map { it.asFile.absolutePath })
    } else {
        // Tests will fail with this IDE, but after the first 'assemble' run 'mpsHome' exists, so it shouldn't happen
        // frequently. During CI the 'downloadMPS' task should be executed in a separate run before the actual 'build'.
        version.set(ideaVersion)
    }
    instrumentCode = false
}

tasks {

    val downloadMPS = register("downloadMPS", Sync::class.java) {
        from(zipTree({ mpsZip.singleFile }))
        into(mpsHome)

        doLast {
            // The build number of a local IDE is expected to contain a product code, otherwise an exception is thrown.
            val buildTxt = mpsHome.get().asFile.resolve("build.txt")
            val buildNumber = buildTxt.readText()
            val prefix = "MPS-"
            if (!buildNumber.startsWith(prefix)) {
                buildTxt.writeText("$prefix$buildNumber")
            }
        }
    }

    assemble { dependsOn(downloadMPS) }
    test { dependsOn(downloadMPS) }

    // This plugin in intended to be used by all 'supported' MPS versions, as a result we need to use the lowest
    // common java version, which is JAVA 11 to ensure bytecode compatibility.
    // However, when building with MPS >= 2022.3 to ensure compileOnly dependency compatibility, we need to build
    // with JAVA 17 explicitly.
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        if (mpsVersion >= "2022.2") {
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
