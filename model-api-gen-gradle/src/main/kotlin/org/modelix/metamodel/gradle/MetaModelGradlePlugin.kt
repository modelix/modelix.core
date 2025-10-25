package org.modelix.metamodel.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.provider.Provider
import org.modelix.buildtools.runner.MPSRunnerConfig
import org.modelix.gradle.mpsbuild.MPSBuildPlugin
import org.modelix.gradle.mpsbuild.MPSBuildSettings
import org.modelix.gradle.mpsbuild.RunMPSTask
import java.io.File
import java.net.URL
import java.util.Enumeration
import java.util.Properties
import javax.inject.Inject

class MetaModelGradlePlugin @Inject constructor(val project: Project) : Plugin<Project> {
    private lateinit var settings: MetaModelGradleSettings
    private var buildDir = project.layout.buildDirectory

    override fun apply(project: Project) {
        this.settings = project.extensions.create("metamodel", MetaModelGradleSettings::class.java)
        val mpsBuildPlugin: MPSBuildPlugin = project.plugins.apply(MPSBuildPlugin::class.java)

        val exporterDependencies = project.configurations.create("metamodel-mps-dependencies")
        val modelixCoreVersion = readModelixCoreVersion() ?: throw RuntimeException("modelix.core version not found")
        project.dependencies.add(exporterDependencies.name, "org.modelix.mps:metamodel-export:$modelixCoreVersion")

        val configProvider: Provider<MPSRunnerConfig> = DefaultProvider {
            getMpsHome()?.let { mpsHome ->
                project.extensions.configure(MPSBuildSettings::class.java) { mpsBuildSettings ->
                    mpsBuildSettings.mpsHome = mpsHome.absolutePath
                }
            }
            MPSRunnerConfig(
                mainClassName = "org.modelix.metamodel.export.CommandlineExporter",
                mainMethodName = if (settings.includedModules.isNotEmpty()) "exportBoth" else "exportLanguages",
                classPathElements = exporterDependencies.incoming.files.toList(),
                mpsHome = getMpsHome(),
                additionalModuleDependencies = listOf(
                    "c72da2b9-7cce-4447-8389-f407dc1158b7(jetbrains.mps.lang.structure)",
                    "ceab5195-25ea-4f22-9b92-103b95ca8c0c(jetbrains.mps.lang.core)",
                ),
                additionalModuleDirs = settings.moduleFolders,
                workDir = getBuildOutputDir().get().asFile,
                buildDir = getBuildOutputDir().get().asFile,
                jvmArgs = listOfNotNull(
                    "-Xmx${settings.mpsHeapSize}",
                    ("-Dmodelix.export.includedModules=" + settings.includedModules.joinToString(",")).takeIf { settings.includedModules.isNotEmpty() },
                ),
            )
        }
        val exportedLanguagesDir = getBuildOutputDir().map { it.dir("exported-languages") }
        val exportMetaModelFromMps = configProvider.map { config ->
            mpsBuildPlugin.createRunMPSTask(
                "exportMetaModelFromMps",
                config,
                (settings.taskDependencies).toTypedArray(),
                RunMPSTask::class.java,
            ).also {
                it.configure { task ->
                    task.enabled = settings.jsonDir == null
                    task.workingDir(getBuildOutputDir())
                    task.inputs.property("coreVersion", modelixCoreVersion)
                    task.outputs.dir(project.layout.buildDirectory.dir("metamodel/exported-languages"))
                    task.outputs.dir(project.layout.buildDirectory.dir("metamodel/exported-modules"))
                }
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
                task.includeTypescriptBarrels.set(settings.includeTypescriptBarrels)
                task.includedNamespaces.addAll(settings.includedLanguageNamespaces)
                task.includedLanguages.addAll(settings.includedLanguages)
                task.includedConcepts.addAll(settings.includedConcepts)
                if (settings.jsonDir != null) {
                    task.exportedLanguagesDir.set(settings.jsonDir)
                } else {
                    task.exportedLanguagesDir.set(exportedLanguagesDir)
                }
                settings.registrationHelperName?.let { task.registrationHelperName.set(it) }
                settings.conceptPropertiesInterfaceName?.let { task.conceptPropertiesInterfaceName.set(it) }
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

    private fun getBuildOutputDir() = buildDir.map { it.dir("metamodel") }

    private fun getAntScriptFile() = getBuildOutputDir().map { it.file("export-languages.xml") }

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
