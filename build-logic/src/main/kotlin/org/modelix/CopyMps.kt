package org.modelix

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.exclude
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

val Project.mpsMajorVersion: String get() {
    if (project != rootProject) return rootProject.mpsMajorVersion
    return project.findProperty("mps.version.major")?.toString()?.takeIf { it.isNotEmpty() }
        ?: project.findProperty("mps.version")?.toString()?.takeIf { it.isNotEmpty() }?.replace(Regex("""(20\d\d\.\d+).*"""), "$1")
        ?: "2025.1"
}

val Project.mpsVersion: String get() {
    if (project != rootProject) return rootProject.mpsVersion
    return project.findProperty("mps.version")?.toString()?.takeIf { it.isNotEmpty() }
        ?: mpsMajorVersion.let {
            requireNotNull(
                mapOf(
                    // https://artifacts.itemis.cloud/service/rest/repository/browse/maven-mps/com/jetbrains/mps/
                    // We only support MPS 2024.1+.
                    "2024.1" to "2024.1.1",
                    "2024.3" to "2024.3",
                    "2025.1" to "2025.1.2",
                    "2026.1" to "2026.1.1",
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
    val mpsHome = mpsHomeDir.get().asFile
    if (mpsHome.exists()) return mpsHome

    println("Extracting MPS ...")

    // Extract MPS during configuration phase, because using it in intellijPlatform.local requires it to already exist.
    // The distribution is resolved in the context of the calling project, because Gradle doesn't allow resolving
    // a configuration of another project (e.g. the root project) while this one is configured.
    val mpsZip = configurations.detachedConfiguration(dependencies.create("com.jetbrains:mps:$mpsVersion"))
    sync {
        from(zipTree(mpsZip.singleFile))
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

        // The IntelliJ Platform Gradle Plugin refuses to parse XML files with a DOCTYPE declaration (XXE protection)
        // and silently ignores such plugins. Many MPS plugin descriptors contain one. The copies are only read by the
        // Gradle plugin, the IDE reads the descriptors from the jars.
        for (descriptor in (pluginFolder.resolve("META-INF").listFiles() ?: emptyArray()).filter { it.extension == "xml" }) {
            val text = descriptor.readText()
            val withoutDoctype = text.replace(Regex("""<!DOCTYPE[^>]*>\s*"""), "")
            if (withoutDoctype != text) descriptor.writeText(withoutDoctype)
        }
    }

    completeProductInfo(mpsHome)
    provideCorePluginDescriptorForIdeaPrefix(mpsHome)
    provideModuleDescriptors(mpsHome)

    // The launch information in product-info.json is meant for launching the real IDE, but it's also used to
    // configure the forked test JVM. It's sanitized here, keeping the file present and valid (it also serves as the
    // PathManager marker).
    val productInfo = mpsHomeDir.get().asFile.resolve("product-info.json")
    if (productInfo.exists()) {
        @Suppress("UNCHECKED_CAST")
        val json = groovy.json.JsonSlurper().parse(productInfo) as MutableMap<String, Any?>
        val launches = json["launch"] as? List<*> ?: emptyList<Any?>()
        for (launch in launches.filterIsInstance<MutableMap<String, Any?>>()) {
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

/**
 * The IntelliJ Platform Gradle Plugin requires a product-info.json that lists the bundled plugins and their class
 * paths (the layout). The Maven distribution of MPS 2024.1 contains no product-info.json at all, and the ones of later
 * versions list neither the bundled plugins nor the layout. The missing parts are generated from the launcher script
 * and the plugin descriptors.
 */
private fun Project.completeProductInfo(mpsHome: File) {
    val productInfo = mpsHome.resolve("product-info.json")

    @Suppress("UNCHECKED_CAST")
    val json: MutableMap<String, Any?> = if (productInfo.exists()) {
        groovy.json.JsonSlurper().parse(productInfo) as MutableMap<String, Any?>
    } else {
        val launcherScript = mpsHome.resolve("bin/mps.sh").readText()
        linkedMapOf(
            "name" to "JetBrains MPS",
            "version" to mpsVersion,
            "buildNumber" to mpsHome.resolve("build.txt").readText().trim().removePrefix("MPS-"),
            "productCode" to "MPS",
            "envVarBaseName" to "MPS",
            "dataDirectoryName" to "MPS$mpsMajorVersion",
            "svgIconPath" to "bin/mps.svg",
            "productVendor" to "JetBrains",
            "launch" to mutableListOf(
                linkedMapOf(
                    "os" to "Linux",
                    "arch" to "amd64",
                    "launcherPath" to "bin/mps.sh",
                    "javaExecutablePath" to "jbr/bin/java",
                    "vmOptionsFilePath" to "bin/mps64.vmoptions",
                    "bootClassPathJarNames" to Regex("""\${'$'}IDE_HOME/lib/([^":]+\.jar)""")
                        .findAll(launcherScript).map { it.groupValues[1] }.toList(),
                    "additionalJvmArguments" to Regex("""--add-opens=\S+""").findAll(launcherScript).map { it.value }.toList(),
                    "mainClass" to (Regex("""MAIN_CLASS=(\S+)""").find(launcherScript)?.groupValues?.get(1) ?: "jetbrains.mps.Launcher"),
                ),
            ),
        )
    }

    if (json["layout"] == null) {
        // The plugin descriptors were copied out of the jars into the META-INF folders by copyMps.
        val plugins = (mpsHome.resolve("plugins").listFiles() ?: emptyArray()).sortedBy { it.name }.mapNotNull { pluginFolder ->
            val descriptor = pluginFolder.resolve("META-INF/plugin.xml").takeIf { it.isFile } ?: return@mapNotNull null
            val descriptorText = descriptor.readText()
            val pluginId = (Regex("<id>([^<]+)</id>").find(descriptorText) ?: Regex("<name>([^<]+)</name>").find(descriptorText))
                ?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            val classPath = (pluginFolder.resolve("lib").listFiles() ?: emptyArray())
                .filter { it.extension == "jar" }
                .sortedBy { it.name }
                .map { it.relativeTo(mpsHome).invariantSeparatorsPath }
            pluginId to classPath
        }
        json["bundledPlugins"] = plugins.map { it.first }
        json["layout"] = plugins.map { (pluginId, classPath) -> linkedMapOf("name" to pluginId, "kind" to "plugin", "classPath" to classPath) }
    }
    if (json["modules"] == null) {
        json["modules"] = emptyList<String>()
    }

    productInfo.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)))
}

/**
 * Since 2026.1, MPS uses its own platform prefix (MPS instead of Idea), so the descriptor of the core plugin is
 * META-INF/MPSPlugin.xml. The IntelliJ Platform Gradle Plugin still assumes the Idea prefix for MPS and only looks for
 * META-INF/IdeaPlugin.xml. A copy of the descriptor under that name is provided in an additional jar. MPS itself
 * ignores it, because it only loads the descriptor matching its platform prefix.
 */
private fun provideCorePluginDescriptorForIdeaPrefix(mpsHome: File) {
    val libDir = mpsHome.resolve("lib")
    val jars = (libDir.listFiles() ?: emptyArray()).filter { it.extension == "jar" }
    fun findEntry(entryName: String): File? = jars.firstOrNull { jar -> ZipFile(jar).use { it.getEntry(entryName) != null } }

    if (findEntry("META-INF/IdeaPlugin.xml") != null) return
    val jarWithDescriptor = findEntry("META-INF/MPSPlugin.xml") ?: return
    val descriptor = ZipFile(jarWithDescriptor).use { zip -> zip.getInputStream(zip.getEntry("META-INF/MPSPlugin.xml")).readAllBytes() }
    ZipOutputStream(libDir.resolve("modelix-idea-core-plugin-descriptor.jar").outputStream()).use { zip ->
        zip.putNextEntry(ZipEntry("META-INF/IdeaPlugin.xml"))
        zip.write(descriptor)
        zip.closeEntry()
    }
}

/**
 * For IDEs based on the IntelliJ Platform 2026.1+, the IntelliJ Platform Gradle Plugin expects the descriptors of the
 * product modules in modules/module-descriptors.jar, which isn't part of MPS. An empty one is sufficient, because we
 * don't depend on any product modules (`bundledModule`).
 */
private fun Project.provideModuleDescriptors(mpsHome: File) {
    if (mpsPlatformVersion < 261) return
    val moduleDescriptorsJar = mpsHome.resolve("modules/module-descriptors.jar")
    if (moduleDescriptorsJar.exists()) return
    moduleDescriptorsJar.parentFile.mkdirs()
    ZipOutputStream(moduleDescriptorsJar.outputStream()).use { zip ->
        zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
        zip.write("Manifest-Version: 1.0\n".toByteArray())
        zip.closeEntry()
    }
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
 * Project-level adjustments for modules whose tests boot MPS via the IntelliJ Platform Gradle Plugin
 * (mps-sync-plugin3, mps-model-adapters-plugin, mps-git-import-plugin, bulk-model-sync-lib/mps-test).
 * Configure the test tasks themselves with [configureMpsTestTask].
 */
fun Project.configureMpsTestClasspath() {
    // The tests run with the Kotlin stdlib bundled with MPS, so the test code is restricted to the same
    // API version as the main code.
    tasks.withType(KotlinJvmCompile::class.java).configureEach {
        compilerOptions.apiVersion.set(MODELIX_KOTLIN_API_VERSION)
    }
}

/**
 * Configures a single test task that boots MPS via the IntelliJ Platform Gradle Plugin. Pair it with
 * [configureMpsTestClasspath] on the owning project.
 */
fun Test.configureMpsTestTask() {
    // MPS 2025.1+ loads platform services (e.g. SettingsController) from module descriptors in
    // lib/modules/*.jar, which the IntelliJ Platform Gradle Plugin doesn't put on the test classpath.
    // They are appended to the classpath instead of being added as dependencies, because dependencies
    // end up in the test sandbox of the plugin, where they could shadow newer versions of libraries
    // (e.g. Ktor) that the plugin brings itself. Older MPS has no lib/modules.
    classpath += project.fileTree(project.mpsHomeDir) { include("lib/modules/*.jar") }

    // Use a provider to avoid eagerly resolving the MPS home dir
    jvmArgumentProviders.add {
        buildList {
            // JNA's native libraries live under lib/jna/<arch> in the MPS home. Point the test JVM there
            // so JNA loads the bundled library instead of trying to unpack one from the classpath.
            // Only set the properties if the directory exists — jna.noclasspath would otherwise leave JNA with no library.
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
