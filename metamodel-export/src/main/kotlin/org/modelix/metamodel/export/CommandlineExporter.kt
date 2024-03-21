package org.modelix.metamodel.export

import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.tool.environment.Environment
import java.io.File

object CommandlineExporter {
    @JvmStatic
    fun exportLanguages(ideaEnvironment: Environment?) {
        println("exportLanguages")
        val repo = MPSModuleRepository.getInstance()
        repo.modelAccess.runReadAction {
            val modules = repo.modules
            val languages = modules.filterIsInstance<Language>()
            val outputDir = File("exported-languages")
            outputDir.mkdirs()
            println("Exporting ${languages.count()} languages into ${outputDir.absolutePath}")
            val exporter = MPSMetaModelExporter(outputDir)
            for (language in languages) {
                exporter.exportLanguage(language)
            }
        }
    }

    @JvmStatic
    fun exportBoth(ideaEnvironment: Environment?) {
        println("exportBoth")
        exportLanguages(ideaEnvironment)
        exportModules(ideaEnvironment)
    }

    @JvmStatic
    fun exportModules(ideaEnvironment: Environment?) {
        println("exportModules")
        val filter = System.getProperty("modelix.export.includedModules")
        println("modules filter: $filter")
        if (filter == null) {
            return
        }
        val filters = filter.split(',').filter { it.isNotEmpty() }
        if (filters.isEmpty()) {
            return
        }

        val repo = MPSModuleRepository.getInstance()
        repo.modelAccess.runReadAction {
            val modules = repo.modules
            val outputDir = File("exported-modules")
            outputDir.mkdirs()
            val exporter = MPSModelExporter(outputDir)
            for (module in modules) {
                val moduleName = module.moduleName ?: continue
                if (filters.any { moduleName == it || moduleName.startsWith((if (it.endsWith(".")) it else "$it.")) }) {
                    exporter.exportModule(module)
                }
            }
        }
    }
}
