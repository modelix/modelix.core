package org.modelix.metamodel.gradle

import org.apache.tools.ant.taskdefs.ExecuteJava
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.modelix.metamodel.generator.LanguageData
import org.modelix.metamodel.generator.LanguageSet
import org.modelix.metamodel.generator.MetaModelGenerator
import org.modelix.metamodel.generator.TypescriptMMGenerator
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.jar.Manifest

class MetaModelGradlePlugin: Plugin<Project> {
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
        project.dependencies.add(exporterDependencies.name, "org.modelix:metamodel-export-mps:$modelixCoreVersion")
        val downloadExporterDependencies = project.tasks.register("downloadMetaModelExporter", Sync::class.java) { task ->
            task.from(exporterDependencies.resolve().map { project.zipTree(it) })
            task.into(exporterDir)
        }

        val generateAntScriptForMpsMetaModelExport = project.tasks.register("generateAntScriptForMpsMetaModelExport", GenerateAntScriptForMpsMetaModelExport::class.java) { task ->
            task.dependsOn(downloadExporterDependencies)
            task.dependsOn(*settings.taskDependencies.toTypedArray())
            task.mpsHome.set(getMpsHome())
            task.antScriptFile.set(getAntScriptFile())
            task.exporterDir.set(exporterDir.absolutePath)
            task.moduleFolders.addAll(settings.moduleFolders.map { it.absolutePath })
        }

        val antDependencies = project.configurations.create("metamodel-ant-dependencies")
        project.dependencies.add(antDependencies.name, "org.apache.ant:ant-junit:1.10.12")

        val exportedLanguagesDir = getBuildOutputDir().resolve("exported-languages")
        val exportMetaModelFromMps = project.tasks.register("exportMetaModelFromMps", JavaExec::class.java) { task ->
            task.workingDir = getBuildOutputDir()
            task.mainClass.set("org.apache.tools.ant.launch.Launcher")
            task.classpath(antDependencies)

            val mpsHome = getMpsHome()
            val antVariables = listOf(
                "mps.home" to mpsHome.absolutePath,
                "mps_home" to mpsHome.absolutePath,
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
            }
        }
        val generateMetaModelSources = project.tasks.register("generateMetaModelSources", GenerateMetaModelSources::class.java) {task ->
            task.dependsOn(exportMetaModelFromMps)
        }
        project.afterEvaluate {
            generateMetaModelSources.configure { task ->
                settings.kotlinDir?.let { task.kotlinOutputDir.set(it) }
                settings.typescriptDir?.let { task.typescriptOutputDir.set(it) }
                task.includedNamespaces.addAll(settings.includedLanguageNamespaces)
                task.includedLanguages.addAll(settings.includedLanguages)
                task.includedConcepts.addAll(settings.includedConcepts)
                task.exportedLanguagesDir.set(exportedLanguagesDir)
                settings.registrationHelperName?.let { task.registrationHelperName.set(it) }
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

    private fun getMpsHome(checkExistence: Boolean = false): File {
        val mpsHome = this.settings.mpsHome
        require(mpsHome != null) { "'mpsHome' is not set in the 'metamodel' settings" }
        if (checkExistence) {
            require(mpsHome.exists()) { "'mpsHome' doesn't exist: ${mpsHome.absolutePath}" }
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
