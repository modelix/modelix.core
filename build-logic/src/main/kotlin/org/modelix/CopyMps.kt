package org.modelix

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
import java.io.File
import java.util.zip.ZipInputStream

val Project.mpsMajorVersion: String get() {
    if (project != rootProject) return rootProject.mpsMajorVersion
    return project.findProperty("mps.version.major")?.toString()?.takeIf { it.isNotEmpty() }
        ?: project.findProperty("mps.version")?.toString()?.takeIf { it.isNotEmpty() }?.replace(Regex("""(20\d\d\.\d+).*"""), "$1")
        ?: "2024.1"
}

val Project.mpsVersion: String get() {
    if (project != rootProject) return rootProject.mpsVersion
    return project.findProperty("mps.version")?.toString()?.takeIf { it.isNotEmpty() }
        ?: mpsMajorVersion.let {
            requireNotNull(
                mapOf(
                    // https://artifacts.itemis.cloud/service/rest/repository/browse/maven-mps/com/jetbrains/mps/
                    // We only support MPS 2022.2+, which runs on JDK 17. Older versions ran on JDK 11 (see MODELIX_JDK_VERSION).
                    "2022.2" to "2022.2.4",
                    "2022.3" to "2022.3.3",
                    "2023.2" to "2023.2.2",
                    "2023.3" to "2023.3.2",
                    "2024.1" to "2024.1.1",
                    "2024.3" to "2024.3",
                    "2025.1" to "2025.1.2",
                )[it],
            ) { "Unknown MPS version: $it" }
        }
}

val Project.mpsPlatformVersion: Int get() {
    return mpsVersion.replace(Regex("""20(\d\d)\.(\d+).*"""), "$1$2").toInt()
}

val Project.mpsHomeDir: Provider<Directory> get() {
    if (project != rootProject) return rootProject.mpsHomeDir
    return project.layout.buildDirectory.dir("mps-$mpsVersion")
}

fun Project.copyMps(): File {
    if (project != rootProject) return rootProject.copyMps()

    val mpsHome = mpsHomeDir.get().asFile
    if (mpsHome.exists()) return mpsHome

    println("Extracting MPS ...")

    // Extract MPS during configuration phase, because using it in intellij.localPath requires it to already exist.
    val mpsZip = configurations.create("mpsZip")
    dependencies {
        mpsZip("com.jetbrains:mps:$mpsVersion")
    }
    sync {
        from(zipTree({ mpsZip.singleFile }))
        into(mpsHomeDir)
    }

    // The IntelliJ gradle plugin doesn't search in jar files when reading plugin descriptors, but the IDE does.
    // Copy the XML files from the jars to the META-INF folders to fix that.
    for (pluginFolder in (mpsHomeDir.get().asFile.resolve("plugins").listFiles() ?: emptyArray())) {
        val jars = (pluginFolder.resolve("lib").listFiles() ?: emptyArray()).filter { it.extension == "jar" }
        for (jar in jars) {
            jar.inputStream().use {
                ZipInputStream(it).use { zip ->
                    val entries = generateSequence { zip.nextEntry }
                    for (entry in entries) {
                        if (entry.name.substringBefore("/") != "META-INF") continue
                        val outputFile = pluginFolder.resolve(entry.name)
                        if (outputFile.extension != "xml") continue
                        if (outputFile.exists()) {
                            println("already exists: $outputFile")
                            continue
                        }
                        outputFile.parentFile.mkdirs()
                        outputFile.writeBytes(zip.readAllBytes())
                        println("copied $outputFile")
                    }
                }
            }
        }
    }

    // The gradle-intellij-plugin 1.x reads the launch information from product-info.json and injects it into the
    // forked test JVM. Two pieces of that, meant for launching the real IDE, break the test executor on 2025.1+;
    // MPS <= 2024.1 ships no product-info.json, so the plugin injects neither and the tests pass. Both are sanitized
    // here, keeping the file present and valid (it also serves as the PathManager marker).
    val productInfo = mpsHomeDir.get().asFile.resolve("product-info.json")
    if (productInfo.exists()) {
        @Suppress("UNCHECKED_CAST")
        val json = groovy.json.JsonSlurper().parse(productInfo) as MutableMap<String, Any?>
        val launches = json["launch"] as? List<*> ?: emptyList<Any?>()
        for (launch in launches.filterIsInstance<MutableMap<String, Any?>>()) {
            // resolveIdeHomeVariable assumes every additionalJvmArgument is "key=value" and does split("=")[1],
            // so an argument without "=" (e.g. -XX:+UseCompressedOops) throws IndexOutOfBoundsException while
            // configuring the test task. Drop them; OpenedPackages provides the --add-opens the platform needs.
            if (launch.containsKey("additionalJvmArguments")) {
                launch["additionalJvmArguments"] = emptyList<String>()
            }
            // The plugin passes every line of the referenced .vmoptions file to the JVM verbatim, including the
            // "#Common IntelliJ Platform options:" comment lines. The launcher treats such a token as the main
            // class, so the -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader it also sets cannot
            // be resolved (the -cp argfile after the token is ignored) and the VM aborts during initialization.
            // Strip comment and blank lines, keeping the real options (heap sizes, GC settings, ...).
            val vmOptionsPath = (launch["vmOptionsFilePath"] as? String)?.removePrefix("../")
            val vmOptionsFile = vmOptionsPath?.let { mpsHomeDir.get().asFile.resolve(it) }
            if (vmOptionsFile != null && vmOptionsFile.exists()) {
                val kept = vmOptionsFile.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                vmOptionsFile.writeText(kept.joinToString("\n", postfix = "\n"))
            }
        }

        // The Maven MPS distribution declares only a Linux/amd64 launch entry. The plugin refuses to
        // run on a host whose os.arch has no matching launch entry, which blocks running the IDE tests
        // locally on e.g. Apple Silicon. Add an entry for the current host (cloned from the existing
        // one, so it inherits the sanitized fields) when none matches. On CI (Linux/amd64) the entry
        // already exists, so this is a no-op.
        val mutableLaunches = launches.filterIsInstance<MutableMap<String, Any?>>()
        if (mutableLaunches.isNotEmpty()) {
            val osName = System.getProperty("os.name").lowercase()
            val currentOs = when {
                osName.contains("win") -> "Windows"
                osName.contains("mac") || osName.contains("darwin") -> "macOS"
                else -> "Linux"
            }
            val currentArch = System.getProperty("os.arch")
            val hasMatch = mutableLaunches.any { it["os"] == currentOs && it["arch"] == currentArch }
            if (!hasMatch) {
                @Suppress("UNCHECKED_CAST")
                val launchList = json["launch"] as MutableList<Any?>
                val hostLaunch = LinkedHashMap(mutableLaunches.first())
                hostLaunch["os"] = currentOs
                hostLaunch["arch"] = currentArch
                launchList.add(hostLaunch)
            }
        }

        productInfo.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)))
    }

    // The build number of a local IDE is expected to contain a product code, otherwise an exception is thrown.
    val buildTxt = mpsHomeDir.get().asFile.resolve("build.txt")
    val buildNumber = buildTxt.readText()
    val prefix = "MPS-"
    if (!buildNumber.startsWith(prefix)) {
        buildTxt.writeText("$prefix$buildNumber")
    }

    println("Extracting MPS done.")
    return mpsHome
}

