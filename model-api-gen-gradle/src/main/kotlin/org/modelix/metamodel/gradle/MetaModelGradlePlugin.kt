package org.modelix.metamodel.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import java.io.File
import java.net.URL
import java.util.Enumeration
import java.util.Properties

class MetaModelGradlePlugin : Plugin<Project> {
    private lateinit var project: Project
    private lateinit var settings: MetaModelGradleSettings
    private lateinit var buildDir: File

    override fun apply(project: Project) {
        this.project = project
        this.settings = project.extensions.create("metamodel", MetaModelGradleSettings::class.java)
        this.buildDir = project.buildDir

        val exporterDependencies = project.configurations.create("metamodel-mps-dependencies")
        val exporterDir = getBuildOutputDir().resolve("mpsExporter")
        val modelixCoreVersion = readModelixCoreVersion() ?: throw RuntimeException("modelix.core version not found")
        project.dependencies.add(exporterDependencies.name, "org.modelix.mps:metamodel-export:$modelixCoreVersion")
        val downloadExporterDependencies = project.tasks.register("downloadMetaModelExporter", Sync::class.java) { task ->
            task.enabled = settings.jsonDir == null
            task.from(exporterDependencies.resolve().map { project.zipTree(it) })
            task.into(exporterDir)
        }

        val generateAntScriptForMpsMetaModelExport = project.tasks.register("generateAntScriptForMpsMetaModelExport", GenerateAntScriptForMpsMetaModelExport::class.java) { task ->
            task.enabled = settings.jsonDir == null
            task.dependsOn(downloadExporterDependencies)
            task.dependsOn(*settings.taskDependencies.toTypedArray())
            task.mpsHome.set(getMpsHome()?.absolutePath)
            task.heapSize.set(settings.mpsHeapSize)
            if (settings.includedModules.isNotEmpty()) task.exportModulesFilter.set(settings.includedModules.joinToString(","))
            task.antScriptFile.set(getAntScriptFile())
            task.exporterDir.set(exporterDir.absolutePath)
            task.moduleFolders.addAll(settings.moduleFolders.map { it.absolutePath })
            task.inputs.property("coreVersion", modelixCoreVersion)
        }

        val antDependencies = project.configurations.create("metamodel-ant-dependencies")
        project.dependencies.add(antDependencies.name, "org.apache.ant:ant-junit:1.10.12")

        val exportedLanguagesDir = getBuildOutputDir().resolve("exported-languages")
        val exportMetaModelFromMps = project.tasks.register("exportMetaModelFromMps", JavaExec::class.java) { task ->
            task.enabled = settings.jsonDir == null
            task.inputs.property("coreVersion", modelixCoreVersion)
            task.outputs.cacheIf { task.enabled }
            task.workingDir = getBuildOutputDir()
            task.mainClass.set("org.apache.tools.ant.launch.Launcher")
            task.classpath(antDependencies)

            val mpsHome = getMpsHome()
            val antVariables = listOf(
                "mps.home" to mpsHome?.absolutePath,
                "mps_home" to mpsHome?.absolutePath,
                "build.dir" to getBuildOutputDir().absolutePath,
            ).map { (key, value) -> "-D$key=$value" }
            task.args(antVariables)
            task.args("-buildfile", getAntScriptFile())
            task.args("export-languages")

            settings.moduleFolders.forEach { task.inputs.dir(it) }
            task.outputs.dir(exportedLanguagesDir)

            task.dependsOn(generateAntScriptForMpsMetaModelExport)
            task.dependsOn(downloadExporterDependencies)
        }
        project.afterEvaluate {
            exportMetaModelFromMps.configure { task ->
                val javaExecutable = settings.javaExecutable
                if (javaExecutable != null) {
                    task.executable(javaExecutable.absoluteFile)
                }
                settings.moduleFolders.forEach { task.inputs.dir(it) }
                task.inputs.dir(getMpsHome())
            }
        }
        val generateMetaModelSources = project.tasks.register("generateMetaModelSources", GenerateMetaModelSources::class.java) { task ->
            task.dependsOn(exportMetaModelFromMps)
            task.inputs.property("coreVersion", modelixCoreVersion)
        }
        project.afterEvaluate {
            generateMetaModelSources.configure { task ->
                settings.kotlinDir?.let { task.kotlinOutputDir.set(it) }
                settings.modelqlKotlinDir?.let { task.modelqlKotlinOutputDir.set(it) }
                settings.typescriptDir?.let { task.typescriptOutputDir.set(it) }
                settings.npmPackageName?.let { task.npmPackageName.set(it) }
                settings.kotlinTargetPlatform?.let { task.kotlinTargetPlatform.set(it) }
                task.includedNamespaces.addAll(settings.includedLanguageNamespaces)
                task.includedLanguages.addAll(settings.includedLanguages)
                task.includedConcepts.addAll(settings.includedConcepts)
                if (settings.jsonDir != null) {
                    task.exportedLanguagesDir.set(settings.jsonDir)
                } else {
                    task.exportedLanguagesDir.set(exportedLanguagesDir)
                }
                settings.registrationHelperName?.let { task.registrationHelperName.set(it) }
                task.nameConfig.set(settings.nameConfig)
            }
        }

        project.afterEvaluate {
            val registerDependencies: (Project) -> Unit = { kotlinProject ->
                kotlinProject.tasks.matching { it.name.matches(Regex("""(.*compile.*Kotlin.*|.*[sS]ourcesJar.*)""")) }.configureEach {
                    it.dependsOn(generateMetaModelSources)
                }
            }
            val configuredKotlinProject = settings.kotlinProject
            if (configuredKotlinProject != null) {
                registerDependencies(configuredKotlinProject)
            } else {
                project.allprojects { registerDependencies(it) }
            }
        }
    }

    private fun getBuildOutputDir() = buildDir.resolve("metamodel")

    private fun getAntScriptFile() = getBuildOutputDir().resolve("export-languages.xml")

    private fun getMpsHome(checkExistence: Boolean = false): File? {
        val mpsHome = this.settings.mpsHome
        val jsonDir = this.settings.jsonDir
        require(mpsHome != null || jsonDir != null) { "'mpsHome' is not set in the 'metamodel' settings" }
        if (checkExistence) {
            require(mpsHome?.exists() ?: false) { "'mpsHome' doesn't exist: ${mpsHome?.absolutePath}" }
        }
        return mpsHome
    }

    private fun readModelixCoreVersion(): String? {
        val resources: Enumeration<URL>? = javaClass.classLoader.getResources("modelix.core.version.properties")
        while (resources != null && resources.hasMoreElements()) {
            val properties = resources.nextElement().openStream().use { Properties().apply { load(it) } }
            return properties.getProperty("modelix.core.version")
        }
        return null
    }
}