val excludeMPSLibraries: (ModuleDependency).() -> Unit = {
    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8")
    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-swing")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    exclude("org.jetbrains", "annotations")
}

/**
 * Project-level classpath adjustments for modules whose tests boot MPS via the gradle-intellij-plugin
 * 1.x (mps-sync-plugin3, mps-model-adapters-plugin, bulk-model-sync-lib/mps-test). Only needed on
 * MPS 2025.1+ (platform >= 251). Configure the test tasks themselves with [configureMpsTestTask].
 */
fun Project.configureMpsTestClasspath() {
    if (mpsPlatformVersion < 251) return

    // MPS 2025.1+ loads platform services (e.g. SettingsController) from module descriptors in
    // lib/modules/*.jar. The 1.x plugin doesn't put these on the test classpath, so add them
    // explicitly; without them the test application fails to boot. Older MPS has no lib/modules.
    dependencies.add(
        "testRuntimeOnly",
        fileTree(mpsHomeDir).matching { include("lib/modules/*.jar") },
    )

    // MPS bundles JetBrains' coroutines fork (lib/util-8.jar) whose BuildersKt has
    // runBlockingWithParallelismCompensation, which the platform calls during boot. The vanilla
    // kotlinx-coroutines-core pulled in transitively lacks that method, so keep it off the test
    // runtime classpath and let MPS's bundled coroutines win.
    configurations.named("testRuntimeClasspath").configure {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
}

/**
 * Configures a single test task that boots MPS via the gradle-intellij-plugin 1.x. Pair it with
 * [configureMpsTestClasspath] on the owning project.
 */
fun Test.configureMpsTestTask() {
    // Use a provider to avoid eagerly resolving the MPS home dir
    jvmArgumentProviders.add {
        buildList {
            // JNA's native libraries live under lib/jna/<arch> in the MPS home. Point the test JVM there
            // so JNA loads the bundled library instead of trying to unpack one from the classpath.
            // Older MPS versions (2022.2) ship no lib/jna and keep the natives inside the classpath jar,
            // so the properties must not be set there — jna.noclasspath would leave JNA with no library.
            val jnaDir = project.mpsHomeDir.get().asFile.resolve("lib/jna/${System.getProperty("os.arch")}")
            if (jnaDir.exists()) {
                add("-Djna.boot.library.path=${jnaDir.absolutePath}")
                add("-Djna.noclasspath=true")
                add("-Djna.nosys=true")
            }

            add("-Dintellij.platform.load.app.info.from.resources=true")
        }
    }
}
